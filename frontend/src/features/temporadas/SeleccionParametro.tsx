import type { ReactNode } from 'react'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'

type Props = {
  id: string
  etiqueta: string
  valor: string
  onCambiar: (valor: string) => void
  /** Rotula el campo «No oficial» mientras el valor sea [DEMO] (D4). */
  noOficial?: boolean
  error?: string
  children: ReactNode
}

/** Lista de opciones de un parámetro deportivo con la insignia «No oficial» (F-11, RF-006). */
export function SeleccionParametro({
  id,
  etiqueta,
  valor,
  onCambiar,
  noOficial = false,
  error,
  children,
}: Props) {
  const idError = error ? `${id}-error` : undefined

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <div className="flex min-h-6 flex-wrap items-center gap-x-2 gap-y-1">
        <label htmlFor={id} className="text-label-md text-text-main">
          {etiqueta}
        </label>
        {noOficial && <InsigniaNoOficial />}
      </div>
      <select
        id={id}
        value={valor}
        aria-invalid={error ? true : undefined}
        aria-describedby={idError}
        onChange={(evento) => onCambiar(evento.target.value)}
        className={[
          'min-h-11 w-full rounded-md border bg-surface px-3 text-body-md text-text-main',
          error ? 'border-error' : 'border-border',
        ].join(' ')}
      >
        {children}
      </select>
      {error && (
        <p id={idError} role="alert" className="text-body-sm text-error-strong">
          {error}
        </p>
      )}
    </div>
  )
}
