-- =====================================================================================================
-- Protege las tablas inmutables contra TRUNCATE
--
-- Motivo: los triggers tg_auditoria_inmutable, tg_resoluciones_inmutable y tg_parametros_inmutable son
-- BEFORE UPDATE OR DELETE ... FOR EACH ROW. TRUNCATE no recorre filas (sustituye los archivos de la
-- tabla), así que esos triggers nunca se disparan con él. siglim_app no tiene el privilegio TRUNCATE,
-- pero el dueño del esquema sí, y también podía vaciarlas de rebote con un TRUNCATE ... CASCADE sobre
-- una tabla referenciada (por ejemplo perfiles o temporadas). Hallado en H-02.2 (SCRUM-257).
--
-- Efecto: un trigger BEFORE TRUNCATE ... FOR EACH STATEMENT por tabla, que reutiliza
-- siglim.tg_inmutable(): lanza P0001 con HINT RNF-015 y revierte toda la sentencia, incluido el
-- TRUNCATE por cascada. No cambia INSERT ni SELECT.
--
-- Límite: el dueño de la tabla aún puede desactivar o borrar el trigger (ALTER TABLE ... DISABLE
-- TRIGGER, DROP TRIGGER). Esto evita el vaciado accidental; la defensa contra un dueño malintencionado
-- es que el backend opera como siglim_app, sin TRUNCATE (ADR-0014).
--
-- Regla: no se edita la migración inicial ya aplicada (supabase/README.md, ADR-0004).
-- Refs: SCRUM-397, H-02.2, RNF-015, RF-062, RF-093, ADR-0014
-- =====================================================================================================

CREATE TRIGGER tg_auditoria_sin_truncate BEFORE TRUNCATE ON siglim.auditoria
  FOR EACH STATEMENT EXECUTE FUNCTION siglim.tg_inmutable();
CREATE TRIGGER tg_resoluciones_sin_truncate BEFORE TRUNCATE ON siglim.resoluciones
  FOR EACH STATEMENT EXECUTE FUNCTION siglim.tg_inmutable();
CREATE TRIGGER tg_parametros_sin_truncate BEFORE TRUNCATE ON siglim.parametros_temporada
  FOR EACH STATEMENT EXECUTE FUNCTION siglim.tg_inmutable();

COMMENT ON TRIGGER tg_auditoria_sin_truncate ON siglim.auditoria IS
  'Impide vaciar la auditoría con TRUNCATE, también por cascada (RNF-015, SCRUM-397).';
COMMENT ON TRIGGER tg_resoluciones_sin_truncate ON siglim.resoluciones IS
  'Impide vaciar las resoluciones con TRUNCATE, también por cascada (RNF-015, SCRUM-397).';
COMMENT ON TRIGGER tg_parametros_sin_truncate ON siglim.parametros_temporada IS
  'Impide vaciar los parámetros de temporada con TRUNCATE, también por cascada (RNF-015, SCRUM-397).';
