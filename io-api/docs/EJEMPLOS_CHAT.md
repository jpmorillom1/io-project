# Ejemplos de prompts para el chat (tutor Pivot)

> Colección de enunciados tipo ejercicio, listos para pegar en el chat, que hacen que el tutor
> formule el modelo y —tras la aprobación humana (HITL)— lo resuelva.
>
> **Cómo funciona el flujo (dos turnos):** al enviar el mensaje, el tutor identifica el modelo y
> muestra una tarjeta **Aprobar / Rechazar**. El solver **solo corre al pulsar Aprobar**. Si el tutor
> pide confirmar el modelo antes, responde algo como *"sí, está correcto, resuélvelo"*.
>
> Los resultados esperados están tomados de los tests del backend, así que son exactos y sirven para
> verificar que el chat resuelve bien.

> **Estado de este documento:** cubre **Programación Dinámica** y **PL Entera (Branch & Bound)**.
> Se irá ampliando con los tipos de problema y ejemplos de los demás métodos (LP, Transporte, Redes,
> Inventarios).

---

## Módulo: Programación Dinámica (determinística)

Cinco submodelos. El tutor elige la herramienta según las palabras clave del enunciado (etapas,
capacidad limitada, demanda por periodo, edad del equipo, repartir un recurso…).

| # | Submodelo | Herramienta que invoca el tutor | Señal en el enunciado |
|---|-----------|----------------------------------|------------------------|
| 1 | Asignación de recursos | `resolverPdAsignacionRecursos` | repartir un recurso entero entre actividades/periodos, con tabla de retornos |
| 2 | Mochila | `resolverPdMochila` | capacidad limitada + ítems con consumo y beneficio |
| 3 | Ruta por etapas | `resolverPdRutaEtapas` | red organizada en etapas, ir de origen a destino |
| 4 | Planificación de producción | `resolverPdPlanificacionProduccion` | demanda conocida **por periodo**, sin faltantes |
| 5 | Reemplazo de equipos | `resolverPdReemplazoEquipos` | conservar o reemplazar cada año, datos según la edad |

---

### 1. Asignación de recursos

Repartir un recurso entero entre actividades cuando el retorno viene en **tabla** (no como fórmula lineal).

**Prompt:**

> Tengo **2 unidades** de presupuesto (en millones) para repartir entre dos proyectos, A y B. El retorno depende de cuántas unidades le asigne a cada uno:
> - Proyecto A: 0 unidades → 0, 1 unidad → 4, 2 unidades → 6
> - Proyecto B: 0 unidades → 0, 1 unidad → 3, 2 unidades → 8
>
> Quiero maximizar el retorno total. Modélalo por programación dinámica y **resuélvelo**.

**Resultado esperado:** asignar **0 a A y 2 a B**, retorno total **8**.

---

### 2. Mochila

Seleccionar ítems que consumen una capacidad limitada, maximizando el beneficio total.

**Prompt:**

> Una mochila soporta **5 kg**. Tengo tres artículos, cada uno se lleva o no se lleva:
> - A: peso 2, valor 3
> - B: peso 3, valor 4
> - C: peso 4, valor 5
>
> Quiero maximizar el valor total sin pasarme del peso. Resuélvelo por programación dinámica.

**Resultado esperado:** llevar **A + B** (peso 5), valor **7** (no C, aunque sea el de mayor valor unitario).

---

### 3. Ruta por etapas (problema de la diligencia)

Ir de un origen a un destino atravesando una columna de nodos por etapa, minimizando el total acumulado.

**Prompt:**

> Debo ir del nodo **A** al nodo **J** cruzando una red por etapas, minimizando la distancia:
> - Etapa 1: A · Etapa 2: B, C, D · Etapa 3: E, F, G · Etapa 4: H, I · Etapa 5: J
> - Arcos (origen→destino: costo): A→B 2, A→C 4, A→D 3; B→E 7, B→F 4, B→G 6; C→E 3, C→F 2, C→G 4; D→E 4, D→F 1, D→G 5; E→H 1, E→I 4; F→H 6, F→I 3; G→H 3, G→I 3; H→J 3, I→J 4.
>
> Encuentra la ruta más corta con programación dinámica y **resuélvela**.

**Resultado esperado:** ruta **A–C–E–H–J** con costo **11**.

> Nota: si el grafo NO estuviera organizado en etapas (arcos que saltan libremente entre nodos), el
> tutor debería usar Redes (Dijkstra), no ruta por etapas.

---

### 4. Planificación de producción

Decidir cuánto producir en cada periodo para cubrir una demanda conocida **por periodo**, sin faltantes,
al mínimo costo total (preparación + producción + inventario).

**Prompt:**

> Debo planificar la producción de 3 periodos con demandas de **3, 2 y 4** unidades. Preparar un lote cuesta **$3** (fijo, cada periodo que produzco), producir una unidad cuesta **$1**, y mantener una unidad en inventario de un periodo al siguiente cuesta **$1**. No se permiten faltantes. Minimiza el costo total con programación dinámica — **resuélvelo**.

**Resultado esperado:** plan **5, 0, 4** (producir 5 en el periodo 1, nada en el 2, 4 en el 3), costo total **$17**.

