-- Prueba de integración del esquema SIGLIM: flujo P-01 → P-02 → P-03 → P-05 → P-06 → P-11
\set ON_ERROR_STOP 1
SET client_min_messages = notice;

-- Utilidades de prueba (fuera del esquema siglim) ---------------------------------------
CREATE SCHEMA prueba;
GRANT USAGE ON SCHEMA prueba TO siglim_app, siglim_publico;
CREATE FUNCTION prueba.como(p_perfil uuid, p_motivo text DEFAULT NULL) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  PERFORM set_config('request.jwt.claims', json_build_object('perfil_id', p_perfil)::text, true);
  PERFORM set_config('siglim.motivo', coalesce(p_motivo, ''), true);
END $$;
CREATE FUNCTION prueba.espera_error(p_sql text, p_codigo text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_hint text; v_msg text;
BEGIN
  BEGIN
    EXECUTE p_sql;
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS v_hint = PG_EXCEPTION_HINT, v_msg = MESSAGE_TEXT;
    IF v_hint = p_codigo OR v_msg LIKE '%' || p_codigo || '%' THEN
      RAISE NOTICE 'OK [%] %', p_codigo, v_msg; RETURN;
    END IF;
    RAISE EXCEPTION 'Se esperaba % y ocurrió: %', p_codigo, v_msg;
  END;
  RAISE EXCEPTION 'Se esperaba el error % y la operación se aceptó: %', p_codigo, p_sql;
END $$;
CREATE FUNCTION prueba.verifica(p_condicion boolean, p_texto text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  IF p_condicion IS NOT TRUE THEN RAISE EXCEPTION 'FALLA: %', p_texto; END IF;
  RAISE NOTICE 'OK %', p_texto;
END $$;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA prueba TO siglim_app, siglim_publico;

-- Cuentas en el Auth simulado y alta del primer administrador -------------------------
INSERT INTO auth.users (id, email) VALUES ('10000000-0000-4000-8000-000000000001', 'admin@liga.mx');
SELECT siglim.registrar_administrador_inicial('admin@liga.mx') AS admin \gset
SELECT prueba.espera_error($$SELECT siglim.registrar_administrador_inicial('admin@liga.mx')$$, 'Ya existe');

\set liga a0000000-0000-4000-8000-000000000001
\set sistema a0000000-0000-4000-8000-000000000002

-- ================= P-01: configuración (Administrador) ================================
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como(:'admin');

-- Personas y perfiles del resto de usuarios
INSERT INTO siglim.personas (id, curp, nombre, apellido_paterno, fecha_nacimiento, creado_por) VALUES
  ('20000000-0000-4000-8000-000000000001', 'PEPR800101HMNRRS01', 'Pedro',  'Presidente', '1980-01-01', :'admin'),
  ('20000000-0000-4000-8000-000000000002', 'AARB850202HMNRRB02', 'Arturo', 'Arbitro',    '1985-02-02', :'admin'),
  ('20000000-0000-4000-8000-000000000003', 'DIRA700303HMNRRC03', 'Diana',  'DirectivaA', '1970-03-03', :'admin'),
  ('20000000-0000-4000-8000-000000000004', 'DIRB710404HMNRRD04', 'Bruno',  'DirectivaB', '1971-04-04', :'admin');
INSERT INTO siglim.perfiles (id, nombre_mostrado, persona_id) VALUES
  ('30000000-0000-4000-8000-000000000001', 'Presidente',  '20000000-0000-4000-8000-000000000001'),
  ('30000000-0000-4000-8000-000000000002', 'Programador', NULL),
  ('30000000-0000-4000-8000-000000000003', 'Capturista',  NULL),
  ('30000000-0000-4000-8000-000000000004', 'Árbitro',     '20000000-0000-4000-8000-000000000002'),
  ('30000000-0000-4000-8000-000000000005', 'Directiva A', '20000000-0000-4000-8000-000000000003'),
  ('30000000-0000-4000-8000-000000000006', 'Directiva B', '20000000-0000-4000-8000-000000000004'),
  ('30000000-0000-4000-8000-000000000007', 'Secretario',  NULL),
  ('30000000-0000-4000-8000-000000000008', 'Com. Arbitraje', NULL),
  ('30000000-0000-4000-8000-000000000009', 'Tesorero',    NULL),
  ('30000000-0000-4000-8000-000000000010', 'Com. Disciplinaria', NULL);

INSERT INTO siglim.clubes (id, liga_id, nombre) VALUES
  ('c0000000-0000-4000-8000-00000000000a', :'liga', 'Club Atlético A'),
  ('c0000000-0000-4000-8000-00000000000b', :'liga', 'Deportivo B'),
  ('c0000000-0000-4000-8000-00000000000c', :'liga', 'Unión C');

INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id, creado_por)
SELECT a.perfil::uuid, r.id, r.liga_id, r.ambito_predeterminado, a.club::uuid, :'admin'
FROM (VALUES
  ('30000000-0000-4000-8000-000000000001', 'PRESIDENTE', NULL),
  ('30000000-0000-4000-8000-000000000002', 'PROGRAMADOR', NULL),
  ('30000000-0000-4000-8000-000000000003', 'CAPTURISTA', NULL),
  ('30000000-0000-4000-8000-000000000004', 'ARBITRO', NULL),
  ('30000000-0000-4000-8000-000000000005', 'DIRECTIVA_CLUB', 'c0000000-0000-4000-8000-00000000000a'),
  ('30000000-0000-4000-8000-000000000006', 'DIRECTIVA_CLUB', 'c0000000-0000-4000-8000-00000000000b'),
  ('30000000-0000-4000-8000-000000000007', 'SECRETARIO', NULL),
  ('30000000-0000-4000-8000-000000000008', 'COMISION_ARBITRAJE', NULL),
  ('30000000-0000-4000-8000-000000000009', 'TESORERO', NULL),
  ('30000000-0000-4000-8000-000000000010', 'COMISION_DISCIPLINARIA', NULL)
) AS a (perfil, rol, club) JOIN siglim.roles r ON r.codigo = a.rol;

-- VAL-022: el administrador no modifica su propio rol
SELECT prueba.espera_error($$INSERT INTO siglim.rol_permisos SELECT r.id, p.id FROM siglim.roles r, siglim.permisos p
  WHERE r.codigo = 'ADMINISTRADOR_GENERAL' AND p.codigo = 'tesoreria.cobro:CREAR'$$, 'VAL-022');

-- Apéndice E: el Secretario registra y edita equipos (migración 20261103100000, H-06.1)
SELECT prueba.verifica(EXISTS (
  SELECT 1 FROM siglim.rol_permisos rp
    JOIN siglim.roles r ON r.id = rp.rol_id
    JOIN siglim.permisos p ON p.id = rp.permiso_id
   WHERE r.codigo = 'SECRETARIO' AND p.codigo = 'organizacion.equipo:EDITAR'),
  'RF-009: el SECRETARIO tiene organizacion.equipo:EDITAR');

-- DEC-55: el Tesorero consulta equipos para dirigir cobros, sin editarlos (migración 20261106160000, DEF-06)
SELECT prueba.verifica(EXISTS (
  SELECT 1 FROM siglim.rol_permisos rp
    JOIN siglim.roles r ON r.id = rp.rol_id
    JOIN siglim.permisos p ON p.id = rp.permiso_id
   WHERE r.codigo = 'TESORERO' AND p.codigo = 'organizacion.equipo:CONSULTAR'),
  'RF-137, DEC-55: el TESORERO tiene organizacion.equipo:CONSULTAR');
SELECT prueba.verifica(NOT EXISTS (
  SELECT 1 FROM siglim.rol_permisos rp
    JOIN siglim.roles r ON r.id = rp.rol_id
    JOIN siglim.permisos p ON p.id = rp.permiso_id
   WHERE r.codigo = 'TESORERO' AND p.codigo = 'organizacion.equipo:EDITAR'),
  'RF-137, DEC-55: el TESORERO no tiene organizacion.equipo:EDITAR');

-- Temporada, categorías con jerarquía, grupos y parámetros
INSERT INTO siglim.categorias (id, liga_id, nombre, tipo, orden) VALUES
  ('40000000-0000-4000-8000-000000000001', :'liga', 'Primera Fuerza', 'LIBRE', 1);
INSERT INTO siglim.categorias (id, liga_id, nombre, tipo, orden, categoria_superior_id) VALUES
  ('40000000-0000-4000-8000-000000000002', :'liga', 'Segunda', 'LIBRE', 2, '40000000-0000-4000-8000-000000000001');
INSERT INTO siglim.temporadas (id, liga_id, nombre, fecha_inicio) VALUES
  ('50000000-0000-4000-8000-000000000001', :'liga', 'Temporada de prueba', '2026-08-01');
INSERT INTO siglim.categorias_temporada (id, temporada_id, categoria_id) VALUES
  ('51000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001'),
  ('51000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000002');
INSERT INTO siglim.grupos (id, categoria_temporada_id, nombre) VALUES
  ('52000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', 'Único'),
  ('52000000-0000-4000-8000-000000000002', '51000000-0000-4000-8000-000000000002', 'Único');

SELECT prueba.espera_error($$UPDATE siglim.temporadas SET estado = 'ACTIVA' WHERE id = '50000000-0000-4000-8000-000000000001'$$, 'RF-100');
INSERT INTO siglim.parametros_temporada (temporada_id, motivo, creado_por)
VALUES ('50000000-0000-4000-8000-000000000001', 'Parámetros de demostración', :'admin');
UPDATE siglim.temporadas SET estado = 'ACTIVA' WHERE id = '50000000-0000-4000-8000-000000000001';
SELECT prueba.espera_error($$UPDATE siglim.parametros_temporada SET puntos_victoria = 2$$, 'permission denied');

-- Equipos y canchas
INSERT INTO siglim.equipos (id, club_id, grupo_id, nombre) VALUES
  ('60000000-0000-4000-8000-00000000000a', 'c0000000-0000-4000-8000-00000000000a', '52000000-0000-4000-8000-000000000002', 'Atlético A Segunda'),
  ('60000000-0000-4000-8000-00000000000b', 'c0000000-0000-4000-8000-00000000000b', '52000000-0000-4000-8000-000000000002', 'Deportivo B Segunda'),
  ('60000000-0000-4000-8000-00000000000c', 'c0000000-0000-4000-8000-00000000000c', '52000000-0000-4000-8000-000000000002', 'Unión C Segunda'),
  ('60000000-0000-4000-8000-0000000000aa', 'c0000000-0000-4000-8000-00000000000a', '52000000-0000-4000-8000-000000000001', 'Atlético A Primera');
INSERT INTO siglim.canchas (id, liga_id, nombre, ubicacion, propietario) VALUES
  ('70000000-0000-4000-8000-000000000001', :'liga', 'Cancha 1', 'UNIDAD_DEPORTIVA', 'LIGA'),
  ('70000000-0000-4000-8000-000000000002', :'liga', 'Cancha 2', 'UNIDAD_DEPORTIVA', 'LIGA');
INSERT INTO siglim.disponibilidad_canchas (cancha_id, tipo, periodo, motivo) VALUES
  ('70000000-0000-4000-8000-000000000002', 'MANTENIMIENTO', '[2026-09-06 08:00-06, 2026-09-06 20:00-06)', 'Resiembra de pasto');
COMMIT;

-- ================= P-02: registro de jugadores ========================================
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000005');   -- Directiva A
INSERT INTO siglim.personas (id, curp, nombre, apellido_paterno, fecha_nacimiento, creado_por) VALUES
  ('21000000-0000-4000-8000-000000000001', 'JUGA950505HMNRRE05', 'Juan',  'Jugador',  '1995-05-05', '30000000-0000-4000-8000-000000000005'),
  ('21000000-0000-4000-8000-000000000002', 'MENO100606HMNRRF06', 'Mario', 'Menor',    '2010-06-06', '30000000-0000-4000-8000-000000000005');
INSERT INTO siglim.registros_jugador (id, persona_id, equipo_id, temporada_id, creado_por) VALUES
  ('80000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-00000000000a', '50000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005'),
  ('80000000-0000-4000-8000-000000000002', '21000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-00000000000a', '50000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005');
SELECT prueba.verifica((SELECT temporada_id FROM siglim.registros_jugador WHERE id = '80000000-0000-4000-8000-000000000001') = '50000000-0000-4000-8000-000000000001', 'temporada del registro fijada por trigger');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000006');   -- Directiva B no ve al jugador del club A
SELECT prueba.verifica((SELECT count(*) FROM siglim.registros_jugador) = 0, 'RN-054: la directiva B no ve registros del club A');
SELECT prueba.verifica((SELECT count(*) FROM siglim.personas WHERE id = '21000000-0000-4000-8000-000000000001') = 0, 'RNF-012: la directiva B no ve la persona del club A');
SELECT prueba.espera_error($$INSERT INTO siglim.registros_jugador (persona_id, equipo_id, temporada_id)
  VALUES ('21000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-00000000000b', '50000000-0000-4000-8000-000000000001')$$, 'VAL-017');
INSERT INTO siglim.personas (id, curp, nombre, apellido_paterno, fecha_nacimiento, creado_por) VALUES
  ('21000000-0000-4000-8000-000000000003', 'OOTR960707HMNRRG07', 'Otto', 'OtroClub', '1996-07-07', '30000000-0000-4000-8000-000000000006');
SELECT prueba.espera_error($$INSERT INTO siglim.registros_jugador (persona_id, equipo_id, temporada_id)
  VALUES ('21000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-00000000000a', '50000000-0000-4000-8000-000000000001')$$, 'row-level security');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000007', 'Documentos completos');   -- Secretario valida
UPDATE siglim.registros_jugador SET estado = 'ACTIVO', validado_por = '30000000-0000-4000-8000-000000000007', validado_en = now()
WHERE id = '80000000-0000-4000-8000-000000000001';
SELECT prueba.espera_error($$UPDATE siglim.registros_jugador SET estado = 'ACTIVO' WHERE id = '80000000-0000-4000-8000-000000000002'$$, 'VAL-025');
SELECT prueba.verifica((SELECT count(*) FROM siglim.registro_estado_historial WHERE registro_id = '80000000-0000-4000-8000-000000000001') = 2, 'RF-018: historial del registro (alta y validación)');
COMMIT;

-- Árbitro presentado por el club C y validado por la Comisión de Arbitraje
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000008');
INSERT INTO siglim.arbitros (id, persona_id, club_id, estado, validado_por, validado_en) VALUES
  ('90000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-00000000000c', 'VALIDADO', '30000000-0000-4000-8000-000000000008', now());
COMMIT;

-- ================= P-03: programación y publicación ===================================
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000002');   -- Programador
INSERT INTO siglim.jornadas (id, grupo_id, numero, creado_por) VALUES
  ('a1000000-0000-4000-8000-000000000001', '52000000-0000-4000-8000-000000000002', 1, '30000000-0000-4000-8000-000000000002');
INSERT INTO siglim.partidos (id, jornada_id, equipo_local_id, equipo_visitante_id, cancha_id, inicio, fin_bloque) VALUES
  ('a2000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-00000000000a', '60000000-0000-4000-8000-00000000000b',
   '70000000-0000-4000-8000-000000000001', '2026-09-05 09:00-06', '2026-09-05 09:00-06');
SELECT prueba.verifica((SELECT fin_bloque - inicio FROM siglim.partidos WHERE id = 'a2000000-0000-4000-8000-000000000001') = interval '120 minutes', 'DEC-25: fin_bloque = inicio + 120 min');
SELECT prueba.espera_error($$INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id, inicio, fin_bloque)
  VALUES ('a1000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-00000000000a', '60000000-0000-4000-8000-00000000000c',
          '70000000-0000-4000-8000-000000000001', '2026-09-05 10:30-06', '2026-09-05 10:30-06')$$, 'VAL-003');
