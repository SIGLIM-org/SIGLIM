import { useId, type KeyboardEvent } from 'react'
import { ChevronLeft, ChevronRight, UsersRound } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import type { Club } from '@/features/organizacion/tipos'
import { filtrosRegistros, type FiltroEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import type { RegistroJugadorFila } from '@/features/registros/tipos'
import { FilaRegistro } from '@/features/registros/validacion/FilaRegistro'
import type { Pagina } from '@/lib/api/tipos'

const formatoEntero = new Intl.NumberFormat('es-MX')

type Props = {
  clubes: readonly Club[]
  clubesCargando: boolean
  clubId: string
  filtro: FiltroEstadoRegistro
  pagina: number
  datos: Pagina<RegistroJugadorFila> | undefined
  cargando: boolean
  error: unknown
  seleccionadoId: string | undefined
  onClub: (clubId: string) => void
  onFiltro: (filtro: FiltroEstadoRegistro) => void
  onPagina: (pagina: number) => void
  onSeleccionar: (registro: RegistroJugadorFila) => void
  onReintentar: () => void
}

/**
 * Bandeja de registros por validar (F-19, DEC-111): pestañas de estado (Pendientes por defecto),
 * filtro de club y paginación de 20, del más antiguo al más reciente. En móvil es la vista principal
 * hasta elegir una fila.
 */
export function BandejaRegistros({
  clubes,
  clubesCargando,
  clubId,
  filtro,
  pagina,
  datos,
  cargando,
  error,
  seleccionadoId,
  onClub,
  onFiltro,
  onPagina,
  onSeleccionar,
  onReintentar,
}: Props) {
  const id = useId()
  const cantidad = datos?.totalElementos ?? 0
  const totalPaginas = datos?.totalPaginas ?? 0
  const filtroActual = filtrosRegistros.find(({ valor }) => valor === filtro)?.texto ?? 'Todos'

  const navegarFiltros = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = filtrosRegistros.findIndex(({ valor }) => valor === filtro)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente =
      filtrosRegistros[(actual + paso + filtrosRegistros.length) % filtrosRegistros.length].valor

    onFiltro(siguiente)
    document.getElementById(`${id}-filtro-${siguiente}`)?.focus()
  }

  return (
    <section
      aria-label="Bandeja de registros"
      className="flex min-w-0 flex-col gap-4 rounded-lg border border-border bg-surface p-4"
    >
      <div className="flex flex-col gap-3">
        <div>
          <h2 className="text-headline-sm text-text-main">Registros</h2>
          <p className="text-body-sm text-text-muted">
            {cantidad === 1
              ? '1 registro en este filtro, del más antiguo al más reciente'
              : `${formatoEntero.format(cantidad)} registros en este filtro, del más antiguo al más reciente`}
          </p>
        </div>

        <div
          role="tablist"
          aria-label="Filtrar registros por estado"
          onKeyDown={navegarFiltros}
          className="grid grid-cols-2 gap-1 rounded-md bg-background p-1 sm:grid-cols-4 lg:grid-cols-2"
        >
          {filtrosRegistros.map(({ valor, texto }) => (
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

        <CampoSeleccion
          id={`${id}-club`}
          etiqueta="Club"
          value={clubId}
          disabled={clubesCargando}
          onChange={(evento) => onClub(evento.target.value)}
        >
          <option value="">{clubesCargando ? 'Cargando clubes…' : 'Todos los clubes'}</option>
          {clubes.map((club) => (
            <option key={club.id} value={club.id}>
              {club.nombre}
            </option>
          ))}
        </CampoSeleccion>
      </div>

      <div
        role="tabpanel"
        id={`${id}-lista`}
        aria-labelledby={`${id}-filtro-${filtro}`}
        className="flex min-w-0 flex-col gap-3"
      >
        {cargando && datos === undefined && (
          <EstadoCarga etiqueta="Cargando registros…" filas={4} columnas={2} />
        )}

        {error != null && <ErrorConsulta error={error} onReintentar={onReintentar} />}

        {!cargando && error == null && datos && datos.contenido.length === 0 && (
          <EstadoVacio
            icono={UsersRound}
            titulo={
              filtro === 'PENDIENTE_VALIDACION'
                ? 'No hay registros por validar'
                : `No hay registros en «${filtroActual.toLowerCase()}»`
            }
            descripcion={
              filtro === 'PENDIENTE_VALIDACION'
                ? 'Cuando un club dé de alta a un jugador, su registro aparecerá aquí.'
                : 'Cambia el estado o el club para ver otros registros.'
            }
          />
        )}

        {datos && datos.contenido.length > 0 && (
          <ul className="flex flex-col gap-2">
            {datos.contenido.map((registro) => (
              <FilaRegistro
                key={registro.id}
                registro={registro}
                seleccionada={registro.id === seleccionadoId}
                onSeleccionar={() => onSeleccionar(registro)}
              />
            ))}
          </ul>
        )}

        {totalPaginas > 1 && (
          <nav aria-label="Páginas de registros" className="flex items-center justify-between gap-3">
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
