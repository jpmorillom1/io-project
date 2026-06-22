# Interpretación de resultados en Programación Lineal

## La solución óptima

Cuando el Simplex termina con todos los coeficientes de la fila z ≥ 0, la solución
en la columna bᵢ (columna del lado derecho) representa:

- Las **variables básicas** tienen los valores indicados en la columna bᵢ.
- Las **variables no básicas** valen 0.
- **Z*** es el valor óptimo de la función objetivo (aparece en la fila z, columna bᵢ).

## Cómo leer la solución

Del tableau final, los valores se leen directamente:

```
Base | x₁  x₂  s₁  s₂ | bi
  x₂ |  0   1  2/3 -1/3|  4/3
  x₁ |  1   0 -1/3  2/3|  10/3
   z |  0   0  ...  ... | Z*
```

Aquí: x₁ = 10/3, x₂ = 4/3 → Z* = 5(10/3) + 4(4/3) = 50/3 + 16/3 = 66/3 = 22

## Significado de las variables de holgura en la solución final

Una **variable de holgura sᵢ** mide la capacidad sobrante del recurso i.

**sᵢ = 0** significa que el recurso está **completamente agotado** — la restricción
es ACTIVA o VINCULANTE (se cumple con igualdad, no sobra nada).
Ejemplo: s₁ = 0 con restricción x₁ + 2x₂ ≤ 6 → se usan las 6 horas exactas.

**sᵢ > 0** significa que el recurso tiene **capacidad sobrante** (holgura disponible),
la restricción NO es vinculante, ese recurso no es un cuello de botella.
Ejemplo: s₂ = 3 → sobran 3 unidades del recurso 2.

**Confusión frecuente — muy importante:** sᵢ = 0 NO significa restricción redundante
ni sin efecto. Es exactamente lo contrario: sᵢ = 0 indica que la restricción SÍ tiene
efecto porque el recurso está AGOTADO. La restricción redundante o sin efecto tiene sᵢ > 0.

Resumen:
- sᵢ = 0 → recurso agotado → restricción activa/vinculante → cuello de botella
- sᵢ > 0 → recurso sobrante → restricción inactiva → no limita la solución

## Interpretación económica de Z*

- En un problema de maximización de ganancia, Z* es la **ganancia máxima** que se puede
  obtener bajo las restricciones del modelo.
- Cada punto factible (combinación de valores que satisface todas las restricciones) da
  un valor de Z inferior o igual a Z*.

## Precios sombra (concepto básico)

Los **precios sombra** (shadow prices) indican cuánto aumentaría Z* si se incrementa
en 1 unidad la disponibilidad de un recurso.

- Un precio sombra > 0 indica que ese recurso es limitante: vale la pena conseguir más.
- Un precio sombra = 0 indica que ya hay holgura: obtener más de ese recurso no mejora Z*.

Los precios sombra se leen de la fila z del tableau final, en las columnas de las
variables de holgura (con signo cambiado en maximización).

## Ejemplo de lectura completa

Problema resuelto: Maximizar Z = 5x₁ + 4x₂
Con x₁ = 10/3 ≈ 3.33 y x₂ = 4/3 ≈ 1.33, Z* = 22.

**Lectura en lenguaje natural:**
"Producir 3.33 unidades del producto A y 1.33 unidades del producto B maximiza la
ganancia en $22. Las restricciones de máquina y mano de obra están ambas agotadas
(restricciones activas), lo cual indica que ambos recursos son cuellos de botella."

Si una holgura s₃ = 5 aparece en la solución, significa: "hay 5 unidades sobrantes
del recurso 3; aumentar su disponibilidad no mejoraría Z*."

## Análisis de sensibilidad (concepto)

El análisis de sensibilidad responde: ¿en qué rango puede variar un coeficiente cᵢ
o un lado derecho bᵢ sin que cambie la base óptima?

- **Rango de los coeficientes de la objetivo (cᵢ)**: entre qué valores puede cambiar
  la ganancia unitaria de x₁ sin que la solución óptima actual deje de serlo.
- **Rango de los lados derechos (bᵢ)**: hasta cuánto puede cambiar la disponibilidad
  de un recurso manteniendo válida la solución base actual.