SELECT prueba.espera_error($$INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id, inicio, fin_bloque)
  VALUES ('a1000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-00000000000c', '60000000-0000-4000-8000-00000000000b',
          '70000000-0000-4000-8000-000000000002', '2026-09-06 09:00-06', '2026-09-06 09:00-06')$$, 'VAL-005');
SELECT prueba.espera_error($$INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id, inicio, fin_bloque)
  VALUES ('a1000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-00000000000c', '60000000-0000-4000-8000-0000000000aa',
          '70000000-0000-4000-8000-000000000001', '2026-09-07 09:00-06', '2026-09-07 09:00-06')$$, 'RF-033');
SELECT prueba.espera_error($$UPDATE siglim.jornadas SET estado_publicacion = 'PUBLICADO' WHERE id = 'a1000000-0000-4000-8000-000000000001'$$, 'RF-090');
UPDATE siglim.jornadas SET estado_publicacion = 'REVISION', enviada_por = '30000000-0000-4000-8000-000000000002', enviada_en = now()
WHERE id = 'a1000000-0000-4000-8000-000000000001';
COMMIT;

-- VAL-004: cancha ocupada (restricción de exclusión) con dos equipos libres a esa hora
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como(:'admin');
INSERT INTO siglim.equipos (id, club_id, grupo_id, nombre) VALUES
  ('60000000-0000-4000-8000-0000000000cc', 'c0000000-0000-4000-8000-00000000000c', '52000000-0000-4000-8000-000000000002', 'Unión C Segunda B');
