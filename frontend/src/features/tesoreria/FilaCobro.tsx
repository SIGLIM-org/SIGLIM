import { Insignia } from '@/components/ui/Insignia'
import { estadoCobro, etiquetaTipoCobro } from '@/features/tesoreria/etiquetas'
import { formatearFecha, formatearMonto } from '@/features/tesoreria/fechas'
import type { Cobro } from '@/features/tesoreria/tipos'

type Props = {
  cobro: Cobro
}

const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

/**
 * Fila de la tabla de cobros (F-33, H-29.2). Bajo `lg` se apila como tarjeta con sus etiquetas
 * visibles (RNF-007). No ofrece acciones de pago: esas historias son H-30 y H-31.
 */
export function FilaCobro({ cobro }: Props) {
  const estado = estadoCobro(cobro.estado)

  return (
    <tr
      role="row"
      className="block border-b border-border p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:border-b lg:p-0"
    >
      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Cobro</span>
        <span className="inline-flex items-center rounded-md border border-border bg-background px-2 py-0.5 text-label-sm text-text-muted">
          {etiquetaTipoCobro[cobro.tipo]}
        </span>
        <span className="mt-1 block break-words text-headline-sm text-text-main">
          {cobro.concepto}
        </span>
        {cobro.periodoInicio && (
          <span className="block text-body-sm text-text-muted">
            Periodo desde el {formatearFecha(cobro.periodoInicio)}
          </span>
        )}
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Destino</span>
        <span className="block break-words text-body-md text-text-main">
          {cobro.equipo?.nombre ?? cobro.club.nombre}
        </span>
        <span className="block text-body-sm text-text-muted">
          {cobro.equipo ? cobro.club.nombre : 'Cargo al club'}
        </span>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3 lg:text-right">
        <span className={CLASE_ETIQUETA}>Monto</span>
        <span className="block font-mono text-mono-data tabular-nums text-text-main">
          {formatearMonto(cobro.monto)}
        </span>
        <span className="block text-body-sm text-text-muted">MXN</span>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Fecha límite</span>
        <span className="block font-mono text-mono-data tabular-nums text-text-main">
          {formatearFecha(cobro.fechaLimite)}
        </span>
      </td>

      <td role="cell" className="block pt-3 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Estado</span>
        <Insignia variante={estado.variante}>{estado.texto}</Insignia>
      </td>
    </tr>
  )
}
