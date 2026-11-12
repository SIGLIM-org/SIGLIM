-- Documentos de un registro (H-10.1, DEC-94): política documentos_editar y reglas de
-- tg_documentos_validar. Usa los datos de prueba_flujo.sql: Mario (21...02, menor) tiene el registro
-- 80...02 PENDIENTE_VALIDACION en «Atlético A Segunda» del club A; la Directiva A es el perfil ...05,
-- la Directiva B (club B) el ...06 y el Secretario (ámbito LIGA) el ...07. Todo corre en una
-- transacción que se deshace al final.
\o /dev/null
BEGIN;
-- Segundo directivo del club A (perfil ...11), dado de alta como dueño del esquema.
INSERT INTO siglim.perfiles (id, nombre_mostrado) VALUES ('30000000-0000-4000-8000-000000000011', 'Directiva A2');
INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
SELECT '30000000-0000-4000-8000-000000000011', id, liga_id, 'CLUB', 'c0000000-0000-4000-8000-00000000000a'
  FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB';

SET LOCAL ROLE siglim_app;

-- La Directiva A carga dos fotografías y el INE del tutor del menor.
SELECT prueba.como('30000000-0000-4000-8000-000000000005');
INSERT INTO siglim.documentos (id, tipo_documento_id, registro_id, s3_bucket, s3_clave, url, tipo_mime, tamano_bytes,
                               sha256, cargado_por)
SELECT x.id::uuid, (SELECT id FROM siglim.tipos_documento WHERE codigo = x.tipo),
       '80000000-0000-4000-8000-000000000002', 'siglim-documentos-prueba', 'liga/registros/' || x.id || '.jpg',
       'https://siglim-documentos-prueba.s3.us-east-2.amazonaws.com/liga/registros/' || x.id || '.jpg',
       'image/jpeg', 1024, repeat('a', 64), '30000000-0000-4000-8000-000000000005'
  FROM (VALUES ('d0000000-0000-4000-8000-000000000001', 'FOTOGRAFIA'),
               ('d0000000-0000-4000-8000-000000000002', 'FOTOGRAFIA'),
               ('d0000000-0000-4000-8000-000000000003', 'INE_TUTOR')) AS x (id, tipo);

-- VAL-015: un usuario de club no valida documentos, aunque sean de su club y los haya cargado.
SELECT prueba.espera_error($$UPDATE siglim.documentos SET estado_validacion = 'APROBADO'
  WHERE id = 'd0000000-0000-4000-8000-000000000003'$$, 'VAL-015');

-- El Secretario rechaza la primera fotografía y aprueba la segunda.
SELECT prueba.como('30000000-0000-4000-8000-000000000007');
UPDATE siglim.documentos
   SET estado_validacion = 'RECHAZADO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now(),
       observaciones = 'La fotografía está borrosa'
 WHERE id = 'd0000000-0000-4000-8000-000000000001';
UPDATE siglim.documentos
   SET estado_validacion = 'APROBADO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now()
 WHERE id = 'd0000000-0000-4000-8000-000000000002';

-- RF-109: un documento aprobado no vuelve a validarse.
SELECT prueba.espera_error($$UPDATE siglim.documentos SET estado_validacion = 'RECHAZADO', observaciones = 'Cambio de opinión'
  WHERE id = 'd0000000-0000-4000-8000-000000000002'$$, 'RF-109');

-- RNF-018: el archivo sigue siendo inmutable, también para la liga.
SELECT prueba.espera_error($$UPDATE siglim.documentos SET sha256 = repeat('b', 64)
  WHERE id = 'd0000000-0000-4000-8000-000000000003'$$, 'RNF-018');

-- DEC-94: el segundo directivo del club A reemplaza la fotografía rechazada que cargó el primero.
SELECT prueba.como('30000000-0000-4000-8000-000000000011');
INSERT INTO siglim.documentos (id, tipo_documento_id, registro_id, s3_bucket, s3_clave, url, tipo_mime, tamano_bytes,
                               sha256, cargado_por)
SELECT 'd0000000-0000-4000-8000-000000000004', id, '80000000-0000-4000-8000-000000000002', 'siglim-documentos-prueba',
       'liga/registros/d0000000-0000-4000-8000-000000000004.png',
       'https://siglim-documentos-prueba.s3.us-east-2.amazonaws.com/liga/registros/d0000000-0000-4000-8000-000000000004.png',
       'image/png', 2048, repeat('c', 64), '30000000-0000-4000-8000-000000000011'
  FROM siglim.tipos_documento WHERE codigo = 'FOTOGRAFIA';
WITH reemplazo AS (
  UPDATE siglim.documentos SET reemplazado_por_id = 'd0000000-0000-4000-8000-000000000004'
   WHERE id = 'd0000000-0000-4000-8000-000000000001' AND reemplazado_por_id IS NULL
  RETURNING 1)
SELECT prueba.verifica((SELECT count(*) FROM reemplazo) = 1,
  'DEC-94: otro directivo del mismo club reemplaza un documento que cargó su compañero');
SELECT prueba.verifica(
  (SELECT estado_validacion = 'RECHAZADO' FROM siglim.documentos WHERE id = 'd0000000-0000-4000-8000-000000000001'),
  'DEC-93: el documento reemplazado conserva su estado');

-- RNF-018: el reemplazo de un documento no cambia una vez puesto.
SELECT prueba.espera_error($$UPDATE siglim.documentos SET reemplazado_por_id = 'd0000000-0000-4000-8000-000000000002'
  WHERE id = 'd0000000-0000-4000-8000-000000000001'$$, 'RNF-018');

-- DEC-94: la Directiva del club B no actualiza documentos de un registro del club A (0 filas).
SELECT prueba.como('30000000-0000-4000-8000-000000000006');
WITH intento AS (
  UPDATE siglim.documentos SET reemplazado_por_id = 'd0000000-0000-4000-8000-000000000004'
   WHERE id = 'd0000000-0000-4000-8000-000000000003'
  RETURNING 1)
SELECT prueba.verifica((SELECT count(*) FROM intento) = 0,
  'DEC-94: un directivo de otro club no actualiza documentos del registro (RLS, 0 filas)');
ROLLBACK;
\o