SELECT prueba.como('30000000-0000-4000-8000-000000000002');
SELECT prueba.espera_error($$INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id, inicio, fin_bloque)
  VALUES ('a1000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-00000000000c', '60000000-0000-4000-8000-0000000000cc',
          '70000000-0000-4000-8000-000000000001', '2026-09-05 10:00-06', '2026-09-05 10:00-06')$$, 'ex_partidos_cancha_ocupada');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000008');   -- Comisión de Arbitraje designa
INSERT INTO siglim.arbitros (id, persona_id, club_id, estado) VALUES
  ('90000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-00000000000a', 'VALIDADO');
SELECT prueba.espera_error($$INSERT INTO siglim.designaciones (partido_id, arbitro_id) VALUES ('a2000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000002')$$, 'VAL-019');
INSERT INTO siglim.designaciones (partido_id, arbitro_id, designado_por) VALUES
  ('a2000000-0000-4000-8000-000000000001', '90000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000008');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000001', 'Programación revisada');   -- Presidente aprueba
UPDATE siglim.jornadas SET aprobada_por = '30000000-0000-4000-8000-000000000001', aprobada_en = now(), estado_publicacion = 'PUBLICADO'
WHERE id = 'a1000000-0000-4000-8000-000000000001';
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_publico;
SELECT prueba.verifica((SELECT count(*) FROM siglim.v_publica_programacion) = 1, 'RF-082: el portal ve la jornada publicada');
SELECT prueba.espera_error($$SELECT * FROM siglim.partidos$$, 'permission denied');
SELECT prueba.espera_error($$SELECT * FROM siglim.personas$$, 'permission denied');
COMMIT;

