import { ArrowDown, ArrowUp } from 'lucide-react'
import { FilaAuditoria } from '@/features/auditoria/FilaAuditoria'
import type { DireccionOrden, RegistroAuditoria } from '@/features/auditoria/tipos'

type Props = {
  registros: readonly RegistroAuditoria[]
  orden: DireccionOrden
  actualizando: boolean
  onAlternarOrden: () => void
}

const CLASE_ENCABEZADO = 'hidden px-0 py-3 pr-3 text-left text-label-sm text-text-muted xl:table-cell'

/**
 * Registros de auditoría (F-14, RF-134): solo lectura y sin columna de rol, porque la auditoría
 * no guarda con cuál actuó el usuario (convenciones de API §6.1). Bajo `xl` las filas se apilan;
 * del encabezado solo queda el orden.
 */
export function TablaAuditoria({ registros, orden, actualizando, onAlternarOrden }: Props) {
  const IconoOrden = orden === 'desc' ? ArrowDown : ArrowUp

  return (
    <div
      className={[
        'overflow-hidden rounded-lg border border-border bg-surface transition-opacity',
        actualizando ? 'opacity-60' : '',
      ].join(' ')}
    >
      <table role="table" aria-busy={actualizando} aria-label="Registros de auditoría" className="block w-full xl:table xl:table-fixed">
        <thead role="rowgroup" className="block border-b-2 border-border bg-background xl:table-header-group">
          <tr role="row" className="block xl:table-row">
            <th role="columnheader" scope="col" className="hidden w-12 xl:table-cell">
              <span className="sr-only">Detalle</span>
            </th>
            <th
              role="columnheader"
              scope="col"
              aria-sort={orden === 'desc' ? 'descending' : 'ascending'}
              className="block text-left xl:table-cell xl:w-[11.5rem]"
            >
              <button
                type="button"
                onClick={onAlternarOrden}
                className="inline-flex min-h-11 items-center gap-1.5 rounded-sm px-3 text-label-sm text-text-muted transition-colors hover:text-text-main xl:px-0"
              >
                Fecha y hora
                <IconoOrden aria-hidden="true" className="size-3.5" />
              </button>
            </th>
            <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} xl:w-[8rem]`}>Usuario</th>
            <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} xl:w-[8rem]`}>Módulo</th>
            <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} xl:w-[10rem]`}>Acción</th>
            <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} xl:w-[10rem]`}>Entidad</th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>Motivo</th>
          </tr>
        </thead>
        <tbody role="rowgroup" className="block xl:table-row-group [&>tr:last-child]:border-b-0">
          {registros.map((registro) => (
            <FilaAuditoria key={registro.id} registro={registro} />
          ))}
        </tbody>
      </table>
    </div>
  )
}
