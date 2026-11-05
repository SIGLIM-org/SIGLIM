import type { Ambito, AsignacionUsuario } from '@/features/usuarios/tipos'

/** Etiqueta visible del ámbito de una asignación (RN-054). */
export const etiquetaAmbito: Record<Ambito, string> = {
  LIGA: 'Liga',
  CLUB: 'Club',
  PROPIO: 'Propio',
}

const formatoFecha = new Intl.DateTimeFormat('es-MX', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'America/Mexico_City',
})

/** Fecha de alta en la zona de negocio, sin hora: el contrato solo guarda el instante. */
export function formatearFechaAlta(iso: string): string {
  const fecha = new Date(iso)

  return Number.isNaN(fecha.getTime()) ? 'Sin fecha' : formatoFecha.format(fecha)
}

const TRATAMIENTOS = new Set(['lic.', 'prof.', 'dr.', 'dra.', 'ing.', 'mtro.', 'mtra.'])

/** Iniciales para el identificador visual de la tabla; no sustituyen el nombre. */
export function iniciales(nombre: string): string {
  const partes = nombre
    .trim()
    .split(/\s+/)
    .filter((parte) => parte && !TRATAMIENTOS.has(parte.toLowerCase()))

  if (partes.length === 0) {
    return '?'
  }

  return partes
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase())
    .join('')
}

/** Texto de una asignación: «Directiva de club · Halcones FC» o «Árbitro · Propio». */
export function describirAsignacion(asignacion: AsignacionUsuario): string {
  return [asignacion.rol.nombre, asignacion.club?.nombre ?? etiquetaAmbito[asignacion.ambito]].join(
    ' · ',
  )
}
