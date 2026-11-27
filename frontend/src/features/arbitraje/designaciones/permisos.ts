import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const CONSULTAR_DESIGNACION = 'arbitraje.designacion:CONSULTAR'
export const EDITAR_DESIGNACION = 'arbitraje.designacion:EDITAR'

/**
 * Acciones visibles de F-24 según `/yo` (H-14.2). `consultar` exige el ámbito `LIGA`: con
 * `PROPIO` el Árbitro solo ve «Mis designaciones» (DEC-205, DEC-212). El backend vuelve a decidir.
 */
export function usePermisosDesignacion() {
  const { yo } = useSesionActual()

  return {
    consultar: yo.permisos.some(
      ({ codigo, ambito }) => codigo === CONSULTAR_DESIGNACION && ambito === 'LIGA',
    ),
    editar: tienePermiso(yo.permisos, EDITAR_DESIGNACION),
  }
}
