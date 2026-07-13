# Casos especiales en Programación Lineal

## 1. Solución infactible

### Qué significa
No existe ningún punto que satisfaga **simultáneamente** todas las restricciones.
La región factible está vacía.

### Causas frecuentes
- Restricciones contradictorias entre sí.
  Ejemplo: x₁ ≥ 10 y x₁ ≤ 5 son incompatibles.
- Restricciones muy estrictas que no dejan espacio de soluciones.
- Error de modelado: coeficientes o lados derechos incorrectos.

### Cómo se detecta en el Simplex (Dos Fases)
Al usar el método de Dos Fases, al final de la Fase I el valor de la función auxiliar
(que minimiza variables artificiales) no llega a 0. Esto indica que alguna variable
artificial permanece en la base con valor positivo: la solución es infactible.

### Señal visual
La región factible graficada no existe (las semiplanos no tienen intersección).

### Qué reporta el solver
`SolveStatus.INFACTIBLE` — la solución es null, pero los pasos indican en qué
iteración se detectó la infactibilidad.

---

## 2. Problema no acotado (unbounded)

### Qué significa
La función objetivo puede crecer indefinidamente (en maximización) sin violar ninguna
restricción. No existe un máximo finito.

### Causas frecuentes
- Falta una restricción que limite el crecimiento de las variables.
- Error de formulación: se omitió una restricción real del problema.
- El sentido de una restricción está invertido (se puso ≥ donde debería ir ≤).

### Cómo se detecta en el Simplex
Durante la búsqueda de la variable **saliente** (razón mínima), todos los coeficientes
de la columna entrante son ≤ 0. No existe razón mínima válida → el problema es no acotado.

Condición: si en la columna pivote (variable entrante j) todos los aᵢⱼ ≤ 0,
entonces el problema es no acotado.

### Ejemplo
Maximizar Z = 3x₁ + 2x₂ con solo x₁ ≥ 0, x₂ ≥ 0 (sin restricciones de ≤).
Podemos hacer x₁ → ∞, luego Z → ∞. Problema no acotado.

### Qué reporta el solver
`SolveStatus.NO_ACOTADO` — no hay solución óptima.

---

## 3. Soluciones óptimas múltiples (degeneración)

### Qué significa
Existen **infinitos puntos óptimos** con el mismo valor Z*. Toda la arista (segmento)
entre dos vértices óptimos también es óptima.

### Cómo se detecta
Al llegar a la condición de optimalidad (fila z ≥ 0 en todas las columnas), si alguna
variable **no básica** tiene coeficiente exactamente **igual a 0** en la fila z, significa
que esa variable podría entrar a la base sin cambiar el valor de Z.

Ese coeficiente cero indica que hay otra solución básica óptima adyacente.

### Implicación práctica
El modelo tiene varias formas igualmente óptimas de asignar recursos. El decisor puede
elegir entre ellas según criterios secundarios (facilidad operativa, preferencias).

### Ejemplo
Si en la fila z final aparece: `0  0  1/3  0  | 12`
El coeficiente 0 en la columna de x₂ (no básica) indica múltiples óptimos: se puede
incluir x₂ en la base y obtener otra solución con Z = 12.

### Qué reporta el solver
`SolveStatus.MULTIPLE_OPTIMO` — se reporta la solución encontrada y se indica que
pueden existir otras igualmente óptimas.

---

## 4. Degeneración (solución básica degenerada)

### Qué significa
Una variable básica tiene valor 0 en alguna iteración. Esto puede causar **ciclado**
(el algoritmo repite la misma base sin progresar).

### Detección
En la razón mínima, si hay un empate (dos filas con la misma razón mínima), la variable
que sale puede ser cualquiera de las dos, pero la otra quedará en la base con bᵢ = 0.

### Solución
Usar la **regla de Bland** (elegir siempre el índice más pequeño) para garantizar
convergencia y evitar ciclado.

---

## Resumen: qué buscar en el tableau

| Situación | Señal en el tableau |
|---|---|
| Óptimo encontrado | Todos los cᵢ en fila z ≥ 0 |
| Múltiples óptimos | Algún cⱼ = 0 con xⱼ no básica |
| No acotado | Columna entrante con todos aᵢⱼ ≤ 0 |
| Infactible | Variable artificial en base con valor > 0 al final de Fase I |
