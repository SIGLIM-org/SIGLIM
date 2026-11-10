import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { GestionTesoreria } from '@/features/tesoreria/GestionTesoreria'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de tesorería (F-33, H-29.2). `RutaConPermiso` solo mira el módulo, así que
 * aquí se exige el permiso de consultar antes de montar la gestión y pedir sus datos.
 */
export function PantallaTesoreria() {
  return (
    <ConPermiso
      permiso="tesoreria.cobro:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <GestionTesoreria />
    </ConPermiso>
  )
}
