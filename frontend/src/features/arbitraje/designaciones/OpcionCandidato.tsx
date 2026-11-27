import { useId } from 'react'
import { Ban, TriangleAlert } from 'lucide-react'
import { nombreArbitro, textoCarga } from '@/features/arbitraje/designaciones/etiquetas'
import type { CandidatoArbitral } from '@/features/arbitraje/designaciones/tipos'

type OpcionProps = {
  candidato: CandidatoArbitral
  grupo: string
  elegido: boolean
  onElegir: () => void
}

/**
 * Un árbitro del grupo de radios. El no elegible queda deshabilitado con sus causas enlazadas por
 * `aria-describedby`, en tono neutro y de peligro, nunca ámbar: el ámbar es solo de la advertencia
 * RF-022, que sí se puede elegir.
 */
export function OpcionCandidato({ candidato, grupo, elegido, onElegir }: OpcionProps) {
  const id = useId()
  const { arbitro, elegible, causas, advertencias, carga } = candidato
  const descritoPor = [
    `${id}-datos`,
    causas.length > 0 ? `${id}-causas` : null,
    advertencias.length > 0 ? `${id}-advertencias` : null,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <li
      className={[
        'relative flex gap-3 rounded-md border p-3 transition-colors',
        elegible
          ? 'border-border bg-surface hover:bg-background has-checked:border-primary has-checked:bg-primary/5'
          : 'border-border bg-background',
      ].join(' ')}
    >
      <input
        id={`${id}-radio`}
        type="radio"
        name={grupo}
        value={arbitro.id}
        checked={elegido}
        disabled={!elegible}
        aria-describedby={descritoPor}
        onChange={onElegir}
        className="mt-0.5 size-[18px] shrink-0 accent-primary disabled:cursor-not-allowed"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <label
          htmlFor={`${id}-radio`}
          className={[
            'break-words text-label-md',
            elegible
              ? 'cursor-pointer text-text-main after:absolute after:inset-0 after:content-[""]'
              : 'cursor-not-allowed text-text-muted',
          ].join(' ')}
        >
          {nombreArbitro({ arbitro })}
        </label>
        <p id={`${id}-datos`} className="text-body-sm text-text-muted">
          {arbitro.club.nombre} · <span className="tabular-nums">{textoCarga(carga)}</span>
        </p>
        {causas.length > 0 && (
          <ul id={`${id}-causas`} className="flex flex-col gap-0.5">
            {causas.map((causa) => (
              <li
                key={causa.codigo}
                className="flex items-start gap-1.5 text-body-sm text-error-strong"
              >
                <Ban aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
                {causa.mensaje}
              </li>
            ))}
          </ul>
        )}
        {advertencias.length > 0 && (
          <ul
            id={`${id}-advertencias`}
            className="flex flex-col gap-0.5 rounded-sm bg-warning/14 px-2 py-1"
          >
            {advertencias.map((advertencia) => (
              <li
                key={advertencia.codigo}
                className="flex items-start gap-1.5 text-body-sm text-warning-strong"
              >
                <TriangleAlert aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
                {advertencia.mensaje}
              </li>
            ))}
          </ul>
        )}
      </div>
    </li>
  )
}
