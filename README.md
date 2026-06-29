# Plataforma genérica de Investigación Operativa con tutoría IA

Resuelve problemas de IO de cualquier instancia ingresada por el usuario y los acompaña
con un **tutor socrático** impulsado por IA que orienta el razonamiento paso a paso,
sin dar la respuesta directamente.

---

## Stack

| Capa | Tecnología |
|------|-----------|
| Backend | Spring Boot 4.1.0 · Java 21 |
| Frontend | React + Vite (`io-ui/`) |
| LLM | Groq — `llama-3.3-70b-versatile` vía OpenAI-compatible API |
| IA framework | LangChain4j 1.13.0-beta23 |
| Embeddings | AllMiniLM-L6-V2 Quantized (local, sin API key) |
| Vector store | ChromaDB 0.6+ (API v2) |
| BD relacional | PostgreSQL 16 |
| Build | Gradle · flag `-parameters` (necesario para records + Jackson) |

---

## Estado de los módulos

| Módulo | Estado | Algoritmos implementados |
|--------|--------|--------------------------|
| Programación Lineal | **Implementado** | Simplex (≤) · Gran M (≤/≥/=) · Dos Fases (≤/≥/=) · Análisis post-óptimo |
| Transporte | TODO estructurado | Esquina Noroeste · Costo Mínimo · Vogel · MODI · Húngaro |
| Redes | TODO estructurado | Dijkstra · Kruskal · Edmonds-Karp |
| PL Entera | TODO estructurado | Branch & Bound · Gomory |
| Programación Dinámica | TODO estructurado | Asignación / Mochila / Ruta por etapas |
| Inventarios | TODO estructurado | EOQ · con faltantes · con descuentos · POQ |

**"TODO estructurado"** = la carcasa (interfaz, contrato I/O) existe pero el algoritmo
se implementa cuando el docente imparte la teoría.

---

## Endpoints implementados

Base URL: `http://localhost:8080/api/v1`

| Método | URL | Descripción |
|--------|-----|-------------|
| POST | `/lp/simplex` | Simplex estándar (solo ≤); devuelve pasos + análisis post-óptimo |
| POST | `/lp/gran-m` | Gran M (≤/≥/=); devuelve pasos + análisis post-óptimo |
| POST | `/lp/dos-fases` | Dos Fases (≤/≥/=); devuelve pasos + análisis post-óptimo |
| POST | `/ai/chat` | Chat socrático con memoria de sesión (RAM) |
| POST | `/ai/sugerir-modelo` | Extrae `ModeloLP` estructurado desde lenguaje natural |
| POST | `/ai/validar-modelo` | Valida modelo del estudiante contra enunciado original |

Los tres solvers LP devuelven en `solution`: `valores`, `holguras`, `preciosSombra`
y `rangosSensibilidad` (rangos de coeficientes de objetivo y de RHS).

Ver [`io-api/docs/API_CONTRACT.md`](io-api/docs/API_CONTRACT.md) para los cuerpos JSON completos.

---

## Tutor IA — cómo funciona

El chat es socrático: no da la solución directamente. Solo resuelve cuando el modelo
está validado y el estudiante lo pide explícitamente.

**Tres responsabilidades separadas:**

- **Comportarse** — `resources/prompts/tutor_system_prompt.txt` (system prompt adaptativo,
  cargado en runtime sin reiniciar)
- **Hacer** — cinco `@Tool` que el LLM invoca según el contexto:
  - `resolverSimplex` / `resolverGranM` / `resolverDosFases` — llaman a los solvers del dominio
  - `registrarModeloSugerido` — extrae el modelo del enunciado
  - `registrarValidacion` — evalúa el modelo del estudiante
- **Saber** — RAG con ChromaDB: el tutor recupera fragmentos de teoría relevantes antes
  de responder

**Base de conocimiento (RAG):**

```
io-api/src/main/resources/corpus/
├── investigacion-de-operaciones-taha-hamdy-2004.pdf   ← libro Taha, 200 págs, todos los módulos
└── lp/
    ├── 01_que_es_programacion_lineal.md
    ├── 02_como_formular_un_modelo_lp.md
    ├── 03_metodo_simplex_teoria.md
    ├── 04_interpretacion_de_resultados.md
    ├── 05_casos_especiales.md
    └── 06_errores_comunes_al_modelar.md
```

