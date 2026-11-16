import { useParams } from 'react-router'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { RevisionJornada } from '@/features/programacion/RevisionJornada'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta `/app/programacion/jornadas/:jornadaId/revision` (F-23, H-13.2). Exige el
 * permiso de consultar la jornada; los dictámenes solo se muestran con el de aprobar.
 */
export function PantallaRevisionJornada() {
  const { jornadaId = '' } = useParams()

  return (
    <ConPermiso
      permiso="programacion.jornada:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <RevisionJornada key={jornadaId} jornadaId={jornadaId} />
    </ConPermiso>
  )
}
