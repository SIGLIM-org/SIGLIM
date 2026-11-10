import { useEffect, useId, useState } from 'react'
import { Link } from 'react-router'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoClub } from '@/features/organizacion/etiquetas'
import { usePermisosClubes } from '@/features/organizacion/permisosClubes'
import type { Club, EstadoClub, FiltrosClubes } from '@/features/organizacion/tipos'
import type { Pagina } from '@/lib/api/tipos'

/** [PROPUESTA] Espera antes de buscar mientras se escribe, igual que en canchas. */
const ESPERA_BUSQUEDA_MS = 300

type Props = {
  filtros: FiltrosClubes
  datos?: Pagina<Club>
  cargando: boolean
  error: unknown
  clubId?: string
  onCambiarFiltros: (filtros: FiltrosClubes) => void
  onReintentar: () => void
  onRegistrar: () => void
}

/** Lista de clubes con búsqueda por nombre, filtro de estado y paginación (F-15, RF-008). */
export function ListaClubes({
  filtros,
  datos,
  cargando,
  error,
  clubId,
  onCambiarFiltros,
  onReintentar,
  onRegistrar,
}: Props) {
  const id = useId()
  const { registrarClub } = usePermisosClubes()
  const [texto, setTexto] = useState(filtros.q ?? '')
  const hayBusqueda = Boolean(filtros.q)

  useEffect(() => {
    const q = texto.trim() || undefined

    if (q === filtros.q) {
      return
    }

    const temporizador = setTimeout(() => {
      onCambiarFiltros({ ...filtros, q, pagina: 0 })
    }, ESPERA_BUSQUEDA_MS)

    return () => clearTimeout(temporizador)
  }, [texto, filtros, onCambiarFiltros])

  const quitarBusqueda = () => {
    setTexto('')
    onCambiarFiltros({ ...filtros, q: undefined, pagina: 0 })
  }

  return (
    <section aria-label="Lista de clubes" className="flex min-w-0 flex-col gap-3">
      <CampoTexto
        id={`${id}-busqueda`}
        etiqueta="Buscar por nombre"
        type="search"
        value={texto}
        onChange={(evento) => setTexto(evento.target.value)}
      />
      <CampoSeleccion
        id={`${id}-estado`}
        etiqueta="Estado"
        value={filtros.estado ?? ''}
        onChange={(evento) =>
          onCambiarFiltros({
            ...filtros,
            estado: (evento.target.value || undefined) as EstadoClub | undefined,
            pagina: 0,
          })
        }
      >
        <option value="ACTIVO">Activos</option>
        <option value="CERRADO">Cerrados</option>
        <option value="">Todos</option>
      </CampoSeleccion>

      {cargando && <EstadoCarga etiqueta="Cargando clubes…" filas={4} columnas={1} />}
      {!cargando && Boolean(error) && <ErrorConsulta error={error} onReintentar={onReintentar} />}
      {!cargando && !error && datos?.contenido.length === 0 && (
        hayBusqueda ? (
          <EstadoVacio
            titulo="Ningún club coincide con la búsqueda"
            accion={
              <Button variante="secundario" onClick={quitarBusqueda}>
                Quitar búsqueda
              </Button>
            }
          />
        ) : filtros.estado === 'CERRADO' ? (
          <EstadoVacio titulo="No hay clubes cerrados" />
        ) : (
          <EstadoVacio
            titulo="Aún no hay clubes registrados"
            accion={
              registrarClub && (
                <Button icono={Plus} onClick={onRegistrar}>
                  Registrar club
                </Button>
              )
            }
          />
        )
      )}
      {!cargando && !error && datos && datos.contenido.length > 0 && (
        <>
          <p className="text-body-sm text-text-muted">
            {datos.totalElementos === 1 ? '1 club' : `${datos.totalElementos} clubes`}
          </p>
          <ul className="flex flex-col overflow-hidden rounded-lg border border-border bg-surface">
            {datos.contenido.map((club) => {
              const seleccionado = club.id === clubId
              const estado = etiquetaEstadoClub[club.estado]

              return (
                <li key={club.id} className="border-b border-border last:border-b-0">
                  <Link
                    to={`/app/organizacion/clubes/${club.id}`}
                    aria-current={seleccionado ? 'page' : undefined}
                    className={[
                      'flex min-h-11 flex-wrap items-center justify-between gap-2 p-3 transition-colors',
                      seleccionado ? 'bg-primary/8 ring-1 ring-inset ring-primary' : 'hover:bg-background',
                    ].join(' ')}
                  >
                    <span className="min-w-0 break-words text-headline-sm text-text-main">{club.nombre}</span>
                    <Insignia variante={estado.variante} icono={estado.icono}>
                      {estado.texto}
                    </Insignia>
                  </Link>
                </li>
              )
            })}
          </ul>
          {datos.totalPaginas > 1 && (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="order-first w-full text-center text-body-sm text-text-muted">
                Página {datos.pagina + 1} de {datos.totalPaginas}
              </span>
              <Button
                variante="secundario"
                disabled={datos.pagina === 0}
                onClick={() => onCambiarFiltros({ ...filtros, pagina: datos.pagina - 1 })}
              >
                Anterior
              </Button>
              <Button
                variante="secundario"
                disabled={datos.pagina + 1 >= datos.totalPaginas}
                onClick={() => onCambiarFiltros({ ...filtros, pagina: datos.pagina + 1 })}
              >
                Siguiente
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  )
}
