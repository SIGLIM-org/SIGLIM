import { CatalogoClubes } from '@/features/organizacion/CatalogoClubes'
import { CONSULTAR_CLUB } from '@/features/organizacion/permisosClubes'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de clubes (F-15, H-06.2). `RutaConPermiso` solo mira el módulo, así que
 * aquí se exige el permiso de consultar clubes antes de montar el catálogo y pedir sus datos.
 */
export function PantallaClubes() {
  return (
    <ConPermiso permiso={CONSULTAR_CLUB} alternativa={<PaginaSinPermiso rutaInicio="/app" />}>
      <CatalogoClubes />
    </ConPermiso>
  )
}
