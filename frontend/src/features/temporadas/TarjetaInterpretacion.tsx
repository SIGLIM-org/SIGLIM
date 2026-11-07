import { CircleCheck, CircleX, Info } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { nombreRegistrador, nombreResolutor } from '@/features/temporadas/actores'
import { etiquetaEstadoInterpretacion } from '@/features/temporadas/etiquetas'
import { formatearFechaHora } from '@/features/temporadas/fechas'
import type { InterpretacionRegla } from '@/features/temporadas/tipos'
import type { Yo } from '@/features/seguridad/tipos'

type Props = {
  interpretacion: InterpretacionRegla
  yo: Yo
  /** `temporadas.interpretacion:APROBAR`; el estado y la autoría aún deciden si se ofrece. */
  puedeResolver: boolean
  onAutorizar: () => void
  onRechazar: () => void
}

/**
 * Ficha de una interpretación de regla (F-11, B-03.2, RF-007). El estado va con insignia, ícono y
 * texto, no solo con color (RNF-008). Las acciones de resolución solo se ofrecen al perfil con el
 * permiso cuando sigue pendiente y no es quien la registró (DEC-57).
 */
export function TarjetaInterpretacion({
  interpretacion,
  yo,
  puedeResolver,
  onAutorizar,
  onRechazar,
}: Props) {
  const etiqueta = etiquetaEstadoInterpretacion[interpretacion.estado]
  const pendiente = interpretacion.estado === 'PENDIENTE'
  const propia = interpretacion.registradaPor === yo.perfil.id
  const resueltaEn = interpretacion.resueltaEn
  const registrada = formatearFechaHora(interpretacion.registradaEn)
  const titulo = `Interpretación registrada el ${registrada}`

  return (
    <article
      aria-labelledby={`titulo-${interpretacion.id}`}
      className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 id={`titulo-${interpretacion.id}`} className="sr-only">
            {titulo}
          </h3>
          <p className="whitespace-pre-line break-words text-body-md text-text-main">
            {interpretacion.descripcion}
          </p>
        </div>
        <Insignia variante={etiqueta.variante} icono={etiqueta.icono}>
          {etiqueta.texto}
        </Insignia>
      </div>

      <dl className="flex flex-col gap-1 text-body-sm text-text-muted">
        <div className="flex flex-wrap gap-x-1.5">
          <dt>Registrada el</dt>
          <dd>
            <time dateTime={interpretacion.registradaEn}>{registrada}</time> por{' '}
            {nombreRegistrador(interpretacion.registradaPor, yo)}
          </dd>
        </div>
        {resueltaEn && (
          <div className="flex flex-wrap gap-x-1.5">
            <dt>Resuelta el</dt>
            <dd>
              <time dateTime={resueltaEn}>{formatearFechaHora(resueltaEn)}</time> por{' '}
              {nombreResolutor(interpretacion.resueltaPor, yo)}
            </dd>
          </div>
        )}
      </dl>

      {interpretacion.observaciones && (
        <div
          className={[
            'rounded-md p-3',
            interpretacion.estado === 'RECHAZADA' ? 'bg-error/8' : 'bg-background',
          ].join(' ')}
        >
          <p className="text-label-sm text-text-muted">
            {interpretacion.estado === 'RECHAZADA'
              ? 'Observaciones del rechazo'
              : 'Nota de autorización'}
          </p>
          <p className="mt-1 whitespace-pre-line break-words text-body-md text-text-main">
            {interpretacion.observaciones}
          </p>
        </div>
      )}

      {pendiente && puedeResolver && propia && (
        <p className="flex items-start gap-2 rounded-md border border-border bg-background p-3 text-body-sm text-text-main">
          <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-info-strong" />
          Registrada por ti: la debe resolver otra persona (DEC-57).
        </p>
      )}

      {pendiente && puedeResolver && !propia && (
        <div className="flex flex-col-reverse gap-2 border-t border-border pt-3 sm:flex-row sm:justify-end">
          <Button
            variante="secundario"
            icono={CircleX}
            aria-label={`Rechazar la interpretación registrada el ${registrada}`}
            onClick={onRechazar}
          >
            Rechazar
          </Button>
          <Button
            icono={CircleCheck}
            aria-label={`Autorizar la interpretación registrada el ${registrada}`}
            onClick={onAutorizar}
          >
            Autorizar
          </Button>
        </div>
      )}

      {pendiente && !puedeResolver && (
        <p className="text-body-sm text-text-muted">
          Pendiente de la autorización de la Presidencia.
        </p>
      )}
    </article>
  )
}