-- ================= P-04 / P-05: partido en curso, cédula y resultado ==================
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como(:'sistema');
SELECT prueba.espera_error($$UPDATE siglim.partidos SET estado = 'EN_CURSO' WHERE id = 'a2000000-0000-4000-8000-000000000001'$$, 'RN-021');
SELECT prueba.como(:'sistema', 'Inicio automático por fecha y hora');
UPDATE siglim.partidos SET estado = 'EN_CURSO' WHERE id = 'a2000000-0000-4000-8000-000000000001';
SELECT prueba.espera_error($$UPDATE siglim.partidos SET estado = 'FINALIZADO' WHERE id = 'a2000000-0000-4000-8000-000000000001'$$, 'RF-127');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000003');   -- Capturista
INSERT INTO siglim.cedulas (id, partido_id, capturista_id) VALUES
  ('b1000000-0000-4000-8000-000000000001', 'a2000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000003');
SELECT prueba.espera_error($$INSERT INTO siglim.alineaciones (cedula_id, registro_jugador_id, equipo_id)
  VALUES ('b1000000-0000-4000-8000-000000000001', '80000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-00000000000a')$$, 'RF-019');
INSERT INTO siglim.alineaciones (id, cedula_id, registro_jugador_id, equipo_id) VALUES
  ('b2000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000001', '80000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-00000000000a');
