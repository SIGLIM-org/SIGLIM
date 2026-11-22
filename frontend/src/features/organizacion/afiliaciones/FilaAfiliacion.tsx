import { CalendarDays, UserRound } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoAfiliacion, nombrePerfil } from '@/features/organizacion/afiliaciones/etiquetas'
import type { Afiliacion } from '@/features/organizacion/afiliaciones/tipos'
import { formatearFechaHora } from '@/features/temporadas/fechas'

type Props = {
  afiliacion: Afiliacion
  seleccionada: boolean
  onSeleccionar: () => void
}

/**
 * Fila de la bandeja de afiliaciones (F-16, RF-101). Es un botón completo para que se pueda elegir
 * con teclado y lectores de pantalla; el estado va con insignia, no solo con color.
 */
export function FilaAfiliacion({ afiliacion, seleccionada, onSeleccionar }: Props) {
  const estado = etiquetaEstadoAfiliacion[afiliacion.estado]

  return (
    <li>
      <button
        type="button"
        aria-current={seleccionada ? 'true' : undefined}
        onClick={onSeleccionar}
        className={[
          'flex w-full min-h-11 flex-col gap-2 rounded-md border p-3 text-left transition-colors',
          seleccionada
            ? 'border-primary bg-primary/6'
            : 'border-border bg-surface hover:bg-background',
        ].join(' ')}
      >
        <span className="flex w-full items-start justify-between gap-2">
          <span className="min-w-0 break-words text-label-md font-semibold text-text-main">
            {afiliacion.club.nombre}
          </span>
          <Insignia variante={estado.variante} icono={estado.icono}>
            {estado.texto}
          </Insignia>
        </span>
        <span className="font-mono text-mono-data font-semibold text-text-main">
          {afiliacion.folio}
        </span>
        <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body-sm text-text-muted">
          <span className="inline-flex items-center gap-1">
            <CalendarDays aria-hidden="true" className="size-3.5 shrink-0" />
            {afiliacion.temporada.nombre}
          </span>
          <span className="inline-flex items-center gap-1">
            <UserRound aria-hidden="true" className="size-3.5 shrink-0" />
            {nombrePerfil(afiliacion.capturadaPor)}
          </span>
        </span>
        <span className="text-body-sm text-text-muted">
          Capturada el {formatearFechaHora(afiliacion.capturadaEn)}
        </span>
      </button>
    </li>
  )
}
