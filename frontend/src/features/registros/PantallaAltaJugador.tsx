import { AsistenteAltaJugador } from '@/features/registros/AsistenteAltaJugador'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

export const RUTA_ALTA_JUGADOR = '/app/organizacion/jugadores/alta'

/** Permiso de la acción de alta: el Secretario consulta registros, pero no los da de alta (DEC-108). */
export const PERMISO_ALTA_JUGADOR = 'registros.registro:EDITAR'

/**
 * Pantalla de alta de jugador (F-18 con F-06, H-09.3). Solo con `registros.registro:EDITAR`; sin él,
 * la 403. El backend vuelve a autorizar cada escritura sobre el club del equipo (DEC-85).
 */
export function PantallaAltaJugador() {
  return (
    <ConPermiso permiso={PERMISO_ALTA_JUGADOR} alternativa={<PaginaSinPermiso rutaInicio="/app" />}>
      <AsistenteAltaJugador />
    </ConPermiso>
  )
}
