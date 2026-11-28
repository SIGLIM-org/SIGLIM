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

/* Un día `AAAA-MM-DD` sin hora se formatea en UTC para que la zona de la máquina no lo mueva. */
const formatoDiaSemanaCorto = new Intl.DateTimeFormat('es-MX', { timeZone: 'UTC', weekday: 'short' })
const formatoMesCorto = new Intl.DateTimeFormat('es-MX', { timeZone: 'UTC', month: 'short' })

function partesDia(dia: string) {
  const fecha = new Date(`${dia}T00:00:00Z`)
  const semana = formatoDiaSemanaCorto.format(fecha).replace('.', '')

  return {
    semana: semana.charAt(0).toUpperCase() + semana.slice(1),
    dia: fecha.getUTCDate(),
    mes: formatoMesCorto.format(fecha).replace('.', ''),
  }
}

/**
 * Días de una fecha de programación: «Sáb 14 y Dom 15 nov», o con el mes en cada día si cambia
 * («Sáb 31 oct y Dom 1 nov»). Un solo día: «Dom 15 nov».
 */
export function diasDeFecha(dias: readonly string[]): string {
  if (dias.length === 0) {
    return ''
  }

  const partes = [...dias].sort().map(partesDia)
  const mismoMes = partes.every(({ mes }) => mes === partes[0].mes)
  const textos = partes.map(({ semana, dia, mes }, indice) =>
    mismoMes && indice < partes.length - 1 ? `${semana} ${dia}` : `${semana} ${dia} ${mes}`,
  )

  if (textos.length === 1) {
    return textos[0]
  }

  return `${textos.slice(0, -1).join(', ')} y ${textos[textos.length - 1]}`
}

/** «Dom 10:00 – 12:00»: el día de la semana y el bloque, para la columna Horario de F-22. */
export function horarioConDia(partido: Pick<Partido, 'inicio' | 'finBloque'>): string {
  const semana = partesDia(fechaEnZona(partido.inicio)).semana

  return `${semana} ${formatearHora(partido.inicio)} – ${formatearHora(partido.finBloque)}`
}
