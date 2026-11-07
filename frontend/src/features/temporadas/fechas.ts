import { ZONA_NEGOCIO } from '@/lib/zonaHoraria'

/*
 * Las fechas de la temporada son días `AAAA-MM-DD` sin hora. Se formatean en UTC
 * para que la zona de la máquina no las mueva un día.
 */
const formatoFecha = new Intl.DateTimeFormat('es-MX', {
  timeZone: 'UTC',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

const formatoFechaHora = new Intl.DateTimeFormat('es-MX', {
  timeZone: ZONA_NEGOCIO,
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function fechaUtc(fecha: string): Date {
  return new Date(`${fecha}T00:00:00Z`)
}

/** «1 oct 2025». */
export function formatearFecha(fecha: string): string {
  return formatoFecha.format(fechaUtc(fecha)).replace('.', '')
}

/** «3 nov 2026, 14:05» en la zona de negocio, para instantes con hora (RF-007). */
export function formatearFechaHora(instante: string): string {
  return formatoFechaHora.format(new Date(instante)).replace(/\./g, '')
}

/** «1 oct 2025 – 30 jun 2026» o «Desde 1 oct 2025» si aún no tiene fecha de fin. */
export function formatearPeriodo(desde: string, hasta?: string): string {
  return hasta ? `${formatearFecha(desde)} – ${formatearFecha(hasta)}` : `Desde ${formatearFecha(desde)}`
}
