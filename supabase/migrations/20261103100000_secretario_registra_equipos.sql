-- =====================================================================================================
-- El Secretario (liga) registra y edita equipos
--
-- Motivo: el Apéndice E del ERS da al Secretario «M-03 C L E» y RF-009 lo nombra como actor de la
-- gestión de equipos («Directiva de club, Secretario»), pero la semilla de la migración inicial
-- (§12.4) solo le dio organizacion.equipo:CONSULTAR. Sin organizacion.equipo:EDITAR no podía registrar
-- los equipos de un club, que es el primer criterio de aceptación de H-06. Hallado en H-06.1
-- (SCRUM-270).
--
-- Efecto: agrega el par (SECRETARIO, organizacion.equipo:EDITAR) a rol_permisos. Es idempotente
-- (ON CONFLICT DO NOTHING). No cambia tablas, funciones ni políticas; la matriz sigue siendo
-- configurable por el Administrador (DEC-04).
--
-- Regla: no se edita la migración inicial ya aplicada (supabase/README.md, ADR-0004).
-- Refs: H-06.1, RF-009, Apéndice E, DEC-04
-- =====================================================================================================

INSERT INTO siglim.rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id
  FROM siglim.roles r
  JOIN siglim.permisos p ON p.codigo = 'organizacion.equipo:EDITAR'
 WHERE r.codigo = 'SECRETARIO'
ON CONFLICT DO NOTHING;
