# Ejemplos de Prompts Happy Path para el Chat (Tutor IA Pivot — Caso Cervecería Nacional)

> Colección exhaustiva de enunciados listos para copiar y pegar en el chat interactivo, diseñados para validar el funcionamiento **Happy Path** de los seis módulos de Investigación Operativa implementados en la plataforma.
>
> **Tema de negocio unificado:** Todos los ejercicios están ambientados en la operación real de **Cervecería Nacional** (plantas cerveceras en Cumbayá y Guayaquil, marcas emblemáticas como *Pilsener* y *Club Premium*, centros de distribución, líneas de embotellado, inventario de lúpulo/botellas y logística vial en Ecuador).
>
> **Cómo ejecutar en el chat:**
> 1. Copia cualquiera de los bloques **Prompt listo para pegar** y envíalo en el chat.
> 2. El agente clasificador semántico identificará automáticamente el módulo (`PL`, `TRANSPORTE`, `REDES`, `ENTERA`, `DINAMICA`, `INVENTARIO`) y sugerirá la formulación matemática con una tarjeta interactiva **Aprobar / Rechazar**.
> 3. Pulsa **"Aprobar"** (o confirma en el chat) para ejecutar el solver y ver los pasos detallados junto con la explicación socrática.

---

## Índice Rápido de Módulos
1. [Programación Lineal Continua (LP)](#1-módulo-programación-lineal-continua-lp)
2. [Transporte](#2-módulo-transporte)
3. [Redes sobre Grafos](#3-módulo-redes-sobre-grafos)
4. [Programación Lineal Entera (Branch & Bound)](#4-módulo-programación-lineal-entera-branch--bound)
5. [Programación Dinámica Determinística](#5-módulo-programación-dinámica-determinística)
6. [Gestión de Inventarios Deterministas](#6-módulo-gestión-de-inventarios-deterministas)

---

## 1. Módulo: Programación Lineal Continua (LP)

Resuelve problemas algebraicos de optimización continua mediante **Simplex Estándar**, **Dos Fases**, **Gran M** y **Método Gráfico** (para 2 variables).

### 1.1 Optimización de Mezcla de Producción (Pilsener vs. Club Premium)
- **Método sugerido:** Simplex Estándar / Método Gráfico (2 variables).
- **Escenario:** Maximizar la ganancia semanal en la planta de Cumbayá respetando la capacidad de las áreas de cocción y envasado.

#### Prompt listo para pegar:
```text
En la planta de Cervecería Nacional en Cumbayá producimos lotes de cerveza Pilsener (x1) y Club Premium (x2). Cada lote de Pilsener genera una ganancia de 300 dólares y requiere 2 horas de cocción y 2 horas de envasado. Cada lote de Club Premium genera 400 dólares y requiere 3 horas de cocción y 1 hora de envasado. Disponemos como máximo de 120 horas para cocción y 80 horas para envasado a la semana. Formula el modelo de programación lineal continua para maximizar la ganancia total y resuélvelo por Simplex.
```

- **Formulación matemática:**
  - $\max Z = 300x_1 + 400x_2$
  - Sujeto a:
    - $2x_1 + 3x_2 \le 120$ *(Horas de cocción)*
    - $2x_1 + x_2 \le 80$ *(Horas de envasado)*
    - $x_1, x_2 \ge 0$
- **Resultado esperado:**
  - **Solución óptima:** $x_1 = 30$ lotes de Pilsener, $x_2 = 20$ lotes de Club Premium.
  - **Valor óptimo ($Z$):** **$17,000** de ganancia máxima.

---

### 1.2 Cumplimiento de Contrato Mínimo de Distribución (Dos Fases)
- **Método sugerido:** Método de las Dos Fases (restricciones con $\ge$).
- **Escenario:** Minimizar costos operativos garantizando entregas mínimas contractuales a cadenas minoristas.

#### Prompt listo para pegar:
```text
Cervecería Nacional debe cumplir un contrato mínimo de abastecimiento produciendo cerveza Pilsener (x1) y Club Premium (x2). El costo unitario de producción es de 50 dólares por hectolitro de Pilsener y 80 dólares por hectolitro de Club Premium. Por contrato debemos producir en total al menos 100 hectolitros entre ambas marcas (x1 + x2 >= 100), y por demanda del segmento premium al menos 30 hectolitros deben ser de Club Premium (x2 >= 30). Formula el modelo para minimizar el costo total de producción y resuélvelo por el Método de Dos Fases.
```

- **Resultado esperado:**
  - **Solución óptima:** $x_1 = 70$ hectolitros de Pilsener, $x_2 = 30$ hectolitros de Club Premium.
  - **Costo mínimo ($Z$):** $50(70) + 80(30) =$ **$5,900**.

---

## 2. Módulo: Transporte

Resuelve la distribución equilibrada desde plantas hacia centros de distribución utilizando **Esquina Noroeste**, **Costo Mínimo**, **Vogel (VAM)** y **MODI**.

### 2.1 Logística de Despacho Nacional (Plantas Guayaquil y Quito)
- **Escenario:** Minimizar el costo de flete terrestre desde dos plantas productoras hacia tres centros de distribución en Ecuador.

#### Prompt listo para pegar:
```text
Cervecería Nacional distribuye camiones con cerveza desde sus plantas en Guayaquil (oferta disponible de 350 camiones) y Quito (oferta disponible de 250 camiones) hacia tres Centros de Distribución: CD-Norte (demanda 200 camiones), CD-Sur (demanda 250 camiones) y CD-Cuenca (demanda 150 camiones). Los costos de flete por camión en dólares son: desde Guayaquil hacia CD-Norte 15, CD-Sur 10, CD-Cuenca 12; y desde Quito hacia CD-Norte 8, CD-Sur 14, CD-Cuenca 18. Formula el problema de transporte y resuélvelo para encontrar la distribución de costo mínimo.
```

- **Tabla de Costos Unitarios y Balance:**
  | Origen \ Destino | CD-Norte | CD-Sur | CD-Cuenca | Oferta |
  |---|---|---|---|---|
  | **Planta Guayaquil** | $15 | $10 | $12 | 350 |
  | **Planta Quito** | $8 | $14 | $18 | 250 |
  | **Demanda** | 200 | 250 | 150 | **Total: 600** |

- **Resultado esperado (Asignación Óptima):**
  - **Desde Quito:** 200 camiones a CD-Norte + 50 camiones a CD-Sur.
  - **Desde Guayaquil:** 200 camiones a CD-Sur + 150 camiones a CD-Cuenca.
  - **Costo total mínimo:** $200(8) + 50(14) + 200(10) + 150(12) =$ **$6,100**.

---

## 3. Módulo: Redes sobre Grafos

Analiza grafos viales y de infraestructura mediante **Dijkstra** (ruta más corta), **Kruskal** (árbol de expansión mínima) y **Edmonds-Karp** (flujo máximo).

### 3.1 Ruta Logística de Reparto Más Corta (Dijkstra)
- **Escenario:** Determinar el recorrido de mínima distancia por carretera para un tráiler cervecero de Guayaquil a Quito.

#### Prompt listo para pegar:
```text
Un tráiler repartidor de Cervecería Nacional debe viajar desde la Planta Guayaquil (nodo Origen) hasta el Megacentro Quito (nodo Destino) recorriendo la red vial del Ecuador. Los nodos intermedios son Babahoyo, Santo Domingo y Ambato. Las distancias por carretera en kilómetros son: Guayaquil->Babahoyo (60), Guayaquil->Ambato (280), Babahoyo->Santo Domingo (190), Ambato->Quito (130), Santo Domingo->Quito (120). Encuentra la ruta más corta en kilómetros usando teoría de redes (Dijkstra) y resuélvela.
```

- **Resultado esperado:**
  - **Ruta óptima:** `Guayaquil` $\to$ `Babahoyo` $\to$ `Santo Domingo` $\to$ `Quito`.
  - **Distancia mínima:** $60 + 190 + 120 =$ **370 km**.

---

### 3.2 Flujo Máximo en Tuberías de Cocción Cerveceras (Edmonds-Karp)
- **Escenario:** Determinar la capacidad máxima de trasiego de mosto entre tanques de elaboración por hora.

#### Prompt listo para pegar:
```text
En la planta cervecera tenemos una red de tuberías de acero inoxidable para trasegar mosto desde el Tanque de Cocción (Origen) hasta la Línea de Embotellado (Destino) pasando por los nodos Filtro y Fermentador. Las capacidades máximas de flujo en hectolitros por hora son: Cocción->Filtro (50), Cocción->Fermentador (40), Filtro->Fermentador (15), Filtro->Embotellado (30), Fermentador->Embotellado (60). Calcula el flujo máximo de cerveza por hora usando el algoritmo de Edmonds-Karp.
```

- **Resultado esperado:**
  - **Flujo Máximo:** **85 hectolitros por hora** saturando las líneas de embotellado.

---

## 4. Módulo: Programación Lineal Entera (Branch & Bound)

Resuelve problemas combinatorios donde las variables de decisión representan decisiones indivisibles (**Enteras** $\mathbb{Z}^+$) o selecciones **Binarias** ($0$ o $1$).

### 4.1 Portafolio de Inversión en Tecnologías Cerveceras (Mochila Binaria 0/1 con Ramificación Múltiple)
- **Escenario:** Seleccionar proyectos indivisibles bajo un presupuesto estricto de 10 millones que obligue a ramificar en el árbol Branch & Bound.

#### Prompt listo para pegar:
```text
Cervecería Nacional evalúa 3 proyectos estratégicos indivisibles (variables binarias 0 o 1) con un presupuesto total de 10 millones de dólares:
- Proyecto 1 (Línea automatizada Cumbayá): costo 6 millones, Retorno VPN 10 millones
- Proyecto 2 (Tanques de maduración Guayaquil): costo 5 millones, Retorno VPN 8 millones
- Proyecto 3 (Sistema de cogeneración energética): costo 5 millones, Retorno VPN 7 millones
Maximiza el retorno VPN total respetando el presupuesto de 10 millones usando Programación Lineal Entera (Branch & Bound) y resuélvelo.
```

- **Comportamiento del Árbol Branch & Bound:**
  - **Relajación Continua (Nodo 0):** Selecciona el Proyecto 1 ($x_1=1$) y una fracción del Proyecto 2 ($x_2=0.8$), obteniendo un VPN fraccionario de **$16.4 millones**.
  - **Ramificación:** Al ser $x_2=0.8$ fraccionario, el solver ramifica en dos subproblemas ($x_2=0$ y $x_2=1$), generando múltiples nodos y podando soluciones no enteras.
- **Resultado Entero Óptimo:**
  - **Proyectos seleccionados:** **Proyecto 2 + Proyecto 3** ($x_1=0, x_2=1, x_3=1$, costo $5+5=10$ millones).
  - **Retorno VPN máximo:** **$15 millones**.

---

### 4.2 Compra de Reactores de Fermentación Indivisibles (Variables Enteras Generales con Ramificación)
- **Escenario:** Determinar el número exacto de reactores a instalar donde la intersección continua cae en coordenadas decimales.

#### Prompt listo para pegar:
```text
Cervecería Nacional desea adquirir reactores de fermentación de dos marcas. Cada reactor Marca A genera $8 (miles) de utilidad diaria, requiere 1 hora de instalación y cuesta $9 (miles). Cada reactor Marca B genera $5 (miles) de utilidad diaria, requiere 1 hora de instalación y cuesta $5 (miles). Se dispone de un máximo de 6 horas de instalación y un presupuesto de $45 (miles). La cantidad de reactores debe ser estrictamente entera. Maximiza la utilidad diaria por Programación Lineal Entera y resuélvelo.
```

- **Comportamiento del Árbol Branch & Bound:**
  - **Relajación Continua (Nodo 0):** Arroja $x_1=3.75$ reactores A y $x_2=2.25$ reactores B con utilidad $Z=41.25$ (fraccionario).
  - **Ramificación:** El solver genera ramas sobre $x_1 \le 3$ y $x_1 \ge 4$, explorando varios nodos intermedios.
- **Resultado Entero Óptimo:**
  - **Solución entera óptima:** **5 reactores Marca A y 0 reactores Marca B** ($x_1=5, x_2=0$).
  - **Utilidad diaria máxima:** **$40 (miles)**.

---

## 5. Módulo: Programación Dinámica Determinística

Resuelve optimización secuencial por etapas aplicando el **Principio de Optimalidad de Bellman**.

### 5.1 Planificación Trimestral de Producción Cervecera (Sin Faltantes)
- **Escenario:** Equilibrar costos de preparación de lote, producción continua e inventario de cerveza.

#### Prompt listo para pegar:
```text
En Cervecería Nacional debemos planificar por Programación Dinámica por etapas la producción de cerveza Club Premium para las etapas de los próximos 3 meses, cuyas demandas por etapa son 3, 2 y 4 lotes respectivamente. En cada etapa, el costo fijo de arrancar la línea de cocción es de $3 (miles), producir cada lote cuesta $1 (mil), y mantener un lote de excedente entre una etapa y la siguiente cuesta $1 (mil). No se permiten faltantes. Determina el plan de producción óptimo por etapa usando Programación Dinámica para minimizar el costo total y resuélvelo.
```

- **Resultado esperado:**
  - **Plan óptimo por mes:** Producir **5 lotes en Mes 1**, **0 lotes en Mes 2** y **4 lotes en Mes 3**.
  - **Costo total mínimo:** **$17 (miles de dólares)**.

---

### 5.2 Asignación de Equipos Promocionales por Regiones (Recursos Discretos)
- **Escenario:** Distribuir 2 equipos móviles de degustación entre las regiones Sierra y Costa con retornos no lineales.

#### Prompt listo para pegar:
```text
Cervecería Nacional dispone de 2 equipos promocionales de degustación para repartir entre sus regiones de ventas Sierra y Costa. El incremento en ventas (en miles de dólares) según la cantidad entera de equipos asignados es:
- Región Sierra: 0 equipos -> 0, 1 equipo -> 40, 2 equipos -> 60
- Región Costa: 0 equipos -> 0, 1 equipo -> 30, 2 equipos -> 80
Maximiza el incremento total de ventas usando Programación Dinámica y resuélvelo.
```

- **Resultado esperado:**
  - **Asignación óptima:** **0 equipos a Sierra y 2 equipos a Costa**.
  - **Incremento máximo de ventas:** **$80 (miles)**.

---

### 5.3 Ruta Logística Intermodal por Etapas (Problema de la Diligencia)
- **Escenario:** Determinar la ruta secuencial de mínimo costo para transportar levadura importada desde el puerto hasta la planta principal por etapas intermedias.

#### Prompt listo para pegar:
```text
Cervecería Nacional debe transportar un cargamento de levadura por etapas desde Guayaquil (etapa 1) hasta Quito (etapa 4) pasando por ciudades intermedias en las etapas 2 y 3. Las etapas y sus ciudades disponibles son:
- Etapa 1: Guayaquil
- Etapa 2: Riobamba, SantoDomingo
- Etapa 3: Ambato, Latacunga
- Etapa 4: Quito

Los costos de transporte en cientos de dólares entre nodos de una etapa a la siguiente son:
- De Guayaquil a Riobamba: 7
- De Guayaquil a SantoDomingo: 5
- De Riobamba a Ambato: 3
- De Riobamba a Latacunga: 4
- De SantoDomingo a Ambato: 6
- De SantoDomingo a Latacunga: 5
- De Ambato a Quito: 3
- De Latacunga a Quito: 2

Encuentra la ruta secuencial óptima por Programación Dinámica por etapas que minimiza el costo total y resuélvela.
```

- **Resultado esperado:**
  - **Ruta óptima por etapas:** **Guayaquil -> SantoDomingo -> Latacunga -> Quito**.
  - **Costo total mínimo:** **$12 (cientos de dólares)** ($5 + $5 + $2 = $12).

---

## 6. Módulo: Gestión de Inventarios Deterministas

Optimiza niveles de stock, pedidos y costos de almacenamiento utilizando modelos de la familia **EOQ / POQ / ROP**.

### 6.1 Lote Económico de Compra (EOQ Básico para Lúpulo Importado)
- **Escenario:** Optimizar la importación anual de sacos de lúpulo aromático.

#### Prompt listo para pegar:
```text
En Cervecería Nacional consumimos una demanda anual constante de 1000 sacos de lúpulo aromático importado. Colocar una orden de compra internacional tiene un costo fijo de 50 dólares por pedido, y almacenar un saco en nuestra bodega refrigerada tiene un costo de mantenimiento de 10 dólares al año. Calcula el Lote Económico de Compra (EOQ) óptimo, el número anual de pedidos y el costo total mínimo de gestión de inventarios.
```

- **Cálculo matemático:**
  - $Q^* = \sqrt{\frac{2 \cdot 1000 \cdot 50}{10}} = \sqrt{10000} = 100 \text{ sacos}$.
- **Resultado esperado:**
  - **Lote óptimo ($Q^*$):** **100 sacos de lúpulo por pedido**.
  - **Frecuencia de pedidos:** **10 pedidos al año** (cada 36.5 días).
  - **Costo anual de gestión mínimo:** **$1,000**.

---

### 6.2 EOQ con Descuentos por Cantidad para Botellas de Vidrio
- **Escenario:** Decidir si aprovechar un descuento por volumen al comprar botellas vacías a un proveedor de vidrio.

#### Prompt listo para pegar:
```text
Cervecería Nacional adquiere botellas de vidrio de 330ml con una demanda anual constante de 10,000 cajas. El costo de emitir una orden es de 100 dólares y la tasa de mantenimiento es del 20% anual sobre el precio unitario. El proveedor ofrece dos tramos de precio por volumen:
- Tramo 1 (1 a 999 cajas): Precio de 10 dólares por caja (mantenimiento $2/año).
- Tramo 2 (1,000 o más cajas): Precio con descuento de 9 dólares por caja (mantenimiento $1.80/año).
Determina la política óptima de pedido evaluando el modelo de inventarios con descuentos por cantidad y resuélvelo.
```

- **Resultado esperado:**
  - **Lote óptimo:** Pedir en lotes de **1,054 cajas** aprovechando el precio con descuento del Tramo 2 ($9/caja).
  - **Costo total mínimo:** **$91,897.37 anuales** (incluyendo el costo de adquisición de botellas).
