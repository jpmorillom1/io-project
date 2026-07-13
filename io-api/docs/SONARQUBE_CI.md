# SonarQube + JaCoCo — pipeline de análisis de calidad

> Documenta el pipeline de CI de este repo (`.github/workflows/sonarqube.yml`) y sirve de
> **guía reutilizable** para conectar cualquier proyecto futuro al mismo servidor SonarQube.

---

## 1. Qué es SonarQube y qué mide

SonarQube es un servidor de **análisis estático de código**: lee el código fuente (sin
ejecutarlo) y lo evalúa contra cientos de reglas por lenguaje. Cada análisis produce:

| Métrica | Qué significa |
|---|---|
| **Bugs** | Código que probablemente se comporta mal en runtime (NPE, condiciones siempre falsas...) |
| **Vulnerabilities** | Problemas de seguridad (inyección, secretos hardcodeados, XSS...) |
| **Code Smells** | Código que funciona pero es difícil de mantener (métodos enormes, duplicación, complejidad) |
| **Coverage** | % de líneas/ramas ejecutadas por los tests — **Sonar no lo calcula: lo importa** de un reporte externo (JaCoCo, lcov) |
| **Duplications** | Bloques de código copiados/pegados |
| **Cyclomatic Complexity** | Nº de caminos de ejecución independientes (ver §5) |

### El Quality Gate

Es el umbral de aprobación. El **por defecto ("Sonar way")** evalúa solo el **código
nuevo** de cada análisis: cobertura de código nuevo ≥ 80 %, 0 bugs nuevos, duplicación
nueva < 3 %, etc. Esto significa que el código histórico sin cobertura **no bloquea**
el pipeline — pero todo lo que escribas de ahora en adelante debe llegar con tests.
Si el gate falla, el paso `sonarqube-quality-gate-action` pone el workflow en rojo.

---

## 2. Arquitectura del pipeline de este repo

Tres archivos cooperan; ninguno funciona solo:

```
io-project/
├── .github/workflows/sonarqube.yml    ← CUÁNDO y CÓMO corre el análisis
├── sonar-project.properties           ← QUÉ se analiza y dónde están los insumos
└── io-api/build.gradle                ← genera el insumo de cobertura (JaCoCo)
```

### Flujo completo

```
git push a develop/master (o PR hacia master)
   │
   ▼
GitHub Actions levanta una VM Ubuntu limpia
   │
   ├─ 1. checkout con fetch-depth: 0        (historial completo → Sonar detecta "código nuevo")
   ├─ 2. setup JDK 21 + Gradle con cache
   ├─ 3. ./gradlew test jacocoTestReport -PciSkipContextTests
   │       └─ compila io-api, corre tests unitarios,
   │          JaCoCo escribe build/reports/jacoco/test/jacocoTestReport.xml
   ├─ 4. sonarqube-scan-action
   │       └─ lee sonar-project.properties, analiza io-api (Java) + io-ui (TS)
   │          en UN solo proyecto (g9-io-project), importa el XML de JaCoCo
   │          y sube todo al servidor
   └─ 5. sonarqube-quality-gate-action
           └─ espera el veredicto del servidor: ✅ verde / ❌ rojo
```

**El orden 3 → 4 es obligatorio**: Sonar no compila ni ejecuta tests; solo analiza.
Si el scan corriera antes que Gradle, no habría `.class` (el análisis Java fallaría)
ni XML de JaCoCo (cobertura 0 %).

### Decisiones específicas de este repo

- **Un solo proyecto Sonar (`g9-io-project`) para backend + frontend.** Se logra usando
  el scanner CLI desde la raíz con `sonar.sources=io-api/src/main/java,io-ui/src`.
  La alternativa (plugin Gradle de Sonar) solo analiza el módulo Java.
- **`-PciSkipContextTests`**: excluye `IoApiApplicationTests` en CI porque levanta el
  contexto Spring completo y necesitaría PostgreSQL, ChromaDB y la API key de Groq,
  que no existen en la VM. Los demás tests son unitarios puros. Localmente
  `gradle test` sigue corriendo todo. (Definido en `io-api/build.gradle`.)
- **io-ui aparece con cobertura 0 %**: no tiene tests todavía. Cuando los tenga
  (vitest), ver §6.4.

