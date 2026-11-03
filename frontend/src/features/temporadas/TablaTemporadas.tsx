import { FilaTemporada } from '@/features/temporadas/FilaTemporada'
import type { Temporada } from '@/features/temporadas/tipos'

type Props = {
  temporadas: readonly Temporada[]
  activandoId: string | null
  /** La lista se está actualizando; se atenúa sin ocultar los datos vigentes. */
  actualizando: boolean
  onEditar: (temporada: Temporada) => void
  onActivar: (temporada: Temporada) => void
  onCerrar: (temporada: Temporada) => void
}

const CLASE_ENCABEZADO = 'px-4 py-3 text-left text-label-sm text-text-muted'

/**
 * Tabla de temporadas (F-10, H-04.2). Bajo `lg` cada fila se apila como tarjeta y conserva los
 * roles de tabla para lectores de pantalla (RNF-007, RNF-008).
 */
export function TablaTemporadas({
  temporadas,
  activandoId,
  actualizando,
  onEditar,
  onActivar,
  onCerrar,
}: Props) {
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
        aria-label="Temporadas de la Liga"
        className="block w-full lg:table"
      >
        <thead
          role="rowgroup"
          className="hidden border-b-2 border-border bg-background lg:table-header-group"
        >
          <tr role="row" className="lg:table-row">
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Temporada
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Periodo
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Estado
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Estructura
            </th>
            <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} text-right`}>
              Acciones
            </th>
          </tr>
        </thead>
        <tbody role="rowgroup" className="block lg:table-row-group">
          {temporadas.map((temporada) => (
            <FilaTemporada
              key={temporada.id}
              temporada={temporada}
              activando={activandoId === temporada.id}
              accionesDeshabilitadas={activandoId !== null}
              onEditar={onEditar}
              onActivar={onActivar}
              onCerrar={onCerrar}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
