import { useId, useMemo, useState, type KeyboardEvent } from 'react'
import {
  BadgeCheck,
  CircleCheck,
  CircleX,
  Info,
  ShieldCheck,
  UserRoundPlus,
  Users,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import {
  TAMANO_ARBITROS,
  useArbitros,
  useConteoArbitros,
  useCuotasDeClubes,
} from '@/features/arbitraje/api'
import { estadosArbitroVisibles } from '@/features/arbitraje/etiquetas'
import { DetalleArbitro } from '@/features/arbitraje/DetalleArbitro'
import { ModalDictamenArbitro } from '@/features/arbitraje/ModalDictamenArbitro'
import { ModalPresentarArbitro } from '@/features/arbitraje/ModalPresentarArbitro'
import { PaginacionArbitros } from '@/features/arbitraje/PaginacionArbitros'
import { TablaArbitros } from '@/features/arbitraje/TablaArbitros'
import { TarjetaCuotaArbitros } from '@/features/arbitraje/TarjetaCuotaArbitros'
import { TarjetaReglaCuota } from '@/features/arbitraje/TarjetaReglaCuota'
import { usePermisosArbitros } from '@/features/arbitraje/permisos'
import type {
  Arbitro,
  DecisionArbitro,
  EstadoArbitroVisible,
} from '@/features/arbitraje/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

export const RUTA_ARBITROS = '/app/programacion/arbitros'

const formatoEntero = new Intl.NumberFormat('es-MX')

/** Filtro de la Comisión por el indicador de cuota del club (B-08.2, RF-114). */
type FiltroCuota = 'TODOS' | 'CUMPLE' | 'NO_CUMPLE'

/**
 * Pantalla de árbitros F-20 (H-11.2, UI-007). La Comisión de Arbitraje revisa por estado y valida
 * o rechaza; la Directiva de club presenta a sus árbitros y ve solo los suyos, porque el ámbito lo
 * filtra el backend. No hay «Colegio»: los árbitros los presentan los clubes (DEC-17, D-02).
 */
export function PantallaArbitros() {
  return (
    <ConPermiso
      permiso="arbitraje.arbitro:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <PaginaArbitros />
    </ConPermiso>
  )
}

function PaginaArbitros() {
  const id = useId()
  const {
    proponer,
    validar,
    disponibilidad,
    puedeRegistrarPersona,
    clubesProponer,
    clubesConsulta,
    consultaAmbitoLiga,
  } = usePermisosArbitros()
  const [filtro, setFiltro] = useState<EstadoArbitroVisible>('PROPUESTO')
  const [pagina, setPagina] = useState(0)
  const [filtroCuota, setFiltroCuota] = useState<FiltroCuota>('TODOS')
  const [presentando, setPresentando] = useState(false)
  const [dictamen, setDictamen] = useState<{
    arbitro: Arbitro
    decision: DecisionArbitro
  } | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [detalle, setDetalle] = useState<Arbitro | null>(null)

  const conteoPropuestos = useConteoArbitros('PROPUESTO')
  const conteoValidados = useConteoArbitros('VALIDADO')
  const conteoRechazados = useConteoArbitros('RECHAZADO')
  const lista = useArbitros({ estado: filtro, pagina, tamano: TAMANO_ARBITROS })
  const clubesDeLaPagina = useMemo(
    () => [...new Set((lista.data?.contenido ?? []).map(({ club }) => club.id))],
    [lista.data],
  )
  const cuotas = useCuotasDeClubes(clubesDeLaPagina, consultaAmbitoLiga)
  const conteos: Record<EstadoArbitroVisible, number | undefined> = {
    PROPUESTO: conteoPropuestos.data,
    VALIDADO: conteoValidados.data,
    RECHAZADO: conteoRechazados.data,
  }
  const puedePresentar = proponer && clubesProponer.length > 0
  const cargandoCuotas = cuotas.some(({ isPending }) => isPending)
  const cuotasSinDato =
    cuotas.length > 0 && cuotas.every(({ isError }) => isError)
  const cumplePorClub = new Map<string, boolean>()
  for (const [indice, clubId] of clubesDeLaPagina.entries()) {
    const cumple = cuotas[indice]?.data?.cuotaArbitros.cumple
    if (cumple !== undefined) {
      cumplePorClub.set(clubId, cumple)
    }
  }
  const filtrandoCuota = consultaAmbitoLiga && filtroCuota !== 'TODOS'
  const filasVisibles = filtrandoCuota
    ? (lista.data?.contenido ?? []).filter(({ club }) => {
        const cumple = cumplePorClub.get(club.id)
        return cumple !== undefined && cumple === (filtroCuota === 'CUMPLE')
      })
    : (lista.data?.contenido ?? [])

  if (esSinPermiso(lista.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const cambiarFiltro = (nuevo: EstadoArbitroVisible) => {
    setFiltro(nuevo)
    setPagina(0)
    setMensaje(null)
  }

  const navegarFiltros = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = estadosArbitroVisibles.findIndex(({ valor }) => valor === filtro)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente =
      estadosArbitroVisibles[
        (actual + paso + estadosArbitroVisibles.length) % estadosArbitroVisibles.length
      ].valor

    cambiarFiltro(siguiente)
    document.getElementById(`${id}-filtro-${siguiente}`)?.focus()
  }

  const alPresentar = (arbitro: Arbitro) => {
    setPresentando(false)
    setFiltro('PROPUESTO')
    setPagina(0)
    setMensaje(
      `«${arbitro.persona.nombreCompleto}» quedó PROPUESTO como árbitro de «${arbitro.club.nombre}». La Comisión ya puede revisarlo.`,
    )
  }

  const alResolver = (arbitro: Arbitro) => {
    const decision = dictamen?.decision
    setDictamen(null)
    setFiltro(decision === 'RECHAZADO' ? 'RECHAZADO' : 'VALIDADO')
    setPagina(0)
    setMensaje(
      decision === 'RECHAZADO'
        ? `El árbitro «${arbitro.persona.nombreCompleto}» quedó RECHAZADO con observaciones.`
        : `«${arbitro.persona.nombreCompleto}» quedó VALIDADO y ya puede designarse a partidos.`,
    )
  }

  const cantidad = lista.data?.totalElementos ?? 0
  const sinDatos = !lista.isPending && !lista.isError && lista.data?.contenido.length === 0

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Árbitros</h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Los clubes presentan a sus árbitros y la Comisión de Arbitraje los valida o rechaza con
            observaciones. Solo los validados se pueden designar a un partido.
          </p>
        </div>
        {puedePresentar && (
          <Button
            icono={UserRoundPlus}
            onClick={() => {
              setMensaje(null)
              setPresentando(true)
            }}
            className="shrink-0"
          >
            Presentar árbitro
          </Button>
        )}
      </header>

      {proponer && !puedePresentar && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-warning/30 bg-warning/14 p-3 text-body-md text-warning-strong"
        >
          <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          Tu cuenta puede presentar árbitros, pero no tiene un club asignado. Pide a la Liga que
          revise el ámbito de tu cuenta.
        </p>
      )}

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      {detalle && (
        <DetalleArbitro
          key={detalle.id}
          arbitro={detalle}
          onCerrar={() => setDetalle(null)}
        />
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_19rem] xl:items-start">
        <section
          aria-label="Catálogo de árbitros"
          className="flex min-w-0 flex-col gap-4 rounded-lg border border-border bg-surface p-4"
        >
          <div className="flex flex-col gap-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-headline-sm text-text-main">Catálogo de árbitros</h2>
                <p className="text-body-sm text-text-muted">
                  {cantidad === 1
                    ? '1 árbitro en este estado'
                    : `${formatoEntero.format(cantidad)} árbitros en este estado`}
                </p>
              </div>
              <Insignia variante="informacion" icono={ShieldCheck}>
                Revisión de la Comisión
              </Insignia>
            </div>

            <div
              role="tablist"
              aria-label="Filtrar árbitros por estado"
              onKeyDown={navegarFiltros}
              className="flex flex-wrap gap-1 rounded-md bg-background p-1"
            >
              {estadosArbitroVisibles.map(({ valor, texto, icono: Icono }) => {
                const seleccionado = filtro === valor
                const conteo = conteos[valor]

                return (
                  <button
                    key={valor}
                    type="button"
                    role="tab"
                    id={`${id}-filtro-${valor}`}
                    aria-selected={seleccionado}
                    aria-controls={`${id}-lista`}
                    tabIndex={seleccionado ? 0 : -1}
                    onClick={() => cambiarFiltro(valor)}
                    className={[
                      'flex min-h-11 items-center gap-2 rounded-md px-3 text-label-md transition-colors',
                      seleccionado
                        ? 'bg-surface text-text-main shadow-sm'
                        : 'text-text-muted hover:text-text-main',
                    ].join(' ')}
                  >
                    <Icono aria-hidden="true" className="size-4 shrink-0" />
                    {texto}
                    <span
                      aria-label={
                        conteo === undefined ? 'Conteo no disponible' : `${conteo} árbitros`
                      }
                      className={[
                        'inline-flex min-w-5 justify-center rounded-[6px] px-1.5 text-label-sm font-semibold',
                        seleccionado
                          ? 'bg-primary/12 text-primary'
                          : 'bg-surface text-text-muted',
                      ].join(' ')}
                    >
                      {conteo === undefined ? '—' : formatoEntero.format(conteo)}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>

          <div
            role="tabpanel"
            id={`${id}-lista`}
            aria-labelledby={`${id}-filtro-${filtro}`}
            className="flex min-w-0 flex-col gap-3"
          >
            {consultaAmbitoLiga && (
              <div className="flex flex-wrap items-end justify-between gap-3">
                <div className="flex flex-col gap-1">
                  <label
                    htmlFor={`${id}-filtro-cuota`}
                    className="text-label-md text-text-main"
                  >
                    Cumple cuota
                  </label>
                  <select
                    id={`${id}-filtro-cuota`}
                    value={filtroCuota}
                    onChange={(evento) =>
                      setFiltroCuota(evento.target.value as FiltroCuota)
                    }
                    className="min-h-11 rounded-md border border-border bg-surface px-3 text-body-md text-text-main"
                  >
                    <option value="TODOS">Todos los clubes</option>
                    <option value="CUMPLE">Solo los que cumplen</option>
                    <option value="NO_CUMPLE">Solo los que no cumplen</option>
                  </select>
                </div>
                {cargandoCuotas && (
                  <p role="status" className="text-body-sm text-text-muted">
                    Consultando la cuota de los clubes…
                  </p>
                )}
              </div>
            )}

            {consultaAmbitoLiga && cuotasSinDato && (
              <p
                role="alert"
                className="rounded-md border border-warning/30 bg-warning/14 p-3 text-body-sm text-warning-strong"
              >
                No se pudo consultar la cuota de los clubes de esta página.
              </p>
            )}

            {filtrandoCuota && lista.data && !lista.isPending && (
              <p role="status" className="text-body-sm text-text-muted">
                Mostrando {filasVisibles.length} de {lista.data.contenido.length} árbitros de
                esta página.
              </p>
            )}

            {lista.isPending && (
              <EstadoCarga etiqueta="Cargando árbitros…" filas={5} columnas={5} />
            )}

            {lista.isError && !esSinPermiso(lista.error) && (
              <ErrorConsulta
                error={lista.error}
                onReintentar={() => void lista.refetch()}
              />
            )}

            {sinDatos && (
              <EstadoVacio
                icono={
                  filtro === 'VALIDADO'
                    ? BadgeCheck
                    : filtro === 'RECHAZADO'
                      ? CircleX
                      : Users
                }
                titulo={
                  filtro === 'PROPUESTO'
                    ? 'No hay árbitros por validar'
                    : filtro === 'VALIDADO'
                      ? 'Aún no hay árbitros validados'
                      : 'No hay árbitros rechazados'
                }
                descripcion={
                  filtro === 'PROPUESTO'
                    ? 'Cuando un club presente a un árbitro, aparecerá aquí para que la Comisión lo revise.'
                    : filtro === 'VALIDADO'
                      ? 'Los árbitros que la Comisión valide aparecerán aquí y podrán designarse.'
                      : 'Los árbitros rechazados conservan su observación y su historial.'
                }
                accion={
                  filtro === 'PROPUESTO' && puedePresentar ? (
                    <Button
                      icono={UserRoundPlus}
                      onClick={() => {
                        setMensaje(null)
                        setPresentando(true)
                      }}
                    >
                      Presentar árbitro
                    </Button>
                  ) : undefined
                }
              />
            )}

            {filtrandoCuota &&
              filasVisibles.length === 0 &&
              lista.data &&
              lista.data.contenido.length > 0 && (
                <p className="rounded-lg border border-border bg-surface p-4 text-body-sm text-text-muted">
                  Ningún club de esta página coincide con el filtro de cuota.
                </p>
              )}

            {lista.data && filasVisibles.length > 0 && (
              <>
                <TablaArbitros
                  arbitros={filasVisibles}
                  actualizando={lista.isFetching && !lista.isPending}
                  puedeValidar={validar}
                  mostrarCuota={consultaAmbitoLiga}
                  onDictamen={(arbitro, decision) => {
                    setMensaje(null)
                    setDictamen({ arbitro, decision })
                  }}
                  onVerDisponibilidad={
                    disponibilidad
                      ? (arbitro) => {
                          setMensaje(null)
                          setDetalle(arbitro)
                        }
                      : undefined
                  }
                />
                {lista.data.totalPaginas > 1 && (
                  <PaginacionArbitros
                    pagina={lista.data.pagina}
                    totalPaginas={lista.data.totalPaginas}
                    onCambiarPagina={(nueva) => {
                      setPagina(nueva)
                      setMensaje(null)
                    }}
                  />
                )}
              </>
            )}
          </div>
        </section>

        <aside className="flex min-w-0 flex-col gap-4">
          {consultaAmbitoLiga ? (
            <TarjetaReglaCuota />
          ) : clubesConsulta.length > 0 ? (
            clubesConsulta.map((club) => (
              <TarjetaCuotaArbitros key={club.id} club={club} />
            ))
          ) : (
            <section
              aria-labelledby={`${id}-cuota-sin-club`}
              className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4"
            >
              <h2 id={`${id}-cuota-sin-club`} className="text-headline-sm text-text-main">
                Cuota de árbitros
              </h2>
              <p className="text-body-sm text-text-muted">
                Tu cuenta no tiene un club asignado, así que no hay una cuota que mostrar.
              </p>
            </section>
          )}

          <section
            aria-labelledby={`${id}-solo-validados`}
            className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-4"
          >
            <h2
              id={`${id}-solo-validados`}
              className="flex items-center gap-2 text-headline-sm text-text-main"
            >
              <BadgeCheck aria-hidden="true" className="size-5 text-primary" />
              Solo los validados
            </h2>
            <p className="text-body-sm text-text-muted">
              Un árbitro propuesto o rechazado no puede designarse a un partido. La validación de
              la Comisión es la que lo habilita.
            </p>
          </section>
        </aside>
      </div>

      {presentando && (
        <ModalPresentarArbitro
          clubes={clubesProponer}
          puedeRegistrarPersona={puedeRegistrarPersona}
          onCerrar={() => setPresentando(false)}
          onPresentado={alPresentar}
        />
      )}

      {dictamen && (
        <ModalDictamenArbitro
          key={`${dictamen.arbitro.id}-${dictamen.decision}`}
          arbitro={dictamen.arbitro}
          decision={dictamen.decision}
          onCerrar={() => setDictamen(null)}
          onResuelto={alResolver}
        />
      )}
    </div>
  )
}