### `sonar-project.properties` comentado

```properties
sonar.projectKey=g9-io-project            # ID único en el servidor (inmutable)
sonar.projectName=g9-io-project           # nombre visible (puede cambiar)

sonar.sources=io-api/src/main/java,io-ui/src   # código de producción de ambos módulos
sonar.tests=io-api/src/test/java               # código de test (reglas distintas)

sonar.java.source=21
sonar.java.binaries=io-api/build/classes/java/main   # OBLIGATORIO para Java:
sonar.java.test.binaries=io-api/build/classes/java/test  # sin .class no hay análisis

# Cobertura: Sonar IMPORTA este XML, no la calcula él
sonar.coverage.jacoco.xmlReportPaths=io-api/build/reports/jacoco/test/jacocoTestReport.xml

sonar.exclusions=**/node_modules/**,**/dist/**,**/build/**,**/*.d.ts
```

---

## 3. JaCoCo — qué es y cómo está configurado

**JaCoCo** (Java Code Coverage) mide qué líneas y ramas del código de producción se
ejecutaron durante los tests. Se engancha a la JVM como *agente* e instrumenta el
bytecode: marca cada línea y cada rama (`if`/`else`) por la que pasa la ejecución.

Configuración en `io-api/build.gradle`:

```groovy
plugins {
    id 'jacoco'                          // plugin incluido en Gradle, sin dependencia externa
}

jacoco {
    toolVersion = '0.8.13'               // versión con soporte de bytecode Java 21
}

tasks.named('test') {
    useJUnitPlatform()
    finalizedBy tasks.jacocoTestReport   // al terminar los tests, genera el reporte solo
    if (project.hasProperty('ciSkipContextTests')) {
        exclude '**/IoApiApplicationTests*'   // ver §2: test de contexto, no corre en CI
    }
}

jacocoTestReport {
    dependsOn tasks.test
    reports {
        xml.required = true    // ← el formato que SonarQube consume
        html.required = true   // ← para humanos: build/reports/jacoco/test/html/index.html
    }
}
```

Salidas (tras `gradle test`):
- `io-api/build/reports/jacoco/test/jacocoTestReport.xml` → lo lee Sonar
- `io-api/build/reports/jacoco/test/html/index.html` → ábrelo en el navegador para ver
  clase por clase las líneas verdes (cubiertas) y rojas (no cubiertas)

---

## 4. Cómo leer el reporte de cobertura

Tres columnas en Sonar:

- **Coverage**: % combinado de líneas y ramas ejecutadas por tests.
- **Uncovered Lines**: líneas que ningún test tocó jamás.
- **Uncovered Conditions**: ramas cubiertas *a medias* — el test ejecutó el `if` pero
  solo por un lado (solo `true` o solo `false`). Un archivo puede tener 84 % de
  cobertura y aun así muchas condiciones descubiertas: el código corre, pero los
  casos borde no están verificados.

**El % global engaña si no miras la distribución.** En este repo (~62 % global):

| Capa | Cobertura | Lectura |
|---|---|---|
| `domain` (solvers) | 84–99 % | Lo más complejo es lo mejor testeado ✅ |
| `application` (services) | 66–71 % | Fachadas simples, ramas de despacho sin test |
| `infrastructure` con tests | 78–92 % | Controllers de Transporte/Redes, cadena HITL |
| `infrastructure/ai` | ~0 % | Testearlo exige mockear el LLM; el test de contexto se excluye en CI |
| `io-ui` completo | 0 % | No existen tests de frontend (no es que fallen) |

Regla de oro: **la cobertura solo duele donde el código es complejo Y no hay tests**.
Cruzar este reporte con el de complejidad ciclomática (§5) te dice dónde invertir.

---

## 5. Cómo leer la complejidad ciclomática

Cuenta los **caminos de ejecución independientes**: 1 base + 1 por cada `if`, `for`,
`while`, `case`, `catch`, `&&`, `||`, `?:`. Cada camino es un escenario que puede
fallar y que un test debería cubrir.

- **El total del proyecto no dice nada** — crece con el tamaño del repo. Importa la
  distribución y, sobre todo, la complejidad **por función** (Sonar levanta issue a
  partir de ~15 por función, no por archivo).
