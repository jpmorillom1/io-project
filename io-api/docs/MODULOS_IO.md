# Especificación de los módulos de IO

Para cada solver: entrada, salida, qué registrar en `steps`, y el ejercicio de validación.

---

## 1. Programación Lineal — COMPLETO

### SimplexSolver
- **Entrada:** `ProblemaLineal` (sentido, objetivo, restricciones tipo ≤, RHS ≥ 0).
- **Salida:** `SolucionLineal` (Z, valores de variables, holguras, precios sombra).
- **steps:** una entrada por iteración con la tabla simplex, columna pivote, fila pivote y
  la operación de fila aplicada.
- **Estados:** detectar NO_ACOTADO (columna pivote sin razón positiva) y MULTIPLE_OPTIMO
  (costo reducido cero en variable no básica).

### DosFasesSolver / GranMSolver
- Manejan restricciones ≥ y =, con variables artificiales.
- Dos Fases: Fase I minimiza suma de artificiales; Fase II optimiza el objetivo real.
- **steps:** marcar claramente el cambio de fase.

### DualGenerator
- Genera el dual a partir del primal (sin resolver). Devuelve un `ProblemaLineal`.

### AnalisisSensibilidad
- A partir de la solución óptima: rangos de los coeficientes del objetivo y de los RHS,
  precios sombra. Documentar fórmulas usadas.

### MetodoGrafico (solo 2 variables)
- No "resuelve" numéricamente distinto; produce los datos para graficar: rectas de
  restricción, región factible (vértices), curvas de nivel del objetivo, punto óptimo.

**Validación:** ejercicio clásico (ej. max Z=3x1+5x2 s.a. x1≤4, 2x2≤12, 3x1+2x2≤18 →
Z=36 en (2,6)). Mételo en `SimplexTest`.

---

## 2. Transporte — COMPLETO

### Soluciones iniciales
- `EsquinaNoroeste`, `CostoMinimo`, `Vogel` (VAM con penalizaciones por fila/columna).
- **steps:** cada asignación y por qué se eligió esa celda.

### ModiSolver (optimización)
- Calcula multiplicadores u/v, costos reducidos de celdas no básicas; si hay negativo,
  mejora trazando el ciclo (vía `CicloSteppingStone`).
- **steps:** tabla con u/v, celda entrante, ciclo y nueva asignación por iteración.
- Manejar degeneración (rellenar con asignación épsilon cuando falten celdas básicas).

### Balanceador
- Si Σoferta ≠ Σdemanda, agrega origen/destino ficticio con costo 0 antes de resolver.

### HungaroSolver (asignación)
- Matriz cuadrada (balancear si no lo es). Reducción por filas/columnas, cobertura mínima
  de ceros, ajuste. **steps:** cada reducción y cobertura.

**Validación:** problema de transporte 3×4 con óptimo conocido; verifica costo total.

---

## 3. Redes — COMPLETO

### DijkstraSolver
- Ruta más corta desde una fuente. `PriorityQueue` por distancia provisional.
- **Salida:** distancias y predecesores; reconstruir la ruta a un destino.
- **steps:** nodo extraído y relajaciones por iteración.
- Pesos no negativos (validar; si hay negativos, ModeloInvalidoException).

### KruskalSolver (+ UnionFind)
- Árbol de expansión mínima en grafo no dirigido conexo.
- Ordena aristas por peso; agrega si no forma ciclo (Union-Find).
- **steps:** cada arista evaluada (aceptada/rechazada) y peso acumulado.

### EdmondsKarpSolver
- Flujo máximo (BFS para caminos de aumento sobre la red residual).
- **Salida:** flujo máximo y flujo por arco.
- **steps:** cada camino de aumento, su cuello de botella y el residual actualizado.

**Validación:** grafo pequeño con resultado conocido para cada uno de los tres.

---

## 4. PL Entera — TODO ESTRUCTURADO

- `ProblemaEntero` = `ProblemaLineal` + conjunto de índices enteros/binarios.
- `BranchAndBoundSolver`: resuelve la relajación lineal con `SimplexSolver`; ramifica sobre
  una variable fraccionaria; poda por cota/infactibilidad/integralidad.