Los `.md` se trocean en chunks de 350 chars; el PDF en 700 chars (prosa académica densa).
Todo se almacena en la colección `io-corpus` de ChromaDB y se recupera con AllMiniLM local.

---

## Levantar el proyecto en local

### Prerequisitos

- Java 21
- Docker (para ChromaDB y PostgreSQL)
- Node 20+ (para el frontend)
- Cuenta en [Groq](https://console.groq.com/) con una API key

### Variables de entorno requeridas

```bash
GROQ_API_KEY=gsk_...          # API key de Groq
GROQ_MODEL_NAME=llama-3.3-70b-versatile   # o cualquier modelo compatible
DB_URL=jdbc:postgresql://localhost:5432/io_platform
DB_USER=io_user
DB_PASSWORD=io_pass
```

### Servicios de infraestructura

```bash
docker compose up chromadb db -d
```

### Backend

```bash
cd io-api
./gradlew bootRun
```

La primera vez (o cuando cambia el corpus) añadir las variables de re-ingesta:

```bash
# Ingesta completa — MD + libro PDF (~2-4 min, solo la primera vez)
RAG_REINGESTAR=true RAG_INCLUIR_PDF=true ./gradlew bootRun

# Re-ingesta rápida — solo archivos .md (~10 s)
RAG_REINGESTAR=true RAG_INCLUIR_PDF=false ./gradlew bootRun
```

Después volver al arranque normal (sin variables) — `RAG_REINGESTAR` es `false` por defecto.

### Frontend

```bash
cd io-ui
npm install
npm run dev
```

---

## Estructura del repositorio

```
io-project/
├── io-api/                    ← Backend Spring Boot
│   ├── src/main/java/jpap/dev/io_api/
│   │   ├── domain/            ← Java puro: solvers, records de I/O
│   │   ├── application/       ← Casos de uso (puertos)
│   │   └── infrastructure/    ← REST, JPA, LangChain4j, CORS
│   ├── src/main/resources/
│   │   ├── corpus/            ← Base de conocimiento RAG
│   │   ├── prompts/           ← System prompt del tutor
│   │   └── db/migration/      ← Flyway SQL
│   └── docs/                  ← Documentación técnica detallada
└── io-ui/                     ← Frontend React + Vite
```

**Arquitectura hexagonal** — el dominio no conoce Spring ni LangChain4j.
Las dependencias siempre fluyen hacia adentro: `infrastructure → application → domain`.

---

## Tests

```bash
cd io-api
./gradlew test
```

17 tests de dominio sin Spring (JUnit 5):
- `SimplexSolverTest` — 7 casos: MAX, MIN, no acotado, holguras, precios sombra, rangos de sensibilidad
- `GranMSolverTest` — 5 casos: LEQ+GEQ, todo-GEQ, EQ, infactible, pasos
- `DosFasesSolverTest` — 5 casos: LEQ+GEQ, todo-GEQ, EQ, infactible, orden de fases

---

## Documentación técnica

| Doc | Contenido |
|-----|-----------|
| [`docs/API_CONTRACT.md`](io-api/docs/API_CONTRACT.md) | Cuerpos JSON completos de todos los endpoints |
| [`docs/ARQUITECTURA_IA.md`](io-api/docs/ARQUITECTURA_IA.md) | Tools, ChatContextStore, RAG, system prompt |
| [`docs/RAG_IMPLEMENTATION_GUIDE.md`](io-api/docs/RAG_IMPLEMENTATION_GUIDE.md) | Guía completa del RAG (corpus, ingesta, retrieval) |
| [`docs/ESTRUCTURA_PAQUETES.md`](io-api/docs/ESTRUCTURA_PAQUETES.md) | Árbol de paquetes real del proyecto |
| [`docs/MODULOS_IO.md`](io-api/docs/MODULOS_IO.md) | Especificación matemática de cada solver |
| [`docs/FRONTEND_INTEGRATION.md`](io-api/docs/FRONTEND_INTEGRATION.md) | Guía para el agente React |
| [`CLAUDE.md`](CLAUDE.md) | Contexto completo para Claude Code |
