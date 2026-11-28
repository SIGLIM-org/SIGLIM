import { Link } from 'react-router'
import { CalendarCheck, CalendarRange, ChevronRight, ListOrdered } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { usePublicacionesPendientes } from '@/features/publicacion/api'
import { formatearFechaHora } from '@/features/publicacion/fechas'

/*
 * [PROPUESTA] Una liga tiene pocas jornadas en revisión a la vez: se pide la primera página de 100
 * pendientes y se avisa si hay más, en lugar de paginar.
 */
const PENDIENTES_POR_PAGINA = 100

/** Cada pendiente abre su revisión: la fecha completa (H-35.3) o la jornada suelta (H-13.2). */
function rutaDeRevision(tipo: 'FECHA' | 'JORNADA', id: string): string {
  return tipo === 'FECHA'
    ? `/app/programacion/jornadas/fechas/${id}/revision`
    : `/app/programacion/jornadas/${id}/revision`
}

/**
 * Programación en revisión para quien puede aprobarla (F-23, H-13.2, H-35.3), desde
 * `GET /publicaciones/pendientes`, del envío más antiguo al más reciente: las fechas de programación,
 * que se aprueban completas (DEC-175), y las jornadas sueltas (fases finales o manuales). Los
 * comunicados de la misma bandeja tienen su propia pantalla, así que aquí se omiten.
 */
export function BandejaRevisionJornadas() {
  const pendientes = usePublicacionesPendientes(0, PENDIENTES_POR_PAGINA)
  const jornadas = (pendientes.data?.contenido ?? []).filter(
    (pendiente): pendiente is typeof pendiente & { tipo: 'FECHA' | 'JORNADA' } =>
      pendiente.tipo === 'FECHA' || pendiente.tipo === 'JORNADA',
  )
  const hayMas = (pendientes.data?.totalPaginas ?? 0) > 1

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
          Programación por revisar
        </h1>
        <p className="mt-1 max-w-2xl text-body-md text-text-muted">
          Revisa cada fecha o jornada que envía el Programador y apruébala para publicarla en el
          portal, o devuélvela con observaciones. Una fecha se aprueba completa, con todas sus
          jornadas.
        </p>
      </header>

      {pendientes.isPending && (
        <EstadoCarga etiqueta="Cargando la programación por revisar…" filas={3} columnas={3} />
      )}

      {pendientes.isError && (
        <ErrorConsulta error={pendientes.error} onReintentar={() => void pendientes.refetch()} />
      )}

      {!pendientes.isPending && !pendientes.isError && jornadas.length === 0 && (
        <EstadoVacio
          icono={CalendarCheck}
          titulo="No hay programación por revisar"
          descripcion="Cuando el Programador envíe una fecha o una jornada a aprobación, aparecerá aquí."
        />
      )}

      {jornadas.length > 0 && (
        <section aria-labelledby="jornadas-por-revisar" className="flex flex-col gap-3">
          <h2 id="jornadas-por-revisar" className="text-headline-sm text-text-muted">
            {jornadas.length === 1 ? '1 pendiente de dictamen' : `${jornadas.length} pendientes de dictamen`}
          </h2>
          <ul className="flex flex-col divide-y divide-border overflow-hidden rounded-lg border border-border bg-surface">
            {jornadas.map((jornada) => (
              <li key={jornada.id}>
                <Link
                  to={rutaDeRevision(jornada.tipo, jornada.id)}
                  className="flex items-center gap-4 p-4 transition-colors hover:bg-background focus-visible:bg-background"
                >
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-headline-sm text-text-main">
                      {jornada.tipo === 'FECHA' ? (
                        <Insignia variante="informacion" icono={CalendarRange}>
                          Fecha completa
                        </Insignia>
                      ) : (
                        <Insignia variante="neutra" icono={ListOrdered}>
                          Jornada
                        </Insignia>
                      )}
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
              Se muestran los envíos más antiguos primero. Dictamina estos para ver los siguientes.
            </p>
          )}
        </section>
      )}
    </div>
  )
}