INSERT INTO siglim.eventos_cedula (cedula_id, tipo, minuto, equipo_id, alineacion_id) VALUES
  ('b1000000-0000-4000-8000-000000000001', 'GOL', 23, '60000000-0000-4000-8000-00000000000a', 'b2000000-0000-4000-8000-000000000001'),
  ('b1000000-0000-4000-8000-000000000001', 'TARJETA_ROJA', 80, '60000000-0000-4000-8000-00000000000a', 'b2000000-0000-4000-8000-000000000001');
INSERT INTO siglim.incidencias (id, cedula_id, tipo_incidencia_id, minuto, descripcion)
SELECT 'b3000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000001', id, 80, 'Expulsión por juego brusco grave'
FROM siglim.tipos_incidencia WHERE codigo = 'EXPULSION';
INSERT INTO siglim.incidencia_involucrados (incidencia_id, persona_id) VALUES
  ('b3000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000001');
UPDATE siglim.cedulas SET goles_local = 1, goles_visitante = 0, no_presentado_equipo_id = NULL WHERE id = 'b1000000-0000-4000-8000-000000000001';
SELECT prueba.espera_error($$UPDATE siglim.cedulas SET estado = 'PENDIENTE_VALIDACION' WHERE id = 'b1000000-0000-4000-8000-000000000001'$$, 'VAL-020');
COMMIT;

