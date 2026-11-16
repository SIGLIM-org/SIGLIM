import { Link } from 'react-router'
import { CalendarCheck, ChevronRight } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { usePublicacionesPendientes } from '@/features/publicacion/api'
import { formatearFechaHora } from '@/features/publicacion/fechas'

/*
 * [PROPUESTA] Una liga tiene pocas jornadas en revisión a la vez: se pide la primera página de 100
 * pendientes y se avisa si hay más, en lugar de paginar.
 */
const PENDIENTES_POR_PAGINA = 100

/**
 * Jornadas en revisión para quien puede aprobarlas (F-23, H-13.2), desde
 * `GET /publicaciones/pendientes`, del envío más antiguo al más reciente. Los comunicados de la
 * misma bandeja tienen su propia pantalla, así que aquí se omiten.
 */
export function BandejaRevisionJornadas() {
  const pendientes = usePublicacionesPendientes(0, PENDIENTES_POR_PAGINA)
  const jornadas = (pendientes.data?.contenido ?? []).filter(({ tipo }) => tipo === 'JORNADA')
  const hayMas = (pendientes.data?.totalPaginas ?? 0) > 1

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
          Jornadas por revisar
        </h1>
        <p className="mt-1 max-w-2xl text-body-md text-text-muted">
          Revisa cada jornada que envía el Programador y apruébala para publicarla en el portal, o
          devuélvela con observaciones.
        </p>
      </header>

      {pendientes.isPending && (
        <EstadoCarga etiqueta="Cargando las jornadas por revisar…" filas={3} columnas={3} />
      )}

      {pendientes.isError && (
        <ErrorConsulta error={pendientes.error} onReintentar={() => void pendientes.refetch()} />
      )}

      {!pendientes.isPending && !pendientes.isError && jornadas.length === 0 && (
        <EstadoVacio
          icono={CalendarCheck}
          titulo="No hay jornadas por revisar"
          descripcion="Cuando el Programador envíe una jornada a aprobación, aparecerá aquí."
        />
      )}

      {jornadas.length > 0 && (
        <section aria-labelledby="jornadas-por-revisar" className="flex flex-col gap-3">
          <h2 id="jornadas-por-revisar" className="text-headline-sm text-text-muted">
            {jornadas.length === 1
              ? '1 jornada en revisión'
              : `${jornadas.length} jornadas en revisión`}
          </h2>
          <ul className="flex flex-col divide-y divide-border overflow-hidden rounded-lg border border-border bg-surface">
            {jornadas.map((jornada) => (
              <li key={jornada.id}>
                <Link
                  to={`/app/programacion/jornadas/${jornada.id}/revision`}
                  className="flex items-center gap-4 p-4 transition-colors hover:bg-background focus-visible:bg-background"
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-headline-sm text-text-main">
                      {jornada.titulo}
                      {jornada.contexto && (
                        <span className="font-normal text-text-muted"> · {jornada.contexto}</span>
                      )}
                    </p>
                    <p className="mt-0.5 text-body-md text-text-muted">
                      {jornada.partidos !== undefined &&
                        `${jornada.partidos} ${jornada.partidos === 1 ? 'partido' : 'partidos'} · `}
                      Enviada por {jornada.enviadoPor.nombre} ·{' '}
                      {formatearFechaHora(jornada.enviadoEn)}
                    </p>
                  </div>
                  <span className="hidden text-label-md text-primary sm:inline">Revisar</span>
                  <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-text-muted" />
                </Link>
              </li>
            ))}
          </ul>
          {hayMas && (
            <p className="text-body-sm text-text-muted">
              Se muestran las jornadas enviadas primero. Dictamina estas para ver las siguientes.
            </p>
          )}
        </section>
      )}
    </div>
  )
}
