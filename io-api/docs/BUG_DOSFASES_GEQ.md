# Reporte de bug — `DosFasesSolver` viola restricciones GEQ

> **Severidad:** Alta (produce resultados **incorrectos**, no un error visible)
> **Estado:** Abierto — sin corregir
> **Descubierto:** 2026-07-07, al implementar el módulo PL Entera (Branch & Bound)
> **Componente:** `domain/lp/dosfases/DosFasesSolver`
> **Impacto:** endpoint `POST /api/v1/lp/dos-fases` y tool `resolverDosFases` del chat

---

## 1. Resumen

Para ciertos modelos con restricciones **≥ (GEQ)**, `DosFasesSolver` devuelve una solución que
**viola esas mismas restricciones** y reporta `status = OPTIMO`/`MULTIPLE_OPTIMO`. El solver no
lanza ninguna excepción ni marca el problema como infactible: entrega con confianza una respuesta
numéricamente errónea.

`GranMSolver`, resolviendo exactamente el mismo modelo, devuelve la solución **correcta**. Esto
descarta que el modelo sea infactible o esté mal planteado: es un defecto del algoritmo de Dos Fases.

---

## 2. Reproducción mínima

Modelo:

```
MAX  Z = 5·x1 + 4·x2
s.a. 6·x1 + 4·x2 ≤ 24
            x1  ≥ 4
     x1, x2 ≥ 0
```

Región factible: `x1 ≥ 4` y `6·x1 + 4·x2 ≤ 24` ⇒ con `x1 = 4`, `x2 ≤ 0` ⇒ **óptimo real `(4, 0)` con `Z* = 20`**.

### Resultado observado

| Solver | Resultado | ¿Correcto? |
|--------|-----------|-----------|
| `DosFasesSolver` | `MULTIPLE_OPTIMO`, `x1 = 0`, `x2 = 6`, `Z = 24` | ❌ **viola `x1 ≥ 4`** (devuelve `x1 = 0`) |
| `GranMSolver` | `OPTIMO`, `x1 = 4`, `x2 = 0`, `Z = 20` | ✅ correcto |

Un segundo caso con más restricciones (la relajación de un nodo de Branch & Bound):

```
MAX 5·x1 + 4·x2
s.a. 6·x1 + 4·x2 ≤ 24
        x1 + 2·x2 ≤ 6
             x2   ≤ 1
             x1   ≥ 4
```

| Solver | Resultado | ¿Correcto? |
|--------|-----------|-----------|
| `DosFasesSolver` | `MULTIPLE_OPTIMO`, `x1 = 3.333`, `x2 = 1`, `Z = 20.667` | ❌ **viola `x1 ≥ 4`** (devuelve `x1 = 3.333`) |
| `GranMSolver` | `OPTIMO`, `x1 = 4`, `x2 = 0`, `Z = 20` | ✅ correcto |

En ambos casos DosFases ignora por completo la restricción `x1 ≥ 4`.

### Cómo se reprodujo

Ejecución directa de ambos solvers (Java puro, sin Spring) sobre los modelos anteriores:

```java
// MAX 5x1+4x2 s.a 6x1+4x2<=24, x1>=4
ModeloLP m = new ModeloLP(
    List.of("x1","x2"),
    new FuncionObjetivo(List.of(5.0,4.0), TipoObjetivo.MAXIMIZAR),
    List.of(new Restriccion(List.of(6.0,4.0), TipoRestriccion.LEQ, 24),
            new Restriccion(List.of(1.0,0.0), TipoRestriccion.GEQ, 4)));

new DosFasesSolver().resolver(m);  // → x1=0, x2=6  (INCORRECTO, viola x1>=4)
new GranMSolver().resolver(m);     // → x1=4, x2=0  (correcto)
```

---

## 3. Síntoma colateral que lo hizo evidente

