import { CornerUpLeft } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { nombreRedactor } from '@/features/publicacion/actores'
import { etiquetaEstadoPublicacion } from '@/features/publicacion/etiquetas'
import { formatearFechaHora } from '@/features/publicacion/fechas'
import type { Comunicado } from '@/features/publicacion/tipos'
import type { Yo } from '@/features/seguridad/tipos'

type Props = {
  comunicado: Comunicado
  seleccionado: boolean
  yo: Yo
  onSeleccionar: (id: string) => void
}

function resumen(cuerpo: string): string {
  const texto = cuerpo.replace(/\s+/g, ' ').trim()

  return texto.length > 150 ? `${texto.slice(0, 150).trimEnd()}…` : texto
}

/**
 * Fila de la bandeja de comunicados (UI-031). Es un botón porque elige el
 * comunicado que se muestra en el panel de detalle; el estado va con insignia
 * y texto, no solo con color (RNF-008).
 */
export function BotonComunicado({ comunicado, seleccionado, yo, onSeleccionar }: Props) {
  const etiqueta = etiquetaEstadoPublicacion[comunicado.estadoPublicacion]
  const devuelto =
    comunicado.estadoPublicacion === 'BORRADOR' && Boolean(comunicado.observaciones)

  return (
    <button
      type="button"
      aria-current={seleccionado ? 'true' : undefined}
      onClick={() => onSeleccionar(comunicado.id)}
      className={[
        'flex w-full flex-col gap-2 rounded-md border p-3 text-left transition-colors',
        seleccionado
          ? 'border-primary bg-primary/5'
          : 'border-border bg-surface hover:bg-background',
      ].join(' ')}
    >
      <span className="flex items-start justify-between gap-2">
        <span className="line-clamp-2 text-label-md text-text-main">{comunicado.titulo}</span>
        <Insignia variante={etiqueta.variante} icono={etiqueta.icono}>
          {etiqueta.texto}
        </Insignia>
      </span>
      <span className="line-clamp-2 text-body-sm text-text-muted">{resumen(comunicado.cuerpo)}</span>
      {devuelto && (
        <span className="flex items-center gap-1 text-body-sm text-warning-strong">
          <CornerUpLeft aria-hidden="true" className="size-3.5 shrink-0" />
          Devuelto con observaciones
        </span>
      )}
      <span className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1 text-body-sm text-text-muted">
        <span className="truncate">{nombreRedactor(comunicado.redactadoPor, yo)}</span>
        <time dateTime={comunicado.actualizadoEn}>
          {formatearFechaHora(comunicado.actualizadoEn)}
        </time>
      </span>
    </button>
  )
}
