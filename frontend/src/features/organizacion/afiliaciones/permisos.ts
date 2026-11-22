import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const CAPTURAR_AFILIACION = 'organizacion.afiliacion:CAPTURAR'
export const APROBAR_AFILIACION = 'organizacion.afiliacion:APROBAR'

/**
 * Acciones visibles de F-16 según los permisos de `/yo` (H-02.4). El backend vuelve a decidir el
 * permiso y el ámbito de cada tarjetón; aquí solo se evita ofrecer lo que no aplica.
 */
export function usePermisosAfiliaciones() {
  const { yo } = useSesionActual()

  return {
    capturar: tienePermiso(yo.permisos, CAPTURAR_AFILIACION),
    aprobar: tienePermiso(yo.permisos, APROBAR_AFILIACION),
  }
}
