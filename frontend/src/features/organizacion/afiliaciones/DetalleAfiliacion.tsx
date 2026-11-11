import { useId, useState } from 'react'
import {
  ArrowLeft,
  CheckCircle2,
  Clock,
  FileCheck2,
  Gavel,
  History,
  PencilLine,
  Shirt,
  XCircle,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import {
  etiquetaEstadoAfiliacion,
  nombrePerfil,
} from '@/features/organizacion/afiliaciones/etiquetas'
import type {
  Afiliacion,
  DecisionAfiliacion,
} from '@/features/organizacion/afiliaciones/tipos'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import { formatearFechaHora } from '@/features/temporadas/fechas'

type Props = {
  afiliacion: Afiliacion
  puedeCapturar: boolean
  puedeAprobar: boolean
  onEditar: () => void
  onResolver: (decision: DecisionAfiliacion) => void
  onVolver: () => void
}

type Vista = 'captura' | 'dictamen'

/**
 * Expediente del tarjetón (F-16). La Directiva ve la captura y puede corregirla mientras esté
 * SOLICITADA; la Presidencia ve el dictamen y aprueba o rechaza. Quien tiene ambos permisos cambia
 * entre las dos vistas sin perder el contexto. El estado y las fechas salen del backend.
 */
export function DetalleAfiliacion({
  afiliacion,
  puedeCapturar,
  puedeAprobar,
  onEditar,
  onResolver,
  onVolver,
}: Props) {
  const id = useId()
  const estado = etiquetaEstadoAfiliacion[afiliacion.estado]
  const resuelta = afiliacion.estado !== 'SOLICITADA'
  const temporadaCerrada = afiliacion.temporada.estado === 'CERRADA'
  const puedeEditar = puedeCapturar && !resuelta && !temporadaCerrada
  const puedeDictaminar = puedeAprobar && !resuelta && !temporadaCerrada
  const conPestanas = puedeCapturar && puedeAprobar && !resuelta
  const [vista, setVista] = useState<Vista>(puedeAprobar ? 'dictamen' : 'captura')

  return (
    <article
      aria-labelledby={`${id}-club`}
      className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-surface"
    >
      <button
        type="button"
        onClick={onVolver}
        className="flex min-h-11 items-center gap-2 border-b border-border px-4 text-label-md text-primary hover:bg-background lg:hidden"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Volver a la bandeja
      </button>

      <header className="flex flex-col gap-4 border-b border-border p-4 sm:p-6">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2
                id={`${id}-club`}
                className="min-w-0 break-words text-headline-lg text-text-main"
              >
                {afiliacion.club.nombre}
              </h2>
              <Insignia variante={estado.variante} icono={estado.icono}>
                {estado.texto}
              </Insignia>
            </div>
            <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-body-md text-text-muted">
              <span>{afiliacion.temporada.nombre}</span>
              <span aria-hidden="true">·</span>
              <span>{etiquetaEstadoTemporada[afiliacion.temporada.estado].texto}</span>
            </p>
            <p className="mt-1 text-body-sm text-text-muted">
              Capturado por {nombrePerfil(afiliacion.capturadaPor)} el{' '}
              {formatearFechaHora(afiliacion.capturadaEn)}
            </p>
          </div>

          <div className="flex shrink-0 flex-col gap-1 rounded-md bg-background px-4 py-3">
            <span className="text-label-sm uppercase tracking-wider text-text-muted">
              Folio del tarjetón
            </span>
            <span className="break-all font-mono text-headline-md font-bold text-primary">
              {afiliacion.folio}
            </span>
          </div>
        </div>
      </header>

      <div className="grid gap-0 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="flex min-w-0 flex-col divide-y divide-border">
          <section className="p-4 sm:p-6" aria-labelledby={`${id}-datos`}>
            <h3 id={`${id}-datos`} className="flex items-center gap-2 text-headline-sm text-text-main">
              <FileCheck2 aria-hidden="true" className="size-5 text-primary" />
              Datos del tarjetón
            </h3>
            <dl className="mt-3 grid gap-x-6 gap-y-3 text-body-md sm:grid-cols-[auto_minmax(0,1fr)]">
              <dt className="text-label-md text-text-muted">Club</dt>
              <dd className="break-words text-text-main">{afiliacion.club.nombre}</dd>
              <dt className="text-label-md text-text-muted">Temporada</dt>
              <dd className="text-text-main">
                {afiliacion.temporada.nombre} ·{' '}
                {etiquetaEstadoTemporada[afiliacion.temporada.estado].texto}
              </dd>
              <dt className="text-label-md text-text-muted">Estado</dt>
              <dd className="text-text-main">{estado.texto}</dd>
            </dl>
          </section>

          <section className="p-4 sm:p-6" aria-labelledby={`${id}-colores`}>
            <h3 id={`${id}-colores`} className="flex items-center gap-2 text-headline-sm text-text-main">
              <Shirt aria-hidden="true" className="size-5 text-primary" />
              Colores del uniforme
            </h3>
            <p className="mt-3 whitespace-pre-line break-words text-body-md text-text-main">
              {afiliacion.coloresUniformes}
            </p>
          </section>

          <section className="p-4 sm:p-6" aria-labelledby={`${id}-categorias`}>
            <h3
              id={`${id}-categorias`}
              className="flex items-center gap-2 text-headline-sm text-text-main"
            >
              <FileCheck2 aria-hidden="true" className="size-5 text-primary" />
              Categorías inscritas
            </h3>
            {afiliacion.categorias.length === 0 ? (
              <p className="mt-3 text-body-md text-text-muted">Sin categorías registradas.</p>
            ) : (
              <ul className="mt-3 flex flex-wrap gap-2">
                {afiliacion.categorias.map((categoria) => (
                  <li
                    key={categoria.id}
                    className="rounded-md bg-background px-2.5 py-1 text-body-sm text-text-main"
                  >
                    {categoria.nombre}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="p-4 sm:p-6" aria-labelledby={`${id}-trazabilidad`}>
            <h3
              id={`${id}-trazabilidad`}
              className="flex items-center gap-2 text-headline-sm text-text-main"
            >
              <History aria-hidden="true" className="size-5 text-primary" />
              Trazabilidad
            </h3>
            <ol className="mt-3 grid gap-3 sm:grid-cols-2">
              <li className="flex items-start gap-3 rounded-md bg-background p-3">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-success/12 text-success-strong">
                  <CheckCircle2 aria-hidden="true" className="size-4" />
                </span>
                <div className="min-w-0">
                  <p className="text-label-md font-semibold text-text-main">Solicitada</p>
                  <p className="text-body-sm text-text-muted">
                    {nombrePerfil(afiliacion.capturadaPor)} ·{' '}
                    {formatearFechaHora(afiliacion.capturadaEn)}
                  </p>
                </div>
              </li>
              <li
                className={[
                  'flex items-start gap-3 rounded-md p-3',
                  resuelta ? 'bg-background' : 'bg-warning/14',
                ].join(' ')}
              >
                <span
                  className={[
                    'flex size-8 shrink-0 items-center justify-center rounded-full',
                    resuelta
                      ? afiliacion.estado === 'APROBADA'
                        ? 'bg-success/12 text-success-strong'
                        : 'bg-error/12 text-error-strong'
                      : 'bg-warning/14 text-warning-strong',
                  ].join(' ')}
                >
                  {resuelta ? (
                    afiliacion.estado === 'APROBADA' ? (
                      <CheckCircle2 aria-hidden="true" className="size-4" />
                    ) : (
                      <XCircle aria-hidden="true" className="size-4" />
                    )
                  ) : (
                    <Clock aria-hidden="true" className="size-4" />
                  )}
                </span>
                <div className="min-w-0">
                  <p className="text-label-md font-semibold text-text-main">
                    {resuelta ? estado.texto : 'Pendiente de dictamen'}
                  </p>
                  <p className="text-body-sm text-text-muted">
                    {resuelta && afiliacion.resueltaPor
                      ? `${nombrePerfil(afiliacion.resueltaPor)} · ${formatearFechaHora(
                          afiliacion.resueltaEn ?? afiliacion.capturadaEn,
                        )}`
                      : 'La Presidencia revisa el tarjetón.'}
                  </p>
                </div>
              </li>
            </ol>
          </section>
        </div>

        <aside className="flex min-w-0 flex-col gap-4 border-t border-border p-4 sm:p-6 lg:border-l lg:border-t-0">
          {conPestanas && (
            <div
              role="tablist"
              aria-label="Vistas del tarjetón"
              className="flex gap-1 rounded-md bg-background p-1"
            >
              {(
                [
                  { valor: 'captura', texto: 'Captura', icono: PencilLine },
                  { valor: 'dictamen', texto: 'Dictamen', icono: Gavel },
                ] as const
              ).map(({ valor, texto, icono: Icono }) => (
                <button
                  key={valor}
                  type="button"
                  role="tab"
                  aria-selected={vista === valor}
                  onClick={() => setVista(valor)}
                  className={[
                    'flex min-h-11 flex-1 items-center justify-center gap-2 rounded-md px-3 text-label-md transition-colors',
                    vista === valor
                      ? 'bg-surface text-text-main shadow-sm'
                      : 'text-text-muted hover:text-text-main',
                  ].join(' ')}
                >
                  <Icono aria-hidden="true" className="size-4" />
                  {texto}
                </button>
              ))}
            </div>
          )}

          {resuelta ? (
            <section aria-labelledby={`${id}-resolucion`} className="flex flex-col gap-3">
              <h3
                id={`${id}-resolucion`}
                className="flex items-center gap-2 text-headline-sm text-text-main"
              >
                <Gavel aria-hidden="true" className="size-5 text-primary" />
                Resolución
              </h3>
              <Insignia variante={estado.variante} icono={estado.icono}>
                {estado.texto}
              </Insignia>
              <dl className="grid gap-x-3 gap-y-2 text-body-sm sm:grid-cols-[auto_minmax(0,1fr)]">
                <dt className="text-text-muted">Resolvió</dt>
                <dd className="min-w-0 break-words text-text-main">
                  {nombrePerfil(afiliacion.resueltaPor)}
                </dd>
                <dt className="text-text-muted">Fecha</dt>
                <dd className="text-text-main">
                  {afiliacion.resueltaEn
                    ? formatearFechaHora(afiliacion.resueltaEn)
                    : 'Sin fecha registrada'}
                </dd>
              </dl>
              {afiliacion.observaciones && (
                <div className="rounded-md bg-background p-3">
                  <p className="text-label-md text-text-muted">
                    {afiliacion.estado === 'RECHAZADA' ? 'Motivo del rechazo' : 'Nota de aprobación'}
                  </p>
                  <p className="mt-1 whitespace-pre-line break-words text-body-md text-text-main">
                    {afiliacion.observaciones}
                  </p>
                </div>
              )}
              <p className="text-body-sm text-text-muted">
                El tarjetón ya no admite cambios. Su historial queda en el expediente del club.
              </p>
            </section>
          ) : vista === 'dictamen' && puedeAprobar ? (
            <section aria-labelledby={`${id}-dictamen`} className="flex flex-col gap-3">
              <h3
                id={`${id}-dictamen`}
                className="flex items-center gap-2 text-headline-sm text-text-main"
              >
                <Gavel aria-hidden="true" className="size-5 text-primary" />
                Dictamen de la Presidencia
              </h3>
              <p className="text-body-md text-text-muted">
                Revisa los colores y las categorías. Aprobar afilia al club en la temporada;
                rechazar exige explicar qué debe corregir la Directiva.
              </p>
              {puedeDictaminar ? (
                <div className="flex flex-col gap-2">
                  <Button icono={CheckCircle2} onClick={() => onResolver('APROBADA')} className="w-full">
                    Aprobar afiliación
                  </Button>
                  <Button
                    variante="destructivo"
                    icono={XCircle}
                    onClick={() => onResolver('RECHAZADA')}
                    className="w-full"
                  >
                    Rechazar afiliación
                  </Button>
                </div>
              ) : (
                <p role="alert" className="rounded-md bg-warning/14 p-3 text-body-sm text-warning-strong">
                  La temporada está cerrada: el tarjetón ya no admite dictamen.
                </p>
              )}
            </section>
          ) : (
            <section aria-labelledby={`${id}-captura`} className="flex flex-col gap-3">
              <h3
                id={`${id}-captura`}
                className="flex items-center gap-2 text-headline-sm text-text-main"
              >
                <PencilLine aria-hidden="true" className="size-5 text-primary" />
                Captura del tarjetón
              </h3>
              <p className="text-body-md text-text-muted">
                {puedeEditar
                  ? 'Puedes corregir los colores y las categorías mientras la Presidencia no resuelva.'
                  : 'El tarjetón ya no admite cambios en este estado.'}
              </p>
              {puedeEditar && (
                <Button icono={PencilLine} onClick={onEditar} className="w-full">
                  Editar tarjetón
                </Button>
              )}
              {!puedeEditar && temporadaCerrada && (
                <p role="alert" className="rounded-md bg-warning/14 p-3 text-body-sm text-warning-strong">
                  La temporada está cerrada: el tarjetón es de solo lectura.
                </p>
              )}
            </section>
          )}
        </aside>
      </div>
    </article>
  )
}
