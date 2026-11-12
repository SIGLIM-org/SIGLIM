-- Validación de un registro (H-10.2, DEC-101): VAL-015 y VAL-001 en tg_registros_validar. Usa los
-- datos de prueba_flujo.sql: Mario (21...02, menor) tiene el registro 80...02 PENDIENTE_VALIDACION en
-- «Atlético A Segunda» (categoría LIBRE: 1 IDENTIFICACION, 2 FOTOGRAFIA y, por ser menor, 1
-- INE_TUTOR) del club A; la Directiva A es el perfil ...05 y el Secretario (ámbito LIGA) el ...07.
-- Se crean dos adultos en el mismo equipo: Lucas (21...04, registro 80...03), que se activa, y Tomás
-- (21...05, registro 80...04, sin documentos), que se rechaza. Todo corre en una transacción que se
-- deshace al final.
\o /dev/null
BEGIN;
-- Los dos adultos y sus registros pendientes, dados de alta como dueño del esquema.
INSERT INTO siglim.personas (id, curp, nombre, apellido_paterno, fecha_nacimiento, creado_por) VALUES
  ('21000000-0000-4000-8000-000000000004', 'LUCA970808HMNRRH08', 'Lucas', 'Adulto', '1997-08-08', '30000000-0000-4000-8000-000000000005'),
  ('21000000-0000-4000-8000-000000000005', 'TOMA750909HMNRRI09', 'Tomás', 'Adulto', '1975-09-09', '30000000-0000-4000-8000-000000000005');
INSERT INTO siglim.registros_jugador (id, persona_id, equipo_id, temporada_id, creado_por) VALUES
  ('80000000-0000-4000-8000-000000000003', '21000000-0000-4000-8000-000000000004', '60000000-0000-4000-8000-00000000000a',
   '50000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005'),
  ('80000000-0000-4000-8000-000000000004', '21000000-0000-4000-8000-000000000005', '60000000-0000-4000-8000-00000000000a',
   '50000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005');

SET LOCAL ROLE siglim_app;

-- La Directiva A carga la identificación y una fotografía del adulto, y los documentos de Mario
-- (identificación, dos fotografías y el INE del tutor).
SELECT prueba.como('30000000-0000-4000-8000-000000000005');
INSERT INTO siglim.documentos (id, tipo_documento_id, registro_id, s3_bucket, s3_clave, url, tipo_mime, tamano_bytes,
                               sha256, cargado_por)
SELECT x.id::uuid, (SELECT id FROM siglim.tipos_documento WHERE codigo = x.tipo), x.registro::uuid,
       'siglim-documentos-prueba', 'liga/registros/' || x.id || '.jpg',
       'https://siglim-documentos-prueba.s3.us-east-2.amazonaws.com/liga/registros/' || x.id || '.jpg',
       'image/jpeg', 1024, repeat('a', 64), '30000000-0000-4000-8000-000000000005'
  FROM (VALUES ('d2000000-0000-4000-8000-000000000001', 'IDENTIFICACION', '80000000-0000-4000-8000-000000000003'),
               ('d2000000-0000-4000-8000-000000000002', 'FOTOGRAFIA',     '80000000-0000-4000-8000-000000000003'),
               ('d2000000-0000-4000-8000-000000000011', 'IDENTIFICACION', '80000000-0000-4000-8000-000000000002'),
               ('d2000000-0000-4000-8000-000000000012', 'FOTOGRAFIA',     '80000000-0000-4000-8000-000000000002'),
               ('d2000000-0000-4000-8000-000000000013', 'FOTOGRAFIA',     '80000000-0000-4000-8000-000000000002'),
               ('d2000000-0000-4000-8000-000000000014', 'INE_TUTOR',      '80000000-0000-4000-8000-000000000002'))
       AS x (id, tipo, registro);

-- VAL-015: la Directiva A no activa ni rechaza un registro de su club, ni se pone como validadora.
SELECT prueba.como('30000000-0000-4000-8000-000000000005', 'La Directiva intenta validar');
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET estado = 'ACTIVO'
  WHERE id = '80000000-0000-4000-8000-000000000003'$$, 'VAL-015');
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET estado = 'RECHAZADO'
  WHERE id = '80000000-0000-4000-8000-000000000003'$$, 'VAL-015');
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET validado_por = '30000000-0000-4000-8000-000000000005'
  WHERE id = '80000000-0000-4000-8000-000000000003'$$, 'VAL-015');

-- VAL-001: faltan una fotografía y todo está PENDIENTE.
SELECT prueba.como('30000000-0000-4000-8000-000000000007', 'Registro validado por la liga');
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET estado = 'ACTIVO'
  WHERE id = '80000000-0000-4000-8000-000000000003'$$, 'VAL-001');

-- La Directiva A carga la segunda fotografía; con los tres documentos solo PENDIENTE sigue faltando.
SELECT prueba.como('30000000-0000-4000-8000-000000000005');
INSERT INTO siglim.documentos (id, tipo_documento_id, registro_id, s3_bucket, s3_clave, url, tipo_mime, tamano_bytes,
                               sha256, cargado_por)
