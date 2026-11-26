-- =====================================================================================================
-- M-07 · Calendario vigente: reemplazo lógico de los emparejamientos (H-32.2)
--
-- Motivo: H-32.2 expone la generación del calendario de la temporada (POST /temporadas/{id}/calendario).
-- Regenerar el calendario de un grupo que todavía no tiene partidos ligados debe conservar el anterior
-- como historial (RN-020: nada con historial se borra) y dejar libres los índices únicos y la regla
-- RN-012 para el calendario nuevo (DEC-189). Un emparejamiento que ya tiene partido no se reemplaza.
--
-- Efecto:
--   1. emparejamientos: vigente (true por defecto), reemplazado_por, reemplazado_en y motivo_reemplazo.
--      CHECK: una fila no vigente dice cuándo y por qué se reemplazó, y no tiene partido.
--   2. ux_emparejamientos_local y ux_emparejamientos_visitante solo cuentan las filas vigentes.
--   3. tg_emparejamientos_equipo_unico (RN-012) solo compara una fila vigente contra las vigentes, y se
--      dispara también al cambiar vigente.
--   4. tg_emparejamientos_reemplazo (RN-020): una fila reemplazada no se modifica, y una fila con
--      partido no se reemplaza.
--   5. Las filas existentes quedan vigentes (DEFAULT true); no cambia ningún dato.
--
-- Regla: no se editan las migraciones ya aplicadas (supabase/README.md, ADR-0004).
-- Refs: H-32.2, DEC-174, DEC-189, RN-012, RN-020, RN-021, RF-034, ADR-0015
-- =====================================================================================================

-- 1. Columnas del reemplazo lógico --------------------------------------------------------------------
ALTER TABLE siglim.emparejamientos
  ADD COLUMN vigente           boolean NOT NULL DEFAULT true,
  ADD COLUMN reemplazado_por   uuid REFERENCES siglim.perfiles (id),
  ADD COLUMN reemplazado_en    timestamptz,
  ADD COLUMN motivo_reemplazo  text,
  ADD CONSTRAINT ck_emparejamientos_reemplazo CHECK (
    vigente OR (reemplazado_en IS NOT NULL AND motivo_reemplazo IS NOT NULL)),
  ADD CONSTRAINT ck_emparejamientos_reemplazo_sin_partido CHECK (vigente OR partido_id IS NULL);

CREATE INDEX ix_emparejamientos_reemplazado_por ON siglim.emparejamientos (reemplazado_por)
  WHERE reemplazado_por IS NOT NULL;

COMMENT ON TABLE siglim.emparejamientos IS
  'M-07 · Calendario por grupo: quién juega contra quién en cada fecha y vuelta, con descansos (método del círculo; DEC-174, RF-034, RF-037). Lo que no cabe queda sin partido, con código y causa (DEC-180, VAL-007). Nunca se borra (DEC-181): regenerar el calendario de un grupo sin partidos marca las filas anteriores vigente = false con motivo (reemplazo lógico, DEC-189).';
COMMENT ON COLUMN siglim.emparejamientos.vigente IS
  'true mientras la fila pertenece al calendario actual del grupo; false cuando se reemplazó al regenerar (DEC-189). Solo las vigentes cuentan para los índices únicos, RN-012 y las consultas.';
COMMENT ON COLUMN siglim.emparejamientos.reemplazado_por IS
  'Programador que regeneró el calendario y reemplazó la fila (DEC-189).';
COMMENT ON COLUMN siglim.emparejamientos.reemplazado_en IS
  'Fecha y hora del reemplazo; obligatoria si la fila no es vigente (DEC-189).';
COMMENT ON COLUMN siglim.emparejamientos.motivo_reemplazo IS
  'Motivo con que se regeneró el calendario (al menos 10 caracteres en el servicio, RN-021); obligatorio si la fila no es vigente (DEC-189).';
COMMENT ON CONSTRAINT ck_emparejamientos_reemplazo ON siglim.emparejamientos IS
  'DEC-189: una fila reemplazada dice cuándo y por qué.';
COMMENT ON CONSTRAINT ck_emparejamientos_reemplazo_sin_partido ON siglim.emparejamientos IS
  'RN-020, DEC-189: una fila con partido no se reemplaza, así que una no vigente nunca tiene partido.';

-- 2. Índices únicos solo sobre el calendario vigente --------------------------------------------------
DROP INDEX siglim.ux_emparejamientos_local;
DROP INDEX siglim.ux_emparejamientos_visitante;
CREATE UNIQUE INDEX ux_emparejamientos_local ON siglim.emparejamientos (grupo_id, numero_fecha, equipo_local_id)
  WHERE vigente;
