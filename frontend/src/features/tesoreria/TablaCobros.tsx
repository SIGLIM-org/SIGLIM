import { FilaCobro } from '@/features/tesoreria/FilaCobro'
import type { Cobro } from '@/features/tesoreria/tipos'

type Props = {
  cobros: readonly Cobro[]
  /** La lista se está actualizando; se atenúa sin ocultar los datos vigentes. */
  actualizando: boolean
  /** Si viene, agrega la columna «Acciones» con «Registrar pago» en los cobros que lo admiten. */
  onRegistrarPago?: (cobro: Cobro) => void
}

const CLASE_ENCABEZADO = 'px-4 py-3 text-left text-label-sm text-text-muted'

/**
 * Tabla de cobros de F-33 (H-29.2, H-31.2). Muestra el tipo, el destino, el monto, la fecha límite
 * y el estado; con `onRegistrarPago` (permiso `tesoreria.pago:REGISTRAR`) agrega la columna de
 * acciones. No incluye folios ni acciones que el contrato de la API no exponga.
 */
export function TablaCobros({ cobros, actualizando, onRegistrarPago }: Props) {
  return (
    <div
      className={[
        'overflow-hidden rounded-lg border border-border bg-surface transition-opacity',
        actualizando ? 'opacity-60' : '',
      ].join(' ')}
    >
      <table
        role="table"
        aria-busy={actualizando}
        aria-label="Cobros registrados"
        className="block w-full lg:table"
      >
        <thead
          role="rowgroup"
          className="hidden border-b-2 border-border bg-background lg:table-header-group"
        >
          <tr role="row" className="lg:table-row">
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Cobro
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Destino
            </th>
            <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} text-right`}>
              Monto
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Fecha límite
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Estado
            </th>
            {onRegistrarPago && (
              <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} text-right`}>
                Acciones
              </th>
            )}
          </tr>
        </thead>
        <tbody role="rowgroup" className="block lg:table-row-group">
          {cobros.map((cobro) => (
            <FilaCobro key={cobro.id} cobro={cobro} onRegistrarPago={onRegistrarPago} />
          ))}
        </tbody>
      </table>
    </div>
  )
}
