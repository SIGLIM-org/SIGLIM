import { useMemo, type CSSProperties } from 'react'
import { Ban, CalendarCheck, ChevronLeft, ChevronRight, TriangleAlert, Wrench, type LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { organizarSemana, type DiaSemana, type SegmentoDia } from '@/features/canchas/calendario'
import { estadoPartido, etiquetaBloque } from '@/features/canchas/etiquetas'
import {
  formatearDiaColumna,
  formatearDiaLargo,
  formatearHorario,
  formatearRangoSemana,
  rangoSemana,
  semanaActual,
  semanaAnterior,
  semanaSiguiente,
} from '@/features/canchas/fechas'
import type { BloqueOcupacion, TipoBloqueOcupacion } from '@/features/canchas/tipos'

type Props = {
  /** Lunes de la semana visible (`AAAA-MM-DD`). */
  semana: string
  bloques?: readonly BloqueOcupacion[]
  cargando?: boolean
  error?: unknown
  onReintentar?: () => void
  onCambiarSemana: (lunes: string) => void
}

/* Un partido de 2 h debe mostrar horario, equipos, estado y el aviso de bloqueo. */
const PX_POR_HORA = 72

/* Rayado de D4 sobre un fondo opaco, para tapar la disponibilidad que coincida. */
function rayado(color: string): CSSProperties {
  return {
    backgroundColor: 'var(--color-surface)',
    backgroundImage: `repeating-linear-gradient(135deg, color-mix(in oklab, var(${color}) 22%, transparent) 0 6px, transparent 6px 12px)`,
  }
}

const ESTILO_BLOQUEO = rayado('--color-error')
const ESTILO_MANTENIMIENTO = rayado('--color-warning')

const APARIENCIA: Record<
  Exclude<TipoBloqueOcupacion, 'PARTIDO'>,
  { icono: LucideIcon; clases: string; estilo?: CSSProperties; texto: string }
> = {
  DISPONIBLE: { icono: CalendarCheck, clases: 'bg-success/12', texto: 'text-success-strong' },
  BLOQUEO: { icono: Ban, clases: 'border border-error/60', estilo: ESTILO_BLOQUEO, texto: 'text-error-strong' },
  MANTENIMIENTO: { icono: Wrench, clases: 'border border-warning/70', estilo: ESTILO_MANTENIMIENTO, texto: 'text-warning-strong' },
}

function textoCompleto({ bloque }: SegmentoDia): string {
  if (bloque.partido) {
    const { equipoLocal, equipoVisitante, estado, periodo } = bloque.partido

    return `${formatearHorario(periodo)} ${equipoLocal.nombre} vs ${equipoVisitante.nombre} (${estadoPartido(estado).texto})`
  }

  const etiqueta = bloque.tipo === 'DISPONIBLE' ? etiquetaBloque.DISPONIBLE : etiquetaBloque[bloque.tipo]

  return `${etiqueta}${bloque.motivo ? `: ${bloque.motivo}` : ''} (${formatearHorario(bloque.periodo)})`
}

function ContenidoPartido({ segmento, compacto = false }: { segmento: SegmentoDia; compacto?: boolean }) {
  const partido = segmento.bloque.partido

  if (!partido) {
    return null
  }

  const estado = estadoPartido(partido.estado)

  return (
    <>
      <span className="font-mono text-mono-data text-text-main">{formatearHorario(partido.periodo)}</span>
      <span className={`text-label-md text-text-main ${compacto ? 'line-clamp-2' : ''}`}>
        {partido.equipoLocal.nombre} vs {partido.equipoVisitante.nombre}
      </span>
      <span>
        <Insignia variante={estado.variante}>{estado.texto}</Insignia>
      </span>
      {segmento.dentroDeBloqueo && (
        <span className="flex items-center gap-1 text-body-sm text-error-strong">
          <TriangleAlert aria-hidden="true" className="size-3.5 shrink-0" />
          Dentro de un bloqueo
        </span>
      )}
    </>
  )
}

function ContenidoRegistro({ bloque, compacto = false }: { bloque: BloqueOcupacion; compacto?: boolean }) {
  if (bloque.tipo === 'PARTIDO') {
    return null
  }

  const { icono: Icono, texto } = APARIENCIA[bloque.tipo]

  return (
    <>
      <span className={`flex items-center gap-1 ${compacto ? 'text-body-sm font-medium' : 'text-label-md'} ${texto}`}>
        <Icono aria-hidden="true" className="size-3.5 shrink-0" />
        {etiquetaBloque[bloque.tipo]}
      </span>
      {bloque.motivo && <span className="text-body-sm text-text-main">{bloque.motivo}</span>}
    </>
  )
}

/** Rejilla de 7 columnas (`lg`). Se desplaza dentro de su región, no en la página (RNF-007). */
function RejillaSemana({
  dias,
  horario,
}: {
  dias: DiaSemana[]
  horario: { inicio: number; fin: number }
}) {
  const alto = ((horario.fin - horario.inicio) / 60) * PX_POR_HORA
  const horas = Array.from(
    { length: (horario.fin - horario.inicio) / 60 },
    (_, indice) => horario.inicio / 60 + indice,
  )

  return (
    <div
      role="region"
      aria-label="Calendario de la semana"
      tabIndex={0}
      className="hidden overflow-x-auto rounded-lg border border-border lg:block"
    >
      <div className="grid min-w-[56rem] grid-cols-[3.5rem_repeat(7,minmax(0,1fr))] bg-surface">
        <div className="sticky left-0 z-20 border-b-2 border-border bg-background" />
        {dias.map(({ fecha }) => (
          <div
            key={fecha}
            className="border-b-2 border-l border-border bg-background px-2 py-2 text-label-sm text-text-muted"
          >
            {formatearDiaColumna(fecha)}
          </div>
        ))}

        <div className="sticky left-0 z-20 bg-surface" style={{ height: alto }}>
          {horas.map((hora) => (
            <div
              key={hora}
              style={{ height: PX_POR_HORA }}
              className="border-b border-border px-1.5 pt-0.5 text-right font-mono text-mono-data text-text-muted"
            >
              {String(hora).padStart(2, '0')}:00
            </div>
          ))}
        </div>

        {dias.map(({ fecha, segmentos }) => (
          <div key={fecha} className="relative border-l border-border" style={{ height: alto }}>
            {horas.map((hora) => (
              <div key={hora} style={{ height: PX_POR_HORA }} className="border-b border-border" />
            ))}
            {segmentos.map((segmento) => {
              const { bloque, capa, inicio, fin } = segmento
              const posicion: CSSProperties = {
                top: ((inicio - horario.inicio) / 60) * PX_POR_HORA,
                height: ((fin - inicio) / 60) * PX_POR_HORA,
                zIndex: capa + 1,
              }

              if (bloque.tipo === 'PARTIDO') {
                return (
                  <div
                    key={segmento.clave}
                    title={textoCompleto(segmento)}
                    style={posicion}
                    className="absolute inset-x-px flex flex-col items-start gap-1 overflow-hidden rounded-md border border-info/50 bg-surface p-1 shadow-overlay"
                  >
                    <ContenidoPartido segmento={segmento} compacto />
                  </div>
                )
              }

              const apariencia = APARIENCIA[bloque.tipo]

              return (
                <div
                  key={segmento.clave}
                  title={textoCompleto(segmento)}
                  style={{ ...posicion, ...apariencia.estilo }}
                  className={`absolute inset-x-0 flex flex-col gap-0.5 overflow-hidden p-1.5 ${apariencia.clases}`}
                >
                  <ContenidoRegistro bloque={bloque} compacto />
                </div>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}

/** Agenda por día (móvil y tableta): nada se recorta y cada bloque lleva su horario. */
function AgendaSemana({ dias }: { dias: DiaSemana[] }) {
  return (
    <ol className="flex flex-col gap-4 lg:hidden">
      {dias.map(({ fecha, segmentos }) => (
        <li key={fecha} className="flex flex-col gap-2">
          <h4 className="text-headline-sm text-text-main">{formatearDiaLargo(fecha)}</h4>
          {segmentos.length === 0 ? (
            <p className="text-body-md text-text-muted">Sin registros</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {segmentos.map((segmento) => {
                const { bloque } = segmento

                if (bloque.tipo === 'PARTIDO') {
                  return (
                    <li
                      key={segmento.clave}
                      className="flex flex-col items-start gap-1 rounded-md border border-info/50 bg-surface p-3"
                    >
                      <ContenidoPartido segmento={segmento} />
                    </li>
                  )
                }

                const apariencia = APARIENCIA[bloque.tipo]

                return (
                  <li
                    key={segmento.clave}
                    style={apariencia.estilo}
                    className={`flex flex-col gap-1 rounded-md p-3 ${apariencia.clases}`}
                  >
                    <ContenidoRegistro bloque={bloque} />
                    <span className="font-mono text-mono-data text-text-main">
                      {formatearHorario(bloque.periodo)}
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
        </li>
      ))}
    </ol>
  )
}

function Leyenda() {
  return (
    <div className="flex flex-col gap-2 text-body-sm text-text-muted">
      <ul className="flex flex-wrap gap-x-4 gap-y-1">
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className="size-3.5 rounded-sm bg-success/12 ring-1 ring-success/30" />
          Disponible (registrada)
        </li>
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" style={ESTILO_BLOQUEO} className="size-3.5 rounded-sm border border-error/60" />
          Bloqueo
        </li>
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" style={ESTILO_MANTENIMIENTO} className="size-3.5 rounded-sm border border-warning/70" />
          Mantenimiento
        </li>
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className="size-3.5 rounded-sm border border-info/50 bg-surface" />
          Partido
        </li>
      </ul>
      <p>
        Las horas sin disponibilidad registrada no están prohibidas: una cancha activa y sin bloqueo
        puede programarse.
      </p>
    </div>
  )
}

/**
 * Ocupación semanal de una cancha (H-07, criterio 2). `DISPONIBLE` es informativo (D3) y
 * `BLOQUEO`/`MANTENIMIENTO` lo tapan (D4); cada bloque dice su tipo con ícono y texto (RNF-008).
 */
export function CalendarioSemanal({
  semana,
  bloques,
  cargando = false,
  error,
  onReintentar,
  onCambiarSemana,
}: Props) {
  const organizada = useMemo(
    () => (bloques ? organizarSemana(bloques, rangoSemana(semana)) : null),
    [bloques, semana],
  )
  const hayBloques = organizada?.dias.some(({ segmentos }) => segmentos.length > 0) ?? false

  return (
    <section aria-labelledby="titulo-calendario" className="flex min-w-0 flex-col gap-4 lg:col-span-2">
      <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
        <h3 id="titulo-calendario" className="text-headline-sm text-text-main">
          {formatearRangoSemana(semana)}
        </h3>
        <div className="flex gap-2">
          <Button
            variante="secundario"
            icono={ChevronLeft}
            aria-label="Semana anterior"
            onClick={() => onCambiarSemana(semanaAnterior(semana))}
          />
          <Button variante="secundario" onClick={() => onCambiarSemana(semanaActual())}>
            Esta semana
          </Button>
          <Button
            variante="secundario"
            icono={ChevronRight}
            aria-label="Semana siguiente"
            onClick={() => onCambiarSemana(semanaSiguiente(semana))}
          />
        </div>
      </div>

      <Leyenda />

      {cargando && <EstadoCarga etiqueta="Cargando la ocupación de la semana…" filas={4} columnas={7} />}
      {!cargando && Boolean(error) && <ErrorConsulta error={error} onReintentar={onReintentar} />}
      {!cargando && !error && organizada && !hayBloques && (
        <EstadoVacio titulo="Sin registros esta semana" />
      )}
      {!cargando && !error && organizada && hayBloques && (
        <>
          <RejillaSemana dias={organizada.dias} horario={organizada.horario} />
          <AgendaSemana dias={organizada.dias} />
        </>
      )}
    </section>
  )
}
