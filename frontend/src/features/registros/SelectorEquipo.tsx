import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import type { GrupoEquipos } from '@/features/registros/asistenteAlta'

type Props = {
  id: string
  grupos: readonly GrupoEquipos[]
  valor: string
  error?: string
  deshabilitado?: boolean
  onCambiar: (equipoId: string) => void
}

/**
 * Equipo del registro, agrupado por temporada y categoría (DEC-108). La temporada del registro es
 * la del grupo del equipo (DEC-85); el backend ya limita la lista al ámbito de la sesión.
 */
export function SelectorEquipo({ id, grupos, valor, error, deshabilitado, onCambiar }: Props) {
  return (
    <CampoSeleccion
      id={id}
      etiqueta="Equipo"
      required
      value={valor}
      error={error}
      ayuda="El registro queda en la temporada del equipo. Un jugador solo puede estar en un equipo por temporada."
      disabled={deshabilitado}
      onChange={(evento) => onCambiar(evento.target.value)}
    >
      <option value="">Elige el equipo</option>
      {grupos.map((grupo) => (
        <optgroup key={grupo.clave} label={grupo.etiqueta}>
          {grupo.equipos.map((equipo) => (
            <option key={equipo.id} value={equipo.id}>
              {equipo.nombre} · {equipo.club.nombre} · Grupo {equipo.grupo.nombre}
            </option>
          ))}
        </optgroup>
      ))}
    </CampoSeleccion>
  )
}
