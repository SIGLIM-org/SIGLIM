-- Programación automática (T-06.2, DEC-174 a DEC-182): restricciones, índices únicos, el trigger de
-- dias_juego, la regla RN-012 de emparejamientos, privilegios y el permiso de la migración 20261125100000_programacion_automatica.sql.
-- Usa los datos de prueba_flujo.sql: la temporada 50...01 (con parámetros versión 1), las categorías
-- LIBRE «Primera Fuerza» y «Segunda» (categorias_temporada 51...01 y 51...02), el grupo de Segunda
-- 52...02 con los equipos 60...0a, 60...0b, 60...0c y 60...cc, el partido a2...01 con su designación
-- central y el Programador (perfil ...02). Todo corre en una transacción que se deshace al final.
\o /dev/null
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000002');   -- Programador

-- DEC-174: vueltas entre 1 y 4 -------------------------------------------------------------------------
SELECT prueba.espera_error($$INSERT INTO siglim.parametros_temporada (temporada_id, motivo, vueltas)
  VALUES ('50000000-0000-4000-8000-000000000001', 'Cero vueltas', 0)$$, 'ck_parametros_vueltas');
SELECT prueba.espera_error($$INSERT INTO siglim.parametros_temporada (temporada_id, motivo, vueltas)
  VALUES ('50000000-0000-4000-8000-000000000001', 'Cinco vueltas', 5)$$, 'ck_parametros_vueltas');

-- DEC-176: el horario de juego termina después de empezar ---------------------------------------------
SELECT prueba.espera_error($$INSERT INTO siglim.parametros_temporada (temporada_id, motivo, hora_inicio_juego, hora_fin_juego)
  VALUES ('50000000-0000-4000-8000-000000000001', 'Horario invertido', '18:00', '08:00')$$, 'ck_parametros_horario_juego');
SELECT prueba.espera_error($$INSERT INTO siglim.parametros_temporada (temporada_id, motivo, hora_inicio_juego, hora_fin_juego)
  VALUES ('50000000-0000-4000-8000-000000000001', 'Horario vacío', '10:00', '10:00')$$, 'ck_parametros_horario_juego');
SELECT prueba.verifica(
  (SELECT vueltas = 1 AND hora_inicio_juego = '08:00' AND hora_fin_juego = '18:00'
     FROM siglim.parametros_temporada WHERE temporada_id = '50000000-0000-4000-8000-000000000001' AND version = 1),
  'DEC-174, DEC-176: los parámetros nacen con 1 vuelta y horario de 08:00 a 18:00 [DEMO]');

-- DEC-177: dias_juego -----------------------------------------------------------------------------------
INSERT INTO siglim.categorias (id, liga_id, nombre, tipo, orden) VALUES
  ('40000000-0000-4000-8000-000000000011', 'a0000000-0000-4000-8000-000000000001', 'Infantil prueba',  'INFANTIL',  11),
  ('40000000-0000-4000-8000-000000000012', 'a0000000-0000-4000-8000-000000000001', 'Veteranos prueba', 'VETERANOS', 12),
  ('40000000-0000-4000-8000-000000000013', 'a0000000-0000-4000-8000-000000000001', 'Libre prueba',     'LIBRE',     13),
  ('40000000-0000-4000-8000-000000000014', 'a0000000-0000-4000-8000-000000000001', 'Libre explícita',  'LIBRE',     14);

SELECT prueba.espera_error($$INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id, dias_juego)
  VALUES ('50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000013', '{}')$$, 'ck_categorias_temporada_dias');
SELECT prueba.espera_error($$INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id, dias_juego)
  VALUES ('50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000013', '{8}')$$, 'ck_categorias_temporada_dias');
SELECT prueba.espera_error($$UPDATE siglim.categorias_temporada SET dias_juego = '{0,6}'
  WHERE id = '51000000-0000-4000-8000-000000000001'$$, 'ck_categorias_temporada_dias');

INSERT INTO siglim.categorias_temporada (id, temporada_id, categoria_id) VALUES
  ('51000000-0000-4000-8000-000000000011', '50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000011');
