import { EditorComunicado } from '@/features/publicacion/EditorComunicado'
import { VistaComunicado } from '@/features/publicacion/VistaComunicado'
import type { Comunicado } from '@/features/publicacion/tipos'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

type Props = {
  comunicado: Comunicado
  onActualizado: (comunicado: Comunicado) => void
  onEnviado: (comunicado: Comunicado) => void
  onResuelto: (comunicado: Comunicado) => void
}

const REDACTAR = 'publicacion.comunicado:REDACTAR'

/**
 * Elige la vista del comunicado: editor mientras está en borrador y la sesión
 * puede redactar; en cualquier otro caso, solo lectura con las acciones que
 * permitan el estado y los permisos (RF-090, UI-031).
 */
export function DetalleComunicado({
  comunicado,
  onActualizado,
  onEnviado,
  onResuelto,
}: Props) {
  const { yo } = useSesionActual()
  const puedeRedactar = tienePermiso(yo.permisos, REDACTAR)

  if (comunicado.estadoPublicacion === 'BORRADOR' && puedeRedactar) {
    return (
      <EditorComunicado
        key={comunicado.id}
        comunicado={comunicado}
        onCreado={onActualizado}
        onActualizado={onActualizado}
        onEnviado={onEnviado}
      />
    )
  }

  return <VistaComunicado comunicado={comunicado} onResuelto={onResuelto} />
}
