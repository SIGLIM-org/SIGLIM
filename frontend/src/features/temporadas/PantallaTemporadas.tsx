import { GestionTemporadas } from '@/features/temporadas/GestionTemporadas'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de temporadas (F-10, H-04.2). `RutaConPermiso` solo mira el módulo, así que
 * aquí se exige el permiso de consultar antes de montar la gestión y pedir sus datos.
 */
export function PantallaTemporadas() {
  return (
    <ConPermiso
      permiso="temporadas.temporada:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <GestionTemporadas />
    </ConPermiso>
  )
}
