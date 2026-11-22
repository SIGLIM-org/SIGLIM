-- H-10.2 · Validación del registro de jugador por la liga: VAL-015 y VAL-001 en tg_registros_validar
-- (DEC-101).
--
-- La política registros_editar deja actualizar un registro a cualquier usuario que ve su club, y el
-- trigger no revisaba quién validaba: una Directiva podía activar su propio registro desde la base,
-- con documentos pendientes o sin ellos. tg_registros_validar se reescribe con la misma firma,
-- SECURITY DEFINER y search_path fijo; conserva todas sus reglas (RF-013, VAL-017, VAL-026 y RN-039
-- del INSERT; RN-039, RF-017, RN-021 y VAL-025 del UPDATE) y agrega dos:
--
-- 1. VAL-015: la transición PENDIENTE_VALIDACION → ACTIVO | RECHAZADO, o cualquier cambio de
--    validado_por o validado_en, solo la hace un usuario de liga (es_usuario_liga()).
-- 2. VAL-001: al pasar de PENDIENTE_VALIDACION a ACTIVO, cada requisito aplicable
--    (requisitos_documento de la liga del club del equipo para el tipo de la categoría; los
--    solo_menores si la persona era menor en alta_en) necesita al menos «cantidad» documentos del
--    registro vigentes (reemplazado_por_id IS NULL) en APROBADO. Es la misma regla que
--    RequisitosDocumentos.listoParaValidar en el backend (DEC-99).
--
-- Orden de las reglas del UPDATE: RF-017 → VAL-015 → RN-021 → VAL-001 → VAL-025. El INE del tutor es
-- un documento del registro: su falta es VAL-001, no VAL-025 (DEC-89, DEC-99). Las transiciones
-- desde ACTIVO y SUSPENDIDO no cambian. La política RLS no cambia.
--
-- El backend valida todo antes y explica (RegistroJugadorService.validar); el trigger es la última
-- garantía. VAL-001 (422) se agrega a TablaCodigosHttp y a convenciones de API §3.2 en el mismo
-- cambio. Se aplica al Supabase compartido al fusionar en develop (DEC-52).
--
-- Refs: H-10.2, RF-017, RF-109, RN-021, VAL-001, VAL-015, VAL-025, DEC-99, DEC-101

CREATE OR REPLACE FUNCTION siglim.tg_registros_validar() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$
DECLARE
  v_equipo     siglim.equipos%ROWTYPE;
  v_otro       record;
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
  ELSIF NEW.equipo_id <> OLD.equipo_id THEN
    PERFORM siglim.error_negocio('RN-039', 'Un registro no cambia de equipo; dé de baja y registre de nuevo si la regla lo permite.');
  END IF;

  IF TG_OP = 'UPDATE' THEN
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
      -- VAL-001 (DEC-101): todos los documentos aplicables, vigentes y aprobados
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
               WHERE d.registro_id = NEW.id AND d.tipo_documento_id = rd.tipo_documento_id
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
  'Alta: equipo activo (RF-013), CURP única en la temporada (VAL-017) y sin cambio de club (VAL-026). Cambio: sin cambio de equipo (RN-039), transiciones de §4.3.3 (RF-017), validación solo por la liga (VAL-015), motivo (RN-021) y, al activar, documentos aplicables aprobados (VAL-001) y tutor vigente con consentimiento de un menor (VAL-025) (DEC-101).';
