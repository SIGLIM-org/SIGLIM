-- B-06 · Renovación de registros y transferencia de jugadores entre temporadas
-- (RF-015, RF-016, RF-111, VAL-026; DEC-13 y DEC-146).
--
-- 1. siglim.registro_anterior(persona, temporada) devuelve el registro no rechazado más reciente de
--    la persona en una temporada anterior de la misma liga, con su club, equipo y temporada. Es
--    SECURITY DEFINER y de lectura mínima, como registros_en_temporada (DEC-88): el club destino de
--    una transferencia puede enlazar el registro sin ver los demás datos del club de origen.
-- 2. tg_registros_validar conserva todas sus reglas y agrega:
--    - Si el INSERT trae registro_anterior_id, el registro anterior debe ser de la misma persona, de
--      una temporada anterior y del mismo club o de una temporada que permita el cambio de club
--      (RF-015, RF-016, RF-111, VAL-026). El enlace no cambia después del alta.
--    - Al activar, VAL-001 cuenta también los documentos vigentes APROBADO de los registros
--      anteriores no rechazados del mismo club: la renovación reutiliza los documentos (RF-015). La cadena se
--      corta cuando cambia el club, así que una transferencia no hereda documentos del club de
--      origen.
-- 3. Índice para recorrer la cadena de registro_anterior_id.
--
-- El servicio valida antes y explica; el trigger es la última garantía. Se aplica al Supabase
-- compartido al fusionar en develop (DEC-52).
--
-- Refs: B-06, RF-015, RF-016, RF-111, VAL-001, VAL-026, DEC-13, DEC-146

-- 1. Registro anterior de la persona en una temporada anterior -----------------------------------

CREATE FUNCTION siglim.registro_anterior(p_persona uuid, p_temporada uuid)
RETURNS TABLE (registro_id uuid, club_id uuid, club_nombre text, equipo_id uuid, equipo_nombre text,
               temporada_id uuid, temporada_nombre text, estado siglim.estado_registro)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$
  SELECT r.id, c.id, c.nombre::text, e.id, e.nombre::text, t.id, t.nombre::text, r.estado
    FROM registros_jugador r
    JOIN equipos e ON e.id = r.equipo_id
    JOIN clubes c ON c.id = e.club_id
    JOIN temporadas t ON t.id = r.temporada_id
   WHERE r.persona_id = p_persona
     AND r.estado <> 'RECHAZADO'
     AND c.liga_id = (SELECT liga_id FROM temporadas WHERE id = p_temporada)
     AND t.fecha_inicio < (SELECT fecha_inicio FROM temporadas WHERE id = p_temporada)
   ORDER BY t.fecha_inicio DESC, r.creado_en DESC, r.id DESC
   LIMIT 1
$$;
COMMENT ON FUNCTION siglim.registro_anterior(uuid, uuid) IS
  'Registro no rechazado más reciente de la persona en una temporada anterior de la liga, con club y equipo, aunque RLS no lo muestre al club destino; el backend solo expone lo necesario para la renovación o transferencia (RF-015, RF-016, DEC-146).';