- Un solver con complejidad 80 repartida en 12 métodos de ~7 está sano; un solo
  método de 80 no.
- **Complejidad esencial vs accidental**: un algoritmo (Simplex, MODI) *es* un árbol
  de decisiones — su complejidad es legítima. Un formulario o un builder de grafo
  con complejidad 90–100 es accidental: candidato a dividir en estrategias por caso
  o a validación declarativa (p. ej. esquemas Zod discriminados).
- Dónde verlo por función: en Sonar, archivo → pestaña **Measures** → Complexity;
  o **Issues** filtrando por la regla *"Cognitive Complexity of functions should
  not be too high"*.

| Complejidad por función | Lectura |
|---|---|
| 1–10 | Sana, testeable |
| 11–20 | Aceptable si es un algoritmo cohesivo |
| 21+ | Partir en métodos privados o mapa de estrategias |

---

## 6. GUÍA: conectar un proyecto nuevo al servidor SonarQube

### 6.1 Requisitos (una sola vez por proyecto)

1. **Crear el proyecto en el servidor**: SonarQube → *Projects → Create Project →
   Manually*. Elige un `projectKey` único (p. ej. `g9-<nombre>`) — es inmutable.
2. **Generar un token**: *My Account → Security → Generate Tokens* (tipo *Project
   Analysis Token* o *Global*). Se muestra UNA vez; guárdalo.
3. **Configurar los secrets en GitHub**: repo → *Settings → Secrets and variables →
   Actions → New repository secret*:
   - `SONAR_TOKEN` = el token del paso 2
   - `SONAR_HOST_URL` = URL del servidor **CON esquema**: `https://sonar.midominio.com`
     (sin `https://` el scanner falla con `Expected URL scheme 'http' or 'https'
     but no scheme was found` — error real que ya nos pasó)

### 6.2 Reglas universales del workflow (cualquier stack)

- `actions/checkout@v4` **siempre con `fetch-depth: 0`** — sin historial completo,
  Sonar no puede calcular "código nuevo" y el Quality Gate pierde sentido.
- **Compilar y testear ANTES del scan** — Sonar solo analiza; los `.class` y los
  reportes de cobertura deben existir cuando el scanner arranca.
- El Quality Gate va al final con `SonarSource/sonarqube-quality-gate-action@v1`
  y `timeout-minutes: 5`.
- Triggers recomendados: `push` a las ramas de trabajo (`develop`) y principal,
  `pull_request` hacia la principal. Recuerda: **Actions solo ve pushes**, no
  commits locales.

### 6.3 Receta A — proyecto solo Java/Gradle

Usa el **plugin Gradle de Sonar** (entiende el proyecto solo, sin properties file):

```groovy
// build.gradle
plugins {
    id 'java'
    id 'jacoco'
    id 'org.sonarqube' version '6.3.1.5724'   // 6.3+ requerido para Gradle 9
}

jacoco { toolVersion = '0.8.13' }

tasks.named('test') {
    useJUnitPlatform()
    finalizedBy tasks.jacocoTestReport
}

jacocoTestReport {
    dependsOn tasks.test
    reports { xml.required = true; html.required = true }
}

sonar {
    properties {
        property 'sonar.projectKey', 'g9-mi-proyecto'
        property 'sonar.projectName', 'g9-mi-proyecto'
    }
}

tasks.named('sonar') { dependsOn tasks.jacocoTestReport }
```

```yaml
# .github/workflows/sonarqube.yml
name: SonarQube Analysis
on:
  push:
    branches: [master, main, develop]
  pull_request:
    branches: [master, main]

jobs:
  sonarqube:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - name: Test + cobertura + análisis
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
        run: |
          chmod +x gradlew
          ./gradlew test jacocoTestReport sonar
      - uses: SonarSource/sonarqube-quality-gate-action@v1
        timeout-minutes: 5
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        with:
          scanMetadataReportFile: build/sonar/report-task.txt
```

> El plugin Gradle lee `SONAR_TOKEN`/`SONAR_HOST_URL` del entorno automáticamente.
> Alternativa sin el paso del gate: añadir `-Dsonar.qualitygate.wait=true` al
> comando `sonar` (Gradle falla directamente si el gate no pasa).

### 6.4 Receta B — proyecto solo frontend (React/Vite/Node)

