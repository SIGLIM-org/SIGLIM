import { CatalogoCanchas } from '@/features/canchas/CatalogoCanchas'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de canchas (F-21, H-07.2). `RutaConPermiso` solo mira el módulo, así que
 * aquí se exige el permiso de consultar antes de montar el catálogo y pedir sus datos.
 */
export function PantallaCanchas() {
  return (
    <ConPermiso
      permiso="canchas.cancha:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <CatalogoCanchas />
    </ConPermiso>
  )
}
