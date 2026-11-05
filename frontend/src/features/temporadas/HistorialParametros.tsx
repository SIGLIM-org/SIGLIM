import { Eye, History } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaDeCampo } from '@/features/temporadas/etiquetas'
import { formatearFecha } from '@/features/temporadas/fechas'
import { InsigniaOficial } from '@/features/temporadas/InsigniaOficial'
import type { ResumenVersion } from '@/features/temporadas/tipos'

type Props = {
  historial: ResumenVersion[]
  onVerInstantanea: (version: number) => void
}

/**
 * Historial de versiones de parámetros, de la mayor a la menor (F-11, RF-006, D7). La primera es
 * la vigente. Cada versión muestra quién y cuándo la registró, su motivo (RN-021) y qué cambió
 * respecto a la anterior.
 */
export function HistorialParametros({ historial, onVerInstantanea }: Props) {
  return (
    <section
      aria-labelledby="titulo-historial-parametros"
      className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4"
    >
      <h3
        id="titulo-historial-parametros"
        className="flex items-center gap-2 text-headline-sm text-text-main"
      >
        <History aria-hidden="true" className="size-5 shrink-0 text-text-muted" />
        Historial de versiones
      </h3>

      {historial.length === 0 ? (
        <p className="text-body-md text-text-muted">Aún no hay versiones registradas.</p>
      ) : (
        <ol className="flex flex-col divide-y divide-border">
          {historial.map((version, indice) => (
            <li key={version.id} className="flex flex-col gap-2 py-3 first:pt-0 last:pb-0">
              <div className="flex flex-wrap items-center gap-2">
                <h4 className="text-label-md text-text-main">Versión {version.version}</h4>
                {indice === 0 && <Insignia variante="informacion">Vigente</Insignia>}
                <InsigniaOficial esDemo={version.esDemo} />
              </div>
              <p className="text-body-sm text-text-muted">
                {formatearFecha(version.creadoEn.slice(0, 10))}
                {version.creadoPor && ` · ${version.creadoPor.nombre}`}
              </p>
              <p className="break-words text-body-md text-text-main">«{version.motivo}»</p>
              {version.camposCambiados.length > 0 && (
                <div>
                  <p className="text-body-sm text-text-muted">
                    {indice === historial.length - 1 ? 'Valores registrados:' : 'Cambió:'}
                  </p>
                  <ul className="mt-1 flex flex-wrap gap-1">
                    {version.camposCambiados.map((campo) => (
                      <li
                        key={campo}
                        className="rounded-sm bg-background px-1.5 py-0.5 text-body-sm text-text-main"
                      >
                        {etiquetaDeCampo(campo)}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <button
                type="button"
                aria-label={`Ver instantánea de la versión ${version.version}`}
                onClick={() => onVerInstantanea(version.version)}
                className="inline-flex min-h-11 items-center gap-1.5 self-start text-label-md text-primary underline-offset-2 hover:underline"
              >
                <Eye aria-hidden="true" className="size-4 shrink-0" />
                Ver instantánea
              </button>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
