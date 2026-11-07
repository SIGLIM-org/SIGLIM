import { FilaCobro } from '@/features/tesoreria/FilaCobro'
import type { Cobro } from '@/features/tesoreria/tipos'

type Props = {
  cobros: readonly Cobro[]
  /** La lista se está actualizando; se atenúa sin ocultar los datos vigentes. */
  actualizando: boolean
  /** La sesión tiene `tesoreria.pago:PAGAR`; sin él no se muestra la columna de acciones. */
  puedePagar: boolean
  onPagar: (cobro: Cobro) => void
}

const CLASE_ENCABEZADO = 'px-4 py-3 text-left text-label-sm text-text-muted'

/**
 * Tabla de cobros de F-33 (H-29.2). Muestra el tipo, el destino, el monto, la fecha límite, el
 * estado y, cuando la sesión puede pagar, la acción de pago simulado de H-30.2.
 */
export function TablaCobros({ cobros, actualizando, puedePagar, onPagar }: Props) {
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
            {puedePagar && (
              <th
                role="columnheader"
                scope="col"
                className={`${CLASE_ENCABEZADO} text-right`}
              >
                Acciones
              </th>
            )}
          </tr>
        </thead>
        <tbody role="rowgroup" className="block lg:table-row-group">
          {cobros.map((cobro) => (
            <FilaCobro
              key={cobro.id}
              cobro={cobro}
              puedePagar={puedePagar}
              onPagar={onPagar}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
