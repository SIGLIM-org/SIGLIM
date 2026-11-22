-- H-10.1 · Documentos de un registro de jugador: reemplazo por la Directiva del club y validación por
-- la liga (DEC-94).
--
-- 1. Política documentos_editar. Solo dejaba actualizar a un usuario de liga o a quien cargó el
--    documento: otro directivo del mismo club no podía reemplazarlo (el UPDATE que fija
--    reemplazado_por_id afectaba 0 filas sin error). Se recrea con USING y WITH CHECK: usuario de
--    liga, quien lo cargó, o un usuario de ámbito CLUB cuando el documento es de un registro de un
--    equipo de su club. No se abre al ámbito PROPIO: el jugador y su tutor solo consultan.
-- 2. tg_documentos_validar (misma firma, SECURITY DEFINER y search_path fijo) conserva el archivo
--    inmutable (RNF-018) y agrega tres reglas:
--    - reemplazado_por_id no cambia una vez puesto (RNF-018): el historial de reemplazos no se
--      reescribe;
--    - estado_validacion solo cambia desde PENDIENTE (RF-109): un documento aprobado o rechazado no
--      vuelve a validarse; un rechazado se corrige con un reemplazo;
--    - solo un usuario de liga cambia estado_validacion, validado_por o validado_en (VAL-015).
--
-- El backend valida todo antes y explica (DocumentoRegistroService); el trigger y la política son la
-- última garantía. RF-109 (409) y VAL-015 (403) se agregan a TablaCodigosHttp y a convenciones de API
-- §3.2 en el mismo cambio. Se aplica al Supabase compartido al fusionar en develop (DEC-52).
--
-- Refs: H-10.1, RF-108, RF-109, RNF-018, VAL-015, DEC-93, DEC-94

-- 1. Quién actualiza un documento ---------------------------------------------------------------

DROP POLICY documentos_editar ON siglim.documentos;
CREATE POLICY documentos_editar ON siglim.documentos FOR UPDATE TO siglim_app
  USING (siglim.es_usuario_liga()
      OR cargado_por = siglim.usuario_actual()
      OR (registro_id IS NOT NULL
          AND EXISTS (SELECT 1 FROM siglim.registros_jugador r
                        JOIN siglim.equipos e ON e.id = r.equipo_id
                       WHERE r.id = registro_id AND e.club_id IN (SELECT siglim.clubes_usuario()))))
  WITH CHECK (siglim.es_usuario_liga()
      OR cargado_por = siglim.usuario_actual()
      OR (registro_id IS NOT NULL
          AND EXISTS (SELECT 1 FROM siglim.registros_jugador r
                        JOIN siglim.equipos e ON e.id = r.equipo_id
                       WHERE r.id = registro_id AND e.club_id IN (SELECT siglim.clubes_usuario()))));
COMMENT ON POLICY documentos_editar ON siglim.documentos IS
  'Actualiza un documento el usuario de liga, quien lo cargó o la Directiva del club del equipo del registro (reemplazo por otro directivo del mismo club). Sin ámbito PROPIO (DEC-94).';

-- 2. Reglas del trigger ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION siglim.tg_documentos_validar() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$
BEGIN
  IF (NEW.s3_bucket, NEW.s3_clave, NEW.url, NEW.sha256, NEW.tamano_bytes)
     IS DISTINCT FROM (OLD.s3_bucket, OLD.s3_clave, OLD.url, OLD.sha256, OLD.tamano_bytes) THEN
    PERFORM siglim.error_negocio('RNF-018', 'El archivo de un documento no cambia; cargue un documento nuevo que lo reemplace.');
  END IF;
  IF OLD.reemplazado_por_id IS NOT NULL AND NEW.reemplazado_por_id IS DISTINCT FROM OLD.reemplazado_por_id THEN
    PERFORM siglim.error_negocio('RNF-018', 'Este documento ya fue reemplazado; su reemplazo no cambia.');
  END IF;
  IF (NEW.estado_validacion, NEW.validado_por, NEW.validado_en)
     IS DISTINCT FROM (OLD.estado_validacion, OLD.validado_por, OLD.validado_en)
     AND NOT es_usuario_liga() THEN
    PERFORM siglim.error_negocio('VAL-015', 'Solo la liga valida documentos.');
  END IF;
  IF NEW.estado_validacion IS DISTINCT FROM OLD.estado_validacion AND OLD.estado_validacion <> 'PENDIENTE' THEN
    PERFORM siglim.error_negocio('RF-109', 'Solo se valida un documento pendiente; un documento rechazado se corrige cargando uno que lo reemplace.');
  END IF;
  RETURN NEW;
END $$;
COMMENT ON FUNCTION siglim.tg_documentos_validar() IS
  'Archivo inmutable y reemplazo que no cambia (RNF-018); la validación solo parte de PENDIENTE (RF-109) y solo la hace un usuario de liga (VAL-015) (DEC-94).';
