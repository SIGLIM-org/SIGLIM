import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const CREAR_TRAMITE = 'canchas.solicitud:CREAR'
export const APROBAR_TRAMITE = 'canchas.solicitud:APROBAR'
export const GENERAR_OFICIO = 'canchas.oficio:GENERAR'

/** Cualquiera de los tres permisos da acceso a la bandeja de trámites [PROPUESTA]. */
export const PERMISOS_TRAMITES = [CREAR_TRAMITE, APROBAR_TRAMITE, GENERAR_OFICIO] as const

/**
 * Acciones visibles de B-07 según los permisos de `/yo` (H-02.4). El backend vuelve a decidir el
 * permiso y el ámbito de cada trámite.
 */
export function usePermisosTramites() {
  const { yo } = useSesionActual()

  return {
    capturar: tienePermiso(yo.permisos, CREAR_TRAMITE),
    dictaminar: tienePermiso(yo.permisos, APROBAR_TRAMITE),
    generarOficio: tienePermiso(yo.permisos, GENERAR_OFICIO),
  }
}
