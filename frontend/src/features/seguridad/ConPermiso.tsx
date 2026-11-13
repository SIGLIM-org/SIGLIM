import type { ReactNode } from 'react'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tieneAlgunPermiso, tieneTodosLosPermisos } from '@/features/seguridad/permisos'

type Props = {
  /** Código `modulo.recurso:ACCION` o varios; por omisión basta con uno de ellos. */
  permiso: string | readonly string[]
  /**
   * Con `true` se exigen todos los permisos de `permiso`, no solo uno (DEC-116). Por omisión es
   * `false`: basta con uno.
   */
  todos?: boolean
  children: ReactNode
  /** Qué mostrar en su lugar. Por omisión no se muestra nada. */
  alternativa?: ReactNode
}

/**
 * Oculta una acción cuando la sesión no tiene el permiso (H-02.4, RNF-003).
 * Debe usarse dentro de `ProveedorSesionActual` (aplicación interna). Es una
 * ayuda de interfaz, nunca la barrera de seguridad: el backend vuelve a
 * autorizar cada operación y responde 403 `VAL-015` (ADR-0007).
 */
export function ConPermiso({
  permiso,
  todos = false,
  children,
  alternativa = null,
}: Props) {
  const { yo } = useSesionActual()
  const codigos = typeof permiso === 'string' ? [permiso] : permiso
  const permitido = todos
    ? tieneTodosLosPermisos(yo.permisos, codigos)
    : tieneAlgunPermiso(yo.permisos, codigos)

  return permitido ? (
    <>{children}</>
  ) : (
    <>{alternativa}</>
  )
}
