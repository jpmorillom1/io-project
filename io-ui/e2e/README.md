# Pruebas E2E — Playwright

Suite de **pruebas funcionales** de la plataforma Ío. Paquete aislado del bundle de `io-ui`
(Playwright vive en su propio `package.json`, no toca el `package-lock.json` de la app).

Implementa el catálogo `PW-*` y los casos `CP-*` de `entrega-final/02_PLAN_DE_PRUEBAS.md`.

## Estructura

```
e2e/
├── playwright.config.ts     # baseURL, webServer, reporters (HTML + JUnit)
├── fixtures/
│   ├── datos-io.ts          # instancias con ÓPTIMO CONOCIDO (el oráculo)
│   └── test-base.ts         # fixture `api` (request context → /api/v1)
├── api/                     # funcional por REST directo (no necesita la SPA)
│   ├── lp.spec.ts           # CP-01 · RF-LP-01/02/05/06/07
│   ├── transporte.spec.ts   # CP-04 · RF-TR-01/02/03
│   ├── redes.spec.ts        # RF-RE-01..05
│   ├── entera.spec.ts       # RF-EN-01/02
│   ├── dinamica.spec.ts     # CP-05 · RF-PD-01/02/03
│   ├── inventario.spec.ts   # CP-05 · RF-IN-01/02
│   ├── validacion.spec.ts   # CP-03 · RF-VAL-01/02 (+ VUL-02/04, defecto `dirigido`)
│   └── ai-endpoints.spec.ts # RF-IA-01..05, RF-PE-01
└── funcional/               # UI E2E (conduce la SPA)
    ├── smoke.spec.ts        # PW-SMOKE · RNF-REL-01
    └── cp02-hitl.spec.ts    # CP-02 · RF-HITL-01/02/03, RF-IA-01  (tag @ai)
```

## Requisitos

- **Node 18+**.
- Los specs de `api/` necesitan el **backend** corriendo en `API_BASE_URL`.
- Los specs de `funcional/` necesitan además la **SPA** (la arranca `webServer` en local).
- Los specs con tag **`@ai`** necesitan **`GROQ_API_KEY`** (chat con LLM). Sin la clave, omítelos.

## Puesta en marcha

```bash
cd io-ui/e2e
npm install
npm run install:browsers        # descarga Chromium

# Backend + BD + Chroma en otra terminal (raíz del repo):
#   docker compose up -d
# y el backend (io-api) en :8080
```

## Ejecución

```bash
# Todo salvo el chat con LLM (recomendado en CI sin clave):
npm run test:no-ai

# Solo funcional por REST (rápido, no arranca la SPA):
E2E_BASE_URL=http://localhost:5173 npm run test:api

# Solo UI:
npm run test:ui

# Solo los que usan el LLM (requiere GROQ_API_KEY):
GROQ_API_KEY=... npm run test:ai

# Reporte HTML:
npm run report
```

## Variables de entorno

| Variable | Default | Para qué |
|---|---|---|
| `API_BASE_URL` | `http://localhost:8080/api/v1` | base REST de los specs de `api/` |
| `E2E_BASE_URL` | `http://localhost:5173` | base de la SPA. **Si se define, NO se arranca `webServer`** (se apunta a un despliegue ya en marcha) |
| `GROQ_API_KEY` | — | habilita los specs `@ai` |
| `CI` | — | activa retries (2) y `forbidOnly` |

## Notas de diseño

- **Óptimos conocidos** en `fixtures/datos-io.ts`, verificados contra los tests JUnit del
  backend y `docs/API_CONTRACT.md`. No cambiar sin recalcular.
- Los specs `@ai` usan aserciones **estructurales** (aparece la tarjeta, se renderiza el
  resultado), nunca sobre el contenido del tutor (no determinista).
- `api/validacion.spec.ts` documenta el **defecto conocido** de `redes/asignacion` sin
  `dirigido` (HTTP 500 actual; 400 deseado) sin romper la suite.
- Selectores estables: se añadieron `data-testid` (`hitl-card`, `hitl-aprobar`,
  `hitl-rechazar`, `hitl-confirmar-rechazo`, `resultado-lp`) al frontend.
