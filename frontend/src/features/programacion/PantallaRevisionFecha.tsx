import { useParams } from 'react-router'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { RevisionFecha } from '@/features/programacion/RevisionFecha'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta `/app/programacion/jornadas/fechas/:fechaId/revision` (F-23, H-35.3). Exige el
 * permiso de consultar la programación; los dictámenes solo se muestran con el de aprobar.
 */
export function PantallaRevisionFecha() {
  const { fechaId = '' } = useParams()

  return (
    <ConPermiso
      permiso="programacion.jornada:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <RevisionFecha key={fechaId} fechaId={fechaId} />
    </ConPermiso>
  )
}