REVOKE EXECUTE ON FUNCTION siglim.registro_anterior(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION siglim.registro_anterior(uuid, uuid) TO siglim_app;

-- 2. Validación del alta con registro anterior y documentos heredados de la renovación -------------

CREATE OR REPLACE FUNCTION siglim.tg_registros_validar() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$
DECLARE
  v_equipo     siglim.equipos%ROWTYPE;
  v_otro       record;
  v_anterior   record;
  v_permite    boolean;
  v_faltantes  text;
BEGIN
  SELECT * INTO v_equipo FROM siglim.equipos WHERE id = NEW.equipo_id;
  NEW.temporada_id := siglim.temporada_de_grupo(v_equipo.grupo_id);

  IF TG_OP = 'INSERT' THEN
    IF v_equipo.estado <> 'ACTIVO' THEN
      PERFORM siglim.error_negocio('RF-013', 'El equipo está dado de baja y no admite registros.');
    END IF;
    -- VAL-017: CURP con registro activo en la temporada (mensaje explicativo antes del índice único)
    SELECT c.nombre AS club, e.nombre AS equipo INTO v_otro
    FROM siglim.registros_jugador r JOIN siglim.equipos e ON e.id = r.equipo_id JOIN siglim.clubes c ON c.id = e.club_id
    WHERE r.persona_id = NEW.persona_id AND r.temporada_id = NEW.temporada_id
      AND r.estado IN ('PENDIENTE_VALIDACION', 'ACTIVO', 'SUSPENDIDO')
    LIMIT 1;
    IF FOUND THEN
      PERFORM siglim.error_negocio('VAL-017', format('La CURP ya tiene registro en la temporada: club %s, equipo %s.', v_otro.club, v_otro.equipo));
    END IF;
    -- VAL-026 / RN-039: sin cambio de club dentro de la temporada
    IF EXISTS (SELECT 1 FROM siglim.registros_jugador r JOIN siglim.equipos e ON e.id = r.equipo_id
               WHERE r.persona_id = NEW.persona_id AND r.temporada_id = NEW.temporada_id
                 AND r.estado <> 'RECHAZADO' AND e.club_id <> v_equipo.club_id) THEN
      PERFORM siglim.error_negocio('VAL-026', 'No se permite cambiar de club dentro de la misma temporada.');
    END IF;
    -- RF-015, RF-016, RF-111, VAL-026: el registro anterior de una renovación o transferencia
    IF NEW.registro_anterior_id IS NOT NULL THEN
      SELECT r.persona_id, e.club_id, r.temporada_id, t.fecha_inicio, r.estado, c.liga_id
        INTO v_anterior
        FROM siglim.registros_jugador r
        JOIN siglim.equipos e ON e.id = r.equipo_id
        JOIN siglim.clubes c ON c.id = e.club_id
        JOIN siglim.temporadas t ON t.id = r.temporada_id
       WHERE r.id = NEW.registro_anterior_id;
      IF NOT FOUND THEN
        PERFORM siglim.error_negocio('RF-015', 'El registro anterior no existe.');
      END IF;
      IF v_anterior.persona_id <> NEW.persona_id THEN
        PERFORM siglim.error_negocio('RF-015', 'El registro anterior no es de la misma persona.');
      END IF;
      IF v_anterior.estado = 'RECHAZADO' THEN
        PERFORM siglim.error_negocio('RF-015', 'El registro anterior fue rechazado: no se puede renovar ni transferir.');
      END IF;
      IF v_anterior.liga_id <> (SELECT liga_id FROM siglim.clubes WHERE id = v_equipo.club_id) THEN
        PERFORM siglim.error_negocio('RF-015', 'El registro anterior es de otra liga.');
      END IF;
      IF v_anterior.temporada_id = NEW.temporada_id THEN
        PERFORM siglim.error_negocio('VAL-026', 'La renovación o transferencia es entre temporadas: el registro anterior es de la misma temporada.');
      END IF;
      IF v_anterior.fecha_inicio >= (SELECT fecha_inicio FROM siglim.temporadas WHERE id = NEW.temporada_id) THEN
        PERFORM siglim.error_negocio('RF-015', 'La temporada destino debe ser posterior a la del registro anterior.');
      END IF;
      IF v_anterior.club_id <> v_equipo.club_id THEN
        SELECT p.permite_cambio_club INTO v_permite
          FROM siglim.parametros_vigentes(NEW.temporada_id) p;
        IF NOT coalesce(v_permite, true) THEN
          PERFORM siglim.error_negocio('RF-111', 'La temporada destino no permite cambios de club.');
        END IF;
      END IF;
    END IF;
  ELSIF NEW.equipo_id <> OLD.equipo_id THEN
    PERFORM siglim.error_negocio('RN-039', 'Un registro no cambia de equipo; dé de baja y registre de nuevo si la regla lo permite.');
  END IF;

  IF TG_OP = 'UPDATE' THEN
    IF NEW.registro_anterior_id IS DISTINCT FROM OLD.registro_anterior_id THEN
      PERFORM siglim.error_negocio('RF-015', 'El registro anterior no cambia después del alta.');
    END IF;
    IF NEW.estado IS DISTINCT FROM OLD.estado
       AND NOT ((OLD.estado = 'PENDIENTE_VALIDACION' AND NEW.estado IN ('ACTIVO', 'RECHAZADO'))
             OR (OLD.estado = 'ACTIVO' AND NEW.estado IN ('SUSPENDIDO', 'BAJA'))
             OR (OLD.estado = 'SUSPENDIDO' AND NEW.estado IN ('ACTIVO', 'BAJA'))) THEN
      PERFORM siglim.error_negocio('RF-017', format('Transición de registro no permitida: %s → %s.', OLD.estado, NEW.estado));
    END IF;
    -- VAL-015 (DEC-101): solo la liga valida un registro o cambia quién y cuándo lo validó
    IF ((OLD.estado = 'PENDIENTE_VALIDACION' AND NEW.estado IN ('ACTIVO', 'RECHAZADO'))
        OR (NEW.validado_por, NEW.validado_en) IS DISTINCT FROM (OLD.validado_por, OLD.validado_en))
       AND NOT siglim.es_usuario_liga() THEN
      PERFORM siglim.error_negocio('VAL-015', 'Solo la liga valida registros de jugadores.');
    END IF;
  END IF;

  IF TG_OP = 'UPDATE' AND NEW.estado IS DISTINCT FROM OLD.estado THEN
    IF siglim.motivo_actual() IS NULL THEN
      PERFORM siglim.error_negocio('RN-021', 'El cambio de estado del registro requiere motivo.');
    END IF;
    IF NEW.estado = 'ACTIVO' AND OLD.estado = 'PENDIENTE_VALIDACION' THEN
      -- VAL-001 (DEC-101, DEC-146): documentos vigentes aprobados del registro y, si es una
      -- renovación, de los registros anteriores del mismo club (la cadena se corta al cambiar club).
      SELECT string_agg(td.nombre, ', ' ORDER BY rd.tipo_documento_id) INTO v_faltantes
        FROM siglim.requisitos_documento rd
        JOIN siglim.tipos_documento td ON td.id = rd.tipo_documento_id
       WHERE rd.liga_id = (SELECT cl.liga_id FROM siglim.clubes cl WHERE cl.id = v_equipo.club_id)
         AND rd.tipo_categoria = (SELECT c.tipo
                                    FROM siglim.grupos g
                                    JOIN siglim.categorias_temporada ct ON ct.id = g.categoria_temporada_id
                                    JOIN siglim.categorias c ON c.id = ct.categoria_id
                                   WHERE g.id = v_equipo.grupo_id)
         AND (NOT rd.solo_menores OR coalesce(siglim.es_menor(NEW.persona_id, NEW.alta_en), false))
         AND (SELECT count(*) FROM siglim.documentos d
                JOIN (WITH RECURSIVE cadena_documentos AS (
                        SELECT r.id, r.registro_anterior_id
                          FROM siglim.registros_jugador r
                         WHERE r.id = NEW.id
                        UNION ALL
                        SELECT r.id, r.registro_anterior_id
                          FROM siglim.registros_jugador r
                          JOIN cadena_documentos c ON r.id = c.registro_anterior_id
                          JOIN siglim.equipos e ON e.id = r.equipo_id
                         WHERE e.club_id = v_equipo.club_id AND r.estado <> 'RECHAZADO'
                      )
                      SELECT id FROM cadena_documentos) cadena ON cadena.id = d.registro_id
               WHERE d.tipo_documento_id = rd.tipo_documento_id
                 AND d.reemplazado_por_id IS NULL AND d.estado_validacion = 'APROBADO') < rd.cantidad;
      IF v_faltantes IS NOT NULL THEN
        PERFORM siglim.error_negocio('VAL-001',
          format('El registro no se activa: faltan documentos aprobados (%s).', v_faltantes));
      END IF;
      -- VAL-025: un menor necesita tutor vigente y consentimiento antes de activarse
      IF siglim.es_menor(NEW.persona_id, NEW.alta_en) THEN
        IF NOT EXISTS (SELECT 1 FROM siglim.tutelas t WHERE t.menor_id = NEW.persona_id AND t.vigente)
           OR NOT EXISTS (SELECT 1 FROM siglim.consentimientos c JOIN siglim.tutelas t
                            ON t.menor_id = c.persona_id AND t.tutor_id = c.otorgado_por_id AND t.vigente
                          WHERE c.persona_id = NEW.persona_id AND c.revocado_en IS NULL) THEN
          PERFORM siglim.error_negocio('VAL-025', 'El registro de un menor requiere tutor vigente y su consentimiento.');
        END IF;
      END IF;
    END IF;
  END IF;
  RETURN NEW;
END $$;
COMMENT ON FUNCTION siglim.tg_registros_validar() IS
  'Alta: equipo activo (RF-013), CURP única en la temporada (VAL-017), sin cambio de club (VAL-026) y registro anterior de una temporada previa del mismo club o con cambio de club permitido (RF-015, RF-016, RF-111). Cambio: el registro anterior no cambia, sin cambio de equipo (RN-039), transiciones de §4.3.3 (RF-017), validación solo por la liga (VAL-015), motivo (RN-021) y, al activar, documentos aplicables aprobados (VAL-001) —también los de los registros anteriores del mismo club— y tutor vigente con consentimiento de un menor (VAL-025) (DEC-101, DEC-146).';

-- 3. Índice de la cadena de renovaciones ----------------------------------------------------------

CREATE INDEX ix_registros_anterior ON siglim.registros_jugador (registro_anterior_id)
  WHERE registro_anterior_id IS NOT NULL;
COMMENT ON INDEX siglim.ix_registros_anterior IS
  'Recorre la cadena de registro_anterior_id para heredar documentos de la renovación (RF-015, DEC-146).';
