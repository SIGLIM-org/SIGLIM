-- M-02/M-07 · Parámetros de la advertencia de localía (RF-120, VAL-027, RN-049, DEC-34; B-15.1).
-- fechas_fase_regular: total de fechas de la fase regular; sin él no se sabe cuáles son las
-- últimas y la regla de la unidad deportiva no se evalúa. fechas_finales_unidad_deportiva: las
-- últimas N fechas en las que se advierte una cancha externa (10 [DEMO]). Se cambian con una
-- versión nueva de parámetros, no con código (AGENTS.md §3.3).

ALTER TABLE siglim.parametros_temporada
  ADD COLUMN fechas_fase_regular smallint,
  ADD COLUMN fechas_finales_unidad_deportiva smallint NOT NULL DEFAULT 10,
  ADD CONSTRAINT ck_parametros_localia CHECK (
    (fechas_fase_regular IS NULL OR fechas_fase_regular > 0)
    AND fechas_finales_unidad_deportiva >= 0);

COMMENT ON COLUMN siglim.parametros_temporada.fechas_fase_regular IS
  'Total de fechas (jornadas) de la fase regular; NULL = sin definir y no se evalúa la regla de las últimas fechas (RN-049, B-15.1).';
COMMENT ON COLUMN siglim.parametros_temporada.fechas_finales_unidad_deportiva IS
  'Últimas fechas de la fase regular en las que se advierte una cancha fuera de la unidad deportiva (10 [DEMO], RF-120, VAL-027, DEC-34).';
