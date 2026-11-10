import { TriangleAlert } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { estadoPartido } from '@/features/canchas/etiquetas'
import { formatearFechaCorta, formatearHorario } from '@/features/canchas/fechas'
import type { PartidoEnCancha } from '@/features/canchas/tipos'

type Props = {
  /** Qué pasa con esos partidos: el `detail` del servidor o el resultado del registro. */
  mensaje: string
  /** Aclaración de que los partidos no cambian. */
  nota: string
  partidos: readonly PartidoEnCancha[]
}

/**
 * Partidos dentro de un bloqueo o de una cancha que se desactiva (PEN-007). Es una
 * advertencia que se puede aceptar, no un bloqueo: los partidos no se modifican.
 */
export function AvisoPartidosAfectados({ mensaje, nota, partidos }: Props) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-warning/50 bg-warning/14 p-4 text-text-main">
      <div className="flex gap-3">
        <TriangleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-strong" />
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-headline-sm text-warning-strong">{mensaje}</p>
          <p className="text-body-md">{nota}</p>
        </div>
      </div>
      <ul className="flex flex-col gap-2">
        {partidos.map((partido) => {
          const estado = estadoPartido(partido.estado)

          return (
            <li key={partido.id} className="flex flex-col gap-1.5 rounded-md border border-border bg-surface p-3">
              <p className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                <span className="text-body-md text-text-muted">
                  {formatearFechaCorta(partido.periodo.desde)}{' '}
                  <span className="font-mono text-mono-data text-text-main">
                    {formatearHorario(partido.periodo)}
                  </span>
                </span>
                <Insignia variante={estado.variante}>{estado.texto}</Insignia>
              </p>
              <p className="text-label-md text-text-main">
                {partido.equipoLocal.nombre} vs {partido.equipoVisitante.nombre}
              </p>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
