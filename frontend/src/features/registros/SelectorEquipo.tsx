import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import type { GrupoEquipos } from '@/features/registros/asistenteAlta'

type Props = {
  id: string
  grupos: readonly GrupoEquipos[]
  valor: string
  error?: string
  deshabilitado?: boolean
  /** Por omisión, «Equipo». */
  etiqueta?: string
  /** Por omisión, la ayuda del alta; `null` la quita. */
  ayuda?: string | null
  /** Por omisión es obligatorio, como en el alta. */
  requerido?: boolean
  /** Muestra el club en cada opción; la plantilla lo omite porque ya se eligió antes. */
  conClub?: boolean
  onCambiar: (equipoId: string) => void
}

const AYUDA_ALTA =
  'El registro queda en la temporada del equipo. Un jugador solo puede estar en un equipo por temporada.'

/**
 * Equipo del registro, agrupado por temporada y categoría (DEC-108). La temporada del registro es
 * la del grupo del equipo (DEC-85); el backend ya limita la lista al ámbito de la sesión. La
 * plantilla (DEC-116) lo reutiliza sin la ayuda del alta.
 */
export function SelectorEquipo({
  id,
  grupos,
  valor,
  error,
  deshabilitado,
  etiqueta = 'Equipo',
  ayuda = AYUDA_ALTA,
  requerido = true,
  conClub = true,
  onCambiar,
}: Props) {
  return (
    <CampoSeleccion
      id={id}
      etiqueta={etiqueta}
      required={requerido}
      value={valor}
      error={error}
      ayuda={ayuda ?? undefined}
      disabled={deshabilitado}
      onChange={(evento) => onCambiar(evento.target.value)}
    >
      <option value="">Elige el equipo</option>
      {grupos.map((grupo) => (
        <optgroup key={grupo.clave} label={grupo.etiqueta}>
          {grupo.equipos.map((equipo) => (
            <option key={equipo.id} value={equipo.id}>
              {conClub
                ? `${equipo.nombre} · ${equipo.club.nombre} · Grupo ${equipo.grupo.nombre}`
                : `${equipo.nombre} · Grupo ${equipo.grupo.nombre}`}
            </option>
          ))}
        </optgroup>
      ))}
    </CampoSeleccion>
  )
}
