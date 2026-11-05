import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { GestionUsuarios } from '@/features/usuarios/GestionUsuarios'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de usuarios (F-12, H-03.2). `RutaConPermiso` solo mira el módulo, así que aquí
 * se exige el permiso de consultar antes de montar la gestión y pedir sus datos.
 */
export function PantallaUsuarios() {
  return (
    <ConPermiso
      permiso="seguridad.usuario:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <GestionUsuarios />
    </ConPermiso>
  )
}
