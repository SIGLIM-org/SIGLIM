import type { ReactNode } from 'react'

type Props = {
  id: string
  etiqueta: string
  /** Explica el efecto de marcar la casilla. */
  descripcion?: ReactNode
  checked: boolean
  onChange: (marcada: boolean) => void
  disabled?: boolean
  error?: string
}

/**
 * Casilla de verificación del sistema de diseño (DESIGN.md, Formularios): etiqueta
 * asociada, texto de apoyo y error enlazados, y foco visible (RNF-008).
 */
export function CampoCasilla({
  id,
  etiqueta,
  descripcion,
  checked,
  onChange,
  disabled = false,
  error,
}: Props) {
  const idDescripcion = descripcion ? `${id}-descripcion` : undefined
  const idError = error ? `${id}-error` : undefined
  const descrito = [idDescripcion, idError].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-start gap-2.5">
        <input
          id={id}
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(evento) => onChange(evento.target.checked)}
          aria-invalid={error ? true : undefined}
          aria-describedby={descrito}
          className="mt-0.5 size-[18px] shrink-0 rounded-sm border border-border accent-primary"
        />
        <div className="min-w-0">
          <label htmlFor={id} className="text-label-md text-text-main">
            {etiqueta}
          </label>
          {descripcion && (
            <p id={idDescripcion} className="mt-0.5 text-body-sm text-text-muted">
              {descripcion}
            </p>
          )}
        </div>
      </div>
      {error && (
        <p id={idError} role="alert" className="text-body-sm text-error-strong">
          {error}
        </p>
      )}
    </div>
  )
}