INSERT INTO siglim.categorias_temporada (id, temporada_id, categoria_id, dias_juego) VALUES
  ('51000000-0000-4000-8000-000000000012', '50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000012', NULL),
  ('51000000-0000-4000-8000-000000000013', '50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000013', NULL),
  ('51000000-0000-4000-8000-000000000014', '50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000014', '{1,3}');
SELECT prueba.verifica(
  (SELECT dias_juego = '{6}' FROM siglim.categorias_temporada WHERE id = '51000000-0000-4000-8000-000000000011'),
  'DEC-177: el trigger llena dias_juego = {6} (sábado) en una categoría INFANTIL');
SELECT prueba.verifica(
  (SELECT dias_juego = '{6}' FROM siglim.categorias_temporada WHERE id = '51000000-0000-4000-8000-000000000012'),
  'DEC-177: el trigger llena dias_juego = {6} (sábado) en una categoría VETERANOS');
SELECT prueba.verifica(
  (SELECT dias_juego = '{7}' FROM siglim.categorias_temporada WHERE id = '51000000-0000-4000-8000-000000000013'),
  'DEC-177: el trigger llena dias_juego = {7} (domingo) en una categoría LIBRE');
SELECT prueba.verifica(
  (SELECT dias_juego = '{1,3}' FROM siglim.categorias_temporada WHERE id = '51000000-0000-4000-8000-000000000014'),
  'DEC-177: el trigger respeta un dias_juego explícito');
SELECT prueba.verifica(
  (SELECT bool_and(dias_juego = '{7}') FROM siglim.categorias_temporada
    WHERE id IN ('51000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000002')),
  'DEC-177: las categorías LIBRE de prueba_flujo.sql juegan en domingo');

-- DEC-175: fechas y jornadas ----------------------------------------------------------------------------
INSERT INTO siglim.fechas_programacion (id, temporada_id, numero, dias, creado_por) VALUES
  ('f0000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 1, '{2026-09-12,2026-09-13}',
   '30000000-0000-4000-8000-000000000002');
SELECT prueba.espera_error($$INSERT INTO siglim.fechas_programacion (temporada_id, numero, dias)
  VALUES ('50000000-0000-4000-8000-000000000001', 2, '{}')$$, 'ck_fechas_dias');
INSERT INTO siglim.jornadas (grupo_id, numero, fecha_id, creado_por) VALUES
  ('52000000-0000-4000-8000-000000000002', 2, 'f0000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002');
SELECT prueba.espera_error($$INSERT INTO siglim.jornadas (grupo_id, numero, fecha_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 3, 'f0000000-0000-4000-8000-000000000001')$$, 'ux_jornadas_fecha_grupo');

-- DEC-174, DEC-180: emparejamientos ---------------------------------------------------------------------
INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id, creado_por) VALUES
  ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-00000000000a', '60000000-0000-4000-8000-00000000000b',
   '30000000-0000-4000-8000-000000000002'),
  ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-00000000000c', NULL,
   '30000000-0000-4000-8000-000000000002');
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 2, '60000000-0000-4000-8000-00000000000a', '60000000-0000-4000-8000-00000000000a')$$,
  'ck_emparejamientos_equipos');
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id, partido_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 2, '60000000-0000-4000-8000-0000000000cc', NULL,
          'a2000000-0000-4000-8000-000000000001')$$, 'ck_emparejamientos_descanso');

-- RN-012: en la fecha 1 ya juegan 0a (local) contra 0b (visitante) y descansa 0c. Un equipo no aparece
-- dos veces en la misma fecha del grupo, en ningún papel.
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-00000000000a', '60000000-0000-4000-8000-0000000000cc')$$,
  'RN-012');   -- otra vez local
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-0000000000cc', '60000000-0000-4000-8000-00000000000b')$$,
  'RN-012');   -- otra vez visitante
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-00000000000b', '60000000-0000-4000-8000-0000000000cc')$$,
  'RN-012');   -- visitante en uno, local en otro
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-0000000000cc', '60000000-0000-4000-8000-00000000000a')$$,
  'RN-012');   -- local en uno, visitante en otro
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-00000000000b', NULL)$$,
  'RN-012');   -- descanso de un equipo que ya juega
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-0000000000cc', '60000000-0000-4000-8000-00000000000c')$$,
  'RN-012');   -- un equipo que descansa no juega esa fecha

