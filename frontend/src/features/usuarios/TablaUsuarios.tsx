import { FilaUsuario } from '@/features/usuarios/FilaUsuario'
import type { Usuario } from '@/features/usuarios/tipos'

type Props = {
  usuarios: readonly Usuario[]
  /** La lista se está actualizando; se atenúa sin ocultar los datos vigentes. */
  actualizando: boolean
  onGestionar: (usuario: Usuario) => void
}

const CLASE_ENCABEZADO = 'px-4 py-3 text-left text-label-sm text-text-muted'

/**
 * Tabla de usuarios (F-12, H-03.2). Muestra los roles activos como chips y el estado de la cuenta;
 * bajo `lg` cada fila se apila como tarjeta y conserva los roles de tabla para lectores de pantalla
 * (RNF-007, RNF-008).
 */
export function TablaUsuarios({ usuarios, actualizando, onGestionar }: Props) {
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
        aria-label="Usuarios de la Liga"
        className="block w-full lg:table"
      >
        <thead
          role="rowgroup"
          className="hidden border-b-2 border-border bg-background lg:table-header-group"
        >
          <tr role="row" className="lg:table-row">
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Usuario
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Roles y ámbitos
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Estado
            </th>
            <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
              Alta
            </th>
            <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} text-right`}>
              Acciones
            </th>
          </tr>
        </thead>
        <tbody role="rowgroup" className="block lg:table-row-group">
          {usuarios.map((usuario) => (
            <FilaUsuario
              key={usuario.id}
              usuario={usuario}
              onGestionar={onGestionar}
            />
          ))}
        </tbody>
      </table>
    </div>
  )
}
