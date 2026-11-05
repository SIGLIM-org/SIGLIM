import type { InputHTMLAttributes } from 'react'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'

type Props = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'id' | 'className' | 'value' | 'onChange' | 'type'
> & {
  id: string
  etiqueta: string
  valor: string
  onCambiar: (valor: string) => void
  /** Rotula el campo «No oficial» mientras el valor sea [DEMO] (D4). */
  noOficial?: boolean
  ayuda?: string
  error?: string
}

/**
 * Campo entero de un parámetro deportivo (F-11, RF-006). Como `CampoTexto`, pero con la insignia
 * «No oficial» junto a la etiqueta y fuera de ella, para que el nombre accesible sea solo el del
 * campo (AGENTS.md §3, RNF-008).
 */
export function CampoParametro({
  id,
  etiqueta,
  valor,
  onCambiar,
  noOficial = false,
  ayuda,
  error,
  ...resto
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
        type="number"
        inputMode="numeric"
        step={1}
        required
        value={valor}
        aria-invalid={error ? true : undefined}
        aria-describedby={descrito}
        onChange={(evento) => onCambiar(evento.target.value)}
        className={[
          'min-h-11 w-full rounded-md border bg-surface px-3 font-mono text-mono-data text-text-main tabular-nums placeholder:text-text-muted/70',
          error ? 'border-error' : 'border-border',
        ].join(' ')}
        {...resto}
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
