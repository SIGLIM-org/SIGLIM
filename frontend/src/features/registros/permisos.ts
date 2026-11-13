import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const CARGAR_DOCUMENTO = 'registros.documento:CREAR'
export const CONSULTAR_DOCUMENTO = 'registros.documento:CONSULTAR'
export const VALIDAR_DOCUMENTO = 'registros.documento:VALIDAR'
export const VALIDAR_REGISTRO = 'registros.registro:VALIDAR'

/**
 * Acciones visibles sobre documentos y registros según `/yo` (H-02.4). El backend vuelve a decidir
 * el permiso y el ámbito de cada registro (DEC-91, DEC-97); aquí solo se evita ofrecer lo que no
 * aplica.
 */
export function usePermisosRegistros() {
  const { yo } = useSesionActual()

  return {
    cargar: tienePermiso(yo.permisos, CARGAR_DOCUMENTO),
    ver: tienePermiso(yo.permisos, CONSULTAR_DOCUMENTO),
    validarDocumento: tienePermiso(yo.permisos, VALIDAR_DOCUMENTO),
    validarRegistro: tienePermiso(yo.permisos, VALIDAR_REGISTRO),
  }
}
