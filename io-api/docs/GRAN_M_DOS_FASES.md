# Implementación Gran M y Dos Fases

> Fecha: 2026-06-27  
> Rama: develop

---

## Contexto

El `SimplexSolver` estándar solo aceptaba restricciones `LEQ` (≤) con `b ≥ 0`.
Los métodos **Gran M** y **Dos Fases** extienden la cobertura a restricciones `GEQ` (≥)
y `EQ` (=) introduciendo variables artificiales. Ambos producen `SolveResult<SolucionLP>`,
por lo que no fue necesario cambiar ningún DTO ni la capa frontend.

---

## Archivos nuevos

### Dominio (`domain/lp/`)

#### `granm/GranMSolver.java`
Implementa el método de penalidad Big-M. Java puro, sin Spring.

**Algoritmo:**
1. Valida el modelo — acepta LEQ/GEQ/EQ; requiere `b ≥ 0` en todas
2. Calcula el esquema de columnas del tableau:
   ```
   nSlack = |{i : tipo[i] == LEQ o GEQ}|
   nArt   = |{i : tipo[i] == GEQ o EQ}|

   Columnas: [ x1..xn | s1..s_nSlack | a1..a_nArt | RHS ]

   LEQ → slackCol[i] = n+slackIdx++ (coef +1), artCol[i] = -1
   GEQ → slackCol[i] = n+slackIdx++ (coef -1), artCol[i] = n+nSlack+artIdx++
   EQ  → slackCol[i] = -1,                      artCol[i] = n+nSlack+artIdx++

   Base inicial: artCol[i] si GEQ/EQ, slackCol[i] si LEQ
   ```
3. Construye el tableau con holguras/superávit/artificiales
4. Fija la fila-z: `isMin ? +c : -c` para decisión; `+M` para artificiales (M = 1.000.000)
5. Eliminación inicial: zeroa los artificiales básicos en la fila-z
6. Corre Simplex estándar (Dantzig + razón mínima + pivote Gauss-Jordan)
7. Al terminar: si algún artificial básico tiene valor > ε → `INFACTIBLE`
8. Extrae solución; `hasMultipleOptima` revisa solo columnas `0..n+nSlack-1` (excluye artificiales)

**Convención z-row** (igual que `SimplexSolver`):
- MAX → almacena `-c`; MIN → almacena `+c`.
- Penalidad M siempre es `+M` en ambos sentidos.

---

#### `dosfases/DosFasesSolver.java`
Implementa el método de dos fases. Java puro, sin Spring.

**Algoritmo — Fase 1:**
1. Mismo preprocessing que GranM (holguras/superávit/artificiales)
2. Fila-z Fase 1: `t[m][artCol[i]] = 1.0` para cada artificial; resto = 0
3. Eliminación inicial de artificiales básicos
4. Corre Simplex hasta encontrar `findEnterCol < 0`
5. `w* = -t[m][cols-1]`; si `w* > ε` → `INFACTIBLE`
6. Registra paso "Fase 1 completada — w* = 0"

**Algoritmo — Fase 2:**
7. Restaura fila-z original: `isMin ? +c : -c`; artificiales = 0
8. Elimina variables básicas actuales de la nueva fila-z para consistencia:
   ```
   for i in 0..m-1:
     factor = t[m][base[i]]
     if |factor| > ε: t[m][j] -= factor * t[i][j]  ∀j
   ```
   *(paso crítico: sin esto la fila-z queda inconsistente con la base actual)*
9. Corre Simplex con `findEnterColExcluding(artColSet)` — impide que artificiales entren
10. Extrae solución

**Numeración de pasos:** continua entre Fase 1 y Fase 2. El campo `titulo` incluye
`"Fase 1"` o `"Fase 2"` para que la UI y el tutor puedan distinguirlos.

---

### Aplicación (`application/lp/`)

| Archivo | Contenido |
|---|---|
| `GranMUseCase.java` | Interfaz: `resolver(ModeloLP) → SolveResult<SolucionLP>` |
| `GranMService.java` | `@Service` que delega a `new GranMSolver()` |
| `DosFasesUseCase.java` | Interfaz: `resolver(ModeloLP) → SolveResult<SolucionLP>` |
| `DosFasesService.java` | `@Service` que delega a `new DosFasesSolver()` |

