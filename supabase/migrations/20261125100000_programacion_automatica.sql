-- =====================================================================================================
-- M-07 · Programación automática de fechas (T-06)
--
-- Motivo: DEC-173 reabre la programación automática que el ERS Rev. 0.4.1 deja fuera (§2.6, DEC-06).
-- El Programador genera una vez el calendario de la temporada (método del círculo, DEC-174) y, cada
-- semana, asigna cancha, horario y árbitro central a todos los partidos de la fecha (H-32 a H-35).
-- Diseño en docs/planning/programacion-automatica.md §3 y algoritmo en ADR-0015.
--
-- Efecto:
--   1. parametros_temporada: vueltas (1 a 4) y horario de juego por defecto 08:00 a 18:00 [DEMO]
--      (DEC-174, DEC-176). ADD COLUMN con DEFAULT no dispara tg_parametros_inmutable: las versiones
--      existentes reciben los valores por defecto.
--   2. categorias_temporada.dias_juego (ISO 8601: 1 = lunes … 7 = domingo; DEC-177), con backfill por
--      categorias.tipo (LIBRE → domingo; INFANTIL y VETERANOS → sábado [DEMO]) y un trigger BEFORE
--      INSERT que aplica la misma regla cuando viene NULL.
--   3. fechas_programacion (DEC-175) y jornadas.fecha_id: la fecha agrupa las jornadas de todos los
--      grupos de una semana, una por grupo.
--   4. emparejamientos (DEC-174, DEC-180): el calendario por grupo y fecha; lo que no cabe queda sin
--      partido, con código y causa. partidos no cambia (cancha e inicio siguen NOT NULL). El trigger
--      tg_emparejamientos_equipo_unico garantiza un solo emparejamiento por equipo, grupo y fecha
--      en cualquier papel (RN-012).
--   5. partidos.asignado_automaticamente y designaciones.automatica (trazabilidad, RF-041).
--   6. Permiso arbitraje.designacion:AUTOMATICA para PROGRAMADOR (DEC-182). No entra en
--      pares_incompatibles.
--   7. Tablas nuevas con RLS y la política app_sin_restriccion (el bucle de la §10 de la migración
--      inicial solo corrió una vez), SELECT, INSERT y UPDATE a siglim_app sin DELETE: nunca se borra un
--      emparejamiento ni una fecha; al regenerar se cancelan los partidos (DEC-181).
--
-- Regla: no se editan las migraciones ya aplicadas (supabase/README.md, ADR-0004).
-- Refs: T-06, DEC-173 a DEC-182, ADR-0015, RF-034, RF-035, RF-037, RF-041, RF-042, RN-016, RN-017,
--       RN-012, VAL-007, DD-005
-- =====================================================================================================

-- 1. Parámetros de la temporada (versionados) ---------------------------------------------------------
ALTER TABLE siglim.parametros_temporada
  ADD COLUMN vueltas            smallint NOT NULL DEFAULT 1,
  ADD COLUMN hora_inicio_juego  time     NOT NULL DEFAULT '08:00',
  ADD COLUMN hora_fin_juego     time     NOT NULL DEFAULT '18:00',
  ADD CONSTRAINT ck_parametros_vueltas CHECK (vueltas BETWEEN 1 AND 4),
  ADD CONSTRAINT ck_parametros_horario_juego CHECK (hora_fin_juego > hora_inicio_juego);

COMMENT ON COLUMN siglim.parametros_temporada.vueltas IS
  'Vueltas del calendario por método del círculo: cada par de equipos del grupo se enfrenta una vez por vuelta, alternando localía (1 a 4; 1 por defecto; DEC-174, RF-034).';
COMMENT ON COLUMN siglim.parametros_temporada.hora_inicio_juego IS
  'Hora de inicio del horario de juego por defecto de cada día de la fecha (08:00 [DEMO], DEC-176). Si la cancha tiene periodos DISPONIBLE ese día, se usan esos.';
COMMENT ON COLUMN siglim.parametros_temporada.hora_fin_juego IS
  'Hora de fin del horario de juego por defecto; el último bloque de cancha debe terminar a esta hora o antes (18:00 [DEMO], DEC-176).';

-- 2. Días de juego por categoría de la temporada (DEC-177) --------------------------------------------
ALTER TABLE siglim.categorias_temporada ADD COLUMN dias_juego smallint[];
UPDATE siglim.categorias_temporada ct
   SET dias_juego = CASE c.tipo WHEN 'LIBRE' THEN '{7}'::smallint[] ELSE '{6}'::smallint[] END
  FROM siglim.categorias c WHERE c.id = ct.categoria_id;
