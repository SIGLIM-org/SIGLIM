-- Prueba de regresión: toda función del esquema siglim fija su search_path.
-- Evita que vuelva el aviso 0011 del asesor de seguridad de Supabase («function_search_path_mutable»)
-- al agregar funciones en migraciones futuras. Refs: RNF-013, ADR-0014.
DO $$
DECLARE
  v_sin_search_path text;
  v_total integer;
BEGIN
  SELECT count(*),
         string_agg(p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')', ', ')
           FILTER (WHERE NOT EXISTS (SELECT 1 FROM unnest(coalesce(p.proconfig, '{}')) c
                                     WHERE c LIKE 'search_path=%'))
    INTO v_total, v_sin_search_path
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'siglim' AND p.prokind IN ('f', 'p');

  IF v_sin_search_path IS NOT NULL THEN
    RAISE EXCEPTION 'Funciones de siglim sin search_path fijo: %', v_sin_search_path;
  END IF;
  RAISE NOTICE 'OK  RNF-013: las % funciones de siglim fijan search_path', v_total;
END $$;

-- Identidad global por CURP (H-09.1, DEC-78). Usa los datos de prueba_flujo.sql: la Directiva B
-- (perfil ...06) no ve por RLS a Juan (21...01) ni a Mario (21...02), creados por la Directiva A.
\o /dev/null
SELECT prueba.verifica(
  (SELECT bool_and(p.prosecdef) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'siglim' AND p.proname IN ('persona_por_curp', 'es_menor', 'es_tutor_vigente')),
  'DEC-78: persona_por_curp, es_menor y es_tutor_vigente son SECURITY DEFINER');
SELECT prueba.verifica(
  NOT has_function_privilege('siglim_publico', 'siglim.persona_por_curp(text)', 'EXECUTE')
  AND NOT has_function_privilege('siglim_publico', 'siglim.es_tutor_vigente(uuid, uuid)', 'EXECUTE')
  AND has_function_privilege('siglim_app', 'siglim.persona_por_curp(text)', 'EXECUTE'),
  'DEC-78: solo siglim_app ejecuta las funciones de identidad');

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000006');   -- Directiva B
SELECT prueba.verifica((SELECT count(*) FROM siglim.personas WHERE id = '21000000-0000-4000-8000-000000000001') = 0,
  'RNF-012: la directiva B sigue sin ver por RLS a la persona del club A');
SELECT prueba.verifica((SELECT id FROM siglim.persona_por_curp('JUGA950505HMNRRE05')) = '21000000-0000-4000-8000-000000000001',
  'DEC-78: persona_por_curp encuentra la CURP aunque RLS no muestre a la persona');
SELECT prueba.verifica(NOT EXISTS (SELECT 1 FROM siglim.persona_por_curp('JUGA950505HMNRRE0')),
  'DEC-78: persona_por_curp no encuentra una CURP incompleta');
SELECT prueba.verifica(siglim.es_menor('21000000-0000-4000-8000-000000000002') IS TRUE,
  'RN-055: es_menor ve a un menor de otro club');
SELECT prueba.verifica(siglim.es_menor('21000000-0000-4000-8000-000000000001') IS FALSE,
  'RN-055: es_menor distingue a un adulto de otro club');
SELECT prueba.verifica(siglim.es_menor(gen_random_uuid()) IS NULL,
  'DEC-78: es_menor devuelve NULL si la persona no existe');
INSERT INTO siglim.tutelas (menor_id, tutor_id, parentesco, creado_por) VALUES
  ('21000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000003', 'Padre', '30000000-0000-4000-8000-000000000006');
SELECT prueba.verifica(siglim.es_tutor_vigente('21000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000003'),
  'VAL-025: es_tutor_vigente reconoce la tutela vigente');
SELECT prueba.verifica(NOT siglim.es_tutor_vigente('21000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000001'),
  'VAL-025: es_tutor_vigente rechaza a quien no es tutor');
RESET ROLE;   -- la directiva B no puede editar la tutela de un menor que no ve (tutelas_editar)
UPDATE siglim.tutelas SET vigente = false
 WHERE menor_id = '21000000-0000-4000-8000-000000000002' AND tutor_id = '21000000-0000-4000-8000-000000000003';
SET LOCAL ROLE siglim_app;
SELECT prueba.verifica(NOT siglim.es_tutor_vigente('21000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000003'),
  'VAL-025: es_tutor_vigente ignora una tutela que ya no está vigente');
