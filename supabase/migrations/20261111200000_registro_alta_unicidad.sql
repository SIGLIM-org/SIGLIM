-- H-09.2 · Alta de un registro de jugador con unicidad por CURP.
--
-- 1. Lectura mínima de los registros de una persona en la temporada (DEC-88). La política
--    registros_leer solo muestra a un ámbito CLUB los registros de su club, así que el servicio no
--    podría explicar dónde está ya registrada una CURP (VAL-017) ni detectar una baja en otro club
--    (VAL-026). registros_en_temporada devuelve solo club, equipo y estado; el backend decide qué
--    expone (nunca el estado ni los ids de otro club).
-- 2. Requisitos de un menor (DEC-89): requisitos_menor evalúa la misma condición que
--    tg_registros_validar al activar (tutela vigente y consentimiento no revocado otorgado por un
--    tutor vigente), aunque RLS no muestre la tutela o el consentimiento registrados por otro club.
-- 3. Comentario de ux_registros_activo para que un 23505 se traduzca como VAL-017 y no como
--    DUPLICADO (convenciones de API §3.2).
--
-- Las funciones son SECURITY DEFINER con search_path fijo y solo las ejecuta siglim_app. El INSERT
-- del registro sigue sujeto a RLS (registros_crear). Se aplica al Supabase compartido al fusionar en
-- develop (DEC-52).
--
-- Refs: H-09.2, RF-013, RF-107, RN-037, RN-039, VAL-017, VAL-025, VAL-026, DEC-88, DEC-89, DEC-90

-- 1. Registros de la persona en la temporada ----------------------------------------------------

CREATE FUNCTION siglim.registros_en_temporada(p_persona uuid, p_temporada uuid)
RETURNS TABLE (club_id uuid, club_nombre text, equipo_id uuid, equipo_nombre text,
               estado siglim.estado_registro)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$
  SELECT c.id, c.nombre::text, e.id, e.nombre::text, r.estado
    FROM registros_jugador r
    JOIN equipos e ON e.id = r.equipo_id
    JOIN clubes c ON c.id = e.club_id
   WHERE r.persona_id = p_persona
     AND r.temporada_id = p_temporada
     AND r.estado <> 'RECHAZADO'
   ORDER BY r.creado_en, r.id
$$;
COMMENT ON FUNCTION siglim.registros_en_temporada(uuid, uuid) IS
  'Registros no rechazados de la persona en la temporada, con club y equipo, aunque RLS no los muestre; el backend no expone el estado ni los ids de otro club (RF-107, VAL-017, VAL-026, DEC-88, DEC-90).';

-- 2. Requisitos de un menor ---------------------------------------------------------------------

CREATE FUNCTION siglim.requisitos_menor(p_persona uuid)
RETURNS TABLE (tiene_tutor_vigente boolean, tiene_consentimiento boolean)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = siglim, pg_temp
AS $$
  SELECT EXISTS (SELECT 1 FROM tutelas t WHERE t.menor_id = p_persona AND t.vigente),
         EXISTS (SELECT 1 FROM consentimientos c
                   JOIN tutelas t ON t.menor_id = c.persona_id AND t.tutor_id = c.otorgado_por_id AND t.vigente
                  WHERE c.persona_id = p_persona AND c.revocado_en IS NULL)
$$;
COMMENT ON FUNCTION siglim.requisitos_menor(uuid) IS
  'Si el menor tiene tutela vigente y consentimiento no revocado de un tutor vigente: la misma condición que tg_registros_validar al activar (VAL-025, DEC-89, DEC-90).';

REVOKE EXECUTE ON FUNCTION siglim.registros_en_temporada(uuid, uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION siglim.requisitos_menor(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION siglim.registros_en_temporada(uuid, uuid) TO siglim_app;
GRANT EXECUTE ON FUNCTION siglim.requisitos_menor(uuid) TO siglim_app;

-- 3. Código del ERS en el índice de unicidad -----------------------------------------------------

COMMENT ON INDEX siglim.ux_registros_activo IS 'VAL-017: la CURP ya tiene un registro activo en la temporada.';
