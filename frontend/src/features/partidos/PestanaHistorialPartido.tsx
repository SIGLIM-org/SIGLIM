import { ArrowRight } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import type { CambioEstadoPartido } from '@/features/partidos/tipos'
import { etiquetaEstadoPartido, varianteEstadoPartido } from '@/features/programacion/etiquetas'
import { formatearFechaHora } from '@/features/temporadas/fechas'

type Props = {
  historial: readonly CambioEstadoPartido[]
}

/**
 * Transiciones del partido con usuario, fecha y motivo (F-25, RF-045, RN-021), de la más reciente a
 * la más antigua. Las automáticas quedan a nombre del perfil Sistema (H-15.2).
 */
export function PestanaHistorialPartido({ historial }: Props) {
  const recientes = [...historial].reverse()

  return (
    <ol className="flex flex-col divide-y divide-border rounded-lg border border-border bg-surface">
      {recientes.map((cambio, indice) => (
        <li key={`${cambio.cambiadoEn}-${indice}`} className="flex flex-col gap-2 p-4 sm:p-5">
          <div className="flex flex-wrap items-center gap-2">
            {cambio.estadoAnterior && (
              <>
                <Insignia variante={varianteEstadoPartido[cambio.estadoAnterior]}>
                  {etiquetaEstadoPartido[cambio.estadoAnterior]}
                </Insignia>
                <ArrowRight aria-label="pasó a" className="size-4 shrink-0 text-text-muted" />
              </>
            )}
            <Insignia variante={varianteEstadoPartido[cambio.estadoNuevo]}>
              {etiquetaEstadoPartido[cambio.estadoNuevo]}
            </Insignia>
          </div>
          <p className="whitespace-pre-line break-words text-body-md text-text-main">{cambio.motivo}</p>
          <p className="text-body-sm text-text-muted">
            <span className="font-mono tabular-nums">{formatearFechaHora(cambio.cambiadoEn)}</span>
            {' · '}
            {cambio.cambiadoPor ? (cambio.cambiadoPor.nombre ?? 'Usuario de la liga') : 'Sin usuario registrado'}
          </p>
        </li>
      ))}
    </ol>
  )
}
