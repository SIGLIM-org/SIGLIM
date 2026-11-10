import { useEffect, useState, type KeyboardEvent } from 'react'
import { CircleCheck, Info, Megaphone, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useComunicado, useComunicados } from '@/features/publicacion/api'
import { BotonComunicado } from '@/features/publicacion/BotonComunicado'
import { DetalleComunicado } from '@/features/publicacion/DetalleComunicado'
import { EditorComunicado } from '@/features/publicacion/EditorComunicado'
import { filtrosComunicados } from '@/features/publicacion/etiquetas'
import type { Comunicado, FiltroEstado } from '@/features/publicacion/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'
import { esSinPermiso, reintentarConsulta } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

const TAMANO = 20
const REDACTAR = 'publicacion.comunicado:REDACTAR'
const APROBAR = 'publicacion.comunicado:APROBAR'
const formatoEntero = new Intl.NumberFormat('es-MX')

/**
 * Página de la ruta de comunicados (UI-031, B-27.2). `RutaConPermiso` solo
 * mira el módulo, así que aquí se exige redactar o aprobar antes de montar la
 * bandeja y pedir sus datos.
 */
export function PantallaComunicados() {
  return (
    <ConPermiso
      permiso={[REDACTAR, APROBAR]}
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <BandejaComunicados />
    </ConPermiso>
  )
}

/**
 * Bandeja de comunicados con su flujo editorial: la Secretaría redacta y envía
 * a revisión; la Presidencia aprueba y publica o devuelve con observaciones
 * (RF-090, CU-027). La selección se mantiene mientras el comunicado siga
 * existiendo y el filtro no cambie.
 */
