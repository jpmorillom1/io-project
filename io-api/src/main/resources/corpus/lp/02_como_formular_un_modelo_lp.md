# Cómo formular un modelo de Programación Lineal

## Paso 1 — Identificar las variables de decisión

Las variables de decisión representan **qué se quiere determinar**: cuánto producir,
cuánto invertir, cuántas unidades transportar, etc.

- Deben ser cuantificables y continuas.
- Se nombran x₁, x₂, ... o con nombres descriptivos (x_A, x_B).
- Siempre van acompañadas de la condición de no negatividad: x₁ ≥ 0, x₂ ≥ 0.

**Ejemplo:** "¿Cuántas unidades del producto A y del producto B producir por semana?"
→ Variables: x₁ = unidades de A por semana, x₂ = unidades de B por semana.

## Paso 2 — Definir la función objetivo

La función objetivo expresa **lo que se quiere optimizar** como combinación lineal de
las variables de decisión.

Forma general:
- Maximizar: Z = c₁x₁ + c₂x₂ + ... + cₙxₙ
- Minimizar: Z = c₁x₁ + c₂x₂ + ... + cₙxₙ

Los coeficientes cᵢ representan la contribución unitaria de cada variable (ganancia
por unidad, costo por unidad, etc.).

**Ejemplo:** Si A deja $5 por unidad y B deja $4:
Z = 5x₁ + 4x₂ → Maximizar

## Paso 3 — Formular las restricciones

Cada restricción expresa una **limitación de recursos o requerimiento** del problema.

Formas posibles:
- ≤ (menor o igual): recurso limitado (horas disponibles, capacidad máxima)
- ≥ (mayor o igual): requerimiento mínimo (demanda mínima, calidad mínima)
- = (igual): balance exacto (mezcla exacta, ecuación de flujo)

**Ejemplo:**
- Horas de máquina: x₁ + 2x₂ ≤ 6 (solo hay 6 horas disponibles)
- Horas de mano de obra: 2x₁ + x₂ ≤ 8
- No negatividad: x₁ ≥ 0, x₂ ≥ 0

## Paso 4 — Escribir el modelo completo

```
Maximizar  Z = 5x₁ + 4x₂
sujeto a:
    x₁ + 2x₂ ≤ 6    (máquina)
   2x₁ +  x₂ ≤ 8    (mano de obra)
        x₁   ≥ 0
             x₂ ≥ 0
```

## Ejemplo resuelto completo: mezcla de productos

**Enunciado:** Una fábrica produce sillas (S) y mesas (M). Cada silla requiere
3 kg de madera y 2 horas de carpintería. Cada mesa requiere 7 kg de madera y
4 horas de carpintería. Hay 210 kg de madera y 120 horas de carpintería disponibles.
La ganancia es $5 por silla y $9 por mesa. Maximizar ganancia.

**Variables:** x₁ = sillas/semana, x₂ = mesas/semana

**Función objetivo:** Maximizar Z = 5x₁ + 9x₂

**Restricciones:**
- Madera:       3x₁ + 7x₂ ≤ 210
- Carpintería:  2x₁ + 4x₂ ≤ 120
- No negatividad: x₁, x₂ ≥ 0

## Errores frecuentes al formular

- **Definir mal la variable**: "cantidad total de horas" en vez de "unidades producidas"
  — las variables deben ser lo que se decide, no un recurso.
- **Confundir el sentido de la restricción**: poner ≤ cuando el enunciado dice
  "se deben producir al menos X unidades" (eso es ≥).
- **Olvidar la no negatividad**: en Simplex estándar todas las variables deben ser ≥ 0.
- **Coeficientes en las unidades incorrectas**: si la capacidad está en minutos y el
  consumo en horas, hay que homogeneizar antes de escribir la restricción.