> Nota: si la demanda fuera **constante** y solo se pidiera la cantidad económica de pedido, sería
> Inventarios (EOQ), no programación dinámica.

---

### 5. Reemplazo de equipos

Cada año, conservar la máquina actual o venderla por su rescate y comprar una nueva, maximizando el
ingreso neto del horizonte.

**Prompt:**

> Tengo una máquina **nueva** y un horizonte de **2 años**. Una máquina nueva cuesta **$10**. Según la edad del equipo (ingreso anual, costo de operación, valor de rescate):
> - Edad 0: ingreso 20, operación 2, rescate 8
> - Edad 1: ingreso 18, operación 4, rescate 6
> - Edad 2: ingreso 15, operación 8, rescate 3 (edad máxima)
>
> Cada año decido conservar o reemplazar, maximizando el ingreso neto. Resuélvelo por programación dinámica.

**Resultado esperado:** **Conservar** el año 1 y **Reemplazar** el año 2, ingreso neto máximo **$38**.

---

## Módulo: Programación Lineal Entera (Branch & Bound)

Una sola herramienta (`resolverEntera`). El tutor la invoca cuando una o más variables **no
pueden tomar valores fraccionarios**: **enteras** (cantidades indivisibles) o **binarias**
(decisiones sí/no). Comunica la integralidad con dos listas de nombres: `variablesEnteras` y
`variablesBinarias` (lo que no aparezca en ninguna se trata como continua).

| Señal en el enunciado | Tipo de variable |
|------------------------|------------------|
| "elegir / seleccionar", "abrir o no", "comprar o no", "sí/no", "0 o 1" | **binaria** |
| "número entero de", "cantidades indivisibles", "cuántas máquinas/personas/camiones" | **entera** |

Además del óptimo, el resultado trae el **valor de la relajación LP** y la **brecha de
integralidad**: sirven para explicar por qué no basta con redondear la relajación.

---

### 1. Selección de proyectos (binaria)

Elegir un subconjunto de proyectos bajo un presupuesto, maximizando el valor total (mochila 0/1).

**Prompt:**

> Una empresa evalúa **4 proyectos** y tiene un presupuesto de **10** (miles de $). Cada proyecto se hace completo o no se hace (no hay medios proyectos):
> - Proyecto A: cuesta 5, VPN 8
> - Proyecto B: cuesta 4, VPN 5
> - Proyecto C: cuesta 3, VPN 4
> - Proyecto D: cuesta 2, VPN 3
>
> Quiero elegir qué proyectos ejecutar para maximizar el VPN total sin pasarme del presupuesto. Resuélvelo con Branch & Bound.

**Modelo:** variables **binarias** A, B, C, D; `MAX 8A+5B+4C+3D` s.a. `5A+4B+3C+2D ≤ 10`.

**Resultado esperado:** ejecutar **A + C + D** (coste 10), VPN total **15**. La relajación LP y el
óptimo coinciden aquí (brecha 0): el reto es la combinatoria, no la fraccionalidad.

---

### 2. Compra de equipos (entera general)

Decidir cuántas máquinas de cada tipo comprar (cantidades enteras) para maximizar la producción.

**Prompt:**

> Un taller quiere comprar máquinas de dos tipos. Cada máquina **tipo A** cuesta 4 (mil $) y produce 30 piezas/día; cada **tipo B** cuesta 3 y produce 20 piezas/día. El presupuesto es **25** (mil $) y hay espacio para **7** máquinas como máximo. El número de máquinas debe ser entero. Maximiza la producción diaria. Resuélvelo por programación entera.

**Modelo:** variables **enteras** x1 (tipo A), x2 (tipo B); `MAX 30x1+20x2` s.a. `4x1+3x2 ≤ 25`, `x1+x2 ≤ 7`.

**Resultado esperado:** producción máxima **180 piezas/día** (con **6 tipo A y 0 tipo B**, o
equivalentemente **4 tipo A y 3 tipo B** — hay óptimos múltiples). La **relajación LP da 187.5**
(comprar 6.25 tipo A), así que hay ramificación y una **brecha de integralidad de 7.5**: redondear
6.25 → 6 casualmente funciona aquí, pero el solver lo demuestra en vez de asumirlo.

---

### 3. Apertura de centros de distribución (binaria)

Elegir en qué ubicaciones abrir un centro (decisión sí/no) bajo presupuesto, maximizando cobertura.

**Prompt:**

> Una cadena decide en cuáles de **3 ciudades** abrir un centro de distribución. Abrir en el **Norte** cuesta 6 y cubre 40 mil clientes; en el **Centro** cuesta 5 y cubre 35 mil; en el **Sur** cuesta 4 y cubre 30 mil. El presupuesto total es **9**. Cada centro se abre o no se abre. Maximiza la cobertura de clientes. Resuélvelo con Branch & Bound.

**Modelo:** variables **binarias** N, C, S; `MAX 40N+35C+30S` s.a. `6N+5C+4S ≤ 9`.

**Resultado esperado:** abrir **Centro + Sur** (coste 9), cobertura **65 mil** clientes (abrir el
Norte, aunque cubra más solo, no cabe en el presupuesto junto a otro).

---

## Otros módulos

_(pendiente de completar: LP · Transporte · Redes · Inventarios)_