ALTER TABLE siglim.categorias_temporada
  ALTER COLUMN dias_juego SET NOT NULL,
  ADD CONSTRAINT ck_categorias_temporada_dias CHECK (
    cardinality(dias_juego) BETWEEN 1 AND 7 AND dias_juego <@ '{1,2,3,4,5,6,7}'::smallint[]);

COMMENT ON COLUMN siglim.categorias_temporada.dias_juego IS
  'Días de la semana en que juega la categoría (ISO 8601: 1 = lunes … 7 = domingo). Inicial por tipo [DEMO]: LIBRE → {7}; INFANTIL y VETERANOS → {6}; editable. Si ningún día de la fecha coincide, se usan todos con advertencia (DEC-177).';

-- Si dias_juego viene NULL, lo llena con la regla inicial por tipo de categoría (DEC-177).
-- Sin SECURITY DEFINER: siglim_app ya lee categorias.
CREATE FUNCTION siglim.tg_categorias_temporada_dias_juego() RETURNS trigger
LANGUAGE plpgsql
SET search_path = siglim, pg_temp
AS $$
BEGIN
  IF NEW.dias_juego IS NULL THEN
    SELECT CASE c.tipo WHEN 'LIBRE' THEN '{7}'::smallint[] ELSE '{6}'::smallint[] END
      INTO NEW.dias_juego
      FROM siglim.categorias c WHERE c.id = NEW.categoria_id;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_categorias_temporada_dias_juego BEFORE INSERT ON siglim.categorias_temporada
  FOR EACH ROW EXECUTE FUNCTION siglim.tg_categorias_temporada_dias_juego();

COMMENT ON FUNCTION siglim.tg_categorias_temporada_dias_juego() IS
  'Llena categorias_temporada.dias_juego cuando se inserta NULL: LIBRE → {7}; INFANTIL y VETERANOS → {6} [DEMO] (DEC-177).';

-- 3. Fechas de programación (DEC-175) -----------------------------------------------------------------
CREATE TABLE siglim.fechas_programacion (
  id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  temporada_id           uuid NOT NULL REFERENCES siglim.temporadas (id),
  numero                 smallint NOT NULL,
  dias                   date[] NOT NULL,
  generada_por           uuid REFERENCES siglim.perfiles (id),
  generada_en            timestamptz,
  parametros_generacion  jsonb,
  creado_por             uuid REFERENCES siglim.perfiles (id),
  creado_en              timestamptz NOT NULL DEFAULT now(),
  actualizado_en         timestamptz NOT NULL DEFAULT now(),
  UNIQUE (temporada_id, numero),
  CONSTRAINT ck_fechas_numero CHECK (numero > 0),
  CONSTRAINT ck_fechas_dias CHECK (cardinality(dias) BETWEEN 1 AND 7)
);
CREATE INDEX ix_fechas_programacion_generada_por ON siglim.fechas_programacion (generada_por) WHERE generada_por IS NOT NULL;
CREATE INDEX ix_fechas_programacion_creado_por ON siglim.fechas_programacion (creado_por) WHERE creado_por IS NOT NULL;
-- temporada_id queda cubierta por el UNIQUE (temporada_id, numero) (columna inicial).
CREATE TRIGGER tg_fechas_programacion_actualizado_en BEFORE UPDATE ON siglim.fechas_programacion
  FOR EACH ROW EXECUTE FUNCTION siglim.tg_actualizado_en();

COMMENT ON TABLE siglim.fechas_programacion IS
  'M-07 · Fecha de la temporada: agrupa las jornadas de todos los grupos de una semana; se envía y se aprueba completa (DEC-175, RF-034, RF-041).';
COMMENT ON COLUMN siglim.fechas_programacion.id IS 'Identificador único.';
COMMENT ON COLUMN siglim.fechas_programacion.temporada_id IS 'Temporada a la que pertenece la fecha.';
COMMENT ON COLUMN siglim.fechas_programacion.numero IS 'Número consecutivo de la fecha en la temporada (> 0; único por temporada; DEC-175).';
COMMENT ON COLUMN siglim.fechas_programacion.dias IS 'Días calendario de la fecha (1 a 7), normalmente sábado y domingo; se cruzan con categorias_temporada.dias_juego (DEC-176, DEC-177).';
COMMENT ON COLUMN siglim.fechas_programacion.generada_por IS 'Programador que ejecutó la última asignación automática de cancha, horario y árbitro (RF-041).';
COMMENT ON COLUMN siglim.fechas_programacion.generada_en IS 'Fecha y hora de la última asignación automática (RF-041).';
COMMENT ON COLUMN siglim.fechas_programacion.parametros_generacion IS 'Datos de la última asignación automática: versión de parámetros, modo (pendientes o regenerar), motivo y totales asignados y sin asignar (RF-041, DEC-181).';
COMMENT ON COLUMN siglim.fechas_programacion.creado_por IS 'Usuario que creó la fecha (al generar el calendario, DEC-174).';
COMMENT ON COLUMN siglim.fechas_programacion.creado_en IS 'Fecha y hora de creación.';
COMMENT ON COLUMN siglim.fechas_programacion.actualizado_en IS 'Fecha y hora de la última modificación.';

