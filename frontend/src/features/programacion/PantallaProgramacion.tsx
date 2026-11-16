import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { BandejaRevisionJornadas } from '@/features/programacion/BandejaRevisionJornadas'
import { ProgramacionJornadas } from '@/features/programacion/ProgramacionJornadas'
import { usePermisosProgramacion } from '@/features/programacion/permisos'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de jornadas (F-22, H-12.4). `RutaConPermiso` solo mira el módulo, así que aquí
 * se exige el permiso de consultar antes de montar la pantalla y pedir sus datos.
 *
 * Quien programa ve F-22. Quien aprueba sin poder editar (la Presidencia) ve la bandeja de
 * jornadas por revisar que lleva a F-23 (H-13.2): el menú no cambia (DEC-69).
 */
export function PantallaProgramacion() {
  const permisos = usePermisosProgramacion()

  return (
    <ConPermiso
      permiso="programacion.jornada:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      {!permisos.editar && permisos.aprobar ? (
        <BandejaRevisionJornadas />
      ) : (
        <ProgramacionJornadas />
      )}
    </ConPermiso>
  )
}
