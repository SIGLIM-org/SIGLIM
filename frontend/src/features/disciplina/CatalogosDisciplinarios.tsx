import { useMemo, useRef, useState, type KeyboardEvent } from 'react'
import {
  BookOpen,
  CircleCheck,
  ClipboardList,
  Info,
  Plus,
  SearchX,
  type LucideIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { useArticulos, useTiposIncidencia } from '@/features/disciplina/api'
import { FormularioArticulo } from '@/features/disciplina/FormularioArticulo'
import { FormularioTipoIncidencia } from '@/features/disciplina/FormularioTipoIncidencia'
import { TablaArticulos } from '@/features/disciplina/TablaArticulos'
import { TablaTiposIncidencia } from '@/features/disciplina/TablaTiposIncidencia'
import type { Articulo, TipoIncidencia } from '@/features/disciplina/tipos'
import { reintentarConsulta } from '@/lib/api/errores'

type Pestana = 'tipos' | 'articulos'

type Edicion =
  | { catalogo: 'tipos'; tipo?: TipoIncidencia }
  | { catalogo: 'articulos'; articulo?: Articulo }

const PESTANAS: readonly {
  valor: Pestana
  texto: string
  textoCorto: string
  icono: LucideIcon
}[] = [
  {
    valor: 'tipos',
    texto: 'Tipos de incidencia',
    textoCorto: 'Incidencias',
    icono: ClipboardList,
  },
  {
    valor: 'articulos',
    texto: 'Artículos del reglamento',
    textoCorto: 'Artículos',
    icono: BookOpen,
  },
]

const formatoEntero = new Intl.NumberFormat('es-MX')

/** Resumen del catálogo: «8 tipos» o «1 de 8 tipos» mientras se busca. */
function resumen(
  cantidad: number,
  total: number,
  singular: string,
  plural: string,
  filtrando: boolean,
): string {
  if (filtrando) {
    return `${formatoEntero.format(cantidad)} de ${formatoEntero.format(total)} ${plural}`
  }

  return `${formatoEntero.format(cantidad)} ${cantidad === 1 ? singular : plural}`
}

/**
 * Catálogos disciplinarios de F-32 (H-26.2, UI-026): tipos de incidencia y
 * artículos del reglamento en pestañas, con un panel lateral para crear y
 * editar. Los valores `[DEMO]` llevan la insignia «No oficial» (H-26 criterio 3).
 */
export function CatalogosDisciplinarios() {
  const tipos = useTiposIncidencia()
  const articulos = useArticulos()
  const [pestana, setPestana] = useState<Pestana>('tipos')
  const [edicion, setEdicion] = useState<Edicion | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [busquedaTipos, setBusquedaTipos] = useState('')
  const [busquedaArticulos, setBusquedaArticulos] = useState('')
  const origenFoco = useRef<HTMLElement | null>(null)

  const tiposFiltrados = useMemo(() => {
    const texto = busquedaTipos.trim().toLocaleLowerCase('es-MX')
    const lista = tipos.data ?? []

    if (!texto) {
      return lista
    }

    return lista.filter((tipo) =>
      `${tipo.codigo} ${tipo.nombre}`.toLocaleLowerCase('es-MX').includes(texto),
    )
  }, [busquedaTipos, tipos.data])

  const articulosFiltrados = useMemo(() => {
    const texto = busquedaArticulos.trim().toLocaleLowerCase('es-MX')
    const lista = articulos.data ?? []

    if (!texto) {
      return lista
    }

    return lista.filter((articulo) =>
      `${articulo.numero} ${articulo.descripcion}`.toLocaleLowerCase('es-MX').includes(texto),
    )
  }, [articulos.data, busquedaArticulos])

  const cambiarPestana = (nueva: Pestana) => {
    setPestana(nueva)
    setEdicion(null)
    setMensaje(null)
  }

  const navegarPestanas = (evento: KeyboardEvent<HTMLDivElement>) => {
    const actual = PESTANAS.findIndex(({ valor }) => valor === pestana)
    let siguiente: number | null = null

    if (evento.key === 'ArrowRight') {
      siguiente = (actual + 1) % PESTANAS.length
    } else if (evento.key === 'ArrowLeft') {
      siguiente = (actual - 1 + PESTANAS.length) % PESTANAS.length
    } else if (evento.key === 'Home') {
      siguiente = 0
    } else if (evento.key === 'End') {
      siguiente = PESTANAS.length - 1
    }

    if (siguiente === null) {
      return
    }

    evento.preventDefault()
    const valor = PESTANAS[siguiente].valor
    cambiarPestana(valor)
    document.getElementById(`pestana-${valor}`)?.focus()
  }

  /** Guarda el control que abrió el panel para devolverle el foco al cerrarlo. */
  const recordarFoco = () => {
    origenFoco.current = document.activeElement as HTMLElement | null
  }

  const cerrarEdicion = () => {
    setEdicion(null)
    window.requestAnimationFrame(() => origenFoco.current?.focus())
  }

  const abrirNuevoTipo = () => {
    recordarFoco()
    setMensaje(null)
    setBusquedaTipos('')
    setEdicion({ catalogo: 'tipos' })
  }

  const abrirNuevoArticulo = () => {
    recordarFoco()
    setMensaje(null)
    setBusquedaArticulos('')
    setEdicion({ catalogo: 'articulos' })
  }

  const editarTipo = (tipo: TipoIncidencia) => {
    recordarFoco()
    setMensaje(null)
    setEdicion({ catalogo: 'tipos', tipo })
  }

  const editarArticulo = (articulo: Articulo) => {
    recordarFoco()
    setMensaje(null)
    setEdicion({ catalogo: 'articulos', articulo })
  }

  const alGuardarTipo = (tipo: TipoIncidencia, creado: boolean) => {
    cerrarEdicion()
    setMensaje(
      creado
        ? `«${tipo.nombre}» se agregó al catálogo.`
        : `«${tipo.nombre}» se actualizó.`,
    )
  }

  const alGuardarArticulo = (articulo: Articulo, creado: boolean) => {
    cerrarEdicion()
    setMensaje(
      creado
        ? `El artículo «${articulo.numero}» se agregó al catálogo.`
        : `El artículo «${articulo.numero}» se actualizó.`,
    )
  }

  const cantidadTipos = tipos.data?.length ?? 0
  const cantidadArticulos = articulos.data?.length ?? 0
  const editandoTipo = edicion?.catalogo === 'tipos'
  const editandoArticulo = edicion?.catalogo === 'articulos'

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <header>
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
          Catálogos disciplinarios
        </h1>
        <p className="mt-1 max-w-3xl text-body-md text-text-muted">
          Configura los tipos de incidencia que abren expediente y los artículos del reglamento
          con su sanción, sujeto, efecto y aplicación automática.
        </p>
      </header>

      <aside
        aria-label="Aviso sobre los valores de demostración"
        className="flex flex-col gap-2 rounded-lg border border-warning/30 bg-warning/8 p-3 sm:flex-row sm:items-center"
      >
        <Info aria-hidden="true" className="size-5 shrink-0 text-warning-strong" />
        <p className="min-w-0 flex-1 text-body-md text-text-muted">
          <span className="font-medium text-text-main">Valores de demostración.</span> Los
          elementos con la insignia «No oficial» son configurables y la Liga aún no los aprueba;
          ajustarlos aquí no cambia el reglamento vigente.
        </p>
        <span className="self-start sm:self-center">
          <InsigniaNoOficial />
        </span>
      </aside>

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
        aria-label="Catálogos disciplinarios"
        onKeyDown={navegarPestanas}
        className="flex min-w-0 overflow-x-auto border-b-2 border-border"
      >
        {PESTANAS.map(({ valor, texto, textoCorto, icono: Icono }) => {
          const activa = pestana === valor
          const cantidad = valor === 'tipos' ? cantidadTipos : cantidadArticulos

          return (
            <button
              key={valor}
              type="button"
              role="tab"
              id={`pestana-${valor}`}
              aria-selected={activa}
              aria-controls={`panel-${valor}`}
              tabIndex={activa ? 0 : -1}
              onClick={() => cambiarPestana(valor)}
              className={[
                '-mb-0.5 flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-label-md transition-colors sm:px-4',
                activa
                  ? 'border-primary text-primary'
                  : 'border-transparent text-text-muted hover:text-text-main',
              ].join(' ')}
            >
              <Icono aria-hidden="true" className="size-4 shrink-0" />
              <span className="sm:hidden">{textoCorto}</span>
              <span className="hidden sm:inline">{texto}</span>
              {cantidad > 0 && (
                <span
                  className={[
                    'rounded-full px-2 py-0.5 font-mono text-mono-data',
                    activa ? 'bg-primary/10 text-primary' : 'bg-border/40 text-text-muted',
                  ].join(' ')}
                >
                  {formatoEntero.format(cantidad)}
                </span>
              )}
            </button>
          )
        })}
      </div>

      <div
        role="tabpanel"
        id="panel-tipos"
        aria-labelledby="pestana-tipos"
        className={pestana === 'tipos' ? 'flex min-w-0 flex-col gap-4' : 'hidden'}
      >
        <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="w-full sm:max-w-xs">
            <CampoTexto
              id="busqueda-tipos"
              type="search"
              etiqueta="Buscar tipo de incidencia"
              placeholder="Código o nombre…"
              value={busquedaTipos}
              onChange={(evento) => setBusquedaTipos(evento.target.value)}
            />
          </div>
          <Button
            icono={Plus}
            onClick={abrirNuevoTipo}
            className="w-full shrink-0 sm:w-auto"
          >
            Nuevo tipo
          </Button>
        </div>

        <div
          className={
            editandoTipo
              ? 'grid gap-4 xl:grid-cols-[minmax(0,1fr)_24rem] xl:items-start'
              : ''
          }
        >
          <div className="flex min-w-0 flex-col gap-4">
            {tipos.isPending && (
              <EstadoCarga etiqueta="Cargando tipos de incidencia…" filas={5} columnas={4} />
            )}

            {tipos.isError && (
              <ErrorConsulta
                error={tipos.error}
                onReintentar={
                  reintentarConsulta(0, tipos.error) ? () => void tipos.refetch() : undefined
                }
              />
            )}

            {!tipos.isError && tipos.data && cantidadTipos === 0 && (
              <EstadoVacio
                icono={ClipboardList}
                titulo="Aún no hay tipos de incidencia"
                descripcion="Registra el primer tipo para usarlo al capturar una cédula."
                accion={
                  <Button icono={Plus} onClick={abrirNuevoTipo}>
                    Nuevo tipo
                  </Button>
                }
              />
            )}

            {!tipos.isError && cantidadTipos > 0 && tiposFiltrados.length === 0 && (
              <EstadoVacio
                icono={SearchX}
                titulo="No hay coincidencias"
                descripcion="Prueba con otro código o nombre."
                accion={
                  <Button variante="secundario" onClick={() => setBusquedaTipos('')}>
                    Limpiar búsqueda
                  </Button>
                }
              />
            )}

            {!tipos.isError && tiposFiltrados.length > 0 && (
              <>
                <p aria-live="polite" className="text-label-md text-text-main">
                  {resumen(
                    tiposFiltrados.length,
                    cantidadTipos,
                    'tipo',
                    'tipos',
                    busquedaTipos.trim() !== '',
                  )}
                </p>
                <TablaTiposIncidencia tipos={tiposFiltrados} onEditar={editarTipo} />
              </>
            )}
          </div>

          {editandoTipo && (
            <FormularioTipoIncidencia
              key={edicion.tipo?.id ?? 'nuevo'}
              tipo={edicion.tipo}
              onCerrar={cerrarEdicion}
              onGuardado={alGuardarTipo}
            />
          )}
        </div>
      </div>

      <div
        role="tabpanel"
        id="panel-articulos"
        aria-labelledby="pestana-articulos"
        className={pestana === 'articulos' ? 'flex min-w-0 flex-col gap-4' : 'hidden'}
      >
        <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="w-full sm:max-w-xs">
            <CampoTexto
              id="busqueda-articulos"
              type="search"
              etiqueta="Buscar artículo"
              placeholder="Número o descripción…"
              value={busquedaArticulos}
              onChange={(evento) => setBusquedaArticulos(evento.target.value)}
            />
          </div>
          <Button
            icono={Plus}
            onClick={abrirNuevoArticulo}
            className="w-full shrink-0 sm:w-auto"
          >
            Nuevo artículo
          </Button>
        </div>

        <div
          className={
            editandoArticulo
              ? 'grid gap-4 xl:grid-cols-[minmax(0,1fr)_24rem] xl:items-start'
              : ''
          }
        >
          <div className="flex min-w-0 flex-col gap-4">
            {articulos.isPending && (
              <EstadoCarga etiqueta="Cargando artículos…" filas={5} columnas={5} />
            )}

            {articulos.isError && (
              <ErrorConsulta
                error={articulos.error}
                onReintentar={
                  reintentarConsulta(0, articulos.error)
                    ? () => void articulos.refetch()
                    : undefined
                }
              />
            )}

            {!articulos.isError && articulos.data && cantidadArticulos === 0 && (
              <EstadoVacio
                icono={BookOpen}
                titulo="Aún no hay artículos"
                descripcion="Registra el primer artículo del reglamento con su sanción."
                accion={
                  <Button icono={Plus} onClick={abrirNuevoArticulo}>
                    Nuevo artículo
                  </Button>
                }
              />
            )}

            {!articulos.isError && cantidadArticulos > 0 && articulosFiltrados.length === 0 && (
              <EstadoVacio
                icono={SearchX}
                titulo="No hay coincidencias"
                descripcion="Prueba con otro número o descripción."
                accion={
                  <Button variante="secundario" onClick={() => setBusquedaArticulos('')}>
                    Limpiar búsqueda
                  </Button>
                }
              />
            )}

            {!articulos.isError && articulosFiltrados.length > 0 && (
              <>
                <p aria-live="polite" className="text-label-md text-text-main">
                  {resumen(
                    articulosFiltrados.length,
                    cantidadArticulos,
                    'artículo',
                    'artículos',
                    busquedaArticulos.trim() !== '',
                  )}
                </p>
                <TablaArticulos articulos={articulosFiltrados} onEditar={editarArticulo} />
              </>
            )}
          </div>

          {editandoArticulo && (
            <FormularioArticulo
              key={edicion.articulo?.id ?? 'nuevo'}
              articulo={edicion.articulo}
              onCerrar={cerrarEdicion}
              onGuardado={alGuardarArticulo}
            />
          )}
        </div>
      </div>
    </div>
  )
}