-- El equipo visitante (B) no se presentó: se registra en la cédula y se envía
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000003', 'Captura completa');
UPDATE siglim.cedulas SET goles_local = 3, goles_visitante = 0, no_presentado_equipo_id = '60000000-0000-4000-8000-00000000000b',
       estado = 'PENDIENTE_VALIDACION' WHERE id = 'b1000000-0000-4000-8000-000000000001';
SELECT prueba.verifica((SELECT plazo_vence_en - enviada_en FROM siglim.cedulas WHERE id = 'b1000000-0000-4000-8000-000000000001') = interval '48 hours', 'DEC-20: plazo de validación de 48 h');
SELECT prueba.espera_error($$UPDATE siglim.cedulas SET goles_local = 5 WHERE id = 'b1000000-0000-4000-8000-000000000001'$$, 'RF-122');
SELECT prueba.espera_error($$INSERT INTO siglim.eventos_cedula (cedula_id, tipo, equipo_id, alineacion_id)
  VALUES ('b1000000-0000-4000-8000-000000000001', 'GOL', '60000000-0000-4000-8000-00000000000a', 'b2000000-0000-4000-8000-000000000001')$$, 'RF-122');
SELECT prueba.espera_error($$UPDATE siglim.cedulas SET estado = 'APROBADA', aprobada_por = '30000000-0000-4000-8000-000000000003', aprobada_en = now()
  WHERE id = 'b1000000-0000-4000-8000-000000000001'$$, 'VAL-021');
SELECT prueba.espera_error($$UPDATE siglim.cedulas SET estado = 'APROBADA', aprobada_por = '30000000-0000-4000-8000-000000000008', aprobada_en = now(), aprobada_por_comision = true
  WHERE id = 'b1000000-0000-4000-8000-000000000001'$$, 'RF-124');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000004', 'Cédula correcta');   -- Árbitro designado aprueba
UPDATE siglim.cedulas SET estado = 'APROBADA', aprobada_por = '30000000-0000-4000-8000-000000000004'
WHERE id = 'b1000000-0000-4000-8000-000000000001';
INSERT INTO siglim.resultados (partido_id, version, goles_local, goles_visitante, origen, cedula_id, por_default, motivo, registrado_por)
VALUES ('a2000000-0000-4000-8000-000000000001', 0, 3, 0, 'CEDULA', 'b1000000-0000-4000-8000-000000000001', true, 'Cédula aprobada', '30000000-0000-4000-8000-000000000004');
SELECT prueba.como('30000000-0000-4000-8000-000000000004', 'Cédula aprobada');
UPDATE siglim.partidos SET estado = 'FINALIZADO' WHERE id = 'a2000000-0000-4000-8000-000000000001';
-- Oyente de disciplina (mismo contexto del árbitro): abre el expediente de la expulsión
INSERT INTO siglim.expedientes (id, liga_id, temporada_id, folio, origen, incidencia_id, abierto_por)
VALUES ('c1000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001',
        'EXP-0001', 'INCIDENCIA', 'b3000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000004');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000004');
SELECT prueba.espera_error($$UPDATE siglim.cedulas SET observaciones = 'x' WHERE id = 'b1000000-0000-4000-8000-000000000001'$$, 'RF-048');
SELECT prueba.espera_error($$UPDATE siglim.resultados SET goles_local = 9$$, 'permission denied');
SELECT prueba.espera_error($$DELETE FROM siglim.partidos$$, 'permission denied');
COMMIT;

-- Tabla calculada y portal
BEGIN;
SET LOCAL ROLE siglim_publico;
SELECT prueba.verifica((SELECT puntos FROM siglim.calcular_tabla('52000000-0000-4000-8000-000000000002') WHERE equipo = 'Atlético A Segunda') = 3, 'RF-049: 3 puntos por victoria');
SELECT prueba.verifica((SELECT posicion FROM siglim.calcular_tabla('52000000-0000-4000-8000-000000000002') WHERE equipo = 'Atlético A Segunda') = 1, 'RF-050: Atlético A en primer lugar');
SELECT prueba.verifica((SELECT count(DISTINCT posicion) FROM siglim.calcular_tabla('52000000-0000-4000-8000-000000000002')) = 3, 'RF-050: empate total comparte posición (4 equipos, 3 posiciones)');
SELECT prueba.verifica((SELECT expulsiones FROM siglim.calcular_tabla('52000000-0000-4000-8000-000000000002') WHERE equipo = 'Atlético A Segunda') = 1, 'DEC-27: expulsiones contadas');
SELECT prueba.verifica((SELECT count(*) FROM siglim.v_publica_resultados WHERE por_default) = 1, 'RF-125: resultado publicado al aprobarse la cédula');
COMMIT;
SELECT * FROM siglim.calcular_tabla('52000000-0000-4000-8000-000000000002');

