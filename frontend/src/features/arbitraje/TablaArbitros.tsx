import { FilaArbitro } from '@/features/arbitraje/FilaArbitro'
import type { Arbitro, DecisionArbitro } from '@/features/arbitraje/tipos'

type Props = {
  arbitros: readonly Arbitro[]
  /** La lista se está actualizando; se atenúa sin ocultar los datos vigentes. */
  actualizando: boolean
  puedeValidar: boolean
  onDictamen: (arbitro: Arbitro, decision: DecisionArbitro) => void
}

const CLASE_ENCABEZADO = 'px-4 py-3 text-left text-label-sm text-text-muted'

/**
 * Tabla de árbitros de F-20. En escritorio es una tabla densa; bajo `lg` cada fila se apila como
 * tarjeta y conserva los roles de tabla para lectores de pantalla (RNF-007, RNF-008).
 */
export function TablaArbitros({ arbitros, actualizando, puedeValidar, onDictamen }: Props) {
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
        aria-label="Árbitros presentados por los clubes"
        className="block w-full lg:table"
      >
        <thead
          role="rowgroup"
          className="hidden border-b-2 border-border bg-background lg:table-header-group"
        >
          <tr role="row" className="lg:table-row">
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Árbitro
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Club que lo presenta
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Propuesta
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Estado
            </th>
            <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} text-right`}>
              Acciones
            </th>
          </tr>
        </thead>
        <tbody role="rowgroup" className="block lg:table-row-group">
          {arbitros.map((arbitro) => (
            <FilaArbitro
              key={arbitro.id}
              arbitro={arbitro}
              puedeValidar={puedeValidar}
              onDictamen={onDictamen}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
