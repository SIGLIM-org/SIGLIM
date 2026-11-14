import { CircleAlert, Clock, MapPin, Pencil } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import {
  etiquetaConflicto,
  etiquetaEstadoPartido,
  varianteEstadoPartido,
} from '@/features/programacion/etiquetas'
import { formatearFechaCorta, horarioPartido } from '@/features/programacion/fechas'
import type { AvisoVerificacion, Partido } from '@/features/programacion/tipos'

type Props = {
  partido: Partido
  /** Posición en la jornada, solo para orientar a la persona. */
  numero: number
  avisos: readonly AvisoVerificacion[]
  puedeEditar: boolean
  onEditar: (partido: Partido) => void
}

/**
 * Un partido de la jornada (F-22). Si la verificación encontró conflictos, la fila se marca y
 * explica cada uno debajo del encuentro (H-12.4).
 */
export function FilaPartido({ partido, numero, avisos, puedeEditar, onEditar }: Props) {
  const tieneConflictos = avisos.length > 0

  return (
    <li
      className={[
        'overflow-hidden rounded-lg border bg-surface',
        tieneConflictos ? 'border-error/40' : 'border-border',
      ].join(' ')}
    >
      <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <p className="text-label-sm uppercase tracking-wide text-text-muted">Partido {numero}</p>
          <p className="mt-0.5 break-words text-headline-sm text-text-main">
            {partido.equipoLocal.nombre}{' '}
            <span className="font-normal text-text-muted">vs.</span>{' '}
            {partido.equipoVisitante.nombre}
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-body-md text-text-muted">
            <span className="inline-flex items-center gap-1.5">
              <Clock aria-hidden="true" className="size-4 shrink-0" />
              <span>
                {formatearFechaCorta(partido.inicio)} · {horarioPartido(partido)}
              </span>
            </span>
            <span className="inline-flex min-w-0 items-center gap-1.5">
              <MapPin aria-hidden="true" className="size-4 shrink-0" />
              <span className="break-words">{partido.cancha.nombre}</span>
            </span>
            <Insignia variante={varianteEstadoPartido[partido.estado]}>
              {etiquetaEstadoPartido[partido.estado]}
            </Insignia>
          </div>
        </div>

        {puedeEditar && (
          <Button
            variante="secundario"
            icono={Pencil}
            onClick={() => onEditar(partido)}
            className="w-full shrink-0 sm:w-auto"
            aria-label={`Editar el partido ${partido.equipoLocal.nombre} vs. ${partido.equipoVisitante.nombre}`}
          >
            Editar
          </Button>
        )}
      </div>

      {tieneConflictos && (
        <div
          role="group"
          aria-label={`Conflictos del partido ${partido.equipoLocal.nombre} vs. ${partido.equipoVisitante.nombre}`}
          className="border-t border-error/20 bg-error/5 px-4 py-3"
        >
          <p className="flex items-center gap-2 text-label-md font-medium text-error-strong">
            <CircleAlert aria-hidden="true" className="size-4 shrink-0" />
            {avisos.length === 1 ? '1 conflicto por resolver' : `${avisos.length} conflictos por resolver`}
          </p>
          <ul className="mt-2 flex flex-col gap-2">
            {avisos.map((aviso, indice) => (
              <li key={`${aviso.codigo}-${indice}`} className="text-body-md text-error-strong">
                <span className="font-semibold">{etiquetaConflicto(aviso.codigo)}:</span>{' '}
                {aviso.mensaje}
              </li>
            ))}
          </ul>
        </div>
      )}
    </li>
  )
}