-- ================= P-06: resolución con sanción y corrección de resultado ============
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000010', 'Inicia revisión');
UPDATE siglim.expedientes SET estado = 'EN_REVISION' WHERE id = 'c1000000-0000-4000-8000-000000000001';
INSERT INTO siglim.articulos (id, liga_id, numero, descripcion, tipo_sancion, sujeto, partidos_suspension)
VALUES ('c2000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'P-1', 'Suspensión por expulsión', 'SUSPENSION_PARTIDOS', 'JUGADOR', 2);
INSERT INTO siglim.resoluciones (id, expediente_id, articulo_id, fundamentos, resuelta_por)
VALUES ('c3000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000001', 'c2000000-0000-4000-8000-000000000001', 'Juego brusco grave', '30000000-0000-4000-8000-000000000010');
INSERT INTO siglim.sanciones (resolucion_id, tipo, sujeto, efecto, persona_id, temporada_id, partidos)
VALUES ('c3000000-0000-4000-8000-000000000001', 'SUSPENSION_PARTIDOS', 'JUGADOR', 'HACIA_ADELANTE', '21000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 2);
SELECT prueba.como('30000000-0000-4000-8000-000000000010', 'Resolución emitida');
UPDATE siglim.expedientes SET estado = 'RESUELTO' WHERE id = 'c1000000-0000-4000-8000-000000000001';
SELECT prueba.espera_error($$UPDATE siglim.resoluciones SET fundamentos = 'otro'$$, 'permission denied');
-- Corrección del resultado por resolución (versión 2)
INSERT INTO siglim.resultados (partido_id, version, goles_local, goles_visitante, origen, resolucion_id, motivo, registrado_por)
VALUES ('a2000000-0000-4000-8000-000000000001', 0, 0, 3, 'RESOLUCION', 'c3000000-0000-4000-8000-000000000001', 'Prueba de corrección', '30000000-0000-4000-8000-000000000010');
SELECT prueba.verifica((SELECT count(*) FROM siglim.resultados WHERE partido_id = 'a2000000-0000-4000-8000-000000000001') = 2
                   AND (SELECT version FROM siglim.resultados WHERE partido_id = 'a2000000-0000-4000-8000-000000000001' AND vigente) = 2, 'RF-052: versión anterior conservada');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000003');
SELECT prueba.verifica((SELECT puntos FROM siglim.calcular_tabla('52000000-0000-4000-8000-000000000002') WHERE equipo = 'Deportivo B Segunda') = 3, 'RN-024: la tabla refleja la corrección');
COMMIT;

-- Directiva A ve la sanción de su jugador; Directiva B no
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000005');
SELECT prueba.verifica((SELECT count(*) FROM siglim.sanciones) = 1, 'RN-054: la directiva A ve la sanción de su jugador');
SELECT prueba.verifica((SELECT count(*) FROM siglim.expedientes) = 1, 'RN-054: la directiva A ve el expediente de su jugador');
SELECT prueba.como('30000000-0000-4000-8000-000000000006');
SELECT prueba.verifica((SELECT count(*) FROM siglim.sanciones) = 0, 'RN-054: la directiva B no ve sanciones del club A');
COMMIT;

-- ================= P-11: cobros y pago simulado ======================================
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000009');   -- Tesorero
INSERT INTO siglim.cobros (id, liga_id, club_id, tipo, concepto, monto, fecha_limite, creado_por)
VALUES ('d1000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-00000000000a', 'MANUAL', 'Credenciales', 150.00, current_date + 7, '30000000-0000-4000-8000-000000000009');
COMMIT;

BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como('30000000-0000-4000-8000-000000000005');   -- Directiva A paga en línea (simulado)
INSERT INTO siglim.intentos_pago (id, cobro_id, referencia, iniciado_por)
VALUES ('d2000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'SIM-20261019-AB12CD', '30000000-0000-4000-8000-000000000005');
UPDATE siglim.intentos_pago SET estado = 'APROBADO', resuelto_en = now() WHERE id = 'd2000000-0000-4000-8000-000000000001';
INSERT INTO siglim.pagos (cobro_id, intento_id, medio, monto, referencia, registrado_por)
VALUES ('d1000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'EN_LINEA_SIMULADO', 150.00, 'SIM-20261019-AB12CD', '30000000-0000-4000-8000-000000000005');
SELECT prueba.verifica((SELECT estado FROM siglim.cobros WHERE id = 'd1000000-0000-4000-8000-000000000001') = 'PAGADO', 'RF-138: cobro pagado');
SELECT prueba.espera_error($$INSERT INTO siglim.pagos (cobro_id, medio, monto, registrado_por)
  VALUES ('d1000000-0000-4000-8000-000000000001', 'EFECTIVO', 150.00, '30000000-0000-4000-8000-000000000005')$$, 'VAL-028');
SELECT prueba.como('30000000-0000-4000-8000-000000000006');
SELECT prueba.verifica((SELECT count(*) FROM siglim.cobros) = 0, 'RN-054: la directiva B no ve cobros del club A');
COMMIT;

-- ================= Auditoría inalterable, notificaciones y control dual ===============
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.como(:'admin');
INSERT INTO siglim.auditoria (liga_id, perfil_id, modulo, accion, entidad, entidad_id, motivo)
VALUES ('a0000000-0000-4000-8000-000000000001', :'admin', 'M-01', 'PRUEBA', 'auditoria', NULL, 'Prueba');
SELECT prueba.espera_error($$UPDATE siglim.auditoria SET motivo = 'x'$$, 'permission denied');
SELECT prueba.espera_error($$DELETE FROM siglim.auditoria$$, 'permission denied');
INSERT INTO siglim.notificaciones (perfil_id, evento, titulo) VALUES ('30000000-0000-4000-8000-000000000005', 'COBRO_GENERADO', 'Nuevo cobro');
SELECT prueba.verifica((SELECT count(*) FROM siglim.notificaciones) = 0, 'Los avisos solo los ve su destinatario');
INSERT INTO siglim.solicitudes_cambio_permisos (id, rol_id, permisos_agregados, motivo, solicitado_por)
SELECT 'e1000000-0000-4000-8000-000000000001', r.id, ARRAY[p.id], 'Prueba de control dual', :'admin'
FROM siglim.roles r, siglim.permisos p WHERE r.codigo = 'CAPTURISTA' AND p.codigo = 'partidos.cedula:APROBAR';
SELECT prueba.espera_error($$UPDATE siglim.solicitudes_cambio_permisos SET estado = 'APROBADO', resuelto_por = siglim.usuario_actual(), resuelto_en = now()$$, 'ck_solicitudes_permisos_dual');
COMMIT;

-- ================= Inmutables contra TRUNCATE (SCRUM-397) =============================
-- siglim_app no tiene el privilegio; el dueño del esquema choca con el trigger de sentencia.
BEGIN;
SET LOCAL ROLE siglim_app;
SELECT prueba.espera_error($$TRUNCATE siglim.auditoria$$, 'permission denied');
COMMIT;
BEGIN;   -- como dueño del esquema (sin SET ROLE)
SELECT count(*) AS n_auditoria FROM siglim.auditoria \gset
SELECT prueba.espera_error($$TRUNCATE siglim.auditoria$$, 'RNF-015');
-- resoluciones y parametros_temporada son referenciadas por otras tablas: sin CASCADE ya las
-- protege la llave foránea, así que se prueba la variante con CASCADE, que sí llegaba a vaciarlas.
SELECT prueba.espera_error($$TRUNCATE siglim.resoluciones CASCADE$$, 'RNF-015');
SELECT prueba.espera_error($$TRUNCATE siglim.parametros_temporada CASCADE$$, 'RNF-015');
SELECT prueba.espera_error($$TRUNCATE siglim.perfiles CASCADE$$, 'RNF-015');
SELECT prueba.verifica((SELECT count(*) FROM siglim.auditoria) = :n_auditoria,
  'RNF-015: la auditoría conserva sus filas tras los TRUNCATE rechazados');
SELECT prueba.verifica((SELECT count(*) FROM siglim.resoluciones) > 0
  AND (SELECT count(*) FROM siglim.parametros_temporada) > 0,
  'RNF-015: resoluciones y parámetros conservan sus filas');
COMMIT;

SELECT 'PRUEBAS COMPLETADAS' AS resultado;
