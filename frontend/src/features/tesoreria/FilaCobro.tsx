import { ReceiptText } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { estadoCobro, etiquetaTipoCobro } from '@/features/tesoreria/etiquetas'
import { formatearFecha, formatearMonto } from '@/features/tesoreria/fechas'
import type { Cobro, EstadoCobro } from '@/features/tesoreria/tipos'

type Props = {
  cobro: Cobro
  /** Si viene, la fila agrega la celda de acciones (permiso `tesoreria.pago:REGISTRAR`). */
  onRegistrarPago?: (cobro: Cobro) => void
}

/** Estados que admiten un pago (VAL-028, convenciones §6.13). */
const ESTADOS_PAGABLES: ReadonlySet<EstadoCobro> = new Set(['PENDIENTE', 'VENCIDO'])

const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

/**
 * Fila de la tabla de cobros (F-33, H-29.2). Bajo `lg` se apila como tarjeta con sus etiquetas
 * visibles (RNF-007). Con `onRegistrarPago`, ofrece «Registrar pago» solo en los cobros
 * `PENDIENTE` o `VENCIDO` (H-31.2, RF-139, VAL-028); en los demás la celda queda vacía. El pago
 * simulado de la Directiva (H-30) no está aquí.
 */
export function FilaCobro({ cobro, onRegistrarPago }: Props) {
  const estado = estadoCobro(cobro.estado)
  const admitePago = ESTADOS_PAGABLES.has(cobro.estado)

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

      {onRegistrarPago && (
        <td
          role="cell"
          className={[
            'block lg:table-cell lg:px-4 lg:py-3 lg:text-right',
            admitePago ? 'pt-3' : 'hidden',
          ].join(' ')}
        >
          {admitePago && (
            <Button
              variante="secundario"
              icono={ReceiptText}
              aria-label={`Registrar pago de «${cobro.concepto}»`}
              onClick={() => onRegistrarPago(cobro)}
              className="w-full whitespace-nowrap lg:w-auto"
            >
              Registrar pago
            </Button>
          )}
        </td>
      )}
    </tr>
  )
}
