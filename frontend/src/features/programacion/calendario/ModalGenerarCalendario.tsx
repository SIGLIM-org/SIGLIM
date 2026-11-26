import { useEffect, useId, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Info, Lock, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { Insignia, type VarianteInsignia } from '@/components/ui/Insignia'
import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { ResultadoCalendario } from '@/features/programacion/calendario/ResultadoCalendario'
import {
  gruposDeConflicto,
  useGenerarCalendario,
} from '@/features/programacion/calendario/api'
import {
  conFinDeSemana,
  DOMINGO,
  diasCambiados,
  diasEntreSemana,
  enumerar,
  esErrorDeFechaInicio,
  estadoGrupo,
  formatearDiasFecha,
  grupoIdsParaEnviar,
  nombresDias,
  primeraFecha,
  requiereMotivo,
  resumenCalendario,
  SABADO,
  seleccionInicial,
  validarMotivo,
  type EstadoGrupoCalendario,
} from '@/features/programacion/calendario/reglas'
import type {
  Calendario,
  CalendarioGenerado,
  GenerarCalendarioPeticion,
} from '@/features/programacion/calendario/tipos'
import { CasillaParametro } from '@/features/temporadas/CasillaParametro'
import { useActualizarDiasJuego, useParametros } from '@/features/temporadas/api'
import { rutaEstructura } from '@/features/temporadas/pestanas'
import type { CategoriaTemporada, Temporada } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  temporada: Temporada
  categorias: CategoriaTemporada[]
  calendario: Calendario
  onCerrar: () => void
}

type ModoFecha = 'conservar' | 'recalcular'

type Vista =
  | { tipo: 'formulario' }
  | { tipo: 'resultado'; generado: CalendarioGenerado | null; diasGuardados: string[] }

const ETIQUETA_ESTADO: Record<EstadoGrupoCalendario, string> = {
  SIN_CALENDARIO: 'Sin calendario',
  CON_CALENDARIO: 'Con calendario',
  CON_PARTIDOS: 'Con partidos',
}

const VARIANTE_ESTADO: Record<EstadoGrupoCalendario, VarianteInsignia> = {
  SIN_CALENDARIO: 'neutra',
  CON_CALENDARIO: 'informacion',
  CON_PARTIDOS: 'advertencia',
}

const FECHA_VALIDA = /^\d{4}-\d{2}-\d{2}$/

function plural(cantidad: number, singular: string, varios: string): string {
  return `${cantidad} ${cantidad === 1 ? singular : varios}`
}

function borradorInicial(categorias: readonly CategoriaTemporada[]): Record<string, number[]> {
  return Object.fromEntries(categorias.map(({ id, diasJuego }) => [id, [...diasJuego]]))
}

/**
 * Modal «Generar calendario» de F-22 (H-32.4, criterios 1 a 6 de H-32): vueltas de los parámetros
 * vigentes (solo lectura, DEC-190), fecha de inicio (DEC-186, DEC-200), grupos que se generan o
 * regeneran con motivo (DEC-198, RN-021, RN-020) y días de juego por categoría (DEC-199, DEC-191).
 * Primero guarda los días que cambiaron, uno tras otro, y después genera; si un día falla, no
 * genera. El resultado se muestra en el mismo modal.
 */