Scanner CLI + `sonar-project.properties` en la raíz:

```properties
sonar.projectKey=g9-mi-front
sonar.projectName=g9-mi-front
sonar.sources=src
sonar.exclusions=**/node_modules/**,**/dist/**,**/*.d.ts
sonar.sourceEncoding=UTF-8
# Con tests (vitest/jest + coverage):
sonar.javascript.lcov.reportPaths=coverage/lcov.info
sonar.tests=src
sonar.test.inclusions=**/*.test.ts,**/*.test.tsx
```

```yaml
jobs:
  sonarqube:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm
      - run: npm ci
      # genera coverage/lcov.info ANTES del scan (requiere @vitest/coverage-v8
      # y "test": "vitest run" en package.json)
      - run: npx vitest run --coverage --coverage.reporter=lcov
      - uses: SonarSource/sonarqube-scan-action@v6
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
      - uses: SonarSource/sonarqube-quality-gate-action@v1
        timeout-minutes: 5
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
```

### 6.5 Receta C — monorepo backend + frontend, UN proyecto Sonar (este repo)

Es la combinación de A y B pero con **scanner CLI desde la raíz** (el plugin Gradle
no puede analizar el frontend). Gradle solo compila y genera cobertura; el scan lo
hace la action leyendo un `sonar-project.properties` en la raíz con las fuentes de
ambos módulos. **Este repo es el ejemplo vivo** — copiar:

- `sonar-project.properties` (raíz) — ver §2
- `.github/workflows/sonarqube.yml`
- el bloque JaCoCo de `io-api/build.gradle` — ver §3

Adaptar: `projectKey`, rutas de los módulos y, si el backend usa Maven:
`mvn verify` (con el plugin `jacoco-maven-plugin`) y
`sonar.java.binaries=back/target/classes`,
`sonar.coverage.jacoco.xmlReportPaths=back/target/site/jacoco/jacoco.xml`.

> Alternativa: dos proyectos Sonar separados (un job por módulo, cada uno con su
> `projectKey`). Ventaja: Quality Gates independientes. Desventaja: dos dashboards.
> Para proyectos de un solo equipo, un proyecto unificado es más simple.

### 6.6 Errores frecuentes y su causa

| Síntoma | Causa | Solución |
|---|---|---|
| `Expected URL scheme 'http' or 'https' but no scheme was found for /api/v...` | Secret `SONAR_HOST_URL` vacío, inexistente o sin `https://` | Crear/corregir el secret con la URL completa |
| `Not authorized` / 401 | `SONAR_TOKEN` inválido, expirado o de otro proyecto | Regenerar token y actualizar el secret |
| Análisis Java falla: *"Please provide compiled classes"* | Falta `sonar.java.binaries` o el scan corrió antes de compilar | Compilar antes; apuntar `sonar.java.binaries` a los `.class` |
| Cobertura 0 % teniendo tests | El scan no encontró el XML/lcov (ruta mal o se generó después) | Verificar ruta en `*.reportPaths` y el orden de pasos |
| Quality Gate siempre "passed" aunque el código es malo | El gate por defecto solo mira **código nuevo**; el primer análisis no tiene "nuevo" | Es el comportamiento esperado; se activa desde el 2º análisis |
| Tests fallan en CI pero pasan localmente | Tests que necesitan servicios (BD, APIs) inexistentes en la VM | Excluirlos con una property de Gradle (patrón `-PciSkipContextTests`, §2) o levantar `services:` en el workflow |
| Archivos que no deberían contar en cobertura (config, UI kit, `main.tsx`) | Todo archivo con líneas ejecutables cuenta | `sonar.coverage.exclusions=...` (excluye de cobertura sin excluir del análisis) |

---

## 7. Referencia rápida de archivos de este repo

| Archivo | Rol |
|---|---|
| `.github/workflows/sonarqube.yml` | Pipeline: triggers, build+tests+JaCoCo, scan, quality gate |
| `sonar-project.properties` (raíz) | projectKey `g9-io-project`, fuentes de io-api + io-ui, ruta del XML de JaCoCo |
| `io-api/build.gradle` | Plugin `jacoco` + reporte XML + exclusión `-PciSkipContextTests` |
| Secrets del repo en GitHub | `SONAR_TOKEN`, `SONAR_HOST_URL` |
