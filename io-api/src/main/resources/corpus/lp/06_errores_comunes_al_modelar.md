# Errores de formulación — equivocaciones del estudiante al escribir el modelo LP

Estos errores ocurren ANTES de ejecutar el Simplex: están en cómo se escribe el modelo,
no en los resultados que produce el algoritmo. Son equivocaciones de formulación, no
casos especiales del método.

## Los seis errores de modelado más frecuentes al formular restricciones y la función objetivo

1. **Confundir MAX con MIN** — el sentido del objetivo es el opuesto al del enunciado.
2. **Invertir coeficientes** — se intercambian los valores entre variables en las restricciones.
3. **Omitir no negatividad** — se olvida declarar xᵢ ≥ 0.
4. **Usar ≥ con Simplex estándar** — el Simplex estándar solo admite restricciones ≤.
5. **Confundir variable de decisión con recurso** — se define la variable como la disponibilidad, no como la cantidad a decidir.
6. **Unidades inconsistentes** — se mezclan horas con minutos, kg con gramos, etc.

---

## Error 1 — Confundir MAX con MIN

### Descripción
El enunciado pide minimizar costos, pero se formula como maximización (o viceversa).

### Consecuencia
El Simplex encontrará el "óptimo" del problema equivocado. La solución será matemáticamente
correcta para un objetivo que no era el del problema real.

### Cómo evitarlo
Leer cuidadosamente el verbo del enunciado:
- "Maximizar ganancia / beneficio / utilidad / producción" → MAX
- "Minimizar costo / gasto / tiempo / distancia" → MIN

### Equivalencia
Minimizar Z equivale a Maximizar (-Z). Si el solver solo hace MAX, se puede transformar:
MIN Z = c₁x₁ + c₂x₂  →  MAX Z' = -c₁x₁ - c₂x₂

---

## Error 2 — Invertir coeficientes en la función objetivo o restricciones

### Descripción
Se intercambian los coeficientes de dos variables, o se pone el consumo de recurso
de A en la columna de B y viceversa.

### Ejemplo
El problema dice: "A consume 3 kg de madera y B consume 7 kg".
Error: formular 7x₁ + 3x₂ ≤ 210 (invertido).

### Consecuencia
El modelo resuelve un problema diferente al real. La solución puede ser factible
matemáticamente pero absurda en el contexto del problema.

### Cómo evitarlo
Construir la tabla de coeficientes antes de escribir las restricciones:

| Recurso     | x₁ (A) | x₂ (B) | Disponible |
|-------------|---------|---------|------------|
| Madera (kg) |    3    |    7    |    210     |
| Horas       |    2    |    4    |    120     |

Luego copiar fila a fila: 3x₁ + 7x₂ ≤ 210; 2x₁ + 4x₂ ≤ 120.

---

## Error 3 — Olvidar las condiciones de no negatividad

### Descripción
Se omite la condición x₁ ≥ 0, x₂ ≥ 0 al escribir el modelo.

### Consecuencia
El Simplex estándar asume implícitamente que las variables son ≥ 0, pero si no se
declaran explícitamente en el modelo puede llevar a confusiones o implementaciones
que permitan valores negativos.

### En la práctica
Siempre escribir explícitamente: "x₁, x₂ ≥ 0" o "xᵢ ≥ 0 para todo i".
Las cantidades físicas (unidades producidas, horas asignadas, kg transportados) no
pueden ser negativas por definición.

---

## Error 4 — Usar ≥ cuando el Simplex estándar solo admite ≤

### Descripción
El Simplex estándar parte de una base inicial formada por variables de holgura,
lo que solo es válido cuando todas las restricciones son de tipo ≤ con bᵢ ≥ 0.

Si una restricción es ≥ (por ejemplo, una demanda mínima), no se puede aplicar
Simplex estándar directamente.

### Consecuencia
Si se trata de aplicar Simplex estándar a una restricción ≥, la base inicial no
es factible (algún bᵢ resultaría negativo), y el método falla o da resultados incorrectos.

### Cómo resolverlo
Para restricciones ≥ o = se deben usar:
- **Método de las Dos Fases**: agrega variables artificiales para encontrar una
  solución básica factible inicial.
- **Método de la Gran M**: penaliza las variables artificiales en la función objetivo
  con un coeficiente M muy grande.

---

## Error 5 — Confundir las variables de decisión con los recursos

### Descripción
Se define una variable como "horas de máquina disponibles" en lugar de "unidades
producidas". Los recursos son parámetros (lados derechos de las restricciones),
no variables de decisión.

### Ejemplo de error
x₁ = horas disponibles de máquina (INCORRECTO)
x₁ = unidades del producto A a producir (CORRECTO)

### Regla
La variable de decisión siempre responde a la pregunta **"¿cuánto de qué decidimos?"**,
no "¿cuánto hay disponible?".

---

## Error 6 — Unidades inconsistentes en las restricciones

### Descripción
Una restricción mezcla unidades diferentes sin convertir (horas vs. minutos, kg vs. g).

### Ejemplo
- La restricción dice: "tiempo disponible: 360 minutos"
- El consumo de x₁ se expresó en horas: "x₁ consume 1.5 horas"
- Se escribe: 1.5 x₁ ≤ 360 (ERROR: se mezclan horas y minutos)

### Consecuencia
La restricción acepta valores 240 veces mayores de lo que debería (360 minutos / 1.5
es correcto, pero 360 minutos / 1.5 horas mezcla unidades).

### Cómo evitarlo
Homogeneizar: convertir todo a la misma unidad antes de escribir la restricción.
- Opción A: 1.5 h = 90 min → 90x₁ ≤ 360
- Opción B: 360 min = 6 h → 1.5x₁ ≤ 6

---

## Checklist antes de resolver

- [ ] ¿Es MAX o MIN? (leer el verbo del enunciado)
- [ ] ¿Las variables representan decisiones, no recursos?
- [ ] ¿Los coeficientes de cada variable son correctos en cada restricción?
- [ ] ¿Todas las restricciones son ≤ con bᵢ ≥ 0 para usar Simplex estándar?
- [ ] ¿Se declaró la no negatividad?
- [ ] ¿Las unidades son consistentes en toda la formulación?
