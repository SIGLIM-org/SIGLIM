import { useId } from 'react'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import type { Club } from '@/features/organizacion/tipos'
import type { GrupoEquipos } from '@/features/registros/asistenteAlta'
import { SelectorEquipo } from '@/features/registros/SelectorEquipo'

type Props = {
  clubes: readonly Club[]
  clubesCargando: boolean
  clubId: string
  grupos: readonly GrupoEquipos[]
  equiposCargando: boolean
  equipoId: string
  onClub: (clubId: string) => void
  onEquipo: (equipoId: string) => void
}

/**
 * Club y luego equipo (DEC-116). Con un solo club el selector queda deshabilitado, pero visible, para
 * que se vea de qué club es la plantilla. Los equipos llegan agrupados por temporada y categoría.
 */
export function SelectoresPlantilla({
  clubes,
  clubesCargando,
  clubId,
  grupos,
  equiposCargando,
  equipoId,
  onClub,
  onEquipo,
}: Props) {
  const id = useId()

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <CampoSeleccion
        id={`${id}-club`}
        etiqueta="Club"
        value={clubId}
        disabled={clubesCargando || clubes.length <= 1}
        onChange={(evento) => onClub(evento.target.value)}
      >
        <option value="">{clubesCargando ? 'Cargando clubes…' : 'Elige el club'}</option>
        {clubes.map((club) => (
          <option key={club.id} value={club.id}>
            {club.nombre}
          </option>
        ))}
      </CampoSeleccion>

      <SelectorEquipo
        id={`${id}-equipo`}
        grupos={grupos}
        valor={equipoId}
        ayuda={null}
        requerido={false}
        conClub={false}
        deshabilitado={!clubId || equiposCargando || grupos.length === 0}
        onCambiar={onEquipo}
      />
    </div>
  )
}