ALTER TABLE siglim.jornadas ADD COLUMN fecha_id uuid REFERENCES siglim.fechas_programacion (id);
CREATE UNIQUE INDEX ux_jornadas_fecha_grupo ON siglim.jornadas (fecha_id, grupo_id) WHERE fecha_id IS NOT NULL;
-- fecha_id queda cubierta por ux_jornadas_fecha_grupo (columna inicial, solo filas con fecha).

COMMENT ON COLUMN siglim.jornadas.fecha_id IS
  'Fecha de programación a la que pertenece la jornada; una jornada por grupo y fecha. NULL en jornadas manuales y de fases finales (DEC-175).';
COMMENT ON INDEX siglim.ux_jornadas_fecha_grupo IS 'DEC-175: una sola jornada por grupo en cada fecha de programación.';

-- 4. Emparejamientos del calendario (DEC-174, DEC-180) ------------------------------------------------
CREATE TABLE siglim.emparejamientos (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  grupo_id             uuid NOT NULL REFERENCES siglim.grupos (id),
  vuelta               smallint NOT NULL,
  numero_fecha         smallint NOT NULL,
  equipo_local_id      uuid NOT NULL REFERENCES siglim.equipos (id),
  equipo_visitante_id  uuid REFERENCES siglim.equipos (id),
  partido_id           uuid UNIQUE REFERENCES siglim.partidos (id),
  codigo_no_asignado   varchar(20),
  causa_no_asignado    text,
  creado_por           uuid REFERENCES siglim.perfiles (id),
  creado_en            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_emparejamientos_numeros CHECK (vuelta > 0 AND numero_fecha > 0),
  CONSTRAINT ck_emparejamientos_equipos CHECK (equipo_visitante_id IS NULL OR equipo_visitante_id <> equipo_local_id),
  CONSTRAINT ck_emparejamientos_descanso CHECK (equipo_visitante_id IS NOT NULL OR partido_id IS NULL)
);
CREATE UNIQUE INDEX ux_emparejamientos_local ON siglim.emparejamientos (grupo_id, numero_fecha, equipo_local_id);
CREATE UNIQUE INDEX ux_emparejamientos_visitante ON siglim.emparejamientos (grupo_id, numero_fecha, equipo_visitante_id)
  WHERE equipo_visitante_id IS NOT NULL;
CREATE INDEX ix_emparejamientos_equipo_local ON siglim.emparejamientos (equipo_local_id);
CREATE INDEX ix_emparejamientos_equipo_visitante ON siglim.emparejamientos (equipo_visitante_id) WHERE equipo_visitante_id IS NOT NULL;
CREATE INDEX ix_emparejamientos_creado_por ON siglim.emparejamientos (creado_por) WHERE creado_por IS NOT NULL;
-- grupo_id queda cubierta por ux_emparejamientos_local y partido_id por su UNIQUE.

COMMENT ON TABLE siglim.emparejamientos IS
  'M-07 · Calendario por grupo: quién juega contra quién en cada fecha y vuelta, con descansos (método del círculo; DEC-174, RF-034, RF-037). Lo que no cabe queda sin partido, con código y causa (DEC-180, VAL-007). Nunca se borra (DEC-181).';
COMMENT ON COLUMN siglim.emparejamientos.id IS 'Identificador único.';
COMMENT ON COLUMN siglim.emparejamientos.grupo_id IS 'Grupo cuyo calendario forma el emparejamiento.';
COMMENT ON COLUMN siglim.emparejamientos.vuelta IS 'Vuelta del calendario (> 0, hasta parametros_temporada.vueltas; DEC-174).';
COMMENT ON COLUMN siglim.emparejamientos.numero_fecha IS 'Número de la fecha del grupo en la temporada (> 0); corresponde a fechas_programacion.numero (DEC-175).';
COMMENT ON COLUMN siglim.emparejamientos.equipo_local_id IS 'Equipo local; en un descanso, el equipo que descansa (RF-037). Un equipo aparece una sola vez por grupo y fecha.';
COMMENT ON COLUMN siglim.emparejamientos.equipo_visitante_id IS 'Equipo visitante; NULL = descanso (RF-037).';
COMMENT ON COLUMN siglim.emparejamientos.partido_id IS 'Partido creado al asignar cancha y horario; NULL mientras no se asigna o si no cupo. Un descanso nunca tiene partido (DEC-180).';
COMMENT ON COLUMN siglim.emparejamientos.codigo_no_asignado IS 'Código del ERS que explica por qué no se asignó (VAL-007, VAL-006 …; DEC-180).';
COMMENT ON COLUMN siglim.emparejamientos.causa_no_asignado IS 'Explicación legible de por qué el emparejamiento quedó sin partido (DEC-180).';
COMMENT ON COLUMN siglim.emparejamientos.creado_por IS 'Programador que generó el calendario (DEC-174).';
COMMENT ON COLUMN siglim.emparejamientos.creado_en IS 'Fecha y hora de creación.';
COMMENT ON INDEX siglim.ux_emparejamientos_local IS 'DEC-174: un equipo juega o descansa una sola vez por grupo y fecha (como local).';
COMMENT ON INDEX siglim.ux_emparejamientos_visitante IS 'DEC-174: un equipo juega una sola vez por grupo y fecha (como visitante).';

