import { useId, useMemo, useState } from 'react'
import {
  CalendarDays,
  CalendarOff,
  CalendarPlus,
  CircleAlert,
  CircleCheck,
  CornerUpLeft,
  Info,
  Lock,
  Plus,
  TriangleAlert,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { ListaPartidos } from '@/features/programacion/ListaPartidos'
import { ModalEnvioJornada } from '@/features/programacion/ModalEnvioJornada'
import { ModalNuevaJornada } from '@/features/programacion/ModalNuevaJornada'
import { ModalPartido } from '@/features/programacion/ModalPartido'
import { ResumenVerificacion } from '@/features/programacion/ResumenVerificacion'
import { TarjetaCalendario } from '@/features/programacion/calendario/TarjetaCalendario'
import {
  useAceptarLocalia,
  useJornada,
  useJornadas,
  useTemporadaActiva,
  useVerificacion,
} from '@/features/programacion/api'
import {
  etiquetaEstadoJornada,
  varianteEstadoJornada,
} from '@/features/programacion/etiquetas'
import { rangoDePartidos } from '@/features/programacion/fechas'
import { usePermisosProgramacion } from '@/features/programacion/permisos'
import type {
  AvisoVerificacion,
  GrupoProgramacion,
  Partido,
} from '@/features/programacion/tipos'
import { formatearFechaHora } from '@/features/publicacion/fechas'
import { useCategoriasTemporada } from '@/features/temporadas/api'
import { ApiError } from '@/lib/api/ApiError'

/** Agrupa los avisos de la verificación por partido; la fila solo muestra los suyos. */
function agruparPorPartido(
  avisos: readonly AvisoVerificacion[],
): Map<string, AvisoVerificacion[]> {
  const porPartido = new Map<string, AvisoVerificacion[]>()

  for (const aviso of avisos) {
    const delPartido = porPartido.get(aviso.partidoId)

    if (delPartido) {
      delPartido.push(aviso)
    } else {
      porPartido.set(aviso.partidoId, [aviso])
    }
  }

  return porPartido
}

/**
 * Programación manual de una jornada (F-22, H-12.4). El Programador elige el grupo y la jornada,
 * registra o ajusta partidos con motivo y ve en línea los conflictos que reporta
 * `POST /jornadas/{id}/verificacion`. El envío a aprobación (H-13.2, RF-121) queda deshabilitado
 * mientras haya conflictos y pide confirmación; si la Presidencia devuelve la jornada, sus
 * observaciones se muestran arriba de los partidos. Las advertencias de localía se aceptan con
 * motivo desde la fila y el motivo queda en el detalle (B-15.2, RF-120).
 */
export function ProgramacionJornadas() {
  const permisos = usePermisosProgramacion()
  const id = useId()
  const temporadas = useTemporadaActiva({ habilitada: permisos.editar })
  const [grupoElegido, setGrupoElegido] = useState<string | null>(null)
  const [jornadaElegida, setJornadaElegida] = useState<string | null>(null)
  const [creandoJornada, setCreandoJornada] = useState(false)
  const [creandoPartido, setCreandoPartido] = useState(false)
  const [partidoEnEdicion, setPartidoEnEdicion] = useState<Partido | null>(null)
  const [partidoAceptando, setPartidoAceptando] = useState<Partido | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [mensaje, setMensaje] = useState<{
    texto: string
    tono: 'exito' | 'informacion'
  } | null>(null)

  const temporada = temporadas.data ?? null
  const categorias = useCategoriasTemporada(temporada?.id ?? '', {
    habilitada: Boolean(temporada) && permisos.editar,
  })

  const grupos = useMemo<GrupoProgramacion[]>(
    () =>
      (categorias.data ?? []).flatMap(({ categoria, grupos: gruposDeCategoria }) =>
        gruposDeCategoria.map((grupo) => ({
          id: grupo.id,
          nombre: grupo.nombre,
          categoria: categoria.nombre,
          equipos: grupo.equipos,
        })),
      ),
    [categorias.data],
  )

  const grupoId =
    grupoElegido && grupos.some(({ id: grupo }) => grupo === grupoElegido)
      ? grupoElegido
      : grupos[0]?.id
  const grupo = grupos.find(({ id: grupo }) => grupo === grupoId)

  const jornadas = useJornadas(grupoId, { habilitada: permisos.editar })
  const listaJornadas = jornadas.data?.contenido ?? []
  const jornadaId =
    jornadaElegida && listaJornadas.some(({ id: jornada }) => jornada === jornadaElegida)
      ? jornadaElegida
      : listaJornadas[listaJornadas.length - 1]?.id

  const detalle = useJornada(jornadaId)
  const jornada = detalle.data
  const partidos = jornada?.partidos ?? []

  const verificacion = useVerificacion(jornada?.id, {
    habilitada: permisos.editar && Boolean(jornada),
  })
  const aceptarLocalia = useAceptarLocalia()
  const conflictos = verificacion.data?.conflictos ?? []
  const advertencias = verificacion.data?.advertencias ?? []

  const conflictosPorPartido = agruparPorPartido(conflictos)
  const advertenciasPorPartido = agruparPorPartido(advertencias)

  const puedeEditarJornada = permisos.editar && jornada?.estadoPublicacion === 'BORRADOR'
  const verificacionLista = !verificacion.isPending && !verificacion.isError
  // El backend no envía una jornada sin partidos vigentes (`409 RF-121`, [PROPUESTA]).
  const partidosVigentes = partidos.filter(({ estado }) => estado !== 'CANCELADO').length
  const puedeEnviar =
    permisos.enviar &&
    jornada?.estadoPublicacion === 'BORRADOR' &&
    partidosVigentes > 0 &&
    verificacionLista &&
    conflictos.length === 0
  const numeroSugerido =
    listaJornadas.reduce((mayor, { numero }) => Math.max(mayor, numero), 0) + 1

  const cambiarGrupo = (siguiente: string) => {
    setGrupoElegido(siguiente)
    setJornadaElegida(null)
    setPartidoAceptando(null)
    setMensaje(null)
  }

  const cambiarJornada = (siguiente: string) => {
    setJornadaElegida(siguiente)
    setPartidoAceptando(null)
    setMensaje(null)
  }

  const alCrearJornada = (nueva: { id: string; numero: number }) => {
    setCreandoJornada(false)
    setJornadaElegida(nueva.id)
    setMensaje({
      texto: `Jornada ${nueva.numero} creada en borrador.`,
      tono: 'exito',
    })
  }

  const alGuardarPartido = () => {
    const eraAjuste = partidoEnEdicion !== null

    setCreandoPartido(false)
    setPartidoEnEdicion(null)
    setMensaje({
      texto: eraAjuste
        ? 'Partido ajustado. El cambio quedó en el historial y la auditoría.'
        : `Partido programado en la jornada ${jornada?.numero ?? ''}.`,
      tono: 'exito',
    })
  }

  const errorAceptacion = aceptarLocalia.isError
    ? aceptarLocalia.error instanceof ApiError
      ? aceptarLocalia.error.problema.detail
      : 'No se pudo aceptar la advertencia de localía. Intenta de nuevo.'
    : null

  const confirmarAceptacion = (motivo: string) => {
    if (!partidoAceptando) {
      return
    }

    aceptarLocalia.mutate(
      { id: partidoAceptando.id, motivoAceptacion: motivo },
      {
        onSuccess: () => {
          setPartidoAceptando(null)
          setMensaje({
            texto:
              'Advertencia de localía aceptada. El motivo quedó en el detalle del partido y en la auditoría.',
            tono: 'exito',
          })
        },
      },
    )
  }

  const alEnviar = () => {
    setMensaje(null)
    setEnviando(true)
  }

  const alEnviarJornada = (enviada: { numero: number }) => {
    setEnviando(false)
    setMensaje({
      texto: `Jornada ${enviada.numero} enviada a aprobación. La Presidencia la revisará antes de publicarla.`,
      tono: 'exito',
    })
  }

  const contextoJornada = grupo ? `${grupo.categoria} · ${grupo.nombre}` : 'el grupo'
  const fueDevuelta =
    jornada?.estadoPublicacion === 'BORRADOR' && Boolean(jornada.observaciones)

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
            Programación de jornadas
          </h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Arma la jornada del grupo, registra cada partido y verifica en línea que no haya
            equipos ni canchas ocupadas. Los ajustes quedan en el historial.
          </p>
        </div>
        {temporada && (
          <div className="shrink-0">
            <Insignia variante="exito" icono={CalendarDays}>
              Temporada activa: {temporada.nombre}
            </Insignia>
          </div>
        )}
      </header>

      {mensaje && (
        <p
          role="status"
          className={[
            'flex items-start gap-2 rounded-lg border p-3 text-body-md',
            mensaje.tono === 'exito'
              ? 'border-success/30 bg-success/8 text-success-strong'
              : 'border-info/30 bg-info/10 text-info-strong',
          ].join(' ')}
        >
          {mensaje.tono === 'exito' ? (
            <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          ) : (
            <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          )}
          {mensaje.texto}
        </p>
      )}

      {!permisos.editar && (
        <EstadoVacio
          icono={Lock}
          titulo="La programación requiere permiso de edición"
          descripcion="Tu cuenta puede consultar jornadas, pero para armarlas necesita el permiso de edición. Pide a la administración de la Liga que revise tu acceso."
        />
      )}

      {permisos.editar && temporadas.isPending && (
        <EstadoCarga etiqueta="Buscando la temporada activa…" filas={3} columnas={2} />
      )}

      {permisos.editar && temporadas.isError && (
        <ErrorConsulta
          error={temporadas.error}
          onReintentar={() => void temporadas.refetch()}
        />
      )}

      {permisos.editar && !temporadas.isPending && !temporadas.isError && !temporada && (
        <EstadoVacio
          icono={CalendarOff}
          titulo="No hay una temporada activa"
          descripcion="La programación de jornadas requiere una temporada activa. Pide a la administración de la Liga que active la temporada en curso."
        />
      )}

      {temporada && permisos.editar && categorias.isPending && (
        <EstadoCarga etiqueta="Cargando categorías y grupos…" filas={3} columnas={2} />
      )}

      {temporada && permisos.editar && categorias.isError && (
        <ErrorConsulta
          error={categorias.error}
          onReintentar={() => void categorias.refetch()}
        />
      )}

      {temporada && permisos.editar && !categorias.isPending && !categorias.isError && grupos.length === 0 && (
        <EstadoVacio
          titulo="La temporada no tiene grupos"
          descripcion="Crea las categorías y los grupos de la temporada para poder programar jornadas."
        />
      )}

      {temporada && permisos.editar && grupos.length > 0 && (
        <>
          <TarjetaCalendario temporada={temporada} categorias={categorias.data ?? []} />

          <section
            aria-label="Selección de la jornada"
            className="rounded-lg border border-border bg-surface p-4"
          >
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)_auto] xl:items-end">
              <CampoSeleccion
                id={`${id}-grupo`}
                etiqueta="Categoría y grupo"
                value={grupoId ?? ''}
                onChange={(evento) => cambiarGrupo(evento.target.value)}
              >
                {grupos.map(({ id: grupo, nombre, categoria, equipos }) => (
                  <option key={grupo} value={grupo}>
                    {categoria} · {nombre} ({equipos} {equipos === 1 ? 'equipo' : 'equipos'})
                  </option>
                ))}
              </CampoSeleccion>

              <CampoSeleccion
                id={`${id}-jornada`}
                etiqueta="Jornada"
                value={jornadaId ?? ''}
                disabled={listaJornadas.length === 0}
                ayuda={
                  jornadas.isPending
                    ? 'Cargando las jornadas del grupo…'
                    : listaJornadas.length === 0
                      ? 'El grupo todavía no tiene jornadas.'
                      : undefined
                }
                onChange={(evento) => cambiarJornada(evento.target.value)}
              >
                {listaJornadas.length === 0 ? (
                  <option value="">
                    {jornadas.isPending ? 'Cargando jornadas…' : 'Sin jornadas'}
                  </option>
                ) : (
                  listaJornadas.map(({ id: jornada, numero, estadoPublicacion }) => (
                    <option key={jornada} value={jornada}>
                      Jornada {numero} · {etiquetaEstadoJornada[estadoPublicacion]}
                    </option>
                  ))
                )}
              </CampoSeleccion>

              <Button
                icono={Plus}
                onClick={() => {
                  setMensaje(null)
                  setCreandoJornada(true)
                }}
                className="w-full sm:col-span-2 xl:col-span-1 xl:w-auto"
              >
                Nueva jornada
              </Button>
            </div>
          </section>

          {jornadas.isError && (
            <ErrorConsulta
              error={jornadas.error}
              onReintentar={() => void jornadas.refetch()}
            />
          )}

          {jornadas.isPending && !jornadas.data && (
            <EstadoCarga etiqueta="Cargando jornadas…" filas={4} columnas={3} />
          )}

          {!jornadas.isPending && !jornadas.isError && listaJornadas.length === 0 && (
            <EstadoVacio
              icono={CalendarPlus}
              titulo="Sin jornadas en este grupo"
              descripcion="Usa «Nueva jornada» para crear la primera y empezar a programar los partidos del grupo."
            />
          )}

          {jornadaId && detalle.isPending && (
            <EstadoCarga etiqueta="Cargando la jornada…" filas={5} columnas={3} />
          )}

          {jornadaId && detalle.isError && (
            <ErrorConsulta
              error={detalle.error}
              onReintentar={() => void detalle.refetch()}
            />
          )}

          {jornada && !detalle.isError && (
            <section aria-labelledby={`${id}-jornada-titulo`} className="flex flex-col gap-4">
              <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2
                      id={`${id}-jornada-titulo`}
                      className="text-headline-md text-text-main"
                    >
                      Jornada {jornada.numero}
                    </h2>
                    <Insignia variante={varianteEstadoJornada[jornada.estadoPublicacion]}>
                      {etiquetaEstadoJornada[jornada.estadoPublicacion]}
                    </Insignia>
                  </div>
                  <p className="mt-1 text-body-md text-text-muted">
                    {grupo ? `${grupo.categoria} · ${grupo.nombre}` : 'Grupo de la jornada'}
                    {partidos.length > 0
                      ? ` · ${partidos.length} ${partidos.length === 1 ? 'partido' : 'partidos'} · ${rangoDePartidos(partidos)}`
                      : ' · Sin partidos programados'}
                  </p>
                </div>

                {puedeEditarJornada && partidos.length > 0 && (
                  <Button
                    icono={CalendarPlus}
                    onClick={() => {
                      setMensaje(null)
                      setCreandoPartido(true)
                    }}
                    className="shrink-0"
                  >
                    Programar partido
                  </Button>
                )}
              </div>

              {jornada.estadoPublicacion !== 'BORRADOR' && (
                <p
                  role="status"
                  className="flex items-start gap-2 rounded-lg border border-info/30 bg-info/10 p-3 text-body-md text-info-strong"
                >
                  <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
                  <span>
                    {jornada.estadoPublicacion === 'REVISION'
                      ? `Esta jornada está en revisión de la Presidencia${jornada.enviadaEn ? ` desde el ${formatearFechaHora(jornada.enviadaEn)}` : ''}. Podrás ajustar partidos si la devuelve a borrador.`
                      : `Esta jornada está publicada${jornada.publicadaEn ? ` desde el ${formatearFechaHora(jornada.publicadaEn)}` : ''} y ya aparece en el portal. Solo se ajustan partidos de una jornada en borrador.`}
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
                    <p className="text-headline-sm text-warning-strong">
                      La Presidencia devolvió esta jornada
                    </p>
                    <p className="mt-1 whitespace-pre-line break-words text-body-md text-text-main">
                      {jornada.observaciones}
                    </p>
                    <p className="mt-1 text-body-sm text-text-muted">
                      Corrige lo señalado y vuelve a enviarla a aprobación.
                    </p>
                  </div>
                </div>
              )}

              {!verificacion.isPending && !verificacion.isError && conflictos.length > 0 && (
                <div
                  role="alert"
                  className="flex items-start gap-3 rounded-lg border border-error/30 bg-error/8 p-4"
                >
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-error text-on-primary">
                    <CircleAlert aria-hidden="true" className="size-5" />
                  </span>
                  <div>
                    <p className="text-headline-sm text-error-strong">
                      {conflictos.length === 1
                        ? '1 conflicto por resolver'
                        : `${conflictos.length} conflictos por resolver`}
                    </p>
                    <p className="mt-1 text-body-md text-text-muted">
                      Ajusta los partidos marcados abajo. Cada fila explica el choque y la jornada
                      no se puede enviar mientras queden conflictos.
                    </p>
                  </div>
                </div>
              )}

              <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_19rem] xl:items-start">
                <div className="min-w-0">
                  <ListaPartidos
                    partidos={partidos}
                    conflictosPorPartido={conflictosPorPartido}
                    advertenciasPorPartido={advertenciasPorPartido}
                    puedeEditar={puedeEditarJornada}
                    onEditar={(partido) => {
                      setMensaje(null)
                      setPartidoEnEdicion(partido)
                    }}
                    onAceptarLocalia={(partido) => {
                      setMensaje(null)
                      aceptarLocalia.reset()
                      setPartidoAceptando(partido)
                    }}
                    onNuevo={() => {
                      setMensaje(null)
                      setCreandoPartido(true)
                    }}
                  />
                </div>

                <ResumenVerificacion
                  estadoJornada={jornada.estadoPublicacion}
                  partidos={partidosVigentes}
                  conflictos={conflictos}
                  advertencias={advertencias}
                  cargando={verificacion.isPending}
                  error={verificacion.error}
                  puedeVerificar={permisos.editar}
                  tienesPermisoEnvio={permisos.enviar}
                  puedeEnviar={puedeEnviar}
                  onVerificar={() => void verificacion.refetch()}
                  onEnviar={alEnviar}
                />
              </div>
            </section>
          )}
        </>
      )}

      {creandoJornada && grupo && (
        <ModalNuevaJornada
          grupo={grupo}
          numeroSugerido={numeroSugerido}
          onCerrar={() => setCreandoJornada(false)}
          onCreada={alCrearJornada}
        />
      )}

      {jornada && enviando && (
        <ModalEnvioJornada
          jornada={jornada}
          contexto={contextoJornada}
          partidos={partidosVigentes}
          onCerrar={() => setEnviando(false)}
          onEnviada={alEnviarJornada}
        />
      )}

      {jornada && (creandoPartido || partidoEnEdicion) && (
        <ModalPartido
          jornada={jornada}
          partido={partidoEnEdicion ?? undefined}
          onCerrar={() => {
            setCreandoPartido(false)
            setPartidoEnEdicion(null)
          }}
          onGuardado={alGuardarPartido}
        />
      )}

      {partidoAceptando && (
        <ModalMotivo
          abierto
          titulo="Aceptar advertencia de localía"
          descripcion="El motivo quedará en el detalle del partido y en la auditoría de la jornada. La advertencia no impide enviar la jornada a aprobación."
          aviso={
            <div className="flex flex-col gap-2">
              <div className="rounded-md border border-warning/30 bg-warning/8 p-3">
                <p className="flex items-center gap-2 text-label-md font-semibold text-warning-strong">
                  <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
                  {partidoAceptando.equipoLocal.nombre} vs.{' '}
                  {partidoAceptando.equipoVisitante.nombre}
                </p>
                <ul className="mt-1.5 flex flex-col gap-1">
                  {(advertenciasPorPartido.get(partidoAceptando.id) ?? []).map(
                    (advertencia, indice) => (
                      <li
                        key={`${advertencia.codigo}-${indice}`}
                        className="break-words text-body-md text-text-main"
                      >
                        {advertencia.mensaje}
                      </li>
                    ),
                  )}
                </ul>
              </div>
              {errorAceptacion && (
                <p
                  role="alert"
                  className="rounded-md border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
                >
                  {errorAceptacion}
                </p>
              )}
            </div>
          }
          etiquetaConfirmar="Aceptar advertencia"
          procesando={aceptarLocalia.isPending}
          onConfirmar={confirmarAceptacion}
          onCancelar={() => {
            aceptarLocalia.reset()
            setPartidoAceptando(null)
          }}
        />
      )}
    </div>
  )
}
