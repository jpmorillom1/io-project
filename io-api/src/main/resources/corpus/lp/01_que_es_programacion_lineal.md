# ¿Qué es la Programación Lineal?

## Definición

La Programación Lineal (PL) es una técnica de Investigación Operativa que permite
**optimizar** (maximizar o minimizar) una función objetivo lineal sujeta a un conjunto
de restricciones también lineales.

El término "lineal" significa que todas las relaciones matemáticas del modelo son de
primer grado: no hay términos cuadráticos, productos entre variables ni funciones no
lineales.

## Cuándo aplicar PL — criterios de decisión

Un problema es candidato a PL cuando cumple estas cuatro condiciones prácticas:

- **Función a optimizar**: hay una ganancia a maximizar o un costo a minimizar.
- **Variables continuas**: pueden tomar cualquier valor real no negativo.
- **Relaciones lineales**: función objetivo y restricciones son de primer grado.
- **Recursos limitados**: expresables como desigualdades o igualdades lineales.

Si las variables deben ser enteras, el problema es PL Entera (requiere Branch & Bound).

## Características matemáticas formales de un problema de PL

Las cuatro propiedades matemáticas que debe cumplir un modelo de PL son:

1. **Proporcionalidad**: la contribución de cada variable es proporcional a su valor (2x unidades → 2x aporte).
2. **Aditividad**: el efecto total es la suma de los efectos individuales; no hay sinergias.
3. **Divisibilidad**: las variables pueden tomar valores fraccionarios (0.5 unidades es válido).
4. **Certidumbre**: todos los coeficientes son conocidos y constantes.

## Ejemplo típico de problema de PL

Una empresa produce dos productos A y B. Cada unidad de A genera $5 de ganancia y
cada unidad de B genera $4. Se dispone de 6 horas de máquina (A usa 1 h, B usa 2 h)
y 8 horas de mano de obra (A usa 2 h, B usa 1 h).

Este problema **sí es de PL** porque:
- La función objetivo (ganancia total) es lineal: Z = 5x₁ + 4x₂
- Las restricciones de recursos son lineales: x₁ + 2x₂ ≤ 6 y 2x₁ + x₂ ≤ 8
- Las variables x₁ y x₂ son continuas y no negativas

## Lo que PL no puede modelar directamente

- Variables que solo pueden ser 0 o 1 (decisiones sí/no) → PL Entera
- Funciones cuadráticas en la objetivo (ej. minimizar x² + y²) → Programación No Lineal
- Dependencias condicionales ("si se produce A, entonces B ≤ 5") → PL Entera con
  variables binarias
