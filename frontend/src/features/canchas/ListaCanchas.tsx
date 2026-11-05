import { useEffect, useId, useState } from 'react'
import { Link } from 'react-router'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { useSedes } from '@/features/canchas/api'
import { etiquetaUbicacion, nombrePropietario } from '@/features/canchas/etiquetas'
import type { Cancha, FiltrosCanchas, UbicacionCancha } from '@/features/canchas/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import type { Pagina } from '@/lib/api/tipos'

/** [PROPUESTA] Espera antes de buscar mientras se escribe. */
const ESPERA_BUSQUEDA_MS = 300

type Props = {
  filtros: FiltrosCanchas
  datos?: Pagina<Cancha>
  cargando: boolean
  error: unknown
  canchaId?: string
  /** Sufijo `?semana=…` para conservar la semana al elegir una cancha. */
  sufijoSemana: string
  onCambiarFiltros: (filtros: FiltrosCanchas) => void
  onReintentar: () => void
  onNueva: () => void
}

/**
 * Catálogo con búsqueda, filtros y paginación (H-07, criterio 1). [PROPUESTA] D8: filtro por
 * sede (RF-028) con «Todas» más las sedes, sin opción «sin sede» porque el contrato no la
 * admite; la sede encabeza la línea de datos de cada cancha.
 */
export function ListaCanchas({
  filtros,
  datos,
  cargando,
  error,
  canchaId,
  sufijoSemana,
  onCambiarFiltros,
  onReintentar,
  onNueva,
}: Props) {
  const id = useId()
  const sedes = useSedes()
  const [texto, setTexto] = useState(filtros.q ?? '')
  const hayFiltros = Boolean(
    filtros.q || filtros.ubicacion || filtros.activa !== undefined || filtros.sedeId,
  )

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

  const quitarFiltros = () => {
    setTexto('')
    onCambiarFiltros({ pagina: 0, tamano: filtros.tamano })
  }

  const estadoSeleccionado = filtros.activa === undefined ? '' : String(filtros.activa)

  return (
    <section aria-label="Catálogo de canchas" className="flex min-w-0 flex-col gap-3">
      <CampoTexto
        id={`${id}-busqueda`}
        etiqueta="Buscar por nombre"
        type="search"
        value={texto}
        onChange={(evento) => setTexto(evento.target.value)}
      />
      <div className="grid grid-cols-2 gap-3">
        <CampoSeleccion
          id={`${id}-ubicacion`}
          etiqueta="Ubicación"
          value={filtros.ubicacion ?? ''}
          onChange={(evento) =>
            onCambiarFiltros({
              ...filtros,
              ubicacion: (evento.target.value || undefined) as UbicacionCancha | undefined,
              pagina: 0,
            })
          }
        >
          <option value="">Todas</option>
          <option value="UNIDAD_DEPORTIVA">{etiquetaUbicacion.UNIDAD_DEPORTIVA}</option>
          <option value="EXTERNA">{etiquetaUbicacion.EXTERNA}</option>
        </CampoSeleccion>
        <CampoSeleccion
          id={`${id}-estado`}
          etiqueta="Estado"
          value={estadoSeleccionado}
          onChange={({ target: { value } }) =>
            onCambiarFiltros({
              ...filtros,
              activa: value === '' ? undefined : value === 'true',
              pagina: 0,
            })
          }
        >
          <option value="">Todas</option>
          <option value="true">Activas</option>
          <option value="false">Inactivas</option>
        </CampoSeleccion>
        <div className="col-span-2">
          <CampoSeleccion
            id={`${id}-sede`}
            etiqueta="Sede"
            disabled={sedes.isPending && !filtros.sedeId}
            value={filtros.sedeId ?? ''}
            onChange={(evento) =>
              onCambiarFiltros({ ...filtros, sedeId: evento.target.value || undefined, pagina: 0 })
            }
          >
            <option value="">{sedes.isPending ? 'Cargando sedes…' : 'Todas'}</option>
            {sedes.data?.map((sede) => (
              <option key={sede.id} value={sede.id}>
                {sede.nombre}
              </option>
            ))}
          </CampoSeleccion>
        </div>
      </div>

      {cargando && <EstadoCarga etiqueta="Cargando canchas…" filas={4} columnas={1} />}
      {!cargando && Boolean(error) && <ErrorConsulta error={error} onReintentar={onReintentar} />}
      {!cargando && !error && datos?.contenido.length === 0 && (
        hayFiltros ? (
          <EstadoVacio
            titulo="Ninguna cancha coincide con estos filtros"
            accion={
              <Button variante="secundario" onClick={quitarFiltros}>
                Quitar filtros
              </Button>
            }
          />
        ) : (
          <EstadoVacio
            titulo="Aún no hay canchas registradas"
            accion={
              <ConPermiso permiso="canchas.cancha:EDITAR">
                <Button icono={Plus} onClick={onNueva}>
                  Registrar cancha
                </Button>
              </ConPermiso>
            }
          />
        )
      )}
      {!cargando && !error && datos && datos.contenido.length > 0 && (
        <>
          <p className="text-body-sm text-text-muted">
            {datos.totalElementos === 1 ? '1 cancha' : `${datos.totalElementos} canchas`}
          </p>
          <ul className="flex flex-col overflow-hidden rounded-lg border border-border bg-surface lg:max-h-72 lg:overflow-y-auto">
            {datos.contenido.map((cancha) => {
              const seleccionada = cancha.id === canchaId

              return (
                <li key={cancha.id} className="border-b border-border last:border-b-0">
                  <Link
                    to={`/app/programacion/canchas/${cancha.id}${sufijoSemana}`}
                    aria-current={seleccionada ? 'page' : undefined}
                    className={[
                      'flex min-h-11 flex-col gap-1 p-3 transition-colors',
                      seleccionada ? 'bg-primary/8 ring-1 ring-inset ring-primary' : 'hover:bg-background',
                    ].join(' ')}
                  >
                    <span className="flex flex-wrap items-center justify-between gap-2">
                      <span className="min-w-0 break-words text-headline-sm text-text-main">{cancha.nombre}</span>
                      {cancha.activa ? (
                        <Insignia variante="exito">Activa</Insignia>
                      ) : (
                        <Insignia variante="neutra">Inactiva</Insignia>
                      )}
                    </span>
                    <span className="break-words text-body-md text-text-muted">
                      {[cancha.sede?.nombre, etiquetaUbicacion[cancha.ubicacion], nombrePropietario(cancha)]
                        .filter(Boolean)
                        .join(' · ')}
                    </span>
                    {cancha.direccion && (
                      <span className="break-words text-body-sm text-text-muted">{cancha.direccion}</span>
                    )}
                  </Link>
                </li>
              )
            })}
          </ul>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="order-first w-full text-center text-body-sm text-text-muted">
              Página {datos.pagina + 1} de {Math.max(datos.totalPaginas, 1)}
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
        </>
      )}
    </section>
  )
}
