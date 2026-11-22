import { Baby, CalendarDays, Shield } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import type { RegistroJugadorFila } from '@/features/registros/tipos'
import { formatearFecha } from '@/features/temporadas/fechas'

type Props = {
  registro: RegistroJugadorFila
  seleccionada: boolean
  onSeleccionar: () => void
}

/**
 * Fila de la bandeja de validación (F-19, DEC-111): nombre, CURP enmascarada (DEC-110), club y
 * equipo, categoría y temporada, estado, «Menor de edad» y fecha de alta. Es un botón completo para
 * elegirla con teclado; el estado va con insignia, no solo con color.
 */
export function FilaRegistro({ registro, seleccionada, onSeleccionar }: Props) {
  const estado = etiquetaEstadoRegistro[registro.estado]

  return (
    <li>
      <button
        type="button"
        aria-current={seleccionada ? 'true' : undefined}
        onClick={onSeleccionar}
        className={[
          'flex min-h-11 w-full flex-col gap-1.5 rounded-md border p-3 text-left transition-colors',
          seleccionada ? 'border-primary bg-primary/6' : 'border-border bg-surface hover:bg-background',
        ].join(' ')}
      >
        <span className="break-words text-label-md font-semibold text-text-main">
          {registro.persona.nombreCompleto}
        </span>
        <span className="font-mono text-mono-data text-text-main">
          <span className="sr-only">CURP </span>
          {registro.persona.curpEnmascarada}
        </span>
        <span className="flex flex-wrap items-center gap-1.5">
          <Insignia variante={estado.variante} icono={estado.icono}>
            {estado.texto}
          </Insignia>
          {registro.esMenor && (
            <Insignia variante="informacion" icono={Baby}>
              Menor de edad
            </Insignia>
          )}
        </span>
        <span className="flex items-center gap-1 break-words text-body-sm text-text-muted">
          <Shield aria-hidden="true" className="size-3.5 shrink-0" />
          {registro.club.nombre} · {registro.equipo.nombre}
        </span>
        <span className="text-body-sm text-text-muted">
          {registro.categoria.nombre} · {registro.temporada.nombre}
        </span>
        <span className="flex items-center gap-1 text-body-sm text-text-muted">
          <CalendarDays aria-hidden="true" className="size-3.5 shrink-0" />
          Alta el {formatearFecha(registro.altaEn)}
        </span>
      </button>
    </li>
  )
}
