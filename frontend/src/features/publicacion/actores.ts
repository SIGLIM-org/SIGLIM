import type { Yo } from '@/features/seguridad/tipos'

/**
 * Nombre visible de quien redactó. El contrato solo trae `perfiles.id`; si no
 * es la sesión actual se usa la oficina que redacta (la Secretaría), sin
 * inventar nombres de personas.
 */
export function nombreRedactor(perfilId: string, yo: Yo): string {
  return perfilId === yo.perfil.id
    ? `${yo.perfil.nombreMostrado} (tú)`
    : 'Secretaría'
}

/** Nombre visible de quien aprobó o devolvió; la oficina es la Presidencia. */
export function nombreAprobador(perfilId: string | undefined, yo: Yo): string {
  if (!perfilId) {
    return '—'
  }

  return perfilId === yo.perfil.id
    ? `${yo.perfil.nombreMostrado} (tú)`
    : 'Presidencia'
}
