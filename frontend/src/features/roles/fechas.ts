import { ZONA_NEGOCIO } from '@/lib/zonaHoraria'

const formatoFechaHora = new Intl.DateTimeFormat('es-MX', {
  timeZone: ZONA_NEGOCIO,
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** «7 nov 2026, 10:05» en la zona de negocio. */
export function formatearFechaHora(instante: string): string {
  return formatoFechaHora.format(new Date(instante)).replace(/\./g, '')
}
