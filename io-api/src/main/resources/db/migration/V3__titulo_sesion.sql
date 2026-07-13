-- V3 — Título de la sesión.
-- La barra lateral del chat lista las conversaciones por nombre. El título lo redacta
-- un modelo pequeño (ver TituloSesionService) a partir del primer mensaje del estudiante;
-- mientras tanto la fila lleva un recorte del enunciado, nunca NULL para una sesión nueva.
ALTER TABLE sesion ADD COLUMN titulo VARCHAR(120);

-- Las sesiones que ya existían no tienen título: se les pone el recorte del enunciado.
UPDATE sesion SET titulo = left(enunciado, 60) WHERE enunciado IS NOT NULL;

-- La lista se lee siempre ordenada por actividad reciente.
CREATE INDEX idx_sesion_actualizada ON sesion(actualizada DESC);
