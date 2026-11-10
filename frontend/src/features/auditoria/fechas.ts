import { ZONA_NEGOCIO } from '@/lib/zonaHoraria'

/* El desplazamiento -06:00 es fijo porque México eliminó el horario de verano en 2022 y la auditoría de SIGLIM empieza en 2026. */
const INICIO_DE_DIA = 'T00:00:00-06:00'

const formatoFechaHora = new Intl.DateTimeFormat('es-MX', {
  timeZone: ZONA_NEGOCIO,
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

/** Día siguiente a un `AAAA-MM-DD`, con aritmética UTC para no depender de la zona del navegador. */
function diaSiguiente(dia: string): string {
  const [anio, mes, numero] = dia.split('-').map(Number)

  return new Date(Date.UTC(anio, mes - 1, numero + 1)).toISOString().slice(0, 10)
}

/** ¿Es un día real con formato `AAAA-MM-DD`? Rechaza fechas como `2026-02-31`. */
export function esDiaValido(dia: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dia)) {
    return false
  }

  const [anio, mes, numero] = dia.split('-').map(Number)
  const fecha = new Date(Date.UTC(anio, mes - 1, numero))

  return fecha.toISOString().slice(0, 10) === dia
}

/**
 * Convierte los días del filtro en los instantes de la API (RF-134): `desde` se incluye desde las
 * 00:00 de su día y `hasta`, que es excluyente, cae a las 00:00 del día siguiente para que el
 * último día elegido entre completo.
 */
export function rangoAInstantes(
  desdeDia?: string,
  hastaDia?: string,
): { desde?: string; hasta?: string } {
  return {
    ...(desdeDia ? { desde: `${desdeDia}${INICIO_DE_DIA}` } : {}),
    ...(hastaDia ? { hasta: `${diaSiguiente(hastaDia)}${INICIO_DE_DIA}` } : {}),
  }
}

/** «25 oct 2026, 12:30:00» en la zona de negocio. */
export function formatearOcurridoEn(instante: string): string {
  return formatoFechaHora.format(new Date(instante)).replace(/\./g, '')
}
