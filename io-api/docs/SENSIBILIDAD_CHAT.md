# Análisis de sensibilidad explicado en términos del problema (IMPLEMENTADO)

> **Estado: IMPLEMENTADO.** Se ejecutó la §3 completa **y** la revisión de la §6 que la
> persistencia habilitaba: los datos llegan al tutor por **dos vías**, no una.
>
> 1. **Al resolver** — `ResolucionEjecutor.formatearTabular(...)` inyecta el bloque
>    `ANÁLISIS DE SENSIBILIDAD` en el resumen `[SISTEMA]`, justo antes del detalle de
>    iteraciones (sobrevive mejor a un truncado). Solo en `OPTIMO`/`MULTIPLE_OPTIMO`.
> 2. **En cualquier turno posterior** — `@Tool explicarSensibilidad()` (`SensibilidadTool`)
>    relee el último `problema_resuelto` de la sesión desde PostgreSQL y vuelve a redactar
>    el bloque. **Esto elimina el riesgo de desalojo de la §4**: ya no importa que el mensaje
>    `[SISTEMA]` se haya caído de la ventana de 14 mensajes. La tool **no pasa por la compuerta
>    HITL** — no ejecuta ningún solver, solo lee un resultado que el humano ya aprobó.
>
> Piezas nuevas:
> - `infrastructure/ai/sensibilidad/SensibilidadFormatter` — el texto (estático, sin estado).
>   Único sitio donde vive el formato; lo consumen las dos vías.
> - `infrastructure/ai/sensibilidad/SensibilidadService` — relectura desde `problema_resuelto`.
>   Cuando no hay nada que explicar (aún no se resolvió, fue método gráfico, fue otro módulo,
>   o el resultado fue infactible/no acotado) devuelve un texto que **se lo dice al LLM
>   explícitamente** en vez de dejarlo inventar.
> - `infrastructure/ai/tools/SensibilidadTool` — la `@Tool`. Registrada en `plSubAgent` y en
>   `tutorAiService`. Es la **única tool del proyecto sin parámetros**: el `sesionId` viaja por
>   el `ChatContextStore`, no por el esquema JSON.
> - `TutorSupervisorService` enruta a PL por palabra clave ("sensibilidad", "precio(s) sombra",
>   "holgura"): el análisis post-óptimo solo existe en LP, y dejar que el clasificador LLM lo
>   mandara a otro módulo habría perdido el resultado.
>
> El **mapeo semántico** (`x1`→"mesas") lo sigue infiriendo el LLM desde el enunciado —no se
> añadió glosario, la alternativa fuerte que planteaba la §6.2. Sigue disponible si hace falta.

---

## 1. Problema que resuelve

Cuando el estudiante pide en el chat "explícame el análisis de sensibilidad" después de resolver
un LP, el tutor hoy **no puede responder con datos reales**: los inventa.

La causa es concreta. Los tres solvers tabulares (Simplex, Gran M, Dos Fases) sí calculan
holguras, precios sombra y rangos vía `SensibilidadCalculator`, y esos datos viajan íntegros al
frontend dentro de `ChatResponse.resultado`. Pero el **único texto que llega al LLM** es
`ResolucionEjecutor.formatearTabular(...)`, que solo vuelca `valorOptimo` y `valores`. Nunca
invoca `solution().holguras()`, `.preciosSombra()` ni `.rangosSensibilidad()` — de hecho esos
accessors no aparecen en ningún punto de `infrastructure/ai`.

A eso se suma que **ningún system prompt menciona sensibilidad, holguras ni precios sombra**.
Lo único que existe es material teórico en el corpus RAG (`corpus/lp/04_interpretacion_de_resultados.md`),
que solo se recupera si la pregunta se parece semánticamente al chunk (`minScore 0.5`) — no es garantía.

