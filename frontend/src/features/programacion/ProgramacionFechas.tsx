import { useId, useMemo, useState, type ReactNode } from 'react'
import {
  CalendarDays,
  CircleAlert,
  CircleCheck,
  CornerUpLeft,
  Info,
  ListChecks,
  RefreshCw,
  Send,
  TriangleAlert,
  Wand2,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { BloqueFecha } from '@/features/programacion/BloqueFecha'
import { ModalEnvioFecha } from '@/features/programacion/ModalEnvioFecha'
import { ModalPartido, type EmparejamientoPendiente } from '@/features/programacion/ModalPartido'
import { TarjetaCalendario } from '@/features/programacion/calendario/TarjetaCalendario'
import { useAsignarFecha, useFecha, useVerificacionFecha } from '@/features/programacion/api'
import {
  etiquetaAdvertenciaFecha,
  etiquetaEstadoJornada,
  varianteEstadoJornada,
} from '@/features/programacion/etiquetas'
import { diasDeFecha } from '@/features/programacion/fechas'
import { usePermisosProgramacion } from '@/features/programacion/permisos'
import type {
  AsignacionFecha,
  AvisoFecha,
  FechaProgramacion,
  FilaPartidoFecha,
} from '@/features/programacion/tipos'
import { useCategoriasTemporada } from '@/features/temporadas/api'
import { formatearFechaHora } from '@/features/publicacion/fechas'
import type { Temporada } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  temporada: Temporada
  /** Fechas de la temporada por número; la pantalla solo se monta si hay al menos una. */
  fechas: readonly FechaProgramacion[]
  /** Selector «Por fecha / Por jornada» que comparte con la programación manual. */
  selectorVista: ReactNode
}

/** Lo que la pantalla tiene abierto en el modal del partido. */
type EdicionPartido =
  | { tipo: 'ajustar'; jornadaId: string; grupoId: string; partido: FilaPartidoFecha }
  | { tipo: 'asignar'; jornadaId: string; grupoId: string; emparejamiento: EmparejamientoPendiente }

function agruparPorPartido(avisos: readonly AvisoFecha[]): Map<string, AvisoFecha[]> {
  const porPartido = new Map<string, AvisoFecha[]>()

  for (const aviso of avisos) {
    if (!aviso.partidoId) {
      continue
    }
    porPartido.set(aviso.partidoId, [...(porPartido.get(aviso.partidoId) ?? []), aviso])
  }

  return porPartido
}

/** «2 sin árbitro · 1 localía»: resumen de las advertencias por tipo, sin códigos (DEC-63). */
function resumenAdvertencias(advertencias: readonly AvisoFecha[]): string {
  const porTipo = new Map<string, number>()

  for (const { codigo } of advertencias) {
    const etiqueta = etiquetaAdvertenciaFecha(codigo)
    porTipo.set(etiqueta, (porTipo.get(etiqueta) ?? 0) + 1)
  }

  return [...porTipo.entries()].map(([etiqueta, total]) => `${total} ${etiqueta.toLowerCase()}`).join(' · ')
}

function plural(cantidad: number, singular: string, varios: string): string {
  return `${cantidad} ${cantidad === 1 ? singular : varios}`
}

/** «3 asignados · 1 sin asignar · 3 árbitros centrales · 1 sin árbitro» de la última asignación. */
function resumenAsignacion({ totales }: AsignacionFecha): string {
  return [
    plural(totales.asignados, 'partido asignado', 'partidos asignados'),
    totales.cancelados > 0 ? plural(totales.cancelados, 'cancelado', 'cancelados') : null,
    totales.noAsignados > 0 ? `${totales.noAsignados} sin asignar` : null,
    totales.centrales > 0 ? plural(totales.centrales, 'árbitro central designado', 'árbitros centrales designados') : null,
    totales.sinArbitro > 0 ? `${totales.sinArbitro} sin árbitro` : null,
  ]
    .filter(Boolean)
    .join(' · ')
}

/** La primera fecha que sigue en borrador o, si todas avanzaron, la última. */
function fechaInicial(fechas: readonly FechaProgramacion[]): string | undefined {
  return (fechas.find(({ estadoPublicacion }) => estadoPublicacion === 'BORRADOR') ?? fechas.at(-1))?.id
}

/**
 * Vista por fecha de F-22 (H-35.2, UI-009, DEC-175) con el formato acordado con el profesor: un
 * bloque por «Categoría – Grupo» con Local vs. Visitante, Cancha, Horario y Árbitro, más los
 * descansos y los partidos sin asignar con su causa.
 *
 * El Programador asigna lo pendiente o regenera la fecha con motivo (H-33, DEC-181), ajusta cancha
 * u horario con motivo (RF-040), asigna a mano un emparejamiento y envía la fecha completa a
 * aprobación (RF-121): el envío se deshabilita mientras la verificación encuentre conflictos duros.
 */
