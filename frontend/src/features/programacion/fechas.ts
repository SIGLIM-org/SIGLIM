import {
  fechaEnZona,
  formatearFechaCorta,
  formatearHora,
} from '@/features/canchas/fechas'
import type { Partido } from '@/features/programacion/tipos'

export { aIsoConDesplazamiento, formatearFechaCorta, formatearHora } from '@/features/canchas/fechas'

/** Valor para un `<input type="datetime-local">` con la hora de Morelia. */
export function valorDatetimeLocal(instante: string): string {
  return `${fechaEnZona(instante)}T${formatearHora(instante)}`
}

/** «10:00 – 12:00» del bloque del partido. */
export function horarioPartido(partido: Partido): string {
  return `${formatearHora(partido.inicio)} – ${formatearHora(partido.finBloque)}`
}

/** «Sáb 10 mar» o «Sáb 10 mar – Dom 11 mar» según los días que ocupan los partidos. */
export function rangoDePartidos(partidos: readonly Partido[]): string {
  if (partidos.length === 0) {
    return ''
  }

  const ordenados = [...partidos].sort((primero, segundo) =>
    primero.inicio.localeCompare(segundo.inicio),
  )
  const primero = ordenados[0]
  const ultimo = ordenados[ordenados.length - 1]

  if (fechaEnZona(primero.inicio) === fechaEnZona(ultimo.inicio)) {
    return formatearFechaCorta(primero.inicio)
  }

  return `${formatearFechaCorta(primero.inicio)} – ${formatearFechaCorta(ultimo.inicio)}`
}
