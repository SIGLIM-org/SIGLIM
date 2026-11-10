import type { Intervalo } from '@/features/canchas/tipos'
import { ZONA_NEGOCIO } from '@/lib/zonaHoraria'

const MS_POR_MINUTO = 60_000

const formatoPartes = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA_NEGOCIO,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})
const formatoDesplazamiento = new Intl.DateTimeFormat('en-US', {
  timeZone: ZONA_NEGOCIO,
  timeZoneName: 'longOffset',
})
/* Una fecha sin hora se representa en UTC para que la zona de la máquina no la mueva. */
const formatoDiaLargo = new Intl.DateTimeFormat('es-MX', {
  timeZone: 'UTC',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
})
const formatoAnio = new Intl.DateTimeFormat('es-MX', { timeZone: 'UTC', year: 'numeric' })
const formatoDiaSemana = new Intl.DateTimeFormat('es-MX', { timeZone: 'UTC', weekday: 'short' })
const formatoDiaMes = new Intl.DateTimeFormat('es-MX', { timeZone: 'UTC', day: 'numeric', month: 'short' })

function parte(partes: Intl.DateTimeFormatPart[], tipo: Intl.DateTimeFormatPartTypes): string {
  return partes.find((elemento) => elemento.type === tipo)?.value ?? ''
}

function mayuscula(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

function fechaUtc(fecha: string): Date {
  return new Date(`${fecha}T00:00:00Z`)
}

/** Fecha `AAAA-MM-DD` en la zona de negocio de un instante. */
export function fechaEnZona(instante: number | string | Date): string {
  const partes = formatoPartes.formatToParts(new Date(instante))

  return `${parte(partes, 'year')}-${parte(partes, 'month')}-${parte(partes, 'day')}`
}

/** Minutos desde la medianoche, en la zona de negocio. */
export function minutosEnZona(instante: number | string | Date): number {
  const partes = formatoPartes.formatToParts(new Date(instante))

  return Number(parte(partes, 'hour')) * 60 + Number(parte(partes, 'minute'))
}

export function sumarDias(fecha: string, dias: number): string {
  const resultado = fechaUtc(fecha)
  resultado.setUTCDate(resultado.getUTCDate() + dias)

  return resultado.toISOString().slice(0, 10)
}

/** Lunes de la semana de una fecha `AAAA-MM-DD`. */
export function lunesDeSemana(fecha: string): string {
  const diaSemana = fechaUtc(fecha).getUTCDay()

  return sumarDias(fecha, diaSemana === 0 ? -6 : 1 - diaSemana)
}

export function semanaActual(ahora: number | Date = Date.now()): string {
  return lunesDeSemana(fechaEnZona(ahora))
}

export function semanaAnterior(lunes: string): string {
  return sumarDias(lunes, -7)
}

export function semanaSiguiente(lunes: string): string {
  return sumarDias(lunes, 7)
}

/** Lunes de la semana del parámetro `?semana`; si falta o no es una fecha, la semana actual. */
export function semanaDeParametro(valor: string | null, ahora: number | Date = Date.now()): string {
  if (valor && /^\d{4}-\d{2}-\d{2}$/.test(valor) && !Number.isNaN(fechaUtc(valor).getTime())) {
    return lunesDeSemana(valor)
  }

  return semanaActual(ahora)
}

/** Desplazamiento de la zona en un instante, como `-06:00`; se pide a `Intl`, nunca se fija. */
function desplazamientoEn(instante: number): string {
  const nombre = parte(formatoDesplazamiento.formatToParts(new Date(instante)), 'timeZoneName')
  const coincidencia = /GMT([+-]\d{2}:\d{2})/.exec(nombre)

  return coincidencia ? coincidencia[1] : '+00:00'
}

function minutosDeDesplazamiento(desplazamiento: string): number {
  const signo = desplazamiento.startsWith('-') ? -1 : 1
  const [horas, minutos] = desplazamiento.slice(1).split(':').map(Number)

  return signo * (horas * 60 + minutos)
}

/**
 * Convierte la hora de Morelia de un `<input type="datetime-local">`
 * (`AAAA-MM-DDTHH:mm`) a ISO 8601 con desplazamiento.
 */
export function aIsoConDesplazamiento(valorLocal: string): string {
  const pared = valorLocal.slice(0, 16)
  const comoUtc = Date.parse(`${pared}:00Z`)
  const aproximado = comoUtc - minutosDeDesplazamiento(desplazamientoEn(comoUtc)) * MS_POR_MINUTO

  return `${pared}:00${desplazamientoEn(aproximado)}`
}

/** Rango de la semana: lunes 00:00 a lunes siguiente 00:00 (`hasta` exclusivo). */
export function rangoSemana(lunes: string): Intervalo {
  return {
    desde: aIsoConDesplazamiento(`${lunes}T00:00`),
    hasta: aIsoConDesplazamiento(`${semanaSiguiente(lunes)}T00:00`),
  }
}

/** Hora `HH:mm` en la zona de negocio. */
export function formatearHora(instante: string): string {
  const partes = formatoPartes.formatToParts(new Date(instante))

  return `${parte(partes, 'hour')}:${parte(partes, 'minute')}`
}

export function formatearHorario(periodo: Intervalo): string {
  return `${formatearHora(periodo.desde)} – ${formatearHora(periodo.hasta)}`
}

/** «Lunes 26 de octubre». */
export function formatearDiaLargo(fecha: string): string {
  return mayuscula(formatoDiaLargo.format(fechaUtc(fecha)).replace(',', ''))
}

/** «Lunes 26 de octubre – Domingo 1 de noviembre de 2026». */
export function formatearRangoSemana(lunes: string): string {
  const domingo = sumarDias(lunes, 6)

  return `${formatearDiaLargo(lunes)} – ${formatearDiaLargo(domingo)} de ${formatoAnio.format(fechaUtc(domingo))}`
}

/** Encabezado de una columna: día abreviado y número, como «Lun 26». */
export function formatearDiaColumna(fecha: string): string {
  return `${mayuscula(formatoDiaSemana.format(fechaUtc(fecha)).replace('.', ''))} ${Number(fecha.slice(8))}`
}

/** Fecha de un partido en una lista: «Lun 26 oct». */
export function formatearFechaCorta(instante: string): string {
  const fecha = fechaEnZona(instante)

  return `${mayuscula(formatoDiaSemana.format(fechaUtc(fecha)).replace('.', ''))} ${formatoDiaMes.format(fechaUtc(fecha)).replace('.', '')}`
}
