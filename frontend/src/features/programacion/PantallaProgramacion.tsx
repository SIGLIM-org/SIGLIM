import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { ProgramacionJornadas } from '@/features/programacion/ProgramacionJornadas'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de jornadas (F-22, H-12.4). `RutaConPermiso` solo mira el módulo, así que aquí
 * se exige el permiso de consultar antes de montar la pantalla y pedir sus datos.
 */
export function PantallaProgramacion() {
  return (
    <ConPermiso
      permiso="programacion.jornada:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <ProgramacionJornadas />
    </ConPermiso>
  )
}
