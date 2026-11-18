-- B-06 · El alta normal respeta RF-111 (DEC-154).
--
-- tg_registros_validar conserva todas sus reglas (migraciones 20261112180000 y 20261117190000) y
-- agrega: en un INSERT sin registro_anterior_id, si el registro anterior más reciente de la persona
-- (siglim.registro_anterior) es de otro club y la versión vigente de los parámetros de la temporada
-- destino no permite el cambio de club, el alta se rechaza con RF-111, como la transferencia. Sin
-- versión de parámetros rige el valor por omisión de la tabla (true). No obliga a usar la renovación
-- o la transferencia: eso sigue pendiente (DEC-146).
--
-- El servicio valida antes y explica; el trigger es la última garantía. Se aplica al Supabase
-- compartido al fusionar en develop (DEC-52).
--
-- Refs: B-06, RF-111, DEC-13, DEC-146, DEC-154

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
    ELSE
      -- RF-111 (DEC-154): un alta normal tampoco cambia de club entre temporadas si la temporada
      -- destino no lo permite. Se compara con el registro anterior más reciente, el mismo que usa la
      -- transferencia. Obligar a usar la renovación o la transferencia sigue pendiente (DEC-146).
      SELECT ra.club_id INTO v_anterior
        FROM siglim.registro_anterior(NEW.persona_id, NEW.temporada_id) ra;
      IF FOUND AND v_anterior.club_id <> v_equipo.club_id THEN
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
  'Alta: equipo activo (RF-013), CURP única en la temporada (VAL-017), sin cambio de club (VAL-026) y registro anterior de una temporada previa del mismo club o con cambio de club permitido (RF-015, RF-016, RF-111); un alta sin registro anterior tampoco cambia de club entre temporadas si la temporada no lo permite (RF-111, DEC-154). Cambio: el registro anterior no cambia, sin cambio de equipo (RN-039), transiciones de §4.3.3 (RF-017), validación solo por la liga (VAL-015), motivo (RN-021) y, al activar, documentos aplicables aprobados (VAL-001) —también los de los registros anteriores del mismo club— y tutor vigente con consentimiento de un menor (VAL-025) (DEC-101, DEC-146, DEC-154).';
