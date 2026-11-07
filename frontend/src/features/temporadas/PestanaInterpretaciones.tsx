import { useState, type KeyboardEvent } from 'react'
import { CircleCheck, Info, Plus, Scale } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useInterpretaciones } from '@/features/temporadas/api'
import { filtrosInterpretaciones } from '@/features/temporadas/etiquetas'
import type {
  FiltroEstadoInterpretacion,
  InterpretacionRegla,
  Temporada,
} from '@/features/temporadas/tipos'
import { ModalRegistrarInterpretacion } from '@/features/temporadas/ModalRegistrarInterpretacion'
import { ModalResolverInterpretacion } from '@/features/temporadas/ModalResolverInterpretacion'
import { TarjetaInterpretacion } from '@/features/temporadas/TarjetaInterpretacion'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { reintentarConsulta } from '@/lib/api/errores'

type Props = {
  temporada: Temporada
  /** `temporadas.interpretacion:REGISTRAR`; el cierre de la temporada también lo limita. */
  puedeRegistrar: boolean
  /** `temporadas.interpretacion:APROBAR`; la autoría de cada fila decide la acción (DEC-57). */
  puedeResolver: boolean
}

type Decision = 'AUTORIZADA' | 'RECHAZADA'

type Resolucion = {
  interpretacion: InterpretacionRegla
  decision: Decision
}

const TAMANO = 10
const formatoEntero = new Intl.NumberFormat('es-MX')

/**
 * Pestaña Interpretaciones de F-11 (B-03.2, RF-007, RN-006): cuando una regla de la temporada no es
 * explícita, se registra una interpretación que nace PENDIENTE y la Presidencia autoriza o rechaza.
 * No es una regla permanente ni modifica los parámetros versionados (RF-006).
 *
 * <p>La temporada cerrada no admite altas, pero sus pendientes se pueden resolver (DEC-59). La
 * separación de funciones de DEC-57 se refleja en la interfaz: una fila registrada por la sesión
 * actual no ofrece los botones de resolución.
 */
