-- H-09.1 · Personas, tutela y consentimiento: identidad global por CURP y aviso de privacidad v1.
--
-- 1. Lectura mínima de identidad (DEC-78). Las políticas personas_leer y tutelas_leer solo muestran
--    a un ámbito CLUB las personas de su club o las que creó, así que una Directiva no podría
--    reutilizar a una persona que estuvo en otro club ni saber si su CURP ya existe (RF-010). Estas
--    funciones SECURITY DEFINER devuelven solo lo indispensable; el PATCH de una persona sigue
--    sujeto a RLS.
-- 2. avisos_privacidad.es_demo y el aviso v1 SIMPLIFICADO de demostración, rotulado «No oficial»
--    (DEC-74, DEC-80). Se aplica al Supabase compartido al fusionar en develop (DEC-52).
--
-- Refs: H-09.1, RF-010, RF-011, RF-097, RF-106, VAL-025, DEC-74, DEC-78, DEC-80

-- 1. Funciones de identidad ------------------------------------------------------------------

CREATE FUNCTION siglim.persona_por_curp(p_curp text)
RETURNS TABLE (id uuid, curp text, nombre text, apellido_paterno text, apellido_materno text)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$
  SELECT p.id, p.curp::text, p.nombre::text, p.apellido_paterno::text, p.apellido_materno::text
    FROM personas p
   WHERE p.curp = CAST(p_curp AS bpchar)
$$;
COMMENT ON FUNCTION siglim.persona_por_curp(text) IS
  'Identidad mínima de la persona con esa CURP, sin datos de contacto y sin importar el club (RF-010, DEC-78).';

-- Misma firma y cuerpo que en la migración inicial; ahora SECURITY DEFINER para que el backend sepa
-- si una persona existe y si es menor aunque RLS no se la muestre (NULL = no existe).
CREATE OR REPLACE FUNCTION siglim.es_menor(p_persona uuid, p_fecha date DEFAULT current_date) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$ SELECT fecha_nacimiento > (p_fecha - interval '18 years')::date FROM personas WHERE id = p_persona $$;
COMMENT ON FUNCTION siglim.es_menor(uuid, date) IS
  'Verdadero si la persona tiene menos de 18 años en la fecha indicada; NULL si no existe (RN-055, DEC-78).';

CREATE FUNCTION siglim.es_tutor_vigente(p_menor uuid, p_tutor uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$
  SELECT EXISTS (SELECT 1 FROM tutelas t
                  WHERE t.menor_id = p_menor AND t.tutor_id = p_tutor AND t.vigente)
$$;
COMMENT ON FUNCTION siglim.es_tutor_vigente(uuid, uuid) IS
  'Verdadero si existe una tutela vigente entre el menor y el tutor, la haya registrado el club que sea (VAL-025, DEC-78).';

REVOKE EXECUTE ON FUNCTION siglim.persona_por_curp(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION siglim.es_menor(uuid, date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION siglim.es_tutor_vigente(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION siglim.persona_por_curp(text) TO siglim_app;
GRANT EXECUTE ON FUNCTION siglim.es_menor(uuid, date) TO siglim_app;
GRANT EXECUTE ON FUNCTION siglim.es_tutor_vigente(uuid, uuid) TO siglim_app;

-- 2. Aviso de privacidad de demostración ---------------------------------------------------------

ALTER TABLE siglim.avisos_privacidad ADD COLUMN es_demo boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN siglim.avisos_privacidad.es_demo IS
  'Valor de demostración [DEMO]: configurable y rotulado «no oficial» en la interfaz.';

INSERT INTO siglim.avisos_privacidad (liga_id, tipo, version, contenido, finalidades, vigente_desde, es_demo)
VALUES (
  'a0000000-0000-4000-8000-000000000001',
  'SIMPLIFICADO',
  'v1',
  'AVISO DE PRIVACIDAD SIMPLIFICADO DE DEMOSTRACIÓN · NO OFICIAL. Este texto es un ejemplo para el '
  || 'proyecto académico SIGLIM y no ha sido aprobado por la Liga.' || E'\n\n'
  || 'Responsable: la Liga Municipal de Futbol Amateur Morelia es la responsable del tratamiento de '
  || 'los datos personales que se registran en SIGLIM.' || E'\n\n'
  || 'Finalidades: (1) REGISTRO_DEPORTIVO: identificar a la persona por su CURP, registrarla como '
  || 'jugador, tutor, directivo o árbitro y verificar su elegibilidad para competir. (2) DIFUSION: '
  || 'publicar en el portal de la Liga su nombre deportivo, equipo y resultados, sin datos de '
  || 'contacto ni documentos. Si la persona es menor de edad, su tutor otorga el consentimiento.' || E'\n\n'
  || 'Derechos ARCO: el titular o su tutor pueden solicitar el acceso, la rectificación, la '
  || 'cancelación o la oposición al tratamiento de sus datos ante la Liga, que registra y da '
  || 'seguimiento a cada solicitud. Una cancelación se atiende bloqueando los datos, porque el '
  || 'historial deportivo no se borra.',
  ARRAY['REGISTRO_DEPORTIVO', 'DIFUSION'],
  DATE '2026-11-11',
  true
)
ON CONFLICT DO NOTHING;
