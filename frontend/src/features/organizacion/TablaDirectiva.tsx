import { FilaDirectiva } from '@/features/organizacion/FilaDirectiva'
import type { FilaDirectiva as Fila } from '@/features/organizacion/directiva'
import type { CargoDirectiva, TipoCargoDirectiva } from '@/features/organizacion/tipos'

type Props = {
  filas: readonly Fila[]
  puedeRegistrar: boolean
  puedeAutorizar: boolean
  /** La lista se está actualizando; se atenúa sin ocultar los datos vigentes. */
  actualizando: boolean
  onRegistrar: (cargo: TipoCargoDirectiva) => void
  onAutorizar: (cargo: CargoDirectiva) => void
}

const CLASE_ENCABEZADO = 'px-4 py-3 text-left text-label-sm text-text-muted'

/**
 * Tabla de la directiva del club (F-15, RF-102). Bajo `lg` cada fila se apila como tarjeta y
 * conserva los roles de tabla para lectores de pantalla (RNF-007, RNF-008).
 */
export function TablaDirectiva({
  filas,
  puedeRegistrar,
  puedeAutorizar,
  actualizando,
  onRegistrar,
  onAutorizar,
}: Props) {
  // La columna solo se pinta si alguna fila tendrá una acción: registrar o autorizar un pendiente.
  const conAcciones =
    puedeRegistrar || (puedeAutorizar && filas.some(({ tipo }) => tipo === 'pendiente'))

  return (
    <div
      className={[
        'overflow-hidden rounded-lg border border-border bg-surface transition-opacity',
        actualizando ? 'opacity-60' : '',
      ].join(' ')}
    >
      <table role="table" aria-busy={actualizando} aria-label="Directiva del club" className="block w-full lg:table">
        <thead role="rowgroup" className="hidden border-b-2 border-border bg-background lg:table-header-group">
          <tr role="row">
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Cargo
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Titular
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Vigencia
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Estado
            </th>
            {conAcciones && (
              <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} text-right`}>
                Acciones
              </th>
            )}
          </tr>
        </thead>
        <tbody role="rowgroup" className="block lg:table-row-group">
          {filas.map((fila) => (
            <FilaDirectiva
              key={fila.tipo === 'vacia' ? `vacia-${fila.cargo}` : fila.cargo.id}
              fila={fila}
              conAcciones={conAcciones}
              puedeRegistrar={puedeRegistrar}
              puedeAutorizar={puedeAutorizar}
              onRegistrar={onRegistrar}
              onAutorizar={onAutorizar}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
