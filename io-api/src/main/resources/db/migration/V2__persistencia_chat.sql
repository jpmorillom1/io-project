-- V2 — Persistencia de la sesión de chat.
-- Hasta ahora la memoria conversacional vivía en RAM (MessageWindowChatMemory) y se
-- perdía al reiniciar el proceso. Aquí se le da un respaldo en PostgreSQL.

-- Ventana viva de mensajes que LangChain4j lee y escribe en cada turno.
-- Una sola fila por sesión: el contrato de ChatMemoryStore es de reemplazo total.
CREATE TABLE chat_memory (
    sesion_id   UUID        PRIMARY KEY REFERENCES sesion(id) ON DELETE CASCADE,
    mensajes    JSONB       NOT NULL,      -- serializado con ChatMessageSerializer
    actualizado TIMESTAMP   NOT NULL DEFAULT now()
);

-- Módulo activo de la sesión: reemplaza el ConcurrentHashMap de TutorSupervisorService.
ALTER TABLE sesion ADD COLUMN modulo_activo VARCHAR(20);

-- Primer mensaje del estudiante. Es el enunciado en lenguaje natural que permite
-- mapear x1 -> "mesas" al explicar resultados (ver docs/SENSIBILIDAD_CHAT.md §6.2).
ALTER TABLE sesion ADD COLUMN enunciado TEXT;

-- El historial se lee siempre por sesión y en orden cronológico.
CREATE INDEX idx_interaccion_sesion_fecha ON interaccion_ia(sesion_id, fecha);
