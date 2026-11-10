import type { Permiso } from '@/features/seguridad/tipos'

/** Datos mínimos de un permiso que necesita la interfaz. */
type PermisoVisible = Pick<Permiso, 'codigo' | 'modulo'>

/**
 * ¿La sesión tiene exactamente este permiso? No mira el ámbito: el backend
 * decide si alcanza el recurso concreto (VAL-015, VAL-024, ADR-0007).
 */
export function tienePermiso(
  permisos: readonly Pick<Permiso, 'codigo'>[],
  codigo: string,
): boolean {
  return permisos.some((permiso) => permiso.codigo === codigo)
}

/** ¿La sesión tiene al menos uno de los permisos indicados? */
export function tieneAlgunPermiso(
  permisos: readonly Pick<Permiso, 'codigo'>[],
  codigos: readonly string[],
): boolean {
  return codigos.some((codigo) => tienePermiso(permisos, codigo))
}

/**
 * ¿Algún permiso de esos módulos da acceso a la sección? Una sección sin
 * módulos es visible siempre (Inicio). `notificaciones.*` no cuenta para las
 * secciones: la campana de F-09 no crea una entrada del menú (H-01.3).
 */
export function puedeVerModulo(
  permisos: readonly PermisoVisible[],
  modulos: readonly string[],
): boolean {
  if (modulos.length === 0) {
    return true
  }

  return permisos.some(
    ({ codigo, modulo }) =>
      !codigo.startsWith('notificaciones.') && modulos.includes(modulo),
  )
}
