-- ─────────────────────────────────────────────────────────────────────────────
-- V1 — Esquema inicial de la plataforma IO
-- ─────────────────────────────────────────────────────────────────────────────

-- Sesiones de trabajo del estudiante (opcional, agrupa interacciones)
CREATE TABLE sesion (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    creada_en   TIMESTAMP   NOT NULL DEFAULT now(),
    actualizada TIMESTAMP   NOT NULL DEFAULT now()
);

-- Problemas resueltos: instancia + resultado (evidencia para la defensa)
CREATE TABLE problema_resuelto (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id   UUID        REFERENCES sesion(id) ON DELETE SET NULL,
    modulo      VARCHAR(30) NOT NULL,         -- LP, TRANSPORTE, REDES, etc.
    enunciado   TEXT,
    modelo_json JSONB,                         -- datos de entrada normalizados
    resultado   JSONB,                         -- SolveResult serializado
    resuelto_en TIMESTAMP   NOT NULL DEFAULT now()
);

-- Registro de interacciones IA (no negociable — alimenta el anexo de prompts)
CREATE TABLE interaccion_ia (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id        UUID        REFERENCES sesion(id) ON DELETE SET NULL,
    herramienta      VARCHAR(60),              -- extract | chat | analyze
    objetivo         TEXT,                     -- qué quería resolver el estudiante
    prompt           TEXT        NOT NULL,
    respuesta        TEXT,
    tool_llamada     VARCHAR(100),             -- nombre del @Tool invocado (si aplica)
    fragmentos_rag   TEXT,                     -- contexto recuperado de ChromaDB
    analisis_critico TEXT,                     -- evaluación de la respuesta
    correccion       TEXT,                     -- ajuste/corrección realizado
    fecha            TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_interaccion_sesion ON interaccion_ia(sesion_id);
CREATE INDEX idx_problema_sesion    ON problema_resuelto(sesion_id);
CREATE INDEX idx_problema_modulo    ON problema_resuelto(modulo);
