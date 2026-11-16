import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const CONSULTAR_JORNADA = 'programacion.jornada:CONSULTAR'
export const EDITAR_JORNADA = 'programacion.jornada:EDITAR'
export const ENVIAR_JORNADA = 'programacion.jornada:ENVIAR'
export const APROBAR_JORNADA = 'programacion.jornada:APROBAR'

/**
 * Acciones visibles de F-22 y F-23 según los permisos de `/yo` (H-02.4). El backend vuelve a autorizar
 * cada operación con su ámbito (ADR-0007).
 */
export function usePermisosProgramacion() {
  const { yo } = useSesionActual()

  return {
    consultar: tienePermiso(yo.permisos, CONSULTAR_JORNADA),
    editar: tienePermiso(yo.permisos, EDITAR_JORNADA),
    enviar: tienePermiso(yo.permisos, ENVIAR_JORNADA),
    aprobar: tienePermiso(yo.permisos, APROBAR_JORNADA),
  }
}