El módulo PL Entera (Branch & Bound) ramifica añadiendo restricciones `x_i ≥ ⌈v⌉` (GEQ). Cuando
B&B usaba `DosFasesSolver` como motor de relajación, un nodo con rama `x1 ≥ 4` recibía de vuelta
una solución con `x1 = 3.333` (que viola la rama). B&B interpretaba ese `3.333` como fraccionario,
volvía a ramificar en `x1 ≤ 3` / `x1 ≥ 4`, y el hijo `x1 ≥ 4` devolvía otra vez `3.333`: **bucle
infinito de ramificación** que solo terminaba al agotar el tope de nodos, colgando el proceso.

Al cambiar el motor de B&B a `GranMSolver`, el problema desapareció y B&B converge correctamente.

---

## 4. Hipótesis de causa raíz (a confirmar)

El defecto está en la Fase 2 de `DosFasesSolver`, no en el planteamiento. Candidatos a revisar,
en orden de sospecha:

1. **Restauración de la fila z al iniciar la Fase 2.** Tras la Fase 1 se restituye el objetivo
   original y se eliminan las variables básicas de la fila z. Si una variable **artificial** queda
   básica a valor 0 (degeneración típica de las GEQ) y no se expulsa antes de la Fase 2, la fila z
   puede quedar inconsistente y permitir soluciones que abandonan la restricción GEQ.
2. **Exclusión de artificiales en Fase 2.** `findEnterColExcluding` ignora las columnas de
   artificiales, pero si una artificial sigue en la **base** (no solo como columna candidata),
   podría estar “cubriendo” la restricción GEQ y permitir que su surplus tome un valor que la viole.
3. **Signo del surplus en GEQ.** En `buildTableau`, la columna de holgura/surplus de una GEQ se
   fija en `-1`. Conviene verificar que el manejo del surplus negativo junto con la artificial
   mantenga la factibilidad al pivotar en Fase 2.

Dato clave: los reportes son `MULTIPLE_OPTIMO`, lo que sugiere que el solver cree tener costo
reducido 0 en columnas que en realidad romperían la factibilidad — coherente con una fila z mal
restaurada tras la Fase 1.

---

## 5. Workaround aplicado

El módulo PL Entera (`domain/entera/branchandbound/BranchAndBoundSolver`) usa **`GranMSolver`**
—no `DosFasesSolver`— como motor de relajación de cada nodo, precisamente porque las ramas GEQ
disparan este bug. `GranMSolver` da resultados correctos en todos los casos probados.

---

## 6. Recomendaciones

1. **Añadir tests de regresión** con GEQ que actualmente fallan, p. ej. en `DosFasesSolverTest`:
   - `MAX 5x1+4x2 s.a 6x1+4x2≤24, x1≥4` ⇒ esperar `(4,0)`, `Z=20`.
   - Un caso todo-GEQ de minimización con óptimo conocido, comparado contra `GranMSolver`.
   - **Property test:** para modelos aleatorios factibles, la solución de DosFases debe satisfacer
     TODAS las restricciones (`Σ aᵢⱼ·xⱼ` vs `rhs` según el tipo) dentro de una tolerancia.
2. **Comparar DosFases vs GranM** sobre un lote de modelos con GEQ/EQ: deben coincidir en `Z*`.
3. **Corregir la Fase 2** según la hipótesis confirmada (probable: expulsar artificiales básicas
   degeneradas antes de restaurar el objetivo, y re-verificar la eliminación de básicas en la fila z).
4. Mientras no se corrija, **considerar que `resolverDosFases` puede dar resultados erróneos con
   GEQ**; el tutor y la UI deberían preferir Gran M para modelos con ≥ hasta que se resuelva, o
   como mínimo advertirlo.

---

## 7. Verificación tras el fix

- Los dos modelos de la §2 deben devolver `(4,0)`, `Z=20` con `DosFasesSolver`.
- `DosFasesSolver` y `GranMSolver` deben coincidir en `status` y `Z*` en el lote de regresión.
- (Opcional) Re-apuntar `BranchAndBoundSolver` a `DosFasesSolver` y confirmar que sigue convergiendo
  — aunque no es necesario: Gran M es un motor válido para B&B.
