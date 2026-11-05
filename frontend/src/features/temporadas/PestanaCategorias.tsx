import { useState, type DragEvent } from 'react'
import { CircleAlert, CircleCheck, Info, Layers, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import {
  useActualizarCategoria,
  useAsignarCategoria,
  useCatalogoCategorias,
  useCategoriasTemporada,
  useReordenarCategorias,
} from '@/features/temporadas/api'
import { FilaCategoria } from '@/features/temporadas/FilaCategoria'
import { FormularioCategoria } from '@/features/temporadas/FormularioCategoria'
import { moverElemento } from '@/features/temporadas/ordenamiento'
import type { Categoria, Temporada } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'
import { reintentarConsulta } from '@/lib/api/errores'

type Props = {
  temporada: Temporada
  /** `temporadas.temporada:EDITAR` y la temporada no está cerrada (D8). */
  puedeEditar: boolean
}

type Edicion = { categoria?: Categoria }

const CLASE_ENCABEZADO = 'px-4 py-3 text-left text-label-sm text-text-muted'

function detalleDe(error: unknown, respaldo: string): string {
  return error instanceof ApiError ? error.problema.detail : respaldo
}

/**
 * Pestaña Categorías de F-11 (H-05.3, RF-099, RF-004): el catálogo de la liga, compartido por todas
 * las temporadas, con su orden, jerarquía y estado, y qué categorías participan en esta temporada.
 * El orden se cambia localmente (Subir/Bajar o arrastre) y se guarda en un solo `POST` con todas
 * las categorías (D3). Solo en `CONFIGURACION` se asignan categorías (RF-004, D8).
 */
export function PestanaCategorias({ temporada, puedeEditar }: Props) {
  const catalogo = useCatalogoCategorias()
  const deTemporada = useCategoriasTemporada(temporada.id)
  const reordenar = useReordenarCategorias()
  const actualizar = useActualizarCategoria()
  const asignar = useAsignarCategoria(temporada.id)
  const [ordenLocal, setOrdenLocal] = useState<string[] | null>(null)
  const [edicion, setEdicion] = useState<Edicion | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const [anuncio, setAnuncio] = useState('')
  const [arrastrando, setArrastrando] = useState<number | null>(null)
  const [sobre, setSobre] = useState<number | null>(null)

  if (catalogo.isPending || deTemporada.isPending) {
    return <EstadoCarga etiqueta="Cargando categorías…" filas={5} columnas={6} />
  }

  if (catalogo.isError || deTemporada.isError) {
    const error = catalogo.error ?? deTemporada.error
    return (
      <ErrorConsulta
        error={error}
        onReintentar={
          reintentarConsulta(0, error)
            ? () => {
                void catalogo.refetch()
                void deTemporada.refetch()
              }
            : undefined
        }
      />
    )
  }

  const categorias = catalogo.data
  const idsServidor = categorias.map((categoria) => categoria.id)
  // Un orden local que ya no corresponde al catálogo (alguien creó una categoría) se descarta.
  const ordenValido =
    ordenLocal !== null &&
    ordenLocal.length === idsServidor.length &&
    idsServidor.every((id) => ordenLocal.includes(id))
  const ids = ordenValido ? ordenLocal : idsServidor
  const porId = new Map(categorias.map((categoria) => [categoria.id, categoria]))
  const lista = ids.map((id) => porId.get(id)).filter((categoria) => categoria !== undefined)
  const ordenCambiado = ordenValido && ids.join() !== idsServidor.join()
  const asignadas = new Set(deTemporada.data.map((elemento) => elemento.categoria.id))
  const enConfiguracion = temporada.estado === 'CONFIGURACION'
  const cerrada = temporada.estado === 'CERRADA'
  const ocupado = reordenar.isPending || actualizar.isPending || asignar.isPending
  const asignandoId = asignar.isPending ? (asignar.variables ?? null) : null

  const limpiarAvisos = () => {
    setMensaje(null)
    setErrorAccion(null)
  }

  const mover = (desde: number, hasta: number) => {
    if (desde === hasta) {
      return
    }

    const nuevo = moverElemento(ids, desde, hasta)
    const destino = Math.min(Math.max(hasta, 0), nuevo.length - 1)
    const categoria = porId.get(nuevo[destino])
    setOrdenLocal(nuevo)
    limpiarAvisos()

    if (categoria) {
      setAnuncio(`${categoria.nombre}, posición ${destino + 1} de ${nuevo.length}`)
      // El foco sigue a la categoría; si su botón quedó deshabilitado, pasa al otro.
      const preferido = hasta < desde ? 'subir' : 'bajar'
      window.requestAnimationFrame(() => {
        const fila = document.querySelector(`[data-categoria="${categoria.id}"]`)
        const boton =
          fila?.querySelector<HTMLButtonElement>(`[data-accion="${preferido}"]:not(:disabled)`) ??
          fila?.querySelector<HTMLButtonElement>('[data-accion]:not(:disabled)')
        boton?.focus()
      })
    }
  }

  const guardarOrden = () => {
    limpiarAvisos()
    reordenar.mutate(ids, {
      onSuccess: () => {
        setOrdenLocal(null)
        setMensaje('Se guardó el orden de las categorías.')
      },
      onError: (fallo) =>
        setErrorAccion(detalleDe(fallo, 'No se pudo guardar el orden. Intenta de nuevo.')),
    })
  }

  const cambiarActiva = (categoria: Categoria) => {
    limpiarAvisos()
    const activa = !categoria.activa
    actualizar.mutate(
      { id: categoria.id, activa },
      {
        onSuccess: () =>
          setMensaje(`«${categoria.nombre}» ${activa ? 'se reactivó' : 'se desactivó'}.`),
        onError: (fallo) =>
          setErrorAccion(detalleDe(fallo, 'No se pudo cambiar el estado. Intenta de nuevo.')),
      },
    )
  }

  const asignarCategoria = (categoria: Categoria) => {
    limpiarAvisos()
    asignar.mutate(categoria.id, {
      onSuccess: () => setMensaje(`«${categoria.nombre}» se asignó a ${temporada.nombre}.`),
      onError: (fallo) =>
        setErrorAccion(detalleDe(fallo, 'No se pudo asignar la categoría. Intenta de nuevo.')),
    })
  }

  const alGuardar = (categoria: Categoria, creada: boolean) => {
    setEdicion(null)
    setMensaje(
      creada
        ? `«${categoria.nombre}» se agregó al catálogo.`
        : `«${categoria.nombre}» se actualizó.`,
    )
  }

  const arrastreSobre = (evento: DragEvent<HTMLTableRowElement>, indice: number) => {
    if (arrastrando !== null) {
      evento.preventDefault()
      setSobre(indice)
    }
  }

  const soltar = (evento: DragEvent<HTMLTableRowElement>, indice: number) => {
    evento.preventDefault()
    if (arrastrando !== null) {
      mover(arrastrando, indice)
    }
    setArrastrando(null)
    setSobre(null)
  }

  return (
    <section aria-labelledby="titulo-catalogo" className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 id="titulo-catalogo" className="text-headline-md text-text-main">
            Catálogo de categorías de la liga
          </h2>
          <p className="mt-1 max-w-3xl text-body-md text-text-muted">
            Compartido por todas las temporadas. La columna «En esta temporada» muestra cuáles
            participan en {temporada.nombre}.
          </p>
          {!puedeEditar && !cerrada && (
            <p className="mt-1 text-body-sm text-text-muted">Solo consulta</p>
          )}
        </div>
        {puedeEditar && (
          <Button
            icono={Plus}
            className="shrink-0"
            onClick={() => {
              limpiarAvisos()
              setEdicion({})
            }}
          >
            Nueva categoría
          </Button>
        )}
      </div>

      {puedeEditar && temporada.estado === 'ACTIVA' && (
        <p className="flex items-start gap-2 rounded-lg border border-border bg-surface p-3 text-body-md text-text-main">
          <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-info-strong" />
          La temporada está activa: ya no se pueden asignar categorías (RF-004). El catálogo sí
          puede editarse.
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

      {errorAccion && (
        <p
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
        >
          <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {errorAccion}
        </p>
      )}

      {ordenCambiado && (
        <div className="sticky top-2 z-10 flex flex-col gap-2 rounded-lg border border-primary/30 bg-surface p-3 shadow-overlay sm:flex-row sm:items-center sm:justify-between">
          <p className="text-body-md text-text-main">Cambiaste el orden de las categorías.</p>
          <div className="flex flex-col-reverse gap-2 sm:flex-row">
            <Button
              variante="secundario"
              disabled={reordenar.isPending}
              onClick={() => {
                setOrdenLocal(null)
                setAnuncio('Se descartó el cambio de orden.')
              }}
            >
              Descartar
            </Button>
            <Button cargando={reordenar.isPending} onClick={guardarOrden}>
              Guardar orden
            </Button>
          </div>
        </div>
      )}

      <p aria-live="polite" className="sr-only">
        {anuncio}
      </p>

      {lista.length === 0 ? (
        <EstadoVacio
          icono={Layers}
          titulo="El catálogo aún no tiene categorías"
          descripcion="Crea la primera categoría de la liga para asignarla a la temporada."
        />
      ) : (
        <div className="overflow-hidden rounded-lg border border-border bg-surface">
          <table role="table" aria-label="Catálogo de categorías" className="block w-full lg:table">
            <thead role="rowgroup" className="hidden border-b-2 border-border bg-background lg:table-header-group">
              <tr role="row">
                <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                  Orden
                </th>
                <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                  Nombre
                </th>
                <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                  Tipo
                </th>
                <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                  Categoría superior
                </th>
                <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                  Estado
                </th>
                <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                  En esta temporada
                </th>
                {puedeEditar && (
                  <th scope="col" role="columnheader" className={`${CLASE_ENCABEZADO} text-right`}>
                    Acciones
                  </th>
                )}
              </tr>
            </thead>
            <tbody role="rowgroup" className="block lg:table-row-group">
              {lista.map((categoria, indice) => (
                <FilaCategoria
                  key={categoria.id}
                  categoria={categoria}
                  posicion={indice + 1}
                  total={lista.length}
                  asignada={asignadas.has(categoria.id)}
                  puedeAsignar={puedeEditar && enConfiguracion && categoria.activa}
                  puedeEditar={puedeEditar}
                  asignando={asignandoId === categoria.id}
                  accionesDeshabilitadas={ocupado}
                  arrastrada={arrastrando === indice}
                  destinoArrastre={sobre === indice && arrastrando !== indice}
                  onMover={(delta) => mover(indice, indice + delta)}
                  onAsignar={() => asignarCategoria(categoria)}
                  onEditar={() => {
                    limpiarAvisos()
                    setEdicion({ categoria })
                  }}
                  onCambiarActiva={() => cambiarActiva(categoria)}
                  onArrastreInicio={() => setArrastrando(indice)}
                  onArrastreSobre={(evento) => arrastreSobre(evento, indice)}
                  onSoltar={(evento) => soltar(evento, indice)}
                  onArrastreFin={() => {
                    setArrastrando(null)
                    setSobre(null)
                  }}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {edicion && (
        <FormularioCategoria
          key={edicion.categoria?.id ?? 'nueva'}
          categoria={edicion.categoria}
          catalogo={categorias}
          onCerrar={() => setEdicion(null)}
          onGuardada={alGuardar}
        />
      )}
    </section>
  )
}