---

### Infraestructura REST (`infrastructure/lp/`)

| Controlador | Endpoint |
|---|---|
| `GranMController.java` | `POST /api/v1/lp/gran-m` |
| `DosFasesController.java` | `POST /api/v1/lp/dos-fases` |

Ambos aceptan `ModeloLP` como `@RequestBody` y devuelven `ResponseEntity<SolveResult<SolucionLP>>`.
El cuerpo de request es idéntico al de `/api/v1/lp/simplex`, pero ahora el campo `"tipo"` en
cada restricción puede ser `"LEQ"`, `"GEQ"` o `"EQ"`.

**Ejemplo de request (Gran M o Dos Fases):**
```json
{
  "variables": ["x1", "x2"],
  "objetivo": { "coeficientes": [3, 2], "tipo": "MAXIMIZAR" },
  "restricciones": [
    { "coeficientes": [1, 1], "tipo": "LEQ", "rhs": 4 },
    { "coeficientes": [1, 3], "tipo": "GEQ", "rhs": 6 }
  ]
}
```

**Ejemplo de respuesta exitosa:**
```json
{
  "status": "OPTIMO",
  "solution": {
    "valores": { "x1": 3.0, "x2": 1.0 },
    "holguras": { "s1": 0.0 },
    "valorOptimo": 11.0,
    "preciosSombra": { "R1": 1.0, "R2": 2.0 },
    "rangosSensibilidad": {
      "coeficientesObjetivo": [
        { "variable": "x1", "valorActual": 3.0, "min": 0.666667, "max": null },
        { "variable": "x2", "valorActual": 2.0, "min": null,     "max": 9.0  }
      ],
      "rhs": [
        { "restriccion": "R1", "valorActual": 4.0, "min": 3.0, "max": 6.0 },
        { "restriccion": "R2", "valorActual": 6.0, "min": 4.0, "max": 8.0 }
      ]
    }
  },
  "steps": [
    { "numero": 0, "titulo": "Tableau inicial (Gran M)", "datos": { "encabezados": [...], "tableau": [...], "base": [...] } },
    { "numero": 1, "titulo": "Iteración 1: entra x2, sale a1", "datos": { "varEntra": "x2", "varSale": "a1", ... } },
    { "numero": 2, "titulo": "Solución óptima encontrada (Gran M)", "datos": { "valorOptimo": 11.0, ... } }
  ]
}
```

---

### AI Tools (`infrastructure/ai/tools/`)

#### `GranMTool.java`
- `@Tool` registrada en el `TutorAiService`
- `RestriccionInput` incluye `TipoRestriccion tipo` (LEQ/GEQ/EQ) — a diferencia de `SimplexTool`
- Escribe a `contextStore.obtener().resultado` (mismo campo que `SimplexTool`)
- El tutor la invoca cuando el estudiante pide **Gran M específicamente**

#### `DosFasesTool.java`
- Mismo patrón que `GranMTool`, delega a `DosFasesUseCase`
- El tutor la invoca como **método predeterminado** para modelos con GEQ/EQ
- `formatearParaTutor` reporta adicionalmente cuántos pasos tuvo cada fase

---

### Archivos modificados

#### `infrastructure/ai/AiConfig.java`
```java
@Bean
public TutorAiService tutorAiService(..., GranMTool granMTool, DosFasesTool dosFasesTool, ...) {
    return AiServices.builder(TutorAiService.class)
            ...
            .tools(simplexTool, sugerirTool, validarTool, granMTool, dosFasesTool)
            .build();
}
```

#### `resources/prompts/tutor_system_prompt.txt`
Cuatro secciones actualizadas:

1. **IDENTIFICAR EL ENFOQUE** — añadido ejemplo para modelos con ≥/=
2. **HERRAMIENTAS DISPONIBLES** — de 3 a 5 herramientas; se documenta cuándo usar `resolverGranM` vs `resolverDosFases`
3. **RESTRICCIONES** — reemplazado `"en desarrollo"` por guía de selección de método
4. **ALCANCE ACTUAL** — Gran M y Dos Fases movidos de EN DESARROLLO a DISPONIBLE

