import { useId, type KeyboardEvent } from 'react'
import { ClipboardList, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { FilaTramite } from '@/features/canchas/tramites/FilaTramite'
import { filtrosDeFormato } from '@/features/canchas/tramites/etiquetas'
import type {
  DecisionSolicitudCancha,
  FiltroEstadoTramite,
  FormatoTramite,
  SolicitudCancha,
} from '@/features/canchas/tramites/tipos'
import { esSinPermiso } from '@/lib/api/errores'
import type { Pagina } from '@/lib/api/tipos'

const formatoEntero = new Intl.NumberFormat('es-MX')

type Props = {
  formato: FormatoTramite
  filtro: FiltroEstadoTramite
  pagina: number
  datos: Pagina<SolicitudCancha> | undefined
  cargando: boolean
  error: unknown
  dictaminar: boolean
  generarOficio: boolean
  onFiltro: (filtro: FiltroEstadoTramite) => void
  onPagina: (pagina: number) => void
  onDecidir: (solicitud: SolicitudCancha, decision: DecisionSolicitudCancha) => void
  onCarta: (solicitud: SolicitudCancha) => void
  onReintentar: () => void
  onNueva?: () => void
}

/**
 * Bandeja de trámites del formato elegido (B-07, UI-028): filtro por estado, insignia del estado y
 * las acciones que el estado y los permisos permiten. En móvil cada fila es una tarjeta.
 */
export function BandejaTramites({
  formato,
  filtro,
  pagina,
  datos,
  cargando,
  error,
  dictaminar,
  generarOficio,
  onFiltro,
  onPagina,
  onDecidir,
  onCarta,
  onReintentar,
  onNueva,
}: Props) {
  const id = useId()
  const filtros = filtrosDeFormato(formato)

  if (esSinPermiso(error)) {
    return null
  }

  const cantidad = datos?.totalElementos ?? 0
  const totalPaginas = datos?.totalPaginas ?? 0
  const textoFiltro = filtros.find(({ valor }) => valor === filtro)?.texto ?? 'Todos'
  const columnas = [
    'Oficio',
    formato === 1 ? 'Club solicitante' : 'Oferente',
    'Cancha',
    'Captura',
    'Estado',
    ...(dictaminar || generarOficio ? ['Acciones'] : []),
  ]

  const navegarFiltros = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = filtros.findIndex(({ valor }) => valor === filtro)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente = filtros[(actual + paso + filtros.length) % filtros.length].valor

    onFiltro(siguiente)
    document.getElementById(`${id}-filtro-${siguiente}`)?.focus()
  }

  return (
    <section aria-label="Bandeja de trámites" className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <p className="text-body-sm text-text-muted">
          {cantidad === 1 ? '1 trámite en este filtro' : `${formatoEntero.format(cantidad)} trámites en este filtro`}
        </p>
        <div
          role="tablist"
          aria-label="Filtrar trámites por estado"
          onKeyDown={navegarFiltros}
          className="flex flex-wrap gap-1 rounded-md bg-background p-1"
        >
          {filtros.map(({ valor, texto }) => (
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
                filtro === valor ? 'bg-surface text-text-main shadow-sm' : 'text-text-muted hover:text-text-main',
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
        {cargando && datos === undefined && <EstadoCarga etiqueta="Cargando trámites…" filas={4} columnas={2} />}

        {error != null && <ErrorConsulta error={error} onReintentar={onReintentar} />}

        {!cargando && !error && datos && datos.contenido.length === 0 && (
          <EstadoVacio
            icono={ClipboardList}
            titulo={
              filtro === 'TODOS'
                ? formato === 1
                  ? 'Aún no hay solicitudes de préstamo o comodato'
                  : 'Aún no hay ofertas de cancha'
                : `No hay trámites en «${textoFiltro.toLowerCase()}»`
            }
            descripcion={
              filtro === 'TODOS'
                ? 'Cuando se capture un trámite de este formato, aparecerá aquí con su número de oficio.'
                : 'Cambia el filtro para ver los trámites en otro estado.'
            }
            accion={
              filtro !== 'TODOS' ? (
                <Button variante="secundario" onClick={() => onFiltro('TODOS')}>
                  Ver todos
                </Button>
              ) : onNueva ? (
                <Button icono={Plus} onClick={onNueva}>
                  Nueva solicitud
                </Button>
              ) : undefined
            }
          />
        )}

        {!cargando && !error && datos && datos.contenido.length > 0 && (
          <>
            <div className="overflow-hidden rounded-lg border border-border bg-surface">
              <table className="w-full border-collapse text-left">
                <caption className="sr-only">Trámites de cancha, {textoFiltro.toLowerCase()}</caption>
                <thead className="hidden lg:table-header-group">
                  <tr className="border-b-2 border-border bg-background">
                    {columnas.map((columna, indice) => (
                      <th
                        key={columna}
                        scope="col"
                        className={[
                          'px-3 py-2 text-label-sm text-text-muted uppercase',
                          columna === 'Acciones' && indice === columnas.length - 1 ? 'text-right' : '',
                        ].join(' ')}
                      >
                        {columna}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="block lg:table-row-group">
                  {datos.contenido.map((solicitud) => (
                    <FilaTramite
                      key={solicitud.id}
                      solicitud={solicitud}
                      dictaminar={dictaminar}
                      generarOficio={generarOficio}
                      onDecidir={onDecidir}
                      onCarta={onCarta}
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
                <Button variante="secundario" disabled={pagina + 1 >= totalPaginas} onClick={() => onPagina(pagina + 1)}>
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
