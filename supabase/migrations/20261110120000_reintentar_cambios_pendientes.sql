-- =====================================================================================================
-- Reintentar los cambios de las migraciones que el proyecto compartido no aplicó
--
-- Causa real (DEF-07): el 5 de noviembre el repositorio se transfirió a SIGLIM-org/SIGLIM, pero la
-- integración de GitHub de Supabase siguió apuntando a israelweil/SIGLIM y dejó de recibir los
-- merges de develop. Por eso el historial del proyecto compartido termina en 20261103100000 y no
-- registra 20261106160000_tesorero_consulta_equipos (DEF-06) ni
-- 20261108100000_cargo_directiva_pendiente (H-22.1), fusionadas después de la transferencia.
-- El Tesorero sigue sin organizacion.equipo:CONSULTAR y ux_cargos_vigentes conserva su definición
-- anterior; tampoco corrieron las migraciones de los merges posteriores.
--
-- Esta migración NO es el arreglo: el arreglo es reconectar la integración con SIGLIM-org/SIGLIM
-- (rama de producción develop) y verificar el historial del compartido; al hacerlo, las migraciones
-- originales se aplican solas (DEC-52, ADR-0004). Mientras tanto, esta migración solo repite los dos
-- cambios con una versión posterior como red de seguridad idempotente, para que el esquema quede
-- consistente cuando la integración vuelva a correr:
--   1. El rol TESORERO recibe organizacion.equipo:CONSULTAR (DEC-55, RF-137), sin EDITAR.
--   2. ux_cargos_vigentes solo cubre los cargos autorizados y vigentes, para que un cambio de
--      directiva pendiente (autorizado_por nulo) conviva con el cargo anterior (H-22.1, RF-102).
--
-- Ambas sentencias son idempotentes: si la integración vuelve a aplicar las migraciones originales,
-- no cambian nada. No modifica columnas, funciones ni políticas; la matriz de permisos sigue siendo
-- configurable por el Administrador (DEC-04). No debe fusionarse antes de reconectar la integración.
--
-- Refs: DEF-07, DEF-06, H-22.1, RF-102, RF-137, DEC-03, DEC-04, DEC-52, DEC-55
-- =====================================================================================================

INSERT INTO siglim.rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id
  FROM siglim.roles r
  JOIN siglim.permisos p ON p.codigo = 'organizacion.equipo:CONSULTAR'
 WHERE r.codigo = 'TESORERO'
ON CONFLICT DO NOTHING;

DROP INDEX IF EXISTS siglim.ux_cargos_vigentes;

CREATE UNIQUE INDEX ux_cargos_vigentes
    ON siglim.cargos_directiva (club_id, cargo)
 WHERE hasta IS NULL AND autorizado_por IS NOT NULL;