**Regla de selección en el tutor:**
```
Todas ≤           → resolverSimplex
≥ o = + pide Gran M → resolverGranM
≥ o = (default)    → resolverDosFases
```

---

## Tests

### `GranMSolverTest` (5 tests)

| Test | Modelo | Esperado |
|---|---|---|
| `maximizacion_mixta_leq_geq` | MAX 3x1+2x2; x1+x2≤4; x1+3x2≥6 | x1=3, x2=1, z=11 |
| `minimizacion_todo_geq` | MIN x1+2x2; x1+x2≥4; 2x1+x2≥6 | x1=4, x2=0, z=4 |
| `maximizacion_con_restriccion_eq` | MAX 2x1+3x2; x1+x2=4; x1-x2≤2 | x1=0, x2=4, z=12 |
| `infactible_restricciones_contradictorias` | MAX x1+x2; x1+x2≤3; x1+x2≥5 | INFACTIBLE |
| `pasos_incluyen_tableau_inicial_y_varEntra` | modelo mixto | encabezados con "a", paso con varEntra |

### `DosFasesSolverTest` (5 tests)

Los mismos 4 casos numéricos más:

| Test | Qué verifica |
|---|---|
| `pasos_incluyen_fase1_y_fase2_en_orden` | pasos con "Fase 1" preceden a "Fase 2"; numeración continua; paso "completada" existe |
| *(infactible)* | `infactible_fase1_detecta_w_positivo` — hay paso con "infactible" en título |

**Todos los tests pasan.** El único test rojo (`contextLoads`) requiere PostgreSQL activo y es preexistente.

---

## Verificación manual

```bash
# Gran M — modelo con GEQ
curl -X POST http://localhost:8080/api/v1/lp/gran-m \
  -H "Content-Type: application/json" \
  -d '{
    "variables":["x1","x2"],
    "objetivo":{"coeficientes":[3,2],"tipo":"MAXIMIZAR"},
    "restricciones":[
      {"coeficientes":[1,1],"tipo":"LEQ","rhs":4},
      {"coeficientes":[1,3],"tipo":"GEQ","rhs":6}
    ]
  }'
# Esperado: status=OPTIMO, valorOptimo=11.0, x1=3.0, x2=1.0

# Dos Fases — modelo con EQ
curl -X POST http://localhost:8080/api/v1/lp/dos-fases \
  -H "Content-Type: application/json" \
  -d '{
    "variables":["x1","x2"],
    "objetivo":{"coeficientes":[2,3],"tipo":"MAXIMIZAR"},
    "restricciones":[
      {"coeficientes":[1,1],"tipo":"EQ","rhs":4},
      {"coeficientes":[1,-1],"tipo":"LEQ","rhs":2}
    ]
  }'
# Esperado: status=OPTIMO, valorOptimo=12.0, x1=0.0, x2=4.0
```

---

## Notas de diseño

- **Sin tocar `SimplexSolver`**: los métodos privados (`findEnterCol`, `findLeaveRow`, `pivot`, etc.)
  se duplicaron en cada solver nuevo para no arriesgar regresiones en código probado.
- **`hasMultipleOptima` corregido**: en los nuevos solvers solo revisa columnas `0..n+nSlack-1`,
  excluyendo artificiales para evitar falsos positivos de óptimos múltiples.
- **Fase 2 z-row**: el paso de eliminación de variables básicas (antes de correr Fase 2)
  es crítico — sin él la fila-z queda inconsistente con la base actual.
- **Sin cambios en DTOs ni frontend**: `ChatContextStore.DatosRespuesta.resultado` y
  `ChatResponse.resultado` ya eran `SolveResult<SolucionLP>`, compatible con los nuevos solvers.
- **Análisis post-óptimo**: los tres solvers usan `SensibilidadCalculator` para extraer
  holguras, precios sombra y rangos de sensibilidad. Para GEQ/EQ, la columna `B⁻¹·eᵢ`
  se toma del artificial (`artCol[i]`), no del superávit, porque el artificial inicia la base con +1.
