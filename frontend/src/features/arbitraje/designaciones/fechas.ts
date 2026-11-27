import {
  aIsoConDesplazamiento,
  fechaEnZona,
  lunesDeSemana,
  sumarDias,
} from '@/features/canchas/fechas'

const formatoDia = new Intl.DateTimeFormat('es-MX', { timeZone: 'UTC', day: 'numeric' })
const formatoMes = new Intl.DateTimeFormat('es-MX', { timeZone: 'UTC', month: 'short' })
const formatoAnio = new Intl.DateTimeFormat('es-MX', { timeZone: 'UTC', year: 'numeric' })

function partes(fecha: string) {
  const dia = new Date(`${fecha}T00:00:00Z`)

  return {
    dia: formatoDia.format(dia),
    mes: formatoMes.format(dia).replace('.', ''),
    anio: formatoAnio.format(dia),
  }
}

/**
 * Rango legible de la semana que empieza en `lunes`: «23 – 29 nov 2026», «30 nov – 6 dic 2026» o
 * «28 dic 2026 – 3 ene 2027».
 */
export function formatearSemanaCorta(lunes: string): string {
  const inicio = partes(lunes)
  const fin = partes(sumarDias(lunes, 6))

  if (inicio.anio !== fin.anio) {
    return `${inicio.dia} ${inicio.mes} ${inicio.anio} – ${fin.dia} ${fin.mes} ${fin.anio}`
  }
  if (inicio.mes !== fin.mes) {
    return `${inicio.dia} ${inicio.mes} – ${fin.dia} ${fin.mes} ${fin.anio}`
  }

  return `${inicio.dia} – ${fin.dia} ${fin.mes} ${fin.anio}`
}

/** Lunes de la semana de un instante, en la zona de la Liga. */
export function lunesDeInstante(instante: string): string {
  return lunesDeSemana(fechaEnZona(instante))
}

/** Inicio del día de hoy en la zona de la Liga, en ISO con desplazamiento (`…T00:00:00-06:00`). */
export function inicioDeHoy(ahora: number | Date = Date.now()): string {
  return aIsoConDesplazamiento(`${fechaEnZona(ahora)}T00:00`)
}
