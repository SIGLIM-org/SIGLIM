import type { Yo } from '@/features/seguridad/tipos'

/**
 * Nombre visible de quien registró una interpretación. El contrato solo trae `perfiles.id`, así
 * que no se inventan nombres: la sesión actual se muestra con su nombre y las demás cuentas como
 * «otro perfil de la Liga» (RF-007, DEC-57).
 */
export function nombreRegistrador(perfilId: string, yo: Yo): string {
  return perfilId === yo.perfil.id ? `${yo.perfil.nombreMostrado} (tú)` : 'otro perfil de la Liga'
}

/** Nombre visible de quien resolvió; mientras está pendiente no hay ninguno (RF-007). */
export function nombreResolutor(perfilId: string | undefined, yo: Yo): string {
  if (!perfilId) {
    return '—'
  }

  return perfilId === yo.perfil.id ? `${yo.perfil.nombreMostrado} (tú)` : 'otro perfil de la Liga'
}
