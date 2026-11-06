import { FilaEquipo } from '@/features/organizacion/FilaEquipo'
import type { Equipo } from '@/features/organizacion/tipos'

type Props = {
  equipos: readonly Equipo[]
  /** Con «Todas» las temporadas, cada fila dice a cuál pertenece. */
  mostrarTemporada: boolean
  /** La sesión puede editar equipos; sin ese permiso no hay columna de acciones. */
  conAcciones: boolean
  /** La lista se está actualizando; se atenúa sin ocultar los datos vigentes. */
  actualizando: boolean
  onEditar: (equipo: Equipo) => void
  onDarDeBaja: (equipo: Equipo) => void
}

const CLASE_ENCABEZADO = 'px-4 py-3 text-left text-label-sm text-text-muted'

/**
 * Equipos del club (F-15, RF-009). Bajo `lg` cada fila se apila como tarjeta y conserva los
 * roles de tabla para lectores de pantalla (RNF-007, RNF-008).
 */
export function TablaEquipos({
  equipos,
  mostrarTemporada,
  conAcciones,
  actualizando,
  onEditar,
  onDarDeBaja,
}: Props) {
  return (
    <div
      className={[
        'overflow-hidden rounded-lg border border-border bg-surface transition-opacity',
        actualizando ? 'opacity-60' : '',
      ].join(' ')}
    >
      <table role="table" aria-busy={actualizando} aria-label="Equipos del club" className="block w-full lg:table">
        <thead role="rowgroup" className="hidden border-b-2 border-border bg-background lg:table-header-group">
          <tr role="row">
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Equipo
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Categoría
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Grupo
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
          {equipos.map((equipo) => (
            <FilaEquipo
              key={equipo.id}
              equipo={equipo}
              mostrarTemporada={mostrarTemporada}
              conAcciones={conAcciones}
              onEditar={onEditar}
              onDarDeBaja={onDarDeBaja}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
