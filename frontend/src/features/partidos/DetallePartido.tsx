import { useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  CalendarDays,
  Clock,
  History,
  LayoutList,
  MapPin,
  Trophy,
  type LucideIcon,
} from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { fechaEnZona, formatearDiaLargo, formatearHora } from '@/features/canchas/fechas'
import { usePartido } from '@/features/partidos/api'
import { BarraAccionesPartido } from '@/features/partidos/BarraAccionesPartido'
import { PestanaHistorialPartido } from '@/features/partidos/PestanaHistorialPartido'
import { PestanaResumenPartido } from '@/features/partidos/PestanaResumenPartido'
import {
  etiquetaEstadoJornada,
  etiquetaEstadoPartido,
  varianteEstadoPartido,
} from '@/features/programacion/etiquetas'
import { ConPermiso } from '@/features/seguridad/ConPermiso'

type Props = {
  partidoId: string
}

/** El resultado oficial tiene su pestaña en H-28.2. */
type Pestana = 'resumen' | 'historial'

const PESTANAS: readonly { valor: Pestana; texto: string; icono: LucideIcon }[] = [
  { valor: 'resumen', texto: 'Resumen', icono: LayoutList },
  { valor: 'historial', texto: 'Historial', icono: History },
]

/** Detalle del partido (F-25, UI-010, H-15.3): encabezado, acciones por estado y pestañas. */
export function DetallePartido({ partidoId }: Props) {
  const consulta = usePartido(partidoId)
  const [pestana, setPestana] = useState<Pestana>('resumen')

  const navegarPestanas = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowRight' && evento.key !== 'ArrowLeft' && evento.key !== 'Home' && evento.key !== 'End') {
      return
    }
    evento.preventDefault()
    const actual = PESTANAS.findIndex(({ valor }) => valor === pestana)
    const siguiente =
      evento.key === 'Home'
        ? 0
        : evento.key === 'End'
          ? PESTANAS.length - 1
          : (actual + (evento.key === 'ArrowRight' ? 1 : -1) + PESTANAS.length) % PESTANAS.length
    const valor = PESTANAS[siguiente].valor
    setPestana(valor)
    document.getElementById(`pestana-partido-${valor}`)?.focus()
  }

  // Se entra al detalle desde la programación de jornadas (F-22).
  const volver = (
    <ConPermiso permiso="programacion.jornada:EDITAR">
      <Link
        to="/app/programacion/jornadas"
        className="inline-flex w-fit items-center gap-1.5 rounded-sm text-label-md text-primary underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Programación de jornadas
      </Link>
    </ConPermiso>
  )

  if (consulta.isPending) {
    return (
      <div className="flex flex-col gap-4">
        {volver}
        <EstadoCarga etiqueta="Cargando el partido…" filas={4} columnas={2} />
      </div>
    )
  }

  if (consulta.isError) {
    return (
      <div className="flex flex-col gap-4">
        {volver}
        <ErrorConsulta error={consulta.error} onReintentar={() => void consulta.refetch()} />
      </div>
    )
  }

  const partido = consulta.data
  const datos: { icono: LucideIcon; termino: string; valor: string; detalle?: string }[] = [
    { icono: CalendarDays, termino: 'Fecha', valor: formatearDiaLargo(fechaEnZona(partido.inicio)) },
    {
      icono: Clock,
      termino: 'Horario y bloque de cancha',
      valor: `${formatearHora(partido.inicio)} – ${formatearHora(partido.finBloque)}`,
      detalle: 'Hora de Morelia',
    },
    { icono: MapPin, termino: 'Cancha', valor: partido.cancha.nombre },
    {
      icono: Trophy,
      termino: `Jornada ${partido.jornada.numero}`,
      valor: `${partido.categoria.nombre} · ${partido.grupo.nombre}`,
      detalle: `${partido.temporada.nombre} · ${etiquetaEstadoJornada[partido.jornada.estadoPublicacion]}`,
    },
  ]

  return (
    <div className="flex min-w-0 flex-col gap-4">
      {volver}
      <header className="flex min-w-0 flex-col gap-5 rounded-lg border border-border bg-surface p-4 sm:p-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <h1 className="min-w-0 text-balance break-words text-display-lg-mobile text-text-main sm:text-display-lg">
            {partido.equipoLocal.nombre}{' '}
            <span className="font-normal text-text-muted">vs.</span>{' '}
            {partido.equipoVisitante.nombre}
          </h1>
          <div className="shrink-0">
            <Insignia variante={varianteEstadoPartido[partido.estado]}>
              {etiquetaEstadoPartido[partido.estado]}
            </Insignia>
          </div>
        </div>
        <dl className="grid gap-4 border-t border-border pt-4 sm:grid-cols-2 lg:grid-cols-4">
          {datos.map(({ icono: Icono, termino, valor, detalle }) => (
            <div key={termino} className="flex min-w-0 items-start gap-3">
              <Icono aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-text-muted" />
              <div className="min-w-0">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">{termino}</dt>
                <dd className="break-words text-body-md font-medium text-text-main">{valor}</dd>
                {detalle && <dd className="text-body-sm text-text-muted">{detalle}</dd>}
              </div>
            </div>
          ))}
        </dl>
      </header>

      <BarraAccionesPartido partido={partido} />

      <div
        role="tablist"
        aria-label="Información del partido"
        onKeyDown={navegarPestanas}
        className="flex min-w-0 overflow-x-auto overflow-y-hidden border-b-2 border-border"
      >
        {PESTANAS.map(({ valor, texto, icono: Icono }) => {
          const activa = pestana === valor

          return (
            <button
              key={valor}
              type="button"
              role="tab"
              id={`pestana-partido-${valor}`}
              aria-selected={activa}
              aria-controls={`panel-partido-${valor}`}
              tabIndex={activa ? 0 : -1}
              onClick={() => setPestana(valor)}
              className={[
                '-mb-0.5 flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-label-md transition-colors sm:px-4',
                activa ? 'border-primary text-primary' : 'border-transparent text-text-muted hover:text-text-main',
              ].join(' ')}
            >
              <Icono aria-hidden="true" className="size-4 shrink-0" />
              {texto}
              {valor === 'historial' && (
                <span className="rounded-sm bg-border px-1.5 font-mono text-label-sm tabular-nums text-text-main">
                  {partido.historial.length}
                </span>
              )}
            </button>
          )
        })}
      </div>

      {PESTANAS.map(({ valor }) => (
        <div
          key={valor}
          role="tabpanel"
          id={`panel-partido-${valor}`}
          aria-labelledby={`pestana-partido-${valor}`}
          hidden={pestana !== valor}
          className="min-w-0"
        >
          {valor === 'resumen' ? (
            <PestanaResumenPartido partido={partido} />
          ) : (
            <PestanaHistorialPartido historial={partido.historial} />
          )}
        </div>
      ))}
    </div>
  )
}