ROLLBACK;

SELECT prueba.verifica(
  (SELECT es_demo AND version = 'v1' AND finalidades = ARRAY['REGISTRO_DEPORTIVO', 'DIFUSION']
     FROM siglim.avisos_privacidad
    WHERE liga_id = 'a0000000-0000-4000-8000-000000000001' AND tipo = 'SIMPLIFICADO' AND version = 'v1'),
  'DEC-74: aviso v1 SIMPLIFICADO de demostración con sus finalidades');

-- Alta de un registro (H-09.2, DEC-88 a DEC-90). Usa los datos de prueba_flujo.sql: Juan (21...01) y
-- Mario (21...02, menor sin tutela) están registrados en «Atlético A Segunda» del club A; la
-- Directiva B (perfil ...06) no los ve por RLS.
SELECT prueba.verifica(
  (SELECT bool_and(p.prosecdef) AND count(*) = 2 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'siglim' AND p.proname IN ('registros_en_temporada', 'requisitos_menor')),
  'DEC-90: registros_en_temporada y requisitos_menor son SECURITY DEFINER');
SELECT prueba.verifica(
  has_function_privilege('siglim_app', 'siglim.registros_en_temporada(uuid, uuid)', 'EXECUTE')
  AND has_function_privilege('siglim_app', 'siglim.requisitos_menor(uuid)', 'EXECUTE')
  AND NOT has_function_privilege('siglim_publico', 'siglim.registros_en_temporada(uuid, uuid)', 'EXECUTE')
  AND NOT has_function_privilege('siglim_publico', 'siglim.requisitos_menor(uuid)', 'EXECUTE')
  AND NOT has_function_privilege('anon', 'siglim.registros_en_temporada(uuid, uuid)', 'EXECUTE')
  AND NOT has_function_privilege('authenticated', 'siglim.requisitos_menor(uuid)', 'EXECUTE'),
  'DEC-90: solo siglim_app ejecuta registros_en_temporada y requisitos_menor');
SELECT prueba.verifica(
  obj_description('siglim.ux_registros_activo'::regclass, 'pg_class') LIKE 'VAL-017:%',
  'DEC-90: ux_registros_activo documenta VAL-017 para traducir su 23505');

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000006');   -- Directiva B
SELECT prueba.verifica(
  (SELECT count(*) = 1 AND bool_and(equipo_nombre = 'Atlético A Segunda' AND club_nombre = 'Club Atlético A')
     FROM siglim.registros_en_temporada('21000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001')),
  'VAL-017: registros_en_temporada nombra club y equipo aunque RLS no muestre el registro');
SELECT prueba.verifica(NOT EXISTS (SELECT 1 FROM siglim.registros_en_temporada(gen_random_uuid(), '50000000-0000-4000-8000-000000000001')),
  'VAL-017: registros_en_temporada no devuelve nada a una persona sin registros');
SELECT prueba.verifica(
  (SELECT NOT tiene_tutor_vigente AND NOT tiene_consentimiento FROM siglim.requisitos_menor('21000000-0000-4000-8000-000000000002')),
  'VAL-025: requisitos_menor sin tutela ni consentimiento');
INSERT INTO siglim.tutelas (menor_id, tutor_id, parentesco, creado_por) VALUES
  ('21000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000003', 'Padre', '30000000-0000-4000-8000-000000000006');
SELECT prueba.verifica(
  (SELECT tiene_tutor_vigente AND NOT tiene_consentimiento FROM siglim.requisitos_menor('21000000-0000-4000-8000-000000000002')),
  'VAL-025: requisitos_menor con tutor vigente pero sin consentimiento');
INSERT INTO siglim.consentimientos (persona_id, otorgado_por_id, aviso_id, finalidades, registrado_por)
SELECT '21000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000003', id,
       ARRAY['REGISTRO_DEPORTIVO'], '30000000-0000-4000-8000-000000000006'
  FROM siglim.avisos_privacidad
 WHERE liga_id = 'a0000000-0000-4000-8000-000000000001' AND tipo = 'SIMPLIFICADO' AND version = 'v1';
SELECT prueba.verifica(
  (SELECT tiene_tutor_vigente AND tiene_consentimiento FROM siglim.requisitos_menor('21000000-0000-4000-8000-000000000002')),
  'VAL-025: requisitos_menor reconoce el consentimiento del tutor vigente');
ROLLBACK;
\o
