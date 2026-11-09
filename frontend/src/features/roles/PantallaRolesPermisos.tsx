import { GestionRolesPermisos } from '@/features/roles/GestionRolesPermisos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de roles y permisos (F-13, H-20.2). `RutaConPermiso` solo mira el módulo M-01,
 * así que aquí se exige el permiso de consultar roles antes de montar la pantalla y pedir sus datos
 * (VAL-015).
 */
export function PantallaRolesPermisos() {
  return (
    <ConPermiso
      permiso="seguridad.rol:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <GestionRolesPermisos />
    </ConPermiso>
  )
}