**Resultado buscado:** que el tutor explique la sensibilidad con los números correctos y
**exclusivamente en el vocabulario del enunciado** ("las horas de carpintería están agotadas;
cada hora extra te daría $2.50 más de utilidad, y eso vale hasta 140 horas"), sin usar `x1`, `Z`,
`R1`, `s1` ni jerga como "holgura" o "precio sombra" como sujeto de la frase.

### Decisiones de alcance tomadas

- El mapeo semántico (`x1`→"mesas") lo infiere el LLM desde el enunciado que sigue en su memoria
  conversacional. No se añade glosario ni parámetros nuevos a ninguna `@Tool` (evita el riesgo de
  `tool_use_failed` de Groq documentado en CLAUDE.md §9).
- Los datos se entregan ampliando el resumen post-resolución que ya se inyecta como `[SISTEMA]`.
- La UI (`ResultBanner.tsx`) no se toca: sus tablas técnicas con claves `x1`/`R1` siguen como
  referencia formal.

---

## 2. Trampa de correctitud (lo más importante de este documento)

Las holguras y los precios sombra **se indexan distinto**, y un formateador ingenuo produciría
explicaciones falsas.

En `SensibilidadCalculator`:
- `preciosSombra` → clave `"R"+(i+1)` para **las m restricciones**.
- `rangosSensibilidad.rhs()` → lista alineada por índice `i`, una entrada por restricción.
- `holguras` → clave `"s"+(++sIdx)`, y `sIdx` **solo avanza cuando `slackCol[i] >= 0`**.

Y en los solvers: `slackCol[i] = (tipo != EQ) ? n + slackIdx++ : -1`.

Es decir: **las restricciones `=` no producen holgura**. Con un modelo `[≤, =, ≥]`, la clave `s2`
corresponde a **R3**, no a R2. Unir `s_i` con `R_i` por número está mal.

La única forma correcta es **reconstruir el mapeo iterando las restricciones con el mismo contador**:

```java
int sIdx = 0;
for (int i = 0; i < modelo.restricciones().size(); i++) {
    Restriccion r = modelo.restricciones().get(i);
    Double precio  = sol.preciosSombra().get("R" + (i + 1));
    RangoRHS rango = sol.rangosSensibilidad().rhs().get(i);   // alineado por índice
    Double holgura = (r.tipo() != TipoRestriccion.EQ)
            ? sol.holguras().get("s" + (++sIdx))              // mismo avance que el solver
            : null;                                            // EQ: no tiene holgura
}
```

Segundo matiz, también pedagógico: para una restricción `≥` esa columna es un **superávit**
(y `calcularHolguras` le aplica `Math.abs`), no una holgura. Significa algo distinto y hay que
decirlo distinto:

| Tipo | Valor 0 significa | Valor > 0 significa |
|---|---|---|
| `≤` | recurso **agotado** — es un cuello de botella | queda recurso **sin usar** |
| `≥` | el mínimo exigido se cumple **justo en el límite** | se **supera** el mínimo por esa cantidad |
| `=` | siempre activa, no tiene sobrante | — |

---

## 3. Cambios

### 3.1 `infrastructure/ai/hitl/ResolucionEjecutor.java` (el grueso del trabajo)

**Cambiar la firma** de `formatearTabular(SolveResult<SolucionLP>, MetodoResolucion)` a que reciba
también el `ModeloLP` — se necesita para el tipo de cada restricción, su álgebra y los nombres de
variables. En la única llamada (línea ~153) la variable `mlp` ya está en alcance.

**Añadir un método privado `formatearSensibilidad(SolucionLP, ModeloLP, TipoObjetivo)`**, invocado
desde `formatearTabular` justo después del bloque de solución óptima y **antes** del detalle de
iteraciones (para que sobreviva mejor si el mensaje se trunca). Solo se emite cuando
`status` es `OPTIMO` o `MULTIPLE_OPTIMO` y `rangosSensibilidad() != null`.

Formato propuesto — redactado ya en registro llano, para que el LLM copie ese tono:

```
--- ANÁLISIS DE SENSIBILIDAD (datos reales del solver) ---
Traduce TODO esto al vocabulario del enunciado. Nunca escribas x1, Z, R1 ni s1 en tu respuesta.

RESTRICCIONES / RECURSOS
  R1: 2.0·x1 + 1.0·x2 <= 100.0
      sin sobrante (recurso agotado, limita la solución)
      1 unidad más del lado derecho cambia el óptimo en +2.5
      ese valor sigue siendo válido mientras el lado derecho esté entre 80.0 y 140.0
  R2: 4.0·x1 + 3.0·x2 <= 240.0
      sobran 15.0 unidades (no limita la solución)
      1 unidad más del lado derecho cambia el óptimo en +0.0
      lado derecho válido entre 225.0 y sin límite superior
  R3: 1.0·x1 + 0.0·x2 = 30.0
      restricción de igualdad: se cumple exacta, no tiene sobrante
      ...

VARIABLES DE DECISIÓN
  x1 = 30.0  (en uso)
      coeficiente actual 5.0; el plan óptimo no cambia mientras esté entre 3.0 y sin límite
  x2 = 0.0   (fuera del plan)
      coeficiente actual 4.0; solo entraría al plan si superara 6.0

COHERENCIA: una restricción con sobrante siempre tiene valor marginal 0, y una sin sobrante
suele tener valor marginal > 0. Úsalo para verificar tu explicación.
```

Detalles de implementación:
- **`null` = infinito** en `RangoCoeficiente.min/max` y `RangoRHS.min/max`. Imprimir
  `"sin límite"` (o `-∞`/`+∞`). Nunca dejar que salga el token `Infinity` — es el gotcha ya
  conocido del proyecto (ver CLAUDE.md §9), y aquí además rompería la lectura del LLM.
- Reusar el helper `round(double)` ya presente en la clase.
- El álgebra de cada restricción se puede formatear con un helper local análogo a
  `AiChatController.formatearTerminos(coefs, vars)` (línea ~286). Considerar extraerlo o duplicarlo
  — es de 8 líneas; duplicarlo mantiene `domain`/`infrastructure` desacoplados y evita tocar el controller.
- La frase de cierre del `switch (metodo)` (rama `default`, "Pregunta al estudiante qué resultado
  esperaba…") debe ampliarse para invitar a la sensibilidad.
- **`MULTIPLE_OPTIMO`**: los rangos siguen siendo válidos, pero conviene advertir al tutor que hay
  otra solución con el mismo óptimo.

Sin cambios en `formatearGrafico` (`SolucionGrafica` no tiene campos de sensibilidad) ni en los
demás formateadores.

### 3.2 `resources/prompts/subagents/pl_system_prompt.txt`

Añadir una sección nueva antes de `REGLAS INAMOVIBLES`:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ANÁLISIS DE SENSIBILIDAD — CÓMO EXPLICARLO
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CUÁNDO: el estudiante pregunta por sensibilidad, holguras, precios sombra, "qué pasa si
tengo más/menos de X", "¿vale la pena comprar más horas?", "¿hasta cuánto puede subir el precio?".

REQUISITO: solo puedes responder si en esta conversación ya llegó un mensaje [SISTEMA] con el
bloque ANÁLISIS DE SENSIBILIDAD. Si no está, dilo con honestidad y ofrece resolver de nuevo.
JAMÁS inventes holguras, precios sombra ni rangos.

REGISTRO OBLIGATORIO — habla del problema, no del modelo:
  ✗ PROHIBIDO como sujeto: x1, x2, Z, R1, s1, "holgura", "precio sombra", "coeficiente",
    "lado derecho", "RHS", "base", "variable no básica".
  ✓ Usa el sustantivo del enunciado y su unidad: "las horas de carpintería", "los kilos de
    acero", "las mesas de roble", "la utilidad en dólares".
  El término técnico solo puede aparecer una vez, entre paréntesis, como etiqueta —
  nunca como sujeto de la frase.

ESTRUCTURA DE LA RESPUESTA (tres bloques, en este orden):
  1. QUÉ SE AGOTÓ Y QUÉ SOBRÓ
     "Las horas de carpintería se usaron por completo: son tu cuello de botella.
      De la madera te sobraron 15 m², así que no te está frenando."
  2. CUÁNTO VALE CONSEGUIR UNA UNIDAD MÁS (y hasta dónde)
     "Cada hora extra de carpintería te daría $2.50 más de utilidad. Te conviene pagar por
      ella cualquier precio menor a $2.50. Ese beneficio se mantiene hasta las 140 horas;
      más allá, el cuello de botella se muda a otro recurso.
      Conseguir más madera no te daría ni un centavo extra: ya te sobra."
  3. CUÁNTO PUEDEN MOVERSE LOS PRECIOS ANTES DE CAMBIAR EL PLAN
     "Mientras cada mesa deje entre $3 y cualquier valor mayor, te sigue conviniendo el mismo
      plan de producción. Las sillas hoy no conviene fabricarlas: solo entrarían si su ganancia
      unitaria superara los $6."

CIERRE SOCRÁTICO: termina con una pregunta que obligue a decidir, no a repetir.
  "Si un proveedor te ofrece 20 horas extra de carpintería a $2 cada una, ¿las tomarías?"

MÉTODO GRÁFICO: no produce análisis de sensibilidad. Si lo piden tras resolver gráficamente,
ofrece volver a resolver con Simplex/Dos Fases para obtenerlo.
```

### 3.3 `infrastructure/ai/AiChatController.java` — una línea

En `mensajeDeDesenlace(...)` (rama aprobada, línea ~243), ampliar la instrucción final para que el
tutor **ofrezca** la sensibilidad. Es la única vía de descubrimiento: la UI no tiene chips de
acción rápida (`ChatPanel.tsx` solo muestra prompts de ejemplo con el chat vacío).

> «…pregunta cómo lo interpreta, ofrece revisar las iteraciones y ofrece explicarle qué recursos
> quedaron como cuello de botella y cuánto valdría conseguir más de cada uno.»

### 3.4 Test — `src/test/java/.../infrastructure/ai/hitl/ResolucionEjecutorTest.java` (nuevo)

`SimplexService`/`GranMService` son delegadores sin estado y se construyen con `new`, así que el
ejecutor se instancia directo (pasando `null` en los use cases que no intervienen).

Casos:
1. **Mapeo con restricciones mixtas `[≤, =, ≥]`** resuelto con Gran M — el caso que rompe el
   zip ingenuo. Afirmar que el sobrante reportado para la tercera restricción es el de `s2`,
   y que la restricción `=` se describe como "no tiene sobrante".
2. Un LP `≤` clásico por Simplex: el resumen contiene el valor marginal de cada restricción y el
   rango de cada coeficiente.
3. **`assertThat(resumen).doesNotContain("Infinity")`** y contiene `"sin límite"` cuando un rango
   es no acotado.
4. `INFACTIBLE` / `NO_ACOTADO` → no se emite bloque de sensibilidad (no hay `solution()`).

---

## 4. Riesgos conocidos

- ~~**Desalojo de memoria.**~~ **RESUELTO.** El mensaje `[SISTEMA]` con la sensibilidad sigue
  saliendo de la ventana de 14 mensajes tras ~7 intercambios, pero ya no es la única fuente:
  `explicarSensibilidad()` relee los números de `problema_resuelto` en cualquier turno posterior.
  Se mantiene además la regla del prompt "si la herramienta dice que no hay datos, dilo y ofrece
  resolver; jamás inventes", que convierte un fallo silencioso (números falsos) en uno visible.
- **Bug preexistente de `DosFasesSolver` con `≥`** (ver `docs/BUG_DOSFASES_GEQ.md`): devuelve
  soluciones que violan restricciones `≥`. Y el prompt manda usar Dos Fases *por defecto* ante `≥`.
  La sensibilidad de una solución incorrecta será incorrecta, así que esta funcionalidad va a
  exhibir el bug justo donde vive. No se corrige aquí, pero conviene decidir si se prefiere Gran M
  como método por defecto para `≥` mientras tanto (es lo que ya hace `BranchAndBoundSolver` por
  esta misma razón).
- Los precios sombra se calculan con los coeficientes del objetivo original. Para un problema de
  **minimización** el signo se lee como "cambio en el costo", no "cambio en la ganancia". El bloque
  debe decir "cambia el óptimo en …" (neutro) e incluir el `TipoObjetivo`, dejando que el tutor
  lo interprete como ganancia o costo según corresponda.

---

## 5. Verificación

**Unitaria (rápida, sin red ni API key):**
```bash
gradle test --tests '*ResolucionEjecutorTest*'
```

**Extremo a extremo (requiere `GROQ_API_KEY` y ChromaDB):**
```bash
docker compose up chromadb -d
gradle bootRun
```
Con el problema clásico de mesas y sillas, para comprobar que la traducción ocurre de verdad:

1. `POST /api/v1/ai/chat` — *"Una carpintería fabrica mesas y sillas. Cada mesa deja $5 de utilidad
   y cada silla $4. Una mesa usa 2 horas de carpintería y 4 m² de madera; una silla, 1 hora y 3 m².
   Hay 100 horas y 240 m² disponibles. ¿Cuánto conviene producir?"*
   → responde con `modeloSugerido` y `solicitudAprobacion`.
2. `POST /api/v1/ai/chat/aprobacion` con `{"solicitudId":"…","aprobado":true}`
   → el tutor explica el resultado y **ofrece** el análisis de sensibilidad.
3. `POST /api/v1/ai/chat` — *"sí, explícame el análisis de sensibilidad"*

**Criterio de aceptación** sobre la respuesta del paso 3:
- Habla de "horas de carpintería", "m² de madera", "mesas", "sillas" y "$ de utilidad".
- **No** aparece `x1`, `x2`, `Z`, `R1`, `s1`, ni "holgura"/"precio sombra" como sujeto.
- Los números coinciden con las tablas que `ResultBanner.tsx` muestra en paralelo (mismo
  `SolveResult`) — comprobación cruzada directa de que no hay alucinación.
- Cierra con una pregunta de decisión.

**Regresión del mapeo `=`:** repetir con un modelo que incluya una restricción de igualdad
en medio (p. ej. "hay que producir exactamente 30 mesas") y verificar que el sobrante que el tutor
atribuye a cada recurso es el correcto, no el corrido en uno.

---

## 6. Cómo cambia este plan con persistencia

Este diseño se eligió bajo la restricción de que **la memoria es RAM y de 14 mensajes**. Cuando
exista `ChatMemoryStore` sobre PostgreSQL, dos decisiones merecen revisarse antes de implementar:

1. **Entrega de los datos.** Con persistencia, guardar el último `SolucionLP` + `ModeloLP` por
   sesión (junto al `sesionId`) deja de ser "arquitectura extra" y pasa a ser natural. Eso permite
   una `@Tool explicarSensibilidad()` que relee el resultado real en cualquier turno posterior, en
   vez de depender de que el mensaje `[SISTEMA]` siga dentro de la ventana. **Elimina por completo
   el riesgo de desalojo** descrito en §4.
   - Punto de enganche: `AprobacionHumanaService.Desenlace` no lleva hoy el `ModeloResoluble`;
     habría que añadirlo (el `solicitud.modelo()` está disponible dentro de `decidir()`, antes del
     `registry.eliminar(...)` del `finally`).
   - La tool **no** necesita compuerta HITL: no ejecuta ningún solver, solo lee un resultado ya
     aprobado.

2. **Mapeo semántico.** Si además se persiste el enunciado en lenguaje natural (hoy no se guarda en
   ningún record ni DTO; solo vive en la ventana de mensajes), el LLM deja de depender de recordarlo
   y el mapeo `x1`→"mesas" se vuelve fiable. La alternativa más fuerte es un **glosario capturado en
   `registrarModeloSugerido`** (`descripcionesVariables`, `nombresRestricciones`, `unidadObjetivo`
   como parámetros `@P(required=false)`), persistido junto al modelo: el mapeo pasa a ser determinista
   y además habilita mostrar etiquetas semánticas en `ResultBanner.tsx` ("R1 · horas de carpintería").

En resumen: **con persistencia, la §3.1 (bloque en el resumen) sigue siendo útil para la explicación
inmediata, pero deja de ser el único vehículo.** Conviene implementarla junto con la tool de relectura.