function BandejaComunicados() {
  const { yo } = useSesionActual()
  const puedeRedactar = tienePermiso(yo.permisos, REDACTAR)
  const puedeAprobar = tienePermiso(yo.permisos, APROBAR)
  const [filtro, setFiltro] = useState<FiltroEstado>(() =>
    puedeAprobar && !puedeRedactar ? 'REVISION' : 'TODOS',
  )
  const [pagina, setPagina] = useState(0)
  const [seleccionadoId, setSeleccionadoId] = useState<string | null>(null)
  const [creando, setCreando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const estadoApi = filtro === 'TODOS' ? undefined : filtro
  const lista = useComunicados(pagina, TAMANO, estadoApi)
  const detalle = useComunicado(creando ? undefined : (seleccionadoId ?? undefined))

  useEffect(() => {
    if (creando || seleccionadoId) {
      return
    }

    const primero = lista.data?.contenido[0]

    if (primero) {
      setSeleccionadoId(primero.id)
    }
  }, [creando, lista.data, seleccionadoId])

  if (esSinPermiso(lista.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const datos = lista.data
  const cantidad = datos?.totalElementos ?? 0
  const nombreFiltro =
    filtrosComunicados.find((opcion) => opcion.valor === filtro)?.texto ?? 'Todos'

  const cambiarFiltro = (nuevo: FiltroEstado) => {
    setFiltro(nuevo)
    setPagina(0)
    setSeleccionadoId(null)
    setMensaje(null)
  }

  const cambiarPagina = (nueva: number) => {
    setPagina(nueva)
    setSeleccionadoId(null)
  }

  const navegarFiltros = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = filtrosComunicados.findIndex((opcion) => opcion.valor === filtro)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente =
      filtrosComunicados[(actual + paso + filtrosComunicados.length) % filtrosComunicados.length]
        .valor

    cambiarFiltro(siguiente)
    document.getElementById(`filtro-${siguiente}`)?.focus()
  }

  const abrirNuevo = () => {
    setMensaje(null)
    setCreando(true)
    setSeleccionadoId(null)
  }

  const cancelarNuevo = () => {
    setCreando(false)
  }

  const alCrear = (comunicado: Comunicado) => {
    setCreando(false)
    setSeleccionadoId(comunicado.id)
    setFiltro('BORRADOR')
    setPagina(0)
    setMensaje(`«${comunicado.titulo}» se guardó como borrador.`)
  }

  const alActualizar = (comunicado: Comunicado) => {
    setSeleccionadoId(comunicado.id)
    setMensaje(`«${comunicado.titulo}» se actualizó.`)
  }

  const alEnviar = (comunicado: Comunicado) => {
    setSeleccionadoId(comunicado.id)
    setFiltro('REVISION')
    setPagina(0)
    setMensaje(`«${comunicado.titulo}» se envió a revisión de la Presidencia.`)
  }

  const alResolver = (comunicado: Comunicado) => {
    const publicado = comunicado.estadoPublicacion === 'PUBLICADO'

    setSeleccionadoId(comunicado.id)
    setFiltro(publicado ? 'PUBLICADO' : 'BORRADOR')
    setPagina(0)
    setMensaje(
      publicado
        ? `«${comunicado.titulo}» se aprobó y publicó.`
        : `«${comunicado.titulo}» se devolvió a borrador con observaciones.`,
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
            Comunicados
          </h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Redacción, aprobación y publicación de los comunicados oficiales de la Liga.
          </p>
        </div>
        <ConPermiso permiso={REDACTAR}>
          <Button icono={Plus} onClick={abrirNuevo} className="shrink-0">
            Redactar comunicado
          </Button>
        </ConPermiso>
      </header>

      <section
        aria-label="Flujo de publicación"
        className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4"
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-info/12 text-info-strong">
          <Info aria-hidden="true" className="size-5" />
        </span>
        <div>
          <h2 className="text-headline-sm text-text-main">Borrador → Revisión → Publicado</h2>
          <p className="mt-0.5 max-w-3xl text-body-md text-text-muted">
            La Secretaría redacta y envía a revisión; la Presidencia aprueba y publica o devuelve
            con observaciones. Quien redacta no puede aprobar su propio comunicado.
          </p>
        </div>
      </section>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      {creando ? (
        <EditorComunicado
          onCreado={alCrear}
          onActualizado={alActualizar}
          onEnviado={alEnviar}
          onCancelar={cancelarNuevo}
        />
      ) : (
        <div className="grid gap-4 lg:grid-cols-[22rem_minmax(0,1fr)] lg:items-start">
          <section
            aria-label="Bandeja de comunicados"
            className="flex min-w-0 flex-col gap-3 rounded-lg border border-border bg-surface p-4"
          >
            <div
              role="tablist"
              aria-label="Filtrar comunicados por estado"
              onKeyDown={navegarFiltros}
              className="flex flex-wrap gap-1 rounded-md bg-background p-1"
            >
              {filtrosComunicados.map((opcion) => (
                <button
                  key={opcion.valor}
                  type="button"
                  role="tab"
                  id={`filtro-${opcion.valor}`}
                  aria-selected={filtro === opcion.valor}
                  aria-controls="bandeja-comunicados"
                  tabIndex={filtro === opcion.valor ? 0 : -1}
                  onClick={() => cambiarFiltro(opcion.valor)}
                  className={[
                    'min-h-11 rounded-md px-3 text-label-md transition-colors',
                    filtro === opcion.valor
                      ? 'bg-surface text-text-main shadow-sm'
                      : 'text-text-muted hover:text-text-main',
                  ].join(' ')}
                >
                  {opcion.texto}
                </button>
              ))}
            </div>

            <div
              role="tabpanel"
              id="bandeja-comunicados"
              aria-labelledby={`filtro-${filtro}`}
              className="flex flex-col gap-3"
            >
              {datos && !lista.isError && (
                <p aria-live="polite" className="text-label-md text-text-main">
                  {cantidad === 1
                    ? '1 comunicado'
                    : `${formatoEntero.format(cantidad)} comunicados`}
                  {filtro === 'TODOS' ? '' : ` · ${nombreFiltro}`}
                </p>
              )}

              {lista.isPending && (
                <EstadoCarga etiqueta="Cargando comunicados…" filas={5} columnas={2} />
              )}

              {lista.isError && !esSinPermiso(lista.error) && (
                <ErrorConsulta
                  error={lista.error}
                  onReintentar={
                    reintentarConsulta(0, lista.error) ? () => void lista.refetch() : undefined
                  }
                />
              )}

              {!lista.isError && datos?.totalElementos === 0 && (
                <EstadoVacio
                  icono={Megaphone}
                  titulo={
                    filtro === 'TODOS'
                      ? 'Aún no hay comunicados'
                      : 'No hay comunicados con este filtro'
                  }
                  descripcion={
                    filtro === 'TODOS'
                      ? 'Redacta el primer comunicado para publicarlo cuando la Presidencia lo apruebe.'
                      : 'Prueba otro filtro o redacta un comunicado nuevo.'
                  }
                  accion={
                    <div className="flex flex-col items-center gap-2 sm:flex-row">
                      {filtro !== 'TODOS' && (
                        <Button variante="secundario" onClick={() => cambiarFiltro('TODOS')}>
                          Ver todos
                        </Button>
                      )}
                      <ConPermiso permiso={REDACTAR}>
                        <Button icono={Plus} onClick={abrirNuevo}>
                          Redactar comunicado
                        </Button>
                      </ConPermiso>
                    </div>
                  }
                />
              )}

              {!lista.isError && datos && datos.contenido.length === 0 && cantidad > 0 && (
                <EstadoVacio
                  titulo="Esta página ya no tiene comunicados"
                  accion={
                    <Button variante="secundario" onClick={() => cambiarPagina(0)}>
                      Ir a la primera página
                    </Button>
                  }
                />
              )}

              {!lista.isError && datos && datos.contenido.length > 0 && (
                <ul className="flex flex-col gap-2">
                  {datos.contenido.map((comunicado) => (
                    <li key={comunicado.id}>
                      <BotonComunicado
                        comunicado={comunicado}
                        seleccionado={comunicado.id === seleccionadoId}
                        yo={yo}
                        onSeleccionar={(id) => {
                          setSeleccionadoId(id)
                          setMensaje(null)
                        }}
                      />
                    </li>
                  ))}
                </ul>
              )}

              {!lista.isError && datos && datos.totalPaginas > 1 && (
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
                  <span className="order-first w-full text-center text-body-sm text-text-muted sm:order-none sm:w-auto">
                    Página {datos.pagina + 1} de {datos.totalPaginas}
                  </span>
                  <Button
                    variante="secundario"
                    disabled={datos.pagina === 0}
                    onClick={() => cambiarPagina(datos.pagina - 1)}
                  >
                    Anterior
                  </Button>
                  <Button
                    variante="secundario"
                    disabled={datos.pagina + 1 >= datos.totalPaginas}
                    onClick={() => cambiarPagina(datos.pagina + 1)}
                  >
                    Siguiente
                  </Button>
                </div>
              )}
            </div>
          </section>

          <section aria-label="Detalle del comunicado" className="min-w-0">
            {!seleccionadoId && !creando && datos && datos.totalElementos > 0 && (
              <EstadoVacio
                icono={Megaphone}
                titulo="Elige un comunicado"
                descripcion="Selecciona un comunicado de la bandeja para leerlo, editarlo o dictaminarlo."
              />
            )}

            {seleccionadoId && detalle.isPending && (
              <EstadoCarga etiqueta="Cargando el comunicado…" filas={6} columnas={2} />
            )}

            {seleccionadoId && detalle.isError && (
              <ErrorConsulta
                error={detalle.error}
                onReintentar={
                  reintentarConsulta(0, detalle.error) ? () => void detalle.refetch() : undefined
                }
                accion={
                  <Button variante="secundario" onClick={() => setSeleccionadoId(null)}>
                    Volver a la bandeja
                  </Button>
                }
              />
            )}

            {seleccionadoId && detalle.data && (
              <DetalleComunicado
                comunicado={detalle.data}
                onActualizado={alActualizar}
                onEnviado={alEnviar}
                onResuelto={alResolver}
              />
            )}
          </section>
        </div>
      )}
    </div>
  )
}
