import type { PropietarioCancha } from '@/features/canchas/tipos'
import { CONSULTAR_CLUB, CONSULTAR_TEMPORADA } from '@/features/organizacion/permisosClubes'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const EDITAR_CANCHA = 'canchas.cancha:EDITAR'

/**
 * Qué puede hacer la sesión con los horarios de club de una cancha (B-13.2, RF-119, DEC-33), según
 * los permisos de `/yo` (H-02.4); nunca por el nombre del rol. El backend sigue siendo la barrera.
 *
 * [PROPUESTA] La sección solo se ve con `temporadas.temporada:CONSULTAR`, el mismo criterio de
 * DEC-54 para elegir grupo: el selector lee las temporadas y sus categorías (M-02), y un rol con
 * `canchas.cancha:CONSULTAR` sin ese permiso (p. ej. Tesorería) vería un 403 en lugar de la tabla.
 * Editar pide `canchas.cancha:EDITAR` y, en una cancha `LIGA`, `organizacion.club:CONSULTAR` para
 * elegir el club de cada comodato.
 */
export function usePermisosHorarios(propietario: PropietarioCancha) {
  const { yo } = useSesionActual()
  const ver = propietario !== 'EXTERNO' && tienePermiso(yo.permisos, CONSULTAR_TEMPORADA)
  const editar =
    ver &&
    tienePermiso(yo.permisos, EDITAR_CANCHA) &&
    (propietario !== 'LIGA' || tienePermiso(yo.permisos, CONSULTAR_CLUB))

  return { ver, editar }
}