-- RN-012: un equipo aparece una sola vez por grupo y fecha, sin importar el papel (local, visitante o
-- descanso). Los índices únicos solo cubren el mismo papel; VAL-003 solo detecta traslape de horario y
-- no impide que un equipo juegue sábado y domingo de la misma fecha.
CREATE FUNCTION siglim.tg_emparejamientos_equipo_unico() RETURNS trigger
LANGUAGE plpgsql
SET search_path = siglim, pg_temp
AS $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM siglim.emparejamientos e
     WHERE e.id <> NEW.id
       AND e.grupo_id = NEW.grupo_id
       AND e.numero_fecha = NEW.numero_fecha
       AND (NEW.equipo_local_id IN (e.equipo_local_id, e.equipo_visitante_id)
            OR (NEW.equipo_visitante_id IS NOT NULL
                AND NEW.equipo_visitante_id IN (e.equipo_local_id, e.equipo_visitante_id)))) THEN
    PERFORM siglim.error_negocio('RN-012', 'El equipo ya tiene un emparejamiento en esta fecha del grupo.');
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_emparejamientos_equipo_unico
  BEFORE INSERT OR UPDATE OF grupo_id, numero_fecha, equipo_local_id, equipo_visitante_id ON siglim.emparejamientos
  FOR EACH ROW EXECUTE FUNCTION siglim.tg_emparejamientos_equipo_unico();

COMMENT ON FUNCTION siglim.tg_emparejamientos_equipo_unico() IS
  'RN-012: rechaza un emparejamiento si alguno de sus equipos ya aparece como local, visitante o descanso en otro emparejamiento del mismo grupo y fecha (DEC-174).';
COMMENT ON TRIGGER tg_emparejamientos_equipo_unico ON siglim.emparejamientos IS
  'RN-012: un equipo tiene un solo emparejamiento por grupo y fecha, en cualquier papel (DEC-174).';

-- 5. Trazabilidad de lo automático (RF-041) -----------------------------------------------------------
ALTER TABLE siglim.partidos      ADD COLUMN asignado_automaticamente boolean NOT NULL DEFAULT false;
ALTER TABLE siglim.designaciones ADD COLUMN automatica               boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN siglim.partidos.asignado_automaticamente IS
  'true si la cancha y el horario los asignó la programación automática (H-33, RF-041, DEC-175); un ajuste manual posterior se registra en la auditoría.';
COMMENT ON COLUMN siglim.designaciones.automatica IS
  'true si el árbitro central lo designó la asignación automática (H-34, DEC-182); el cambio manual lo hace la Comisión de Arbitraje.';

-- 6. Permiso de la designación automática (DEC-182) ---------------------------------------------------
INSERT INTO siglim.permisos (codigo, modulo, descripcion)
VALUES ('arbitraje.designacion:AUTOMATICA', 'M-05', 'Designar automáticamente al árbitro central (H-34)')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO siglim.rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id
  FROM siglim.roles r
  JOIN siglim.permisos p ON p.codigo = 'arbitraje.designacion:AUTOMATICA'
 WHERE r.codigo = 'PROGRAMADOR'
ON CONFLICT DO NOTHING;

-- 7. Seguridad de las tablas nuevas (ADR-0007, ADR-0014) ----------------------------------------------
ALTER TABLE siglim.fechas_programacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE siglim.emparejamientos     ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_sin_restriccion ON siglim.fechas_programacion TO siglim_app USING (true) WITH CHECK (true);
CREATE POLICY app_sin_restriccion ON siglim.emparejamientos     TO siglim_app USING (true) WITH CHECK (true);

-- Sin DELETE: nada con historial se borra (RN-005, DEC-181).
GRANT SELECT, INSERT, UPDATE ON siglim.fechas_programacion, siglim.emparejamientos TO siglim_app;