- **Dejar como `// TODO`** con la firma y el contrato listos. No implementar hasta indicación.

---

## 5. Programación Dinámica — COMPLETADO (backend)

Determinística, por recursión hacia atrás. Cinco submodelos que comparten `ModeloDinamico`,
`SolucionDinamica` y la misma forma de salida (tablas por etapa + política óptima).
Detalle completo en `docs/DINAMICA.md`.

### ModeloDinamico
- **Entrada:** `metodo` (`MetodoDinamico`), `sentido` (`SentidoOptimizacion`, opcional) y los campos
  del submodelo; el resto van null. Implementa `ModeloResoluble`. Tiene `Builder` (21 componentes).
- **Validación:** `DinamicaValidador`, un método por submodelo. Malformado → `IllegalArgumentException`.
  La infactibilidad NUNCA lanza.

### SolucionDinamica
- **Salida:** `valorOptimo`, `tablas` (`List<TablaEtapa>`), `politicaOptima` (`List<DecisionOptima>`),
  `rutaOptima` (solo RUTA_ETAPAS), y los cinco textos del modelo: `definicionEtapas`,
  `definicionEstados`, `definicionDecisiones`, `funcionRecurrencia`, `principioOptimalidad`,
  `interpretacionPolitica`. Tiene `Builder`.
- **steps:** paso 1 = formulación; un paso por etapa (con su `TablaEtapa` en `datos.tabla`, en orden de
  la recursión hacia atrás); paso final = recuperación de la política hacia adelante.

### AsignacionRecursosSolver
- **Estados:** recurso disponible `s = 0..recursoTotal`. **Decisión:** `x` unidades a la actividad.
- `f_i(s) = opt{ r_i(x) + f_(i+1)(s − x) : 0 ≤ x ≤ s }`, `f_(n+1)(s) = 0`. Nunca infactible.

### MochilaSolver
- **Estados:** capacidad libre. **Decisión:** unidades del artículo (`unidadesMaximas` omitido ⇒ 0/1).
- `f_i(s) = max{ v_i·x + f_(i+1)(s − p_i·x) }`, `f_(n+1)(s) = 0`. Maximiza siempre. Nunca infactible.

### RutaEtapasSolver
- **Estados:** nodo actual dentro de la etapa. **Decisión:** nodo de la etapa siguiente.
- `f_k(s) = opt{ c(s,d) + f_(k+1)(d) }`, `f_K(destino) = 0`. Minimiza por defecto.
- **Puede ser INFACTIBLE** (origen sin camino al destino). Los nodos inalcanzables no generan fila.

### PlanificacionProduccionSolver
- **Estados:** inventario al inicio del periodo. **Decisión:** cuánto producir.
- `f_t(i) = min{ K·[x>0] + c·x + h·(i+x−d_t) + f_(t+1)(i+x−d_t) }`; frontera `f_(T+1)(i) = 0` solo si
  `i == inventarioFinal`. Minimiza siempre. **Puede ser INFACTIBLE** (capacidad insuficiente).

### ReemplazoEquiposSolver
- **Estados:** edad del equipo. **Decisión:** CONSERVAR o REEMPLAZAR (a la edad máxima, solo reemplazar).
- `f_t(e) = max{ r(e) − c(e) + f_(t+1)(e+1) ; s(e) − I + r(0) − c(0) + f_(t+1)(1) }`, `f_(n+1)(e) = s(e)`.
  Maximiza el ingreso neto. Nunca infactible.

> ⚠ Los estados inalcanzables valen ±∞ internamente y se OMITEN de las tablas: un `Infinity` en el JSON
> de salida no es JSON válido.

---

## 6. Inventarios — TODO ESTRUCTURADO

- `ParametrosInventario` (demanda, costo de pedido, costo de mantener, etc.).
- Modelos: EOQ básico, EOQ con faltantes, EOQ con descuentos por cantidad, POQ, punto de
  reorden. Son fórmulas cerradas; **steps** = sustitución en la fórmula + interpretación.
- **Dejar como `// TODO`** hasta que se imparta.
