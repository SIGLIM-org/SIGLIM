import { useId, type KeyboardEvent, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight, Info, UsersRound } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import {
  filtrosRegistros,
  type FiltroEstadoRegistro,
} from '@/features/registros/documentos/etiquetas'
import type { PermisosAccionesPlantilla } from '@/features/registros/plantilla/AccionesRegistroPlantilla'
import { FilaPlantilla } from '@/features/registros/plantilla/FilaPlantilla'
import { TarjetaPlantilla } from '@/features/registros/plantilla/TarjetaPlantilla'
import type { RegistroJugadorFila } from '@/features/registros/tipos'
import type { Pagina } from '@/lib/api/tipos'

/** Las pestañas de la bandeja, con «Todos» primero: la plantilla abre ahí (DEC-117). */
export const FILTROS_PLANTILLA = [
  ...filtrosRegistros.filter(({ valor }) => valor === 'TODOS'),
  ...filtrosRegistros.filter(({ valor }) => valor !== 'TODOS'),
]

const ENCABEZADO = 'px-4 py-3 text-left text-label-sm uppercase text-text-muted'

type Props = {
  /** Nombre del equipo, para el título accesible de la tabla. */
  equipo: string
  filtro: FiltroEstadoRegistro
  pagina: number
  datos: Pagina<RegistroJugadorFila> | undefined
  cargando: boolean
  /** Hay datos de otra selección mientras llegan los nuevos; se atenúan. */
  actualizando: boolean
  error: unknown
  permisos: PermisosAccionesPlantilla
  /** «Registrar jugador» para el estado vacío, solo con `EDITAR`. */
  accionRegistrar?: ReactNode
  onFiltro: (filtro: FiltroEstadoRegistro) => void
  onPagina: (pagina: number) => void
  onReintentar: () => void
}

/**
 * Pestañas de estado, tabla (≥ 1024 px) o tarjetas, y paginación de la plantilla (F-17, DEC-117).
 * Sin contadores por pestaña: el backend no los da.
 */
export function ListaPlantilla({
  equipo,
  filtro,
  pagina,
  datos,
  cargando,
  actualizando,
  error,
  permisos,
  accionRegistrar,
  onFiltro,
  onPagina,
  onReintentar,
}: Props) {
  const id = useId()
  const totalPaginas = datos?.totalPaginas ?? 0
  const registros = datos?.contenido ?? []
  const filtroActual = FILTROS_PLANTILLA.find(({ valor }) => valor === filtro)?.texto ?? 'Todos'

  const navegarFiltros = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = FILTROS_PLANTILLA.findIndex(({ valor }) => valor === filtro)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente =
      FILTROS_PLANTILLA[(actual + paso + FILTROS_PLANTILLA.length) % FILTROS_PLANTILLA.length].valor

    onFiltro(siguiente)
    document.getElementById(`${id}-filtro-${siguiente}`)?.focus()
  }

  return (
    <section aria-label={`Plantilla de ${equipo}`} className="flex min-w-0 flex-col gap-4">
      <div
        role="tablist"
        aria-label="Filtrar jugadores por estado"
        onKeyDown={navegarFiltros}
        className="grid grid-cols-2 gap-1 rounded-md border border-border bg-surface p-1 sm:inline-grid sm:grid-cols-4 sm:self-start"
      >
        {FILTROS_PLANTILLA.map(({ valor, texto }) => (
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
              'min-h-11 rounded-md px-4 text-label-md transition-colors',
              filtro === valor
                ? 'bg-primary/8 text-primary'
                : 'text-text-muted hover:bg-background hover:text-text-main',
            ].join(' ')}
          >
            {texto}
          </button>
        ))}
      </div>

      {registros.length > 0 && (
        <p className="flex items-start gap-2 text-body-sm text-text-muted">
          <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          La elegibilidad se calcula con el estado del registro. Las sanciones se revisan al capturar
          la alineación.
        </p>
      )}

      <div
        role="tabpanel"
        id={`${id}-lista`}
        aria-labelledby={`${id}-filtro-${filtro}`}
        aria-busy={actualizando || undefined}
        className="flex min-w-0 flex-col gap-3"
      >
        {error != null ? (
          <ErrorConsulta error={error} onReintentar={onReintentar} />
        ) : datos === undefined ? (
          cargando && <EstadoCarga etiqueta="Cargando la plantilla…" filas={5} columnas={4} />
        ) : registros.length === 0 ? (
          filtro === 'TODOS' ? (
            <EstadoVacio
              icono={UsersRound}
              titulo="Este equipo aún no tiene jugadores registrados"
              descripcion="Los jugadores que registre el club aparecerán aquí con su estado."
              accion={accionRegistrar}
            />
          ) : (
            <EstadoVacio
              icono={UsersRound}
              titulo={`No hay jugadores en «${filtroActual}»`}
              descripcion="Elige otra pestaña para ver el resto de la plantilla."
            />
          )
        ) : (
          <div
            className={[
              'overflow-hidden rounded-lg border border-border bg-surface transition-opacity',
              actualizando ? 'opacity-60' : '',
            ].join(' ')}
          >
            <table className="hidden w-full lg:table">
              <caption className="sr-only">
                Plantilla de {equipo}: {filtroActual.toLowerCase()}, por apellidos de la A a la Z
              </caption>
              <thead className="border-b-2 border-border bg-background">
                <tr>
                  <th scope="col" className={ENCABEZADO}>
                    Jugador
                  </th>
                  <th scope="col" className={ENCABEZADO}>
                    CURP
                  </th>
                  <th scope="col" className={ENCABEZADO}>
                    Estado
                  </th>
                  <th scope="col" className={ENCABEZADO}>
                    Elegibilidad
                  </th>
                  <th scope="col" className={ENCABEZADO}>
                    Alta
                  </th>
                  <th scope="col" className={`${ENCABEZADO} text-right`}>
                    Acciones
                  </th>
                </tr>
              </thead>
              <tbody>
                {registros.map((registro) => (
                  <FilaPlantilla key={registro.id} registro={registro} permisos={permisos} />
                ))}
              </tbody>
            </table>

            <ul
              aria-label={`Plantilla de ${equipo}`}
              className="divide-y divide-border lg:hidden"
            >
              {registros.map((registro) => (
                <TarjetaPlantilla key={registro.id} registro={registro} permisos={permisos} />
              ))}
            </ul>
          </div>
        )}

        {totalPaginas > 1 && (
          <nav
            aria-label="Páginas de la plantilla"
            className="flex items-center justify-between gap-3"
          >
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
