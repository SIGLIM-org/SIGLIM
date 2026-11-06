-- =====================================================================================================
-- El Tesorero (liga) consulta equipos para dirigir cobros
--
-- Motivo: a diferencia de 20261103100000, esta migración NO corrige una omisión de la semilla: la
-- semilla de la migración inicial (§12.4) coincide con el Apéndice E del ERS, que no da M-03 al
-- Tesorero. Amplía el Apéndice E por DEC-55: RF-137 hace al Tesorero actor de los cobros «a un club o
-- a un equipo», pero sin organizacion.equipo:CONSULTAR el modal «Nuevo cobro» de F-33 no le ofrece
-- equipos y solo puede cargar cobros al club. Hallado en la prueba de aceptación H-29.3 (DEF-06,
-- SCRUM-404).
--
-- Efecto: agrega el par (TESORERO, organizacion.equipo:CONSULTAR) a rol_permisos. Es idempotente
-- (ON CONFLICT DO NOTHING). Solo lectura: no concede organizacion.equipo:EDITAR. No cambia tablas,
-- funciones ni políticas; la matriz sigue siendo configurable por el Administrador (DEC-04).
--
-- Regla: no se editan las migraciones ya aplicadas (supabase/README.md, ADR-0004).
-- Refs: DEF-06, SCRUM-404, H-29.3, RF-137, Apéndice E, DEC-55, DEC-04
-- =====================================================================================================

INSERT INTO siglim.rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id
  FROM siglim.roles r
  JOIN siglim.permisos p ON p.codigo = 'organizacion.equipo:CONSULTAR'
 WHERE r.codigo = 'TESORERO'
ON CONFLICT DO NOTHING;
