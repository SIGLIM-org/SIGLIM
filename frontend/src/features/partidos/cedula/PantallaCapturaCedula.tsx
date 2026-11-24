import { useParams } from 'react-router'
import { CapturaCedula } from '@/features/partidos/cedula/CapturaCedula'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta `/app/partidos/:partidoId/cedula` (F-26, H-16.3). Exige capturar cédulas; el
 * backend vuelve a autorizar cada operación (VAL-015).
 */
export function PantallaCapturaCedula() {
  const { partidoId = '' } = useParams()

  return (
    <ConPermiso permiso="partidos.cedula:CAPTURAR" alternativa={<PaginaSinPermiso rutaInicio="/app" />}>
      <CapturaCedula key={partidoId} partidoId={partidoId} />
    </ConPermiso>
  )
}
