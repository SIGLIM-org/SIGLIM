import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { EstructuraTemporada } from '@/features/temporadas/EstructuraTemporada'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de estructura y parámetros de una temporada (F-11, H-05.3). `RutaConPermiso`
 * solo mira el módulo, así que aquí se exige el permiso de consultar antes de pedir datos (D8).
 */
export function PantallaEstructuraTemporada() {
  return (
    <ConPermiso
      permiso="temporadas.temporada:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <EstructuraTemporada />
    </ConPermiso>
  )
}
