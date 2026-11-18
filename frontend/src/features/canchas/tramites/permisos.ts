import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const CREAR_TRAMITE = 'canchas.solicitud:CREAR'
export const APROBAR_TRAMITE = 'canchas.solicitud:APROBAR'
export const GENERAR_OFICIO = 'canchas.oficio:GENERAR'

/** Cualquiera de los tres permisos da acceso a la bandeja de trámites (DEC-136, [PROPUESTA]). */
export const PERMISOS_TRAMITES = [CREAR_TRAMITE, APROBAR_TRAMITE, GENERAR_OFICIO] as const

/**
 * Acciones visibles de B-07 según los permisos de `/yo` (H-02.4). El backend vuelve a decidir el
 * permiso y el ámbito de cada trámite.
 */
export function usePermisosTramites() {
  // DEC-137: la carta solo la genera quien tiene canchas.oficio:GENERAR (en la matriz inicial, el
  // Secretario); la Directiva no la imprime salvo que la matriz le otorgue el permiso.
  const { yo } = useSesionActual()

  return {
    capturar: tienePermiso(yo.permisos, CREAR_TRAMITE),
    dictaminar: tienePermiso(yo.permisos, APROBAR_TRAMITE),
    generarOficio: tienePermiso(yo.permisos, GENERAR_OFICIO),
  }
}