export function ProgramacionFechas({ temporada, fechas, selectorVista }: Props) {
  const permisos = usePermisosProgramacion()
  const id = useId()
  const [fechaElegida, setFechaElegida] = useState<string | undefined>(() => fechaInicial(fechas))
  const [edicion, setEdicion] = useState<EdicionPartido | null>(null)
  const [regenerando, setRegenerando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  /** Resultado de la última asignación, con sus totales, causas sin árbitro y advertencias. */
  const [ultimaAsignacion, setUltimaAsignacion] = useState<AsignacionFecha | null>(null)
  const categorias = useCategoriasTemporada(temporada.id, { habilitada: permisos.editar })

  const fechaId = fechas.some(({ id: fecha }) => fecha === fechaElegida)
    ? fechaElegida
    : fechaInicial(fechas)
  const detalle = useFecha(fechaId)
  const fecha = detalle.data
  const enBorrador = fecha?.estadoPublicacion === 'BORRADOR'
  const verificacion = useVerificacionFecha(fecha?.id, { habilitada: Boolean(fecha) && enBorrador })
  const asignar = useAsignarFecha()

  const conflictos = useMemo(() => verificacion.data?.conflictos ?? [], [verificacion.data])
  const advertencias = verificacion.data?.advertencias ?? []
  const conflictosPorPartido = useMemo(() => agruparPorPartido(conflictos), [conflictos])
  const causasSinArbitro = useMemo(
    () =>
      new Map(
        ultimaAsignacion && ultimaAsignacion.fecha.id === fechaId
          ? ultimaAsignacion.sinArbitro.map(({ partidoId, causa }) => [partidoId, causa] as const)
          : [],
      ),
    [ultimaAsignacion, fechaId],
  )

  const puedeEditar = permisos.editar && enBorrador
  const verificacionLista = verificacion.isSuccess && !verificacion.isFetching
  const puedeEnviar =
    permisos.enviar && enBorrador && (fecha?.partidos ?? 0) > 0 && verificacionLista && conflictos.length === 0
  const motivoSinEnvio = !fecha || !enBorrador
    ? null
    : fecha.partidos === 0
      ? 'Asigna al menos un partido para poder enviar la fecha.'
      : !verificacionLista
        ? 'Esperando la verificación de la fecha…'
        : conflictos.length > 0
          ? 'Resuelve los conflictos marcados para poder enviar la fecha.'
          : null

  const errorAsignacion = asignar.isError
    ? asignar.error instanceof ApiError
      ? asignar.error.problema.detail
      : 'No se pudo asignar la fecha. Intenta de nuevo.'
    : null

  const cambiarFecha = (siguiente: string) => {
    setFechaElegida(siguiente)
    setMensaje(null)
    setUltimaAsignacion(null)
    asignar.reset()
  }

  const asignarPendientes = () => {
    if (!fecha) {
      return
    }
    setMensaje(null)
    setUltimaAsignacion(null)
    asignar.mutate(
      { fechaId: fecha.id, modo: 'PENDIENTES' },
      {
        onSuccess: (resultado) => {
          setUltimaAsignacion(resultado)
          setMensaje(`Asignación terminada: ${resumenAsignacion(resultado)}.`)
        },
      },
    )
  }

  const regenerar = (motivo: string) => {
    if (!fecha) {
      return
    }
    asignar.mutate(
      { fechaId: fecha.id, modo: 'REGENERAR', motivo },
      {
        onSuccess: (resultado) => {
          setRegenerando(false)
          setUltimaAsignacion(resultado)
          setMensaje(
            `Fecha regenerada: ${resumenAsignacion(resultado)}. Los partidos anteriores quedaron cancelados con el motivo.`,
          )
        },
        onError: () => setRegenerando(false),
      },
    )
  }

  const fueDevuelta = enBorrador && Boolean(fecha?.observaciones)

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
            Programación por fecha
          </h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Revisa la fecha completa por categoría y grupo, ajusta lo necesario con motivo y envíala
            a aprobación en un solo paso.
          </p>
        </div>
        <div className="flex shrink-0 flex-col items-start gap-2 sm:items-end">
          <Insignia variante="exito" icono={CalendarDays}>
            Temporada activa: {temporada.nombre}
          </Insignia>
          {selectorVista}
        </div>
      </header>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      {ultimaAsignacion && ultimaAsignacion.advertencias.length > 0 && (
        <div
          role="note"
          aria-label="Advertencias de la asignación"
          className="rounded-lg border border-warning/30 bg-warning/8 p-3"
        >
          <p className="flex items-center gap-2 text-label-md font-semibold text-warning-strong">
            <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
            Advertencias de la asignación
          </p>
          <ul className="mt-1.5 flex list-disc flex-col gap-1 pl-6">
            {ultimaAsignacion.advertencias.map((advertencia, indice) => (
              <li key={indice} className="break-words text-body-md text-text-main">
                {advertencia}
              </li>
            ))}
          </ul>
        </div>
      )}

      {errorAsignacion && (
        <p
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
        >
          <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {errorAsignacion}
        </p>
      )}

      {permisos.editar && categorias.data && (
        <TarjetaCalendario temporada={temporada} categorias={categorias.data} />
      )}

      <section
        aria-label="Selección de la fecha"
        className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4"
      >
        <div className="grid gap-4 lg:grid-cols-[minmax(0,22rem)_minmax(0,1fr)] lg:items-end">
          <CampoSeleccion
            id={`${id}-fecha`}
            etiqueta="Fecha"
            value={fechaId ?? ''}
            onChange={(evento) => cambiarFecha(evento.target.value)}
          >
            {fechas.map(({ id: opcion, numero, dias, estadoPublicacion }) => (
              <option key={opcion} value={opcion}>
                Fecha {numero} · {diasDeFecha(dias)} · {etiquetaEstadoJornada[estadoPublicacion]}
              </option>
            ))}
          </CampoSeleccion>

          {fecha && puedeEditar && (
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap lg:justify-end">
              <Button
                variante="secundario"
                icono={Wand2}
                cargando={asignar.isPending && !regenerando}
                disabled={asignar.isPending}
                onClick={asignarPendientes}
              >
                Asignar pendientes
              </Button>
              <Button
                variante="secundario"
                icono={RefreshCw}
                disabled={asignar.isPending}
                onClick={() => {
                  setMensaje(null)
                  asignar.reset()
                  setRegenerando(true)
                }}
              >
                Regenerar
              </Button>
              <Button
                variante="secundario"
                icono={ListChecks}
                cargando={verificacion.isFetching}
                onClick={() => void verificacion.refetch()}
              >
                Verificar
              </Button>
              {permisos.enviar && (
                <Button
                  icono={Send}
                  disabled={!puedeEnviar}
                  aria-describedby={motivoSinEnvio ? `${id}-sin-envio` : undefined}
                  onClick={() => {
                    setMensaje(null)
                    setEnviando(true)
                  }}
                >
                  Enviar fecha
                </Button>
              )}
            </div>
          )}
        </div>

        {fecha && puedeEditar && permisos.enviar && motivoSinEnvio && (
          <p id={`${id}-sin-envio`} className="text-body-sm text-text-muted lg:text-right">
            {motivoSinEnvio}
          </p>
        )}
      </section>

      {detalle.isPending && <EstadoCarga etiqueta="Cargando la fecha…" filas={5} columnas={4} />}

      {detalle.isError && (
        <ErrorConsulta error={detalle.error} onReintentar={() => void detalle.refetch()} />
      )}

      {fecha && !detalle.isError && (
        <section aria-labelledby={`${id}-titulo`} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 id={`${id}-titulo`} className="text-headline-md text-text-main">
                Fecha {fecha.numero} · {diasDeFecha(fecha.dias)}
              </h2>
              <Insignia variante={varianteEstadoJornada[fecha.estadoPublicacion]}>
                {etiquetaEstadoJornada[fecha.estadoPublicacion]}
              </Insignia>
            </div>
            <p className="text-body-md text-text-muted">
              {fecha.partidos} {fecha.partidos === 1 ? 'partido' : 'partidos'}
              {' · '}
              {fecha.jornadas} {fecha.jornadas === 1 ? 'jornada' : 'jornadas'}
              {fecha.sinAsignar > 0 && ` · ${fecha.sinAsignar} sin asignar`}
            </p>
          </div>

          {!enBorrador && (
            <p
              role="status"
              className="flex items-start gap-2 rounded-lg border border-info/30 bg-info/10 p-3 text-body-md text-info-strong"
            >
              <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              <span>
                {fecha.estadoPublicacion === 'REVISION'
                  ? `La fecha está en revisión de la Presidencia${fecha.enviadaEn ? ` desde el ${formatearFechaHora(fecha.enviadaEn)}` : ''}. Podrás ajustarla si la devuelve a borrador.`
                  : `La fecha está publicada${fecha.publicadaEn ? ` desde el ${formatearFechaHora(fecha.publicadaEn)}` : ''} y ya aparece en el portal.`}
              </span>
            </p>
          )}

          {fueDevuelta && (
            <div
              role="note"
              aria-label="Observaciones de la Presidencia"
              className="flex items-start gap-3 rounded-lg border border-warning/40 bg-warning/10 p-4"
            >
              <CornerUpLeft aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-strong" />
              <div className="min-w-0">
                <p className="text-headline-sm text-warning-strong">La Presidencia devolvió esta fecha</p>
                <p className="mt-1 whitespace-pre-line break-words text-body-md text-text-main">
                  {fecha.observaciones}
                </p>
                <p className="mt-1 text-body-sm text-text-muted">
                  Corrige lo señalado y vuelve a enviarla a aprobación.
                </p>
              </div>
            </div>
          )}

          {enBorrador && verificacion.isError && (
            <ErrorConsulta
              error={verificacion.error}
              onReintentar={() => void verificacion.refetch()}
            />
          )}

          {enBorrador && verificacion.isSuccess && conflictos.length > 0 && (
            <div
              role="alert"
              className="flex items-start gap-3 rounded-lg border border-error/30 bg-error/8 p-4"
            >
              <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-error-strong" />
              <div>
                <p className="text-headline-sm text-error-strong">
                  {conflictos.length === 1
                    ? '1 conflicto por resolver'
                    : `${conflictos.length} conflictos por resolver`}
                </p>
                <p className="mt-1 text-body-md text-text-muted">
                  Ajusta los partidos marcados en rojo. La fecha no se puede enviar mientras queden
                  conflictos.
                </p>
              </div>
            </div>
          )}

          {enBorrador && verificacion.isSuccess && conflictos.length === 0 && (
            <p
              role="status"
              className="flex items-start gap-2 rounded-lg border border-border bg-surface p-3 text-body-md text-text-main"
            >
              <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-success-strong" />
              <span>
                Sin conflictos.
                {advertencias.length > 0 &&
                  ` Advertencias que no impiden el envío: ${resumenAdvertencias(advertencias)}.`}
              </span>
            </p>
          )}

          {(fecha.bloques ?? []).length === 0 ? (
            <p className="rounded-lg border border-border bg-surface p-4 text-body-md text-text-muted">
              Esta fecha todavía no tiene grupos con emparejamientos.
            </p>
          ) : (
            <div className="flex flex-col gap-4">
              {(fecha.bloques ?? []).map((bloque) => (
                <BloqueFecha
                  key={bloque.grupo.id}
                  bloque={bloque}
                  conflictosPorPartido={conflictosPorPartido}
                  causasSinArbitro={causasSinArbitro}
                  puedeEditar={puedeEditar}
                  onAjustar={(partido, { grupo, jornadaId }) => {
                    if (!jornadaId) {
                      return
                    }
                    setMensaje(null)
                    setEdicion({ tipo: 'ajustar', jornadaId, grupoId: grupo.id, partido })
                  }}
                  onAsignar={(pendiente, { grupo, jornadaId }) => {
                    if (!jornadaId) {
                      return
                    }
                    setMensaje(null)
                    setEdicion({
                      tipo: 'asignar',
                      jornadaId,
                      grupoId: grupo.id,
                      emparejamiento: {
                        id: pendiente.emparejamientoId,
                        equipoLocal: pendiente.equipoLocal,
                        equipoVisitante: pendiente.equipoVisitante,
                        causa: pendiente.causa,
                      },
                    })
                  }}
                />
              ))}
            </div>
          )}
        </section>
      )}

      {edicion && (
        <ModalPartido
          jornada={{ id: edicion.jornadaId, grupoId: edicion.grupoId }}
          partido={edicion.tipo === 'ajustar' ? edicion.partido : undefined}
          emparejamiento={edicion.tipo === 'asignar' ? edicion.emparejamiento : undefined}
          onCerrar={() => setEdicion(null)}
          onGuardado={() => {
            setMensaje(
              edicion.tipo === 'ajustar'
                ? 'Partido ajustado. El cambio quedó en el historial y la auditoría.'
                : 'Partido asignado. Ya no aparece como sin asignar.',
            )
            setEdicion(null)
          }}
        />
      )}

      {regenerando && fecha && (
        <ModalMotivo
          abierto
          titulo={`Regenerar la fecha ${fecha.numero}`}
          descripcion="Todos los partidos de la fecha se cancelarán (no se borran) junto con sus designaciones, y se volverán a asignar cancha, horario y árbitro. Los ajustes manuales se pierden. El motivo queda en la auditoría."
          etiquetaConfirmar="Regenerar fecha"
          varianteConfirmar="destructivo"
          procesando={asignar.isPending}
          onConfirmar={regenerar}
          onCancelar={() => setRegenerando(false)}
        />
      )}

      {enviando && fecha && (
        <ModalEnvioFecha
          fecha={fecha}
          advertencias={advertencias.length}
          onCerrar={() => setEnviando(false)}
          onEnviada={(enviada) => {
            setEnviando(false)
            setMensaje(
              `Fecha ${enviada.numero} enviada a aprobación. La Presidencia la revisará completa antes de publicarla.`,
            )
          }}
        />
      )}
    </div>
  )
}
