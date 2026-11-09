-- RF-102 · Registrar un cambio de directiva pendiente mientras sigue vigente el cargo anterior.
-- Refs: H-22.1, RF-102, DEC-03

DROP INDEX IF EXISTS siglim.ux_cargos_vigentes;

CREATE UNIQUE INDEX ux_cargos_vigentes
    ON siglim.cargos_directiva (club_id, cargo)
 WHERE hasta IS NULL AND autorizado_por IS NOT NULL;
