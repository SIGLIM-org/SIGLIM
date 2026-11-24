import { useParams } from 'react-router'
import { DetallePartido } from '@/features/partidos/DetallePartido'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta `/app/partidos/:partidoId` (F-25, H-15.3). Exige consultar partidos; el backend
 * acota el alcance y responde 404 fuera de él (DEC-161).
 */
export function PantallaDetallePartido() {
  const { partidoId = '' } = useParams()

  return (
    <ConPermiso
      permiso="partidos.partido:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <DetallePartido key={partidoId} partidoId={partidoId} />
    </ConPermiso>
  )
}
