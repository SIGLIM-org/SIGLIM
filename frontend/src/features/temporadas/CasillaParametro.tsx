import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'

type Props = {
  id: string
  etiqueta: string
  marcada: boolean
  onCambiar: (marcada: boolean) => void
  /** Rotula el campo «No oficial» mientras el valor sea [DEMO] (D4). */
  noOficial?: boolean
  /** Se anuncia como interruptor (`role="switch"`): activa o desactiva una regla (RF-111). */
  interruptor?: boolean
  /** Texto de ayuda bajo la casilla, asociado con `aria-describedby`. */
  ayuda?: string
  error?: string
}

/** Casilla de un parámetro deportivo booleano (F-11, RF-006), con área táctil de 44 px. */
export function CasillaParametro({
  id,
  etiqueta,
  marcada,
  onCambiar,
  noOficial = false,
  interruptor = false,
  ayuda,
  error,
}: Props) {
  const idError = error ? `${id}-error` : undefined
  const idAyuda = ayuda ? `${id}-ayuda` : undefined
  const descripcion = [idAyuda, idError].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex min-w-0 flex-col gap-1">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <label htmlFor={id} className="flex min-h-11 cursor-pointer items-center gap-3 text-label-md text-text-main">
          <input
            id={id}
            type="checkbox"
            role={interruptor ? 'switch' : undefined}
            checked={marcada}
            aria-invalid={error ? true : undefined}
            aria-describedby={descripcion}
            onChange={(evento) => onCambiar(evento.target.checked)}
            className="size-[18px] shrink-0 accent-primary"
          />
          {etiqueta}
        </label>
        {noOficial && <InsigniaNoOficial />}
      </div>
      {ayuda && (
        <p id={idAyuda} className="text-body-sm text-text-muted">
          {ayuda}
        </p>
      )}
      {error && (
        <p id={idError} role="alert" className="text-body-sm text-error-strong">
          {error}
        </p>
      )}
    </div>
  )
}
