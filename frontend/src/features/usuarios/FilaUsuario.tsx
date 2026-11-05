import { Eye, Settings2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import {
  describirAsignacion,
  formatearFechaAlta,
  iniciales,
} from '@/features/usuarios/etiquetas'
import type { Usuario } from '@/features/usuarios/tipos'

type Props = {
  usuario: Usuario
  onGestionar: (usuario: Usuario) => void
}

const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

function ChipsRoles({ usuario }: { usuario: Usuario }) {
  const activas = usuario.asignaciones.filter((asignacion) => asignacion.activa)

  if (activas.length === 0) {
    return <span className="text-body-sm text-text-muted">Sin rol asignado</span>
  }

  return (
    <ul className="flex flex-wrap gap-1.5">
      {activas.map((asignacion) => (
        <li
          key={asignacion.id}
          className="inline-flex max-w-full items-center rounded-md border border-border bg-background px-2 py-1 text-label-sm text-text-main"
          title={describirAsignacion(asignacion)}
        >
          <span className="break-words">{describirAsignacion(asignacion)}</span>
        </li>
      ))}
    </ul>
  )
}

/**
 * Fila de la tabla de usuarios (F-12, H-03.2, RF-094). Bajo `lg` se apila como tarjeta con sus
 * etiquetas visibles (RNF-007). El botón de gestionar abre el panel lateral; con solo consulta dice
 * «Ver detalle» y el panel se muestra sin acciones de escritura.
 */
export function FilaUsuario({ usuario, onGestionar }: Props) {
  return (
    <tr
      role="row"
      className="block border-b border-border p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:border-b lg:p-0"
    >
      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Usuario</span>
        <div className="flex min-w-0 items-center gap-3">
          <span
            aria-hidden="true"
            className="grid size-10 shrink-0 place-items-center rounded-md border border-border bg-background text-label-md font-semibold text-text-main"
          >
            {iniciales(usuario.nombreMostrado)}
          </span>
          <div className="min-w-0">
            <button
              type="button"
              onClick={() => onGestionar(usuario)}
              className="block max-w-full truncate text-left text-headline-sm text-text-main underline-offset-2 hover:underline"
            >
              {usuario.nombreMostrado}
            </button>
            <span className="block break-all font-mono text-mono-data text-text-muted">
              {usuario.correo ?? 'Sin correo'}
            </span>
          </div>
        </div>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Roles y ámbitos</span>
        <ChipsRoles usuario={usuario} />
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Estado</span>
        <Insignia variante={usuario.activo ? 'exito' : 'neutra'}>
          {usuario.activo ? 'Activo' : 'Inactivo'}
        </Insignia>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Alta</span>
        <span className="font-mono text-mono-data text-text-main">
          {formatearFechaAlta(usuario.creadoEn)}
        </span>
      </td>

      <td role="cell" className="block pt-3 lg:table-cell lg:px-4 lg:py-3 lg:text-right">
        <span className={CLASE_ETIQUETA}>Acciones</span>
        <ConPermiso
          permiso="seguridad.usuario:EDITAR"
          alternativa={
            <Button
              variante="secundario"
              icono={Eye}
              aria-label={`Ver detalle de ${usuario.nombreMostrado}`}
              onClick={() => onGestionar(usuario)}
            >
              Ver detalle
            </Button>
          }
        >
          <Button
            variante="secundario"
            icono={Settings2}
            aria-label={`Gestionar a ${usuario.nombreMostrado}`}
            onClick={() => onGestionar(usuario)}
          >
            Gestionar
          </Button>
        </ConPermiso>
      </td>
    </tr>
  )
}