export function PestanaInterpretaciones({ temporada, puedeRegistrar, puedeResolver }: Props) {
  const { yo } = useSesionActual()
  const cerrada = temporada.estado === 'CERRADA'
  const [filtro, setFiltro] = useState<FiltroEstadoInterpretacion>(() =>
    puedeResolver && !puedeRegistrar ? 'PENDIENTE' : 'TODOS',
  )
  const [pagina, setPagina] = useState(0)
  const [creando, setCreando] = useState(false)
  const [resolucion, setResolucion] = useState<Resolucion | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const estadoApi = filtro === 'TODOS' ? undefined : filtro
  const consulta = useInterpretaciones(temporada.id, pagina, TAMANO, estadoApi)
  const datos = consulta.data
  const cantidad = datos?.totalElementos ?? 0
  const nombreFiltro =
    filtrosInterpretaciones.find((opcion) => opcion.valor === filtro)?.texto ?? 'Todas'
  const registroHabilitado = puedeRegistrar && !cerrada

  const cambiarFiltro = (nuevo: FiltroEstadoInterpretacion) => {
    setFiltro(nuevo)
    setPagina(0)
    setMensaje(null)
  }

  const cambiarPagina = (nueva: number) => {
    setPagina(nueva)
    setMensaje(null)
  }

  const navegarFiltros = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = filtrosInterpretaciones.findIndex((opcion) => opcion.valor === filtro)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente =
      filtrosInterpretaciones[
        (actual + paso + filtrosInterpretaciones.length) % filtrosInterpretaciones.length
      ].valor

    cambiarFiltro(siguiente)
    document.getElementById(`filtro-interpretacion-${siguiente}`)?.focus()
  }

  const abrirRegistro = () => {
    setMensaje(null)
    setCreando(true)
  }

  const alRegistrar = () => {
    setCreando(false)
    // La nueva interpretación siempre nace pendiente: se muestra en «Todas» aunque el filtro
    // anterior fuera otro, para no dejarla fuera de la vista.
    setFiltro('TODOS')
    setPagina(0)
    setMensaje('La interpretación quedó registrada y pendiente de autorización.')
  }

  const alResolver = (resuelta: InterpretacionRegla) => {
    setResolucion(null)
    setPagina(0)
    setMensaje(
      resuelta.estado === 'AUTORIZADA'
        ? 'La interpretación quedó autorizada.'
        : 'La interpretación quedó rechazada y las observaciones se guardaron.',
    )
  }

  return (
    <section aria-labelledby="titulo-interpretaciones" className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 id="titulo-interpretaciones" className="text-headline-md text-text-main">
            Interpretaciones de regla
          </h2>
          <p className="mt-1 max-w-3xl text-body-md text-text-muted">
            Cuando una regla de la temporada no es explícita, se registra la interpretación que
            propone la Liga y la Presidencia la autoriza o rechaza. Una interpretación no es una
            regla permanente ni modifica los parámetros versionados (RF-006, RF-007).
          </p>
        </div>
        {registroHabilitado && (
          <Button icono={Plus} className="shrink-0" onClick={abrirRegistro}>
            Registrar interpretación
          </Button>
        )}
      </div>

      <section
        aria-label="Cómo funciona la interpretación"
        className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4"
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-info/12 text-info-strong">
          <Info aria-hidden="true" className="size-5" />
        </span>
        <div>
          <h3 className="text-headline-sm text-text-main">
            Pendiente → Autorizada o rechazada
          </h3>
          <p className="mt-0.5 max-w-3xl text-body-md text-text-muted">
            La resolución es definitiva y queda en la auditoría. Quien registra la interpretación no
            puede autorizarla ni rechazarla, aunque tenga el permiso de aprobar (DEC-57).
          </p>
        </div>
      </section>

      {cerrada && (
        <p className="flex items-start gap-2 rounded-lg border border-border bg-surface p-3 text-body-md text-text-main">
          <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-info-strong" />
          La temporada está cerrada: no admite interpretaciones nuevas. Las pendientes aún se pueden
          resolver (DEC-59).
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

      <div
        role="tablist"
        aria-label="Filtrar interpretaciones por estado"
        onKeyDown={navegarFiltros}
        className="flex flex-wrap gap-1 rounded-md bg-background p-1"
      >
        {filtrosInterpretaciones.map((opcion) => (
          <button
            key={opcion.valor}
            type="button"
            role="tab"
            id={`filtro-interpretacion-${opcion.valor}`}
            aria-selected={filtro === opcion.valor}
            aria-controls="bandeja-interpretaciones"
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
        id="bandeja-interpretaciones"
        aria-labelledby={`filtro-interpretacion-${filtro}`}
        className="flex flex-col gap-3"
      >
        {datos && !consulta.isError && (
          <p aria-live="polite" className="text-label-md text-text-main">
            {cantidad === 1
              ? '1 interpretación'
              : `${formatoEntero.format(cantidad)} interpretaciones`}
            {filtro === 'TODOS' ? '' : ` · ${nombreFiltro}`}
          </p>
        )}

        {consulta.isPending && (
          <EstadoCarga etiqueta="Cargando interpretaciones…" filas={4} columnas={3} />
        )}

        {consulta.isError && (
          <ErrorConsulta
            error={consulta.error}
            onReintentar={
              reintentarConsulta(0, consulta.error) ? () => void consulta.refetch() : undefined
            }
          />
        )}

        {!consulta.isError && datos?.totalElementos === 0 && (
          <EstadoVacio
            icono={Scale}
            titulo={
              filtro === 'TODOS'
                ? 'Aún no hay interpretaciones'
                : 'No hay interpretaciones con este filtro'
            }
            descripcion={
              filtro === 'TODOS'
                ? cerrada
                  ? 'La temporada está cerrada y no tiene interpretaciones registradas.'
                  : registroHabilitado
                    ? 'Registra la primera interpretación con el botón de arriba para que la Presidencia la autorice o rechace.'
                    : 'Cuando la Liga registre una interpretación pendiente aparecerá aquí.'
                : registroHabilitado
                  ? 'Prueba otro filtro o registra una interpretación nueva.'
                  : 'Prueba otro filtro.'
            }
            accion={
              filtro !== 'TODOS' ? (
                <Button variante="secundario" onClick={() => cambiarFiltro('TODOS')}>
                  Ver todas
                </Button>
              ) : undefined
            }
          />
        )}

        {!consulta.isError && datos && datos.contenido.length === 0 && cantidad > 0 && (
          <EstadoVacio
            titulo="Esta página ya no tiene interpretaciones"
            accion={
              <Button variante="secundario" onClick={() => cambiarPagina(0)}>
                Ir a la primera página
              </Button>
            }
          />
        )}

        {!consulta.isError && datos && datos.contenido.length > 0 && (
          <ul className="flex flex-col gap-3">
            {datos.contenido.map((interpretacion) => (
              <li key={interpretacion.id}>
                <TarjetaInterpretacion
                  interpretacion={interpretacion}
                  yo={yo}
                  puedeResolver={puedeResolver}
                  onAutorizar={() => {
                    setMensaje(null)
                    setResolucion({ interpretacion, decision: 'AUTORIZADA' })
                  }}
                  onRechazar={() => {
                    setMensaje(null)
                    setResolucion({ interpretacion, decision: 'RECHAZADA' })
                  }}
                />
              </li>
            ))}
          </ul>
        )}

        {!consulta.isError && datos && datos.totalPaginas > 1 && (
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

      {creando && (
        <ModalRegistrarInterpretacion
          temporadaId={temporada.id}
          onCerrar={() => setCreando(false)}
          onRegistrada={alRegistrar}
        />
      )}

      {resolucion && (
        <ModalResolverInterpretacion
          key={`${resolucion.interpretacion.id}-${resolucion.decision}`}
          interpretacion={resolucion.interpretacion}
          decision={resolucion.decision}
          onCerrar={() => setResolucion(null)}
          onResuelta={alResolver}
        />
      )}
    </section>
  )
}