export function ModalGenerarCalendario({ temporada, categorias, calendario, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const id = useId()
  const idTitulo = `${id}-titulo`
  const tituloRef = useRef<HTMLHeadingElement>(null)

  const parametros = useParametros(temporada.id)
  const generar = useGenerarCalendario(temporada.id)
  const actualizarDias = useActualizarDiasJuego(temporada.id)

  // El calendario que llega al abrir: las invalidaciones lo refrescan, pero la selección y los
  // estados de la sesión se calculan sobre la foto inicial más lo que responda el servidor.
  const [inicial] = useState(calendario)
  const hayGenerado = inicial.grupos.some((grupo) => grupo.generado)
  const resumen = resumenCalendario(inicial)

  const [vista, setVista] = useState<Vista>({ tipo: 'formulario' })
  const [seleccion, setSeleccion] = useState<string[]>(() => seleccionInicial(inicial))
  const [bloqueadosEnSesion, setBloqueadosEnSesion] = useState<string[]>([])
  const [modoFecha, setModoFecha] = useState<ModoFecha>('conservar')
  const [fechaInicio, setFechaInicio] = useState(temporada.fechaInicio)
  const [motivo, setMotivo] = useState('')
  const [borrador, setBorrador] = useState(() => borradorInicial(categorias))
  const [errorFecha, setErrorFecha] = useState<string | null>(null)
  const [errorMotivo, setErrorMotivo] = useState<string | null>(null)
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const [procesando, setProcesando] = useState(false)

  useEffect(() => {
    if (vista.tipo === 'resultado') {
      tituloRef.current?.focus()
    }
  }, [vista])

  // Foco inicial en el primer campo en cuanto llegan los parámetros (el diálogo se abre antes).
  const hayFormulario = Boolean(parametros.data?.vigente)
  const primerCampoRef = useRef<HTMLElement>(null)

  useEffect(() => {
    if (hayFormulario) {
      primerCampoRef.current
        ?.querySelector<HTMLInputElement>('input:checked, input:not([type="radio"])')
        ?.focus()
    }
  }, [hayFormulario])

  const equiposPorGrupo = useMemo(
    () =>
      new Map(
        categorias.flatMap(({ grupos }) => grupos.map((grupo) => [grupo.id, grupo.equipos] as const)),
      ),
    [categorias],
  )

  const categoriasOrdenadas = useMemo(
    () => [...categorias].sort((primera, segunda) => primera.categoria.orden - segunda.categoria.orden),
    [categorias],
  )

  const estadoDe = (grupoId: string): EstadoGrupoCalendario => {
    if (bloqueadosEnSesion.includes(grupoId)) {
      return 'CON_PARTIDOS'
    }
    const grupo = inicial.grupos.find((candidato) => candidato.grupoId === grupoId)

    return grupo ? estadoGrupo(grupo) : 'SIN_CALENDARIO'
  }

  const nombreGrupo = (grupoId: string, nombre: string) => {
    const grupo = inicial.grupos.find((candidato) => candidato.grupoId === grupoId)

    return grupo ? `${grupo.categoria.nombre} · ${grupo.nombre}` : nombre
  }

  const gruposPorCategoria = useMemo(() => {
    const bloques: { categoriaId: string; categoria: string; grupos: Calendario['grupos'] }[] = []

    for (const grupo of inicial.grupos) {
      const ultimo = bloques[bloques.length - 1]

      if (ultimo && ultimo.categoriaId === grupo.categoria.id) {
        ultimo.grupos.push(grupo)
      } else {
        bloques.push({
          categoriaId: grupo.categoria.id,
          categoria: grupo.categoria.nombre,
          grupos: [grupo],
        })
      }
    }

    return bloques
  }, [inicial])

  const elegibles = inicial.grupos
    .map(({ grupoId }) => grupoId)
    .filter((grupoId) => estadoDe(grupoId) !== 'CON_PARTIDOS')
  const sinCalendario = elegibles.filter((grupoId) => estadoDe(grupoId) === 'SIN_CALENDARIO')

  const pideMotivo = requiereMotivo(inicial, seleccion)
  const reemplazados = inicial.grupos.filter(
    (grupo) => grupo.generado && seleccion.includes(grupo.grupoId),
  )
  const cambios = diasCambiados(borrador, categorias)
  const categoriasSinDias = categorias.filter((categoria) => (borrador[categoria.id] ?? []).length === 0)
  const mandaFecha = !hayGenerado || modoFecha === 'recalcular'
  const fechaCompleta = FECHA_VALIDA.test(fechaInicio)
  const vistaPrevia =
    mandaFecha && fechaCompleta && fechaInicio >= temporada.fechaInicio
      ? formatearDiasFecha(primeraFecha(fechaInicio))
      : null

  const accion: 'generar' | 'dias' | null =
    seleccion.length > 0 ? 'generar' : cambios.length > 0 ? 'dias' : null

  const limpiarErrores = () => {
    setErrorGeneral(null)
  }

  const alternarGrupo = (grupoId: string, marcado: boolean) => {
    limpiarErrores()
    setErrorMotivo(null)
    setSeleccion((actual) =>
      marcado ? [...actual, grupoId] : actual.filter((elegido) => elegido !== grupoId),
    )
  }

  const cambiarFinDeSemana = (categoria: CategoriaTemporada, dia: number, marcado: boolean) => {
    limpiarErrores()
    setBorrador((actual) => {
      const dias = actual[categoria.id] ?? categoria.diasJuego
      const sabado = dia === SABADO ? marcado : dias.includes(SABADO)
      const domingo = dia === DOMINGO ? marcado : dias.includes(DOMINGO)

      return { ...actual, [categoria.id]: conFinDeSemana(dias, sabado, domingo) }
    })
  }

  const validarFecha = (): string | null => {
    if (!mandaFecha) {
      return null
    }
    if (!fechaCompleta) {
      return 'Elige la fecha de inicio del calendario.'
    }
    if (fechaInicio < temporada.fechaInicio) {
      return 'La fecha de inicio no puede ser anterior al inicio de la temporada.'
    }

    return null
  }

  /** PATCH de los días que cambiaron, uno tras otro; se detiene en el primero que falla. */
  const guardarDias = async (): Promise<{
    nombres: string[]
    lineas: string[]
    error: string | null
  }> => {
    const nombres: string[] = []
    const lineas: string[] = []

    for (const { categoria, diasJuego } of cambios) {
      try {
        await actualizarDias.mutateAsync({ categoriaTemporadaId: categoria.id, diasJuego })
        nombres.push(categoria.categoria.nombre)
        lineas.push(`${categoria.categoria.nombre}: ${nombresDias(diasJuego)}`)
      } catch (error) {
        const causa =
          error instanceof ApiError ? error.problema.detail : 'Intenta de nuevo en un momento.'
        const previos = nombres.length > 0 ? `Se guardaron los días de ${enumerar(nombres)}. ` : ''
        const consecuencia = accion === 'generar' ? ' No se generó el calendario.' : ''

        return {
          nombres,
          lineas,
          error: `${previos}No se pudieron guardar los de ${categoria.categoria.nombre}: ${causa}${consecuencia}`,
        }
      }
    }

    return { nombres, lineas, error: null }
  }

  const alFallarGeneracion = (error: unknown, avisoDias: string) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral(`${avisoDias}No se pudo generar el calendario. Intenta de nuevo.`)
      return
    }
    const { status, codigo, detail } = error.problema

    if (status === 409 && codigo === 'RN-020') {
      const bloqueados = gruposDeConflicto(error).map(({ grupoId }) => grupoId)

      setBloqueadosEnSesion((actual) => [...new Set([...actual, ...bloqueados])])
      setSeleccion((actual) => actual.filter((grupoId) => !bloqueados.includes(grupoId)))
      setErrorGeneral(`${avisoDias}${detail}`)
      return
    }
    if (status === 400 && codigo === 'RN-021') {
      setErrorMotivo(detail)
      if (avisoDias) {
        setErrorGeneral(avisoDias.trim())
      }
      return
    }
    if (status === 422 && mandaFecha && esErrorDeFechaInicio(detail)) {
      setErrorFecha(detail)
      if (hayGenerado) {
        setModoFecha('conservar')
      }
      if (avisoDias) {
        setErrorGeneral(avisoDias.trim())
      }
      return
    }

    setErrorGeneral(`${avisoDias}${detail}`)
  }

  const alEnviar = async (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    if (procesando || accion === null) {
      return
    }
    setErrorGeneral(null)

    const errorDeFecha = accion === 'generar' ? validarFecha() : null
    const errorDeMotivo = accion === 'generar' && pideMotivo ? validarMotivo(motivo) : null

    setErrorFecha(errorDeFecha)
    setErrorMotivo(errorDeMotivo)
    if (errorDeFecha || errorDeMotivo || categoriasSinDias.length > 0) {
      // El foco va al primer campo con error, en el orden del formulario (RNF-008).
      const primerError = errorDeFecha
        ? idFecha
        : errorDeMotivo
          ? idMotivo
          : `${id}-dias-${categoriasSinDias[0].id}-sabado`

      document.getElementById(primerError)?.focus()
      return
    }

    setProcesando(true)
    try {
      const { nombres, lineas, error } = await guardarDias()

      if (error) {
        setErrorGeneral(error)
        return
      }
      if (accion === 'dias') {
        setVista({ tipo: 'resultado', generado: null, diasGuardados: lineas })
        return
      }

      const peticion: GenerarCalendarioPeticion = {}
      const grupoIds = grupoIdsParaEnviar(inicial, seleccion)

      if (mandaFecha) {
        peticion.fechaInicio = fechaInicio
      }
      if (grupoIds) {
        peticion.grupoIds = grupoIds
      }
      if (pideMotivo) {
        peticion.motivo = motivo.trim()
      }

      try {
        const generado = await generar.mutateAsync(peticion)

        setVista({ tipo: 'resultado', generado, diasGuardados: lineas })
      } catch (errorGeneracion) {
        const avisoDias =
          nombres.length > 0 ? `Se guardaron los días de juego de ${enumerar(nombres)}. ` : ''

        alFallarGeneracion(errorGeneracion, avisoDias)
      }
    } finally {
      setProcesando(false)
    }
  }

  const vigente = parametros.data?.vigente ?? null
  const vueltasDistintas =
    vigente !== null && resumen.vueltas !== null && resumen.vueltas !== vigente.vueltas
  const idFecha = `${id}-fecha`
  const idMotivo = `${id}-motivo`
  const idSinAccion = `${id}-sin-accion`
  const motivoLimpio = motivo.trim()

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!procesando) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      {vista.tipo === 'resultado' ? (
        <ResultadoCalendario
          idTitulo={idTitulo}
          tituloRef={tituloRef}
          generado={vista.generado}
          diasGuardados={vista.diasGuardados}
          nombreGrupo={nombreGrupo}
          onCerrar={onCerrar}
        />
      ) : (
        <form onSubmit={(evento) => void alEnviar(evento)} noValidate className="flex flex-col">
          <div className="flex flex-col gap-6 p-6">
            <div>
              <h2 id={idTitulo} ref={tituloRef} tabIndex={-1} className="text-balance text-headline-md outline-none">
                {resumen.grupos > 0 && resumen.generados === resumen.grupos
                  ? 'Regenerar calendario'
                  : 'Generar calendario'}
              </h2>
              <p className="mt-1 text-body-md text-text-muted">
                {temporada.nombre}. Se arman los enfrentamientos de cada grupo y las fechas en que
                se juegan; la cancha y el horario se asignan después.
              </p>
            </div>

            {parametros.isPending && (
              <p role="status" className="text-body-md text-text-muted">
                Consultando los parámetros deportivos…
              </p>
            )}

            {parametros.isError && (
              <ErrorConsulta error={parametros.error} onReintentar={() => void parametros.refetch()} />
            )}

            {parametros.isSuccess && vigente === null && (
              <div
                role="note"
                className="flex items-start gap-3 rounded-lg border border-info/30 bg-info/10 p-4"
              >
                <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-info-strong" />
                <div className="min-w-0 text-body-md text-text-main">
                  <p>
                    Las vueltas salen de los parámetros deportivos y la temporada todavía no tiene.
                    Pide a quien administra la temporada que los registre.
                  </p>
                  <Link
                    to={rutaEstructura(temporada.id, 'parametros')}
                    className="mt-2 inline-flex min-h-11 items-center text-primary underline underline-offset-2"
                  >
                    Ver los parámetros de la temporada
                  </Link>
                </div>
              </div>
            )}

            {vigente !== null && (
              <>
                <section aria-labelledby={`${id}-vueltas`} className="flex flex-col gap-2">
                  <h3 id={`${id}-vueltas`} className="text-label-md text-text-main">
                    Vueltas
                  </h3>
                  <p className="text-body-md text-text-main">
                    {plural(vigente.vueltas, 'vuelta', 'vueltas')} · parámetros versión{' '}
                    {vigente.version}
                  </p>
                  {vueltasDistintas && resumen.vueltas !== null && (
                    <p className="flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 p-3 text-body-md text-warning-strong">
                      <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                      El calendario actual tiene {plural(resumen.vueltas, 'vuelta', 'vueltas')} y
                      los parámetros vigentes, {vigente.vueltas}. Regenera los grupos para
                      aplicarlas.
                    </p>
                  )}
                </section>

                <section
                  ref={primerCampoRef}
                  aria-labelledby={`${id}-inicio`}
                  className="flex flex-col gap-3"
                >
                  <h3 id={`${id}-inicio`} className="text-label-md text-text-main">
                    Inicio del calendario
                  </h3>

                  {hayGenerado && (
                    <fieldset className="flex flex-col gap-1" aria-describedby={errorFecha && modoFecha === 'conservar' ? `${idFecha}-error-modo` : undefined}>
                      <legend className="sr-only">Días de las fechas</legend>
                      {(
                        [
                          ['conservar', 'Conservar los días de las fechas actuales'],
                          ['recalcular', 'Recalcular desde otra fecha de inicio'],
                        ] as const
                      ).map(([valor, etiqueta]) => (
                        <label
                          key={valor}
                          className="flex min-h-11 cursor-pointer items-center gap-3 text-body-md text-text-main"
                        >
                          <input
                            type="radio"
                            name={`${id}-modo-fecha`}
                            value={valor}
                            checked={modoFecha === valor}
                            onChange={() => {
                              setModoFecha(valor)
                              setErrorFecha(null)
                              limpiarErrores()
                            }}
                            className="size-[18px] shrink-0 accent-primary"
                          />
                          {etiqueta}
                        </label>
                      ))}
                      {errorFecha && modoFecha === 'conservar' && (
                        <p id={`${idFecha}-error-modo`} role="alert" className="text-body-sm text-error-strong">
                          {errorFecha}
                        </p>
                      )}
                    </fieldset>
                  )}

                  {mandaFecha && (
                    <div className="flex flex-col gap-2">
                      <CampoTexto
                        id={idFecha}
                        etiqueta="Fecha de inicio"
                        type="date"
                        required
                        min={temporada.fechaInicio}
                        value={fechaInicio}
                        error={errorFecha ?? undefined}
                        ayuda="La fecha 1 es el primer sábado en o después de este día."
                        onChange={(evento) => {
                          setFechaInicio(evento.target.value)
                          setErrorFecha(null)
                          limpiarErrores()
                        }}
                      />
                      <p aria-live="polite" className="text-body-md text-text-main">
                        {vistaPrevia ? `La fecha 1 será el ${vistaPrevia}.` : ''}
                      </p>
                      {hayGenerado && (
                        <p className="flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 p-3 text-body-md text-warning-strong">
                          <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                          Se recalculan los días de todas las fechas, también los que se cambiaron
                          a mano.
                        </p>
                      )}
                    </div>
                  )}
                </section>

                <fieldset className="flex flex-col gap-3">
                  <legend className="text-label-md text-text-main">Grupos</legend>
                  <p className="-mt-1 text-body-sm text-text-muted">
                    Se genera el calendario de los grupos marcados. Un grupo que ya tiene
                    calendario se reemplaza con un motivo.
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variante="secundario"
                      disabled={elegibles.length === 0}
                      onClick={() => {
                        limpiarErrores()
                        setSeleccion(elegibles)
                      }}
                    >
                      Marcar todos
                    </Button>
                    <Button
                      variante="secundario"
                      disabled={elegibles.length === 0}
                      onClick={() => {
                        limpiarErrores()
                        setErrorMotivo(null)
                        setSeleccion(sinCalendario)
                      }}
                    >
                      Solo los que no tienen
                    </Button>
                  </div>

                  {gruposPorCategoria.map((bloque) => (
                    <div key={bloque.categoriaId} className="flex flex-col">
                      <p className="text-label-sm uppercase text-text-muted">{bloque.categoria}</p>
                      <ul className="divide-y divide-border">
                        {bloque.grupos.map((grupo) => {
                          const estado = estadoDe(grupo.grupoId)
                          const bloqueado = estado === 'CON_PARTIDOS'
                          const equipos = equiposPorGrupo.get(grupo.grupoId)
                          const idCasilla = `${id}-grupo-${grupo.grupoId}`

                          return (
                            <li key={grupo.grupoId} className="flex flex-col gap-0.5 py-1">
                              <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                                <label
                                  htmlFor={idCasilla}
                                  className={[
                                    'flex min-h-11 items-center gap-3 text-body-md text-text-main',
                                    bloqueado ? 'cursor-not-allowed opacity-70' : 'cursor-pointer',
                                  ].join(' ')}
                                >
                                  <input
                                    id={idCasilla}
                                    type="checkbox"
                                    checked={seleccion.includes(grupo.grupoId)}
                                    disabled={bloqueado || procesando}
                                    aria-describedby={bloqueado ? `${idCasilla}-ayuda` : undefined}
                                    onChange={(evento) =>
                                      alternarGrupo(grupo.grupoId, evento.target.checked)
                                    }
                                    className="size-[18px] shrink-0 accent-primary"
                                  />
                                  {grupo.nombre}{' '}
                                  {equipos !== undefined && (
                                    <span className="text-body-sm text-text-muted">
                                      {plural(equipos, 'equipo', 'equipos')}
                                    </span>
                                  )}
                                </label>
                                <Insignia
                                  variante={VARIANTE_ESTADO[estado]}
                                  icono={bloqueado ? Lock : undefined}
                                >
                                  {ETIQUETA_ESTADO[estado]}
                                </Insignia>
                              </div>
                              {bloqueado && (
                                <p id={`${idCasilla}-ayuda`} className="pl-[30px] text-body-sm text-text-muted">
                                  Ya tiene partidos programados; su calendario no se puede
                                  regenerar (RN-020).
                                </p>
                              )}
                            </li>
                          )
                        })}
                      </ul>
                    </div>
                  ))}
                </fieldset>

                {pideMotivo && (
                  <div className="flex flex-col gap-1.5">
                    <label htmlFor={idMotivo} className="text-label-md">
                      Motivo del reemplazo
                      <span aria-hidden="true" className="ml-0.5 text-error">
                        *
                      </span>
                    </label>
                    <textarea
                      id={idMotivo}
                      rows={3}
                      required
                      maxLength={MAXIMO_MOTIVO}
                      value={motivo}
                      aria-invalid={errorMotivo ? true : undefined}
                      aria-describedby={
                        errorMotivo ? `${idMotivo}-error ${idMotivo}-ayuda` : `${idMotivo}-ayuda`
                      }
                      onChange={(evento) => {
                        setMotivo(evento.target.value)
                        setErrorMotivo(null)
                        limpiarErrores()
                      }}
                      className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md aria-invalid:border-error"
                    />
                    <p id={`${idMotivo}-ayuda`} className="flex justify-between gap-2 text-body-sm text-text-muted">
                      <span>
                        Se reemplaza el calendario de:{' '}
                        {enumerar(reemplazados.map((grupo) => `${grupo.categoria.nombre} · ${grupo.nombre}`))}
                        . Mínimo {MINIMO_MOTIVO} caracteres; quedará en el historial.
                      </span>
                      <span className="shrink-0 font-mono">
                        {motivoLimpio.length}/{MAXIMO_MOTIVO}
                      </span>
                    </p>
                    {errorMotivo && (
                      <p id={`${idMotivo}-error`} role="alert" className="text-body-sm text-error-strong">
                        {errorMotivo}
                      </p>
                    )}
                  </div>
                )}

                <section aria-labelledby={`${id}-dias`} className="flex flex-col gap-3">
                  <div>
                    <h3 id={`${id}-dias`} className="text-label-md text-text-main">
                      Días de juego
                    </h3>
                    <p className="mt-0.5 text-body-sm text-text-muted">
                      Se usan al asignar cancha y horario a cada fecha. El valor inicial depende del
                      tipo de categoría.
                    </p>
                  </div>
                  <div className="flex flex-col divide-y divide-border">
                    {categoriasOrdenadas.map((categoria) => {
                      const dias = borrador[categoria.id] ?? categoria.diasJuego
                      const entreSemana = diasEntreSemana(dias)
                      const sinDias = dias.length === 0
                      const idCategoria = `${id}-dias-${categoria.id}`
                      const descritos = [
                        entreSemana.length > 0 ? `${idCategoria}-tambien` : null,
                        sinDias ? `${idCategoria}-error` : null,
                      ]
                        .filter(Boolean)
                        .join(' ')

                      return (
                        <fieldset
                          key={categoria.id}
                          aria-describedby={descritos || undefined}
                          className="flex flex-col gap-1 py-2 sm:flex-row sm:flex-wrap sm:items-center sm:gap-x-6"
                        >
                          <legend className="float-left w-full text-body-md text-text-main sm:w-48">
                            {categoria.categoria.nombre}
                          </legend>
                          <div className="flex flex-wrap gap-x-6">
                            <CasillaParametro
                              id={`${idCategoria}-sabado`}
                              etiqueta="Sábado"
                              marcada={dias.includes(SABADO)}
                              onCambiar={(marcada) => cambiarFinDeSemana(categoria, SABADO, marcada)}
                            />
                            <CasillaParametro
                              id={`${idCategoria}-domingo`}
                              etiqueta="Domingo"
                              marcada={dias.includes(DOMINGO)}
                              onCambiar={(marcada) => cambiarFinDeSemana(categoria, DOMINGO, marcada)}
                            />
                          </div>
                          {entreSemana.length > 0 && (
                            <p id={`${idCategoria}-tambien`} className="text-body-sm text-text-muted">
                              También: {nombresDias(entreSemana)}
                            </p>
                          )}
                          {sinDias && (
                            <p
                              id={`${idCategoria}-error`}
                              role="alert"
                              className="w-full text-body-sm text-error-strong"
                            >
                              Marca al menos un día de juego para {categoria.categoria.nombre}.
                            </p>
                          )}
                        </fieldset>
                      )
                    })}
                  </div>
                </section>
              </>
            )}

            {errorGeneral && (
              <p
                role="alert"
                className="rounded-md border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
              >
                {errorGeneral}
              </p>
            )}
          </div>

          <div className="sticky bottom-0 flex flex-col gap-2 border-t border-border bg-surface px-6 py-4">
            {vigente !== null && accion === null && (
              <p id={idSinAccion} className="text-body-sm text-text-muted sm:text-right">
                Marca al menos un grupo o cambia los días de juego.
              </p>
            )}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button variante="secundario" onClick={onCerrar} disabled={procesando}>
                {vigente === null ? 'Cerrar' : 'Cancelar'}
              </Button>
              {vigente !== null && (
                <Button
                  type="submit"
                  cargando={procesando}
                  disabled={accion === null}
                  aria-describedby={accion === null ? idSinAccion : undefined}
                >
                  {accion === 'dias' ? 'Guardar días de juego' : 'Generar calendario'}
                </Button>
              )}
            </div>
          </div>
        </form>
      )}
    </dialog>
  )
}
