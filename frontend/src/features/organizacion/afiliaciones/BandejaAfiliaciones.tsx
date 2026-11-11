import { useId, type KeyboardEvent } from 'react'
import { ChevronLeft, ChevronRight, FileText } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { FilaAfiliacion } from '@/features/organizacion/afiliaciones/FilaAfiliacion'
import { filtrosAfiliaciones } from '@/features/organizacion/afiliaciones/etiquetas'
import type {
  Afiliacion,
  FiltroEstadoAfiliacion,
  TemporadaResumenAfiliacion,
} from '@/features/organizacion/afiliaciones/tipos'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import type { Pagina } from '@/lib/api/tipos'
import { esSinPermiso } from '@/lib/api/errores'

const formatoEntero = new Intl.NumberFormat('es-MX')

type Props = {
  temporadas: readonly TemporadaResumenAfiliacion[]
  temporadasCargando: boolean
  temporadaId: string
  filtro: FiltroEstadoAfiliacion
  pagina: number
  datos: Pagina<Afiliacion> | undefined
  cargando: boolean
  error: unknown
  seleccionadaId: string | undefined
  puedeCapturar: boolean
  onTemporada: (temporadaId: string) => void
  onFiltro: (filtro: FiltroEstadoAfiliacion) => void
  onPagina: (pagina: number) => void
  onSeleccionar: (afiliacion: Afiliacion) => void
  onNueva: () => void
  onReintentar: () => void
}

/**
 * Bandeja de tarjetones (F-16). Filtra por temporada y estado, mantiene la selección visible y
 * ofrece el alta solo a quien puede capturar. En móvil es la vista principal hasta elegir una fila.
 */
export function BandejaAfiliaciones({
  temporadas,
  temporadasCargando,
  temporadaId,
  filtro,
  pagina,
  datos,
  cargando,
  error,
  seleccionadaId,
  puedeCapturar,
  onTemporada,
  onFiltro,
  onPagina,
  onSeleccionar,
  onNueva,
  onReintentar,
}: Props) {
  const id = useId()

  if (esSinPermiso(error)) {
    return null
  }

  const cantidad = datos?.totalElementos ?? 0
  const filtroActual = filtrosAfiliaciones.find(({ valor }) => valor === filtro)?.texto ?? 'Todas'
  const totalPaginas = datos?.totalPaginas ?? 0

  const navegarFiltros = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = filtrosAfiliaciones.findIndex(({ valor }) => valor === filtro)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente =
      filtrosAfiliaciones[(actual + paso + filtrosAfiliaciones.length) % filtrosAfiliaciones.length]
        .valor

    onFiltro(siguiente)
    document.getElementById(`${id}-filtro-${siguiente}`)?.focus()
  }

  return (
    <section
      aria-label="Bandeja de afiliaciones"
      className="flex min-w-0 flex-col gap-4 rounded-lg border border-border bg-surface p-4"
    >
      <div className="flex flex-col gap-3">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-headline-sm text-text-main">Tarjetones</h2>
            <p className="text-body-sm text-text-muted">
              {cantidad === 1
                ? '1 tarjetón en este filtro'
                : `${formatoEntero.format(cantidad)} tarjetones en este filtro`}
            </p>
          </div>
        </div>

        <CampoSeleccion
          id={`${id}-temporada`}
          etiqueta="Temporada"
          value={temporadaId}
          disabled={temporadasCargando}
          onChange={(evento) => onTemporada(evento.target.value)}
        >
          <option value="">
            {temporadasCargando ? 'Cargando temporadas…' : 'Todas las temporadas'}
          </option>
          {temporadas.map((temporada) => (
            <option key={temporada.id} value={temporada.id}>
              {temporada.nombre} · {etiquetaEstadoTemporada[temporada.estado].texto}
            </option>
          ))}
        </CampoSeleccion>

        <div
          role="tablist"
          aria-label="Filtrar tarjetones por estado"
          onKeyDown={navegarFiltros}
          className="flex flex-wrap gap-1 rounded-md bg-background p-1"
        >
          {filtrosAfiliaciones.map(({ valor, texto }) => (
            <button
              key={valor}
              type="button"
              role="tab"
              id={`${id}-filtro-${valor}`}
              aria-selected={filtro === valor}
              aria-controls={`${id}-lista`}
              tabIndex={filtro === valor ? 0 : -1}
              onClick={() => onFiltro(valor)}
              className={[
                'min-h-11 rounded-md px-3 text-label-md transition-colors',
                filtro === valor
                  ? 'bg-surface text-text-main shadow-sm'
                  : 'text-text-muted hover:text-text-main',
              ].join(' ')}
            >
              {texto}
            </button>
          ))}
        </div>
      </div>

      <div
        role="tabpanel"
        id={`${id}-lista`}
        aria-labelledby={`${id}-filtro-${filtro}`}
        className="flex min-w-0 flex-col gap-3"
      >
        {cargando && datos === undefined && (
          <EstadoCarga etiqueta="Cargando afiliaciones…" filas={4} columnas={2} />
        )}

        {error != null && !esSinPermiso(error) && (
          <ErrorConsulta error={error} onReintentar={onReintentar} />
        )}

        {!cargando && !error && datos && datos.contenido.length === 0 && (
          <EstadoVacio
            icono={FileText}
            titulo={
              filtro === 'TODOS'
                ? 'Aún no hay afiliaciones en esta temporada'
                : `No hay tarjetones en «${filtroActual.toLowerCase()}»`
            }
            descripcion={
              filtro === 'TODOS'
                ? 'Cuando la Directiva registre el tarjetón, aparecerá aquí con su folio.'
                : 'Cambia el filtro de estado o de temporada para ver otros tarjetones.'
            }
            accion={
              filtro !== 'TODOS' ? (
                <Button variante="secundario" onClick={() => onFiltro('TODOS')}>
                  Ver todos
                </Button>
              ) : puedeCapturar ? (
                <Button onClick={onNueva}>Registrar afiliación</Button>
              ) : undefined
            }
          />
        )}

        {datos && datos.contenido.length > 0 && (
          <ul className="flex flex-col gap-2">
            {datos.contenido.map((afiliacion) => (
              <FilaAfiliacion
                key={afiliacion.id}
                afiliacion={afiliacion}
                seleccionada={afiliacion.id === seleccionadaId}
                onSeleccionar={() => onSeleccionar(afiliacion)}
              />
            ))}
          </ul>
        )}

        {totalPaginas > 1 && (
          <nav aria-label="Páginas de afiliaciones" className="flex items-center justify-between gap-3">
            <Button
              variante="secundario"
              icono={ChevronLeft}
              disabled={pagina === 0 || cargando}
              onClick={() => onPagina(pagina - 1)}
            >
              Anterior
            </Button>
            <span className="text-body-sm text-text-muted">
              Página {pagina + 1} de {totalPaginas}
            </span>
            <Button
              variante="secundario"
              icono={ChevronRight}
              disabled={pagina + 1 >= totalPaginas || cargando}
              onClick={() => onPagina(pagina + 1)}
            >
              Siguiente
            </Button>
          </nav>
        )}
      </div>
    </section>
  )
}
