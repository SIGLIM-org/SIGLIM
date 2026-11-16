import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const SOLICITAR_OPERACION = 'organizacion.venta:SOLICITAR'
export const APROBAR_OPERACION = 'organizacion.venta:APROBAR'

/**
 * Acciones visibles de B-09 según los permisos de `/yo` (H-02.4). El backend vuelve a decidir el
 * permiso y el ámbito de cada operación; aquí solo se evita ofrecer lo que no aplica.
 */
export function usePermisosOperaciones() {
  const { yo } = useSesionActual()

  return {
    solicitar: tienePermiso(yo.permisos, SOLICITAR_OPERACION),
    aprobar: tienePermiso(yo.permisos, APROBAR_OPERACION),
  }
}
