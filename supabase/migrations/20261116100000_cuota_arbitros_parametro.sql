-- M-02 · Parámetro [DEMO] de la cuota de árbitros por club (RF-114, DEC-17).
-- El 4 es el valor de demostración de la Liga: se cambia con una versión nueva de parámetros,
-- no con código (AGENTS.md §3.3, DEC-72).

ALTER TABLE siglim.parametros_temporada
  ADD COLUMN equipos_por_arbitro smallint NOT NULL DEFAULT 4,
  ADD CONSTRAINT ck_parametros_arbitros CHECK (equipos_por_arbitro > 0);

COMMENT ON COLUMN siglim.parametros_temporada.equipos_por_arbitro IS
  'Equipos activos del club por cada árbitro validado que debe presentar (4 [DEMO], RF-114, DEC-17).';
