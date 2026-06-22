# Método Simplex — Teoría y mecánica

## ¿Qué es el Simplex?

El método Simplex es un algoritmo iterativo que recorre los **vértices (puntos extremos)**
de la región factible buscando la solución óptima. En cada iteración se mueve de un
vértice al vértice adyacente con mejor valor de la función objetivo.

El Simplex estándar aplica directamente cuando:
- Todas las restricciones son ≤
- Todos los lados derechos (b) son ≥ 0
- Todas las variables son ≥ 0

Para restricciones ≥ o = se requieren métodos auxiliares (Dos Fases, Gran M).

## El tableau Simplex

El tableau es la representación matricial del sistema en cada iteración.

Estructura para un problema con n variables y m restricciones:

```
Base | x₁  x₂ ... xₙ | s₁  s₂ ... sₘ | bi
-----|-----------------|----------------|----
 s₁  | a₁₁ a₁₂ ... a₁ₙ|  1   0  ...  0 | b₁
 s₂  | a₂₁ a₂₂ ... a₂ₙ|  0   1  ...  0 | b₂
 ... | ...             | ...            | ...
 sₘ  | aₘ₁ aₘ₂ ... aₘₙ|  0   0  ...  1 | bₘ
-----|-----------------|----------------|----
  z  | c₁  c₂  ... cₙ |  0   0  ...  0 |  0
```

## Variables de holgura

Para convertir cada restricción ≤ en una ecuación se agrega una **variable de holgura** sᵢ:

```
x₁ + 2x₂ ≤ 6   →   x₁ + 2x₂ + s₁ = 6    (s₁ ≥ 0)
```

Las variables de holgura representan la capacidad no utilizada de ese recurso.
En la solución inicial todas las variables de holgura forman la **base inicial**.

## Solución básica factible inicial

Con todas las variables originales en 0 y las holguras como base:
- x₁ = 0, x₂ = 0 → s₁ = b₁, s₂ = b₂, ...
- Esta solución es el origen (0, 0) y es factible si todos bᵢ ≥ 0.
- Z inicial = 0.

## Regla de entrada (Dantzig) — variable entrante

Se busca la variable no básica que **más incrementa Z por unidad**.

- En maximización: elegir la columna con el coeficiente **más negativo** en la fila z.
- Si todos los coeficientes de la fila z son ≥ 0, la solución es óptima.

*¿Por qué el más negativo?* Los coeficientes en la fila z aparecen con signo invertido
en el tableau estándar. El más negativo indica la mayor ganancia marginal.

## Regla de salida (razón mínima) — variable saliente

Entre las filas con coeficiente > 0 en la columna entrante, se elige la que da la
**razón mínima**: bᵢ / aᵢⱼ

- Si todos los coeficientes de la columna entrante son ≤ 0, el problema es **no acotado**.
- La variable básica de la fila con razón mínima sale de la base.

La razón mínima garantiza que la nueva solución básica sea factible (ningún bᵢ se vuelve negativo).

## Operación de pivote

El elemento pivote es la intersección de la columna entrante y la fila saliente.

Pasos:
1. **Dividir la fila pivote** entre el elemento pivote → el pivote se convierte en 1.
2. **Eliminar en las demás filas**: para cada fila i ≠ pivote:
   nueva_fila_i = fila_i − (aᵢⱼ × fila_pivote_nueva)
   → el coeficiente en la columna entrante se vuelve 0.
3. La variable entrante reemplaza a la saliente en la columna Base.

## Condición de optimalidad

La solución es óptima cuando **todos los coeficientes reducidos de la fila z son ≥ 0**
(en maximización). Significa que ninguna variable no básica puede mejorar Z.

## Ejemplo numérico de una iteración

Problema: Maximizar Z = 5x₁ + 4x₂
Restricciones: x₁ + 2x₂ ≤ 6; 2x₁ + x₂ ≤ 8

Tableau inicial:
```
Base | x₁  x₂  s₁  s₂ | bi
  s₁ |  1   2   1   0  |  6
  s₂ |  2   1   0   1  |  8
   z | -5  -4   0   0  |  0
```
- Variable entrante: x₁ (coeficiente -5, el más negativo)
- Razones: fila s₁: 6/1=6; fila s₂: 8/2=4 → sale s₂ (razón mínima = 4)
- Pivote: elemento (s₂, x₁) = 2

Tras el pivote:
```
Base | x₁  x₂    s₁    s₂ | bi
  s₁ |  0   3/2   1   -1/2 |  2
  x₁ |  1   1/2   0    1/2 |  4
   z |  0  -3/2   0    5/2 | 20
```
- Variable entrante: x₂ (coeficiente -3/2)
- Razones: fila s₁: 2/(3/2)=4/3; fila x₁: 4/(1/2)=8 → sale s₁
- Continúa la siguiente iteración...