CREATE UNIQUE INDEX ux_emparejamientos_visitante ON siglim.emparejamientos (grupo_id, numero_fecha, equipo_visitante_id)
  WHERE vigente AND equipo_visitante_id IS NOT NULL;
-- grupo_id queda cubierta por ix_emparejamientos_grupo (las filas no vigentes ya no entran en el único).
CREATE INDEX ix_emparejamientos_grupo ON siglim.emparejamientos (grupo_id, numero_fecha);

COMMENT ON INDEX siglim.ux_emparejamientos_local IS
  'DEC-174, DEC-189: un equipo juega o descansa una sola vez por grupo y fecha (como local) en el calendario vigente.';
COMMENT ON INDEX siglim.ux_emparejamientos_visitante IS
  'DEC-174, DEC-189: un equipo juega una sola vez por grupo y fecha (como visitante) en el calendario vigente.';
COMMENT ON INDEX siglim.ix_emparejamientos_grupo IS
  'Calendario de un grupo por fecha, vigente o reemplazado (DEC-189).';

-- 3. RN-012 solo entre filas vigentes -----------------------------------------------------------------
-- Una fila que ya era historial (OLD.vigente = false) no se evalúa aquí: los triggers BEFORE corren por
-- orden de nombre y tg_emparejamientos_reemplazo, que va después, la rechaza siempre con RN-020.
CREATE OR REPLACE FUNCTION siglim.tg_emparejamientos_equipo_unico() RETURNS trigger
LANGUAGE plpgsql
SET search_path = siglim, pg_temp
AS $$
BEGIN
  IF TG_OP = 'UPDATE' AND NOT OLD.vigente THEN
    RETURN NEW;
  END IF;
  IF NEW.vigente AND EXISTS (
    SELECT 1 FROM siglim.emparejamientos e
     WHERE e.id <> NEW.id
       AND e.vigente
       AND e.grupo_id = NEW.grupo_id
       AND e.numero_fecha = NEW.numero_fecha
       AND (NEW.equipo_local_id IN (e.equipo_local_id, e.equipo_visitante_id)
            OR (NEW.equipo_visitante_id IS NOT NULL
                AND NEW.equipo_visitante_id IN (e.equipo_local_id, e.equipo_visitante_id)))) THEN
    PERFORM siglim.error_negocio('RN-012', 'El equipo ya tiene un emparejamiento en esta fecha del grupo.');
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER tg_emparejamientos_equipo_unico ON siglim.emparejamientos;
CREATE TRIGGER tg_emparejamientos_equipo_unico
  BEFORE INSERT OR UPDATE OF grupo_id, numero_fecha, equipo_local_id, equipo_visitante_id, vigente
  ON siglim.emparejamientos
  FOR EACH ROW EXECUTE FUNCTION siglim.tg_emparejamientos_equipo_unico();

COMMENT ON FUNCTION siglim.tg_emparejamientos_equipo_unico() IS
  'RN-012: rechaza un emparejamiento vigente si alguno de sus equipos ya aparece como local, visitante o descanso en otro emparejamiento vigente del mismo grupo y fecha (DEC-174, DEC-189). Las filas reemplazadas no cuentan.';
COMMENT ON TRIGGER tg_emparejamientos_equipo_unico ON siglim.emparejamientos IS
  'RN-012: un equipo tiene un solo emparejamiento vigente por grupo y fecha, en cualquier papel (DEC-174, DEC-189).';

-- 4. RN-020: el historial no se toca y lo que tiene partido no se reemplaza ---------------------------
CREATE FUNCTION siglim.tg_emparejamientos_reemplazo() RETURNS trigger
LANGUAGE plpgsql
SET search_path = siglim, pg_temp
AS $$
BEGIN
  IF NOT OLD.vigente THEN
    PERFORM siglim.error_negocio('RN-020', 'El calendario reemplazado se conserva como historial y no se modifica.');
  END IF;
  IF NOT NEW.vigente AND OLD.partido_id IS NOT NULL THEN
    PERFORM siglim.error_negocio('RN-020', 'El emparejamiento ya tiene un partido y no se puede reemplazar.');
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_emparejamientos_reemplazo BEFORE UPDATE ON siglim.emparejamientos
  FOR EACH ROW EXECUTE FUNCTION siglim.tg_emparejamientos_reemplazo();

COMMENT ON FUNCTION siglim.tg_emparejamientos_reemplazo() IS
  'RN-020, DEC-189: una fila reemplazada (vigente = false) no se modifica, y una fila vigente con partido no se marca como reemplazada.';
COMMENT ON TRIGGER tg_emparejamientos_reemplazo ON siglim.emparejamientos IS
  'RN-020: el calendario reemplazado es historial inalterable; lo que ya tiene partido no se regenera (DEC-189).';
