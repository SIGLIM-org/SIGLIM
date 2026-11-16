import { useId, type KeyboardEvent } from 'react'
import { ClipboardList, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { FilaOperacion } from '@/features/organizacion/operaciones/FilaOperacion'
import { filtrosOperaciones } from '@/features/organizacion/operaciones/etiquetas'
import type {
  FiltroEstadoOperacionEquipo,
  OperacionEquipo,
} from '@/features/organizacion/operaciones/tipos'
import type { Pagina } from '@/lib/api/tipos'
import { esSinPermiso } from '@/lib/api/errores'

const formatoEntero = new Intl.NumberFormat('es-MX')

type Props = {
  filtro: FiltroEstadoOperacionEquipo
  pagina: number
  datos: Pagina<OperacionEquipo> | undefined
  cargando: boolean
  error: unknown
  conAcciones: boolean
  onFiltro: (filtro: FiltroEstadoOperacionEquipo) => void
  onPagina: (pagina: number) => void
  onAutorizar: (operacion: OperacionEquipo) => void
  onRechazar: (operacion: OperacionEquipo) => void
  onReintentar: () => void
  onNueva?: () => void
}

const COLUMNAS = ['Operación', 'Clubes', 'Temporada', 'Solicitud', 'Estado', 'Acciones']

/**
 * Bandeja de operaciones de equipo (B-09, UI-034). Muestra el estado de cada venta o conservación
 * con su insignia y, en la vista Presidencia, los botones del dictamen. En móvil cada fila se lee
 * como una tarjeta con etiquetas.
 */
export function BandejaOperaciones({
  filtro,
  pagina,
  datos,
  cargando,
  error,
  conAcciones,
  onFiltro,
  onPagina,
  onAutorizar,
  onRechazar,
  onReintentar,
  onNueva,
}: Props) {
  const id = useId()

  if (esSinPermiso(error)) {
    return null
  }

  const cantidad = datos?.totalElementos ?? 0
  const columnas = conAcciones ? COLUMNAS : COLUMNAS.filter((columna) => columna !== 'Acciones')
  const totalPaginas = datos?.totalPaginas ?? 0
  const textoFiltro = filtrosOperaciones.find(({ valor }) => valor === filtro)?.texto ?? 'Todas'

  const navegarFiltros = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = filtrosOperaciones.findIndex(({ valor }) => valor === filtro)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente =
      filtrosOperaciones[(actual + paso + filtrosOperaciones.length) % filtrosOperaciones.length]
        .valor

    onFiltro(siguiente)
    document.getElementById(`${id}-filtro-${siguiente}`)?.focus()
  }

  return (
    <section aria-label="Bandeja de operaciones de equipo" className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-headline-sm text-text-main">Operaciones</h2>
          <p className="text-body-sm text-text-muted">
            {cantidad === 1
              ? '1 operación en este filtro'
              : `${formatoEntero.format(cantidad)} operaciones en este filtro`}
          </p>
        </div>
        <div
          role="tablist"
          aria-label="Filtrar operaciones por estado"
          onKeyDown={navegarFiltros}
          className="flex flex-wrap gap-1 rounded-md bg-background p-1"
        >
          {filtrosOperaciones.map(({ valor, texto }) => (
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
          <EstadoCarga etiqueta="Cargando operaciones…" filas={4} columnas={2} />
        )}

        {error != null && !esSinPermiso(error) && (
          <ErrorConsulta error={error} onReintentar={onReintentar} />
        )}

        {!cargando && !error && datos && datos.contenido.length === 0 && (
          <EstadoVacio
            icono={ClipboardList}
            titulo={
              filtro === 'TODOS'
                ? 'Aún no hay operaciones registradas'
                : `No hay operaciones en «${textoFiltro.toLowerCase()}»`
            }
            descripcion={
              filtro === 'TODOS'
                ? 'Cuando la Directiva registre una venta o una conservación de plaza, aparecerá aquí.'
                : 'Cambia el filtro para ver las operaciones en otro estado.'
            }
            accion={
              filtro !== 'TODOS' ? (
                <Button variante="secundario" onClick={() => onFiltro('TODOS')}>
                  Ver todas
                </Button>
              ) : onNueva ? (
                <Button icono={Plus} onClick={onNueva}>
                  Registrar operación
                </Button>
              ) : undefined
            }
          />
        )}

        {!cargando && !error && datos && datos.contenido.length > 0 && (
          <>
            <div className="overflow-hidden rounded-lg border border-border bg-surface">
              <table className="w-full border-collapse text-left">
                <caption className="sr-only">
                  Operaciones de venta y conservación de plaza, {textoFiltro.toLowerCase()}
                </caption>
                <thead className="hidden lg:table-header-group">
                  <tr className="border-b-2 border-border bg-background">
                    {columnas.map((columna, indice) => (
                      <th
                        key={columna}
                        scope="col"
                        className={[
                          'px-3 py-2 text-label-sm text-text-muted uppercase',
                          indice === columnas.length - 1 ? 'text-right' : '',
                        ].join(' ')}
                      >
                        {columna}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="block lg:table-row-group">
                  {datos.contenido.map((operacion) => (
                    <FilaOperacion
                      key={operacion.id}
                      operacion={operacion}
                      conAcciones={conAcciones}
                      onAutorizar={onAutorizar}
                      onRechazar={onRechazar}
                    />
                  ))}
                </tbody>
              </table>
            </div>

            {totalPaginas > 1 && (
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="order-first w-full text-center text-body-sm text-text-muted">
                  Página {pagina + 1} de {totalPaginas}
                </span>
                <Button variante="secundario" disabled={pagina === 0} onClick={() => onPagina(pagina - 1)}>
                  Anterior
                </Button>
                <Button
                  variante="secundario"
                  disabled={pagina + 1 >= totalPaginas}
                  onClick={() => onPagina(pagina + 1)}
                >
                  Siguiente
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  )
}
