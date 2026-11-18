import { useId, type ReactNode } from 'react'
import { ShieldCheck } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import {
  etiquetaEstadoSancion,
  etiquetaTipoSancionPropia,
} from '@/features/registros/mi-informacion/etiquetas'
import type { SancionPropia } from '@/features/registros/mi-informacion/tipos'
import { formatearPeriodo } from '@/features/temporadas/fechas'

function Dato({ termino, children }: { termino: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap gap-x-2">
      <dt className="text-text-muted">{termino}:</dt>
      <dd className="min-w-0 break-words text-text-main">{children}</dd>
    </div>
  )
}

/**
 * Sanciones de la persona, no por registro (DEC-141): tipo, estado, partidos cumplidos, vigencia,
 * artículo del reglamento y folio del expediente. Va una sola vez, después de los registros (DEC-152).
 *
 * Refs: RF-020, RF-144, DEC-141, DEC-151, DEC-152
 */
export function SeccionSanciones({ sanciones }: { sanciones: readonly SancionPropia[] }) {
  const id = useId()

  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      <h2 id={id} className="text-headline-md text-text-main">
        Sanciones
      </h2>

      {sanciones.length === 0 ? (
        <p className="flex items-center gap-2 rounded-lg border border-border bg-surface px-4 py-3 text-body-md text-text-muted">
          <ShieldCheck aria-hidden="true" className="size-5 shrink-0" />
          Sin sanciones
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-border rounded-lg border border-border bg-surface">
          {sanciones.map((sancion) => {
            const estado = etiquetaEstadoSancion[sancion.estado]

            return (
              <li key={sancion.id} className="flex flex-col gap-2 px-4 py-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-label-md text-text-main">
                    {etiquetaTipoSancionPropia[sancion.tipo]}
                  </p>
                  <Insignia variante={estado.variante} icono={estado.icono}>
                    {estado.texto}
                  </Insignia>
                </div>
                <dl className="flex flex-col gap-1 text-body-md">
                  {sancion.partidos !== null && (
                    <Dato termino="Partidos cumplidos">
                      <span className="font-mono tabular-nums">{sancion.partidosCumplidos}</span>{' '}
                      de <span className="font-mono tabular-nums">{sancion.partidos}</span>
                    </Dato>
                  )}
                  <Dato termino="Vigencia">
                    {formatearPeriodo(sancion.vigenteDesde, sancion.vigenteHasta ?? undefined)}
                  </Dato>
                  {sancion.articulo && (
                    <Dato termino="Artículo">
                      {sancion.articulo.numero} · {sancion.articulo.descripcion}
                    </Dato>
                  )}
                  <Dato termino="Expediente">
                    <span className="font-mono">{sancion.folio}</span>
                  </Dato>
                </dl>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
