import { CalendarPlus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { FilaPartido } from '@/features/programacion/FilaPartido'
import type { AvisoVerificacion, Partido } from '@/features/programacion/tipos'

type Props = {
  partidos: readonly Partido[]
  conflictosPorPartido: ReadonlyMap<string, readonly AvisoVerificacion[]>
  puedeEditar: boolean
  onEditar: (partido: Partido) => void
  onNuevo: () => void
}

/** Partidos de la jornada, en el orden en que llegan del API (por inicio). */
export function ListaPartidos({
  partidos,
  conflictosPorPartido,
  puedeEditar,
  onEditar,
  onNuevo,
}: Props) {
  if (partidos.length === 0) {
    return (
      <EstadoVacio
        icono={CalendarPlus}
        titulo="Sin partidos programados"
        descripcion="Programa el primer encuentro de esta jornada."
        accion={
          puedeEditar ? (
            <Button icono={CalendarPlus} onClick={onNuevo}>
              Programar partido
            </Button>
          ) : undefined
        }
      />
    )
  }

  return (
    <ol className="flex flex-col gap-3">
      {partidos.map((partido, indice) => (
        <FilaPartido
          key={partido.id}
          partido={partido}
          numero={indice + 1}
          avisos={conflictosPorPartido.get(partido.id) ?? []}
          puedeEditar={puedeEditar}
          onEditar={onEditar}
        />
      ))}
    </ol>
  )
}