-- RN-012: el mismo equipo sí juega en fechas distintas del grupo; mover un emparejamiento a una fecha
-- donde el equipo ya está se rechaza (UPDATE OF numero_fecha).
INSERT INTO siglim.emparejamientos (id, grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id) VALUES
  ('e0000000-0000-4000-8000-000000000001', '52000000-0000-4000-8000-000000000002', 1, 3,
   '60000000-0000-4000-8000-00000000000b', '60000000-0000-4000-8000-00000000000a'),
  ('e0000000-0000-4000-8000-000000000002', '52000000-0000-4000-8000-000000000002', 1, 3,
   '60000000-0000-4000-8000-00000000000c', '60000000-0000-4000-8000-0000000000cc');
SELECT prueba.verifica(
  (SELECT count(DISTINCT numero_fecha) = 2 FROM siglim.emparejamientos
    WHERE grupo_id = '52000000-0000-4000-8000-000000000002'
      AND '60000000-0000-4000-8000-00000000000a' IN (equipo_local_id, equipo_visitante_id)),
  'RN-012: el mismo equipo tiene emparejamientos en fechas distintas del grupo');
SELECT prueba.espera_error($$UPDATE siglim.emparejamientos SET numero_fecha = 1
  WHERE id = 'e0000000-0000-4000-8000-000000000002'$$, 'RN-012');

-- DEC-174: los índices únicos siguen como última garantía si el trigger no corre (dueño del esquema,
-- trigger desactivado solo dentro de esta transacción).
RESET ROLE;
ALTER TABLE siglim.emparejamientos DISABLE TRIGGER tg_emparejamientos_equipo_unico;
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-00000000000a', '60000000-0000-4000-8000-0000000000cc')$$,
  'ux_emparejamientos_local');
SELECT prueba.espera_error($$INSERT INTO siglim.emparejamientos (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id)
  VALUES ('52000000-0000-4000-8000-000000000002', 1, 1, '60000000-0000-4000-8000-0000000000cc', '60000000-0000-4000-8000-00000000000b')$$,
  'ux_emparejamientos_visitante');
ALTER TABLE siglim.emparejamientos ENABLE TRIGGER tg_emparejamientos_equipo_unico;
SET LOCAL ROLE siglim_app;

-- DEC-181, RN-005: siglim_app no borra fechas ni emparejamientos ----------------------------------------
SELECT prueba.espera_error($$DELETE FROM siglim.fechas_programacion$$, 'permission denied');
SELECT prueba.espera_error($$DELETE FROM siglim.emparejamientos$$, 'permission denied');

-- DEC-182: permiso de la designación automática ---------------------------------------------------------
SELECT prueba.verifica(EXISTS (
  SELECT 1 FROM siglim.rol_permisos rp
    JOIN siglim.roles r ON r.id = rp.rol_id
    JOIN siglim.permisos p ON p.id = rp.permiso_id
   WHERE r.codigo = 'PROGRAMADOR' AND p.codigo = 'arbitraje.designacion:AUTOMATICA' AND p.modulo = 'M-05'),
  'DEC-182: el PROGRAMADOR tiene arbitraje.designacion:AUTOMATICA');
SELECT prueba.verifica(NOT EXISTS (
  SELECT 1 FROM siglim.pares_incompatibles pi
    JOIN siglim.permisos p ON p.id IN (pi.permiso_a_id, pi.permiso_b_id)
   WHERE p.codigo = 'arbitraje.designacion:AUTOMATICA'),
  'DEC-182: arbitraje.designacion:AUTOMATICA no forma parte de pares incompatibles');

-- RF-041: lo manual nace marcado como no automático -----------------------------------------------------
SELECT prueba.verifica(
  (SELECT NOT asignado_automaticamente FROM siglim.partidos WHERE id = 'a2000000-0000-4000-8000-000000000001'),
  'RF-041: partidos.asignado_automaticamente nace en false');
SELECT prueba.verifica(
  (SELECT bool_and(NOT automatica) FROM siglim.designaciones WHERE partido_id = 'a2000000-0000-4000-8000-000000000001'),
  'DEC-182: designaciones.automatica nace en false');
ROLLBACK;
\o
