import { useState, type KeyboardEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import {
  ChevronLeft,
  Layers,
  Lock,
  Scale,
  SlidersHorizontal,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'
import { useTemporada } from '@/features/temporadas/api'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import {
  PESTANAS_ESTRUCTURA,
  pestanaDeParametro,
  type PestanaEstructura,
} from '@/features/temporadas/pestanas'
import { PestanaCategorias } from '@/features/temporadas/PestanaCategorias'
import { PestanaGrupos } from '@/features/temporadas/PestanaGrupos'
import { PestanaInterpretaciones } from '@/features/temporadas/PestanaInterpretaciones'
import { PestanaParametros } from '@/features/temporadas/PestanaParametros'
import type { Temporada } from '@/features/temporadas/tipos'
import { esSinPermiso, reintentarConsulta } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

const RUTA_TEMPORADAS = '/app/administracion/temporadas'

const DATOS_PESTANA: Record<PestanaEstructura, { texto: string; icono: LucideIcon }> = {
  parametros: { texto: 'Parámetros', icono: SlidersHorizontal },
  categorias: { texto: 'Categorías', icono: Layers },
  grupos: { texto: 'Grupos', icono: Users },
  interpretaciones: { texto: 'Interpretaciones', icono: Scale },
}

/**
 * Contador de cada pestaña, con los datos reales de la temporada (D5). Interpretaciones no tiene
 * un contador en `Temporada`, así que no muestra insignia en vez de inventar un número.
 */
function contadorDe(pestana: PestanaEstructura, temporada: Temporada): string | null {
  if (pestana === 'interpretaciones') {
    return null
  }

  if (pestana === 'parametros') {
    return temporada.versionParametros !== undefined ? `v${temporada.versionParametros}` : '—'
  }

  return String(pestana === 'categorias' ? temporada.categoriasAsignadas : temporada.gruposCreados)
}

/**
 * Estructura y parámetros de una temporada (F-11, H-05.3, UI-004): parámetros versionados,
 * catálogo y categorías de la temporada, y grupos, en pestañas accesibles (D0, D1). Cada panel se
 * monta la primera vez que se visita y luego se conserva oculto, para no perder el borrador. Una
 * temporada cerrada queda en solo lectura (RF-003, D8).
 */
export function EstructuraTemporada() {
  const { temporadaId = '' } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const { yo } = useSesionActual()
  const consulta = useTemporada(temporadaId)
  const puedeRegistrarInterpretacion = tienePermiso(
    yo.permisos,
    'temporadas.interpretacion:REGISTRAR',
  )
  const puedeResolverInterpretacion = tienePermiso(
    yo.permisos,
    'temporadas.interpretacion:APROBAR',
  )
  const puedeVerInterpretaciones =
    puedeRegistrarInterpretacion || puedeResolverInterpretacion
  // Sin ninguno de los dos permisos la pestaña no existe; si se pide por URL, abre Parámetros.
  const pestanas = puedeVerInterpretaciones
    ? PESTANAS_ESTRUCTURA
    : PESTANAS_ESTRUCTURA.filter((valor) => valor !== 'interpretaciones')
  const solicitada = pestanaDeParametro(parametros.get('pestana'))
  const pestana = pestanas.includes(solicitada) ? solicitada : 'parametros'
  const [visitadas, setVisitadas] = useState<PestanaEstructura[]>([pestana])

  // La pestaña también cambia por enlaces (p. ej. desde un estado vacío): se registra al pintar.
  if (!visitadas.includes(pestana)) {
    setVisitadas([...visitadas, pestana])
  }

  if (esSinPermiso(consulta.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const cambiarPestana = (nueva: PestanaEstructura) => {
    setParametros(
      (actuales) => {
        const siguientes = new URLSearchParams(actuales)
        siguientes.set('pestana', nueva)
        return siguientes
      },
      { replace: true },
    )
  }

  const navegarPestanas = (evento: KeyboardEvent<HTMLDivElement>) => {
    const actual = pestanas.indexOf(pestana)
    const total = pestanas.length
    let siguiente: number | null = null

    if (evento.key === 'ArrowRight') {
      siguiente = (actual + 1) % total
    } else if (evento.key === 'ArrowLeft') {
      siguiente = (actual - 1 + total) % total
    } else if (evento.key === 'Home') {
      siguiente = 0
    } else if (evento.key === 'End') {
      siguiente = total - 1
    }

    if (siguiente === null) {
      return
    }

    evento.preventDefault()
    const valor = pestanas[siguiente]
    cambiarPestana(valor)
    document.getElementById(`pestana-${valor}`)?.focus()
  }

  const migas = (
    <nav aria-label="Ruta de navegación">
      <Link
        to={RUTA_TEMPORADAS}
        className="inline-flex min-h-11 items-center gap-1 text-label-md text-primary underline-offset-2 hover:underline"
      >
        <ChevronLeft aria-hidden="true" className="size-4 shrink-0" />
        Temporadas
      </Link>
    </nav>
  )

  if (consulta.isPending) {
    return (
      <div className="flex flex-col gap-6">
        {migas}
        <EstadoCarga etiqueta="Cargando la temporada…" filas={3} columnas={3} />
      </div>
    )
  }

  if (consulta.isError) {
    return (
      <div className="flex flex-col gap-6">
        {migas}
        <ErrorConsulta
          error={consulta.error}
          onReintentar={
            reintentarConsulta(0, consulta.error) ? () => void consulta.refetch() : undefined
          }
          accion={
            <Link to={RUTA_TEMPORADAS} className={clasesBoton('secundario')}>
              Volver a temporadas
            </Link>
          }
        />
      </div>
    )
  }

  const temporada = consulta.data
  const estado = etiquetaEstadoTemporada[temporada.estado]
  const cerrada = temporada.estado === 'CERRADA'
  const puedeEditar = !cerrada && tienePermiso(yo.permisos, 'temporadas.temporada:EDITAR')
  const puedeEditarParametros =
    !cerrada && tienePermiso(yo.permisos, 'temporadas.parametros:EDITAR')

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <header className="flex flex-col gap-3">
        {migas}
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <h1 className="min-w-0 text-display-lg-mobile text-text-main sm:text-display-lg">
            Estructura y parámetros
            <span className="block break-words text-headline-md text-text-muted">
              {temporada.nombre}
            </span>
          </h1>
          <div className="flex shrink-0 self-start">
            <Insignia variante={estado.variante} icono={estado.icono}>
              {estado.texto}
            </Insignia>
          </div>
        </div>
        <dl className="flex flex-wrap gap-x-6 gap-y-1 text-body-md">
          <div className="flex gap-1.5">
            <dt className="text-text-muted">Categorías asignadas:</dt>
            <dd className="font-mono text-mono-data leading-5 text-text-main">
              {temporada.categoriasAsignadas}
            </dd>
          </div>
          <div className="flex gap-1.5">
            <dt className="text-text-muted">Grupos:</dt>
            <dd className="font-mono text-mono-data leading-5 text-text-main">
              {temporada.gruposCreados}
            </dd>
          </div>
          <div className="flex gap-1.5">
            <dt className="text-text-muted">Parámetros:</dt>
            <dd className="text-text-main">
              {temporada.versionParametros !== undefined
                ? `versión ${temporada.versionParametros}`
                : 'Sin parámetros'}
            </dd>
          </div>
        </dl>
      </header>

      {cerrada && (
        <p className="flex items-start gap-2 rounded-lg border border-border bg-surface p-3 text-body-md text-text-main">
          <Lock aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-text-muted" />
          Temporada cerrada: solo lectura (RF-003).
        </p>
      )}

      <div
        role="tablist"
        aria-label="Estructura de la temporada"
        onKeyDown={navegarPestanas}
        // La línea base es una sombra interior y no un borde: queda dentro de la caja, bajo las
        // pestañas, y la línea activa se encima sin desbordar (sin barra vertical en Windows).
        className="flex min-w-0 overflow-x-auto overflow-y-hidden shadow-[inset_0_-2px_0_var(--color-border)]"
      >
        {pestanas.map((valor) => {
          const { texto, icono: Icono } = DATOS_PESTANA[valor]
          const activa = pestana === valor
          const contador = contadorDe(valor, temporada)

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
                'flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-label-md transition-colors sm:px-4',
                activa
                  ? 'border-primary text-primary'
                  : 'border-transparent text-text-muted hover:text-text-main',
              ].join(' ')}
            >
              <Icono aria-hidden="true" className="hidden size-4 shrink-0 sm:block" />
              {texto}
              {contador !== null && (
                <span
                  aria-hidden="true"
                  className={[
                    'rounded-sm px-1.5 py-0.5 font-mono text-mono-data',
                    activa ? 'bg-primary/10 text-primary' : 'bg-border/40 text-text-muted',
                  ].join(' ')}
                >
                  {contador}
                </span>
              )}
            </button>
          )
        })}
      </div>

      {pestanas.map((valor) => (
        <div
          key={valor}
          role="tabpanel"
          id={`panel-${valor}`}
          aria-labelledby={`pestana-${valor}`}
          hidden={pestana !== valor}
          className="min-w-0"
        >
          {visitadas.includes(valor) && valor === 'parametros' && (
            <PestanaParametros temporada={temporada} puedeEditar={puedeEditarParametros} />
          )}
          {visitadas.includes(valor) && valor === 'categorias' && (
            <PestanaCategorias temporada={temporada} puedeEditar={puedeEditar} />
          )}
          {visitadas.includes(valor) && valor === 'grupos' && (
            <PestanaGrupos temporada={temporada} puedeEditar={puedeEditar} />
          )}
          {visitadas.includes(valor) && valor === 'interpretaciones' && (
            <PestanaInterpretaciones
              temporada={temporada}
              puedeRegistrar={puedeRegistrarInterpretacion}
              puedeResolver={puedeResolverInterpretacion}
            />
          )}
        </div>
      ))}
    </div>
  )
}
