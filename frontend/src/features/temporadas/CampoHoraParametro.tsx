import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'

type Props = {
  id: string
  etiqueta: string
  /** `HH:mm`, el formato de `<input type="time">`. */
  valor: string
  onCambiar: (valor: string) => void
  /** Rotula el campo «No oficial» mientras el valor sea [DEMO] (DEC-176). */
  noOficial?: boolean
  ayuda?: string
  error?: string
}

/**
 * Hora de un parámetro deportivo (F-11, H-32.3, DEC-176, DEC-194). Misma estructura que
 * `CampoParametro`: la insignia «No oficial» va junto a la etiqueta y fuera de ella, para que el
 * nombre accesible sea solo el del campo (RNF-008). Se captura en horas y minutos (DEC-195).
 */
export function CampoHoraParametro({
  id,
  etiqueta,
  valor,
  onCambiar,
  noOficial = false,
  ayuda,
  error,
}: Props) {
  const idAyuda = ayuda && !error ? `${id}-ayuda` : undefined
  const idError = error ? `${id}-error` : undefined
  const descrito = [idAyuda, idError].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <div className="flex min-h-6 flex-wrap items-center gap-x-2 gap-y-1">
        <label htmlFor={id} className="text-label-md text-text-main">
          {etiqueta}
        </label>
        {noOficial && <InsigniaNoOficial />}
      </div>
      <input
        id={id}
        type="time"
        step={60}
        required
        value={valor}
        aria-invalid={error ? true : undefined}
        aria-describedby={descrito}
        onChange={(evento) => onCambiar(evento.target.value)}
        className={[
          'min-h-11 w-full rounded-md border bg-surface px-3 font-mono text-mono-data text-text-main tabular-nums',
          error ? 'border-error' : 'border-border',
        ].join(' ')}
      />
      {error ? (
        <p id={idError} role="alert" className="text-body-sm text-error-strong">
          {error}
        </p>
      ) : (
        ayuda && (
          <p id={idAyuda} className="text-body-sm text-text-muted">
            {ayuda}
          </p>
        )
      )}
    </div>
  )
}