SELECT 'd2000000-0000-4000-8000-000000000003', id, '80000000-0000-4000-8000-000000000003', 'siglim-documentos-prueba',
       'liga/registros/d2000000-0000-4000-8000-000000000003.jpg',
       'https://siglim-documentos-prueba.s3.us-east-2.amazonaws.com/liga/registros/d2000000-0000-4000-8000-000000000003.jpg',
       'image/jpeg', 1024, repeat('a', 64), '30000000-0000-4000-8000-000000000005'
  FROM siglim.tipos_documento WHERE codigo = 'FOTOGRAFIA';
SELECT prueba.como('30000000-0000-4000-8000-000000000007', 'Registro validado por la liga');
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET estado = 'ACTIVO'
  WHERE id = '80000000-0000-4000-8000-000000000003'$$, 'VAL-001');

-- Un documento APROBADO pero reemplazado no cuenta: el Secretario aprueba los tres y una fotografía
-- aprobada queda reemplazada por un documento nuevo PENDIENTE que carga la Directiva.
UPDATE siglim.documentos
   SET estado_validacion = 'APROBADO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now()
 WHERE registro_id = '80000000-0000-4000-8000-000000000003';
SELECT prueba.como('30000000-0000-4000-8000-000000000005');
INSERT INTO siglim.documentos (id, tipo_documento_id, registro_id, s3_bucket, s3_clave, url, tipo_mime, tamano_bytes,
                               sha256, cargado_por)
SELECT 'd2000000-0000-4000-8000-000000000004', id, '80000000-0000-4000-8000-000000000003', 'siglim-documentos-prueba',
       'liga/registros/d2000000-0000-4000-8000-000000000004.jpg',
       'https://siglim-documentos-prueba.s3.us-east-2.amazonaws.com/liga/registros/d2000000-0000-4000-8000-000000000004.jpg',
       'image/jpeg', 1024, repeat('a', 64), '30000000-0000-4000-8000-000000000005'
  FROM siglim.tipos_documento WHERE codigo = 'FOTOGRAFIA';
SELECT prueba.como('30000000-0000-4000-8000-000000000007', 'Registro validado por la liga');
UPDATE siglim.documentos SET reemplazado_por_id = 'd2000000-0000-4000-8000-000000000004'
 WHERE id = 'd2000000-0000-4000-8000-000000000003';
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET estado = 'ACTIVO'
  WHERE id = '80000000-0000-4000-8000-000000000003'$$, 'VAL-001');

-- Con el reemplazo aprobado, todos los aplicables están aprobados y el Secretario activa el registro.
UPDATE siglim.documentos
   SET estado_validacion = 'APROBADO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now()
 WHERE id = 'd2000000-0000-4000-8000-000000000004';
UPDATE siglim.registros_jugador
   SET estado = 'ACTIVO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now()
 WHERE id = '80000000-0000-4000-8000-000000000003';
SELECT prueba.verifica(
  (SELECT estado = 'ACTIVO' FROM siglim.registros_jugador WHERE id = '80000000-0000-4000-8000-000000000003'),
  'VAL-001: con todos los documentos aplicables vigentes y aprobados, la liga activa el registro');

-- Menor: sin el INE del tutor aprobado → VAL-001, aunque lo demás esté aprobado.
UPDATE siglim.documentos
   SET estado_validacion = 'APROBADO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now()
 WHERE id IN ('d2000000-0000-4000-8000-000000000011', 'd2000000-0000-4000-8000-000000000012',
              'd2000000-0000-4000-8000-000000000013');
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET estado = 'ACTIVO'
  WHERE id = '80000000-0000-4000-8000-000000000002'$$, 'VAL-001');

-- Con el INE del tutor aprobado, VAL-001 se cumple y sigue VAL-025: Mario no tiene tutela.
UPDATE siglim.documentos
   SET estado_validacion = 'APROBADO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now()
 WHERE id = 'd2000000-0000-4000-8000-000000000014';
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET estado = 'ACTIVO'
  WHERE id = '80000000-0000-4000-8000-000000000002'$$, 'VAL-025');

-- Rechazo con motivo: procede aunque el registro de Tomás no tenga documentos, y el historial guarda
-- el motivo.
SELECT prueba.como('30000000-0000-4000-8000-000000000007', 'La CURP no corresponde a la persona registrada');
UPDATE siglim.registros_jugador
   SET estado = 'RECHAZADO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now(),
       observaciones = 'La CURP no corresponde a la persona registrada'
 WHERE id = '80000000-0000-4000-8000-000000000004';
SELECT prueba.verifica(
  (SELECT estado = 'RECHAZADO' FROM siglim.registros_jugador WHERE id = '80000000-0000-4000-8000-000000000004'),
  'RF-109: la liga rechaza un registro aunque no tenga documentos');
SELECT prueba.verifica(
  (SELECT motivo = 'La CURP no corresponde a la persona registrada' AND estado_anterior = 'PENDIENTE_VALIDACION'
     FROM siglim.registro_estado_historial
    WHERE registro_id = '80000000-0000-4000-8000-000000000004' AND estado_nuevo = 'RECHAZADO'),
  'RN-021: el historial del rechazo guarda el motivo');
ROLLBACK;
\o
