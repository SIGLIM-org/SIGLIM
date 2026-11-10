import {
  aIsoConDesplazamiento,
  fechaEnZona,
  minutosEnZona,
  sumarDias,
} from '@/features/canchas/fechas'
import type { BloqueOcupacion, Intervalo, TipoBloqueOcupacion } from '@/features/canchas/tipos'

/** [PROPUESTA] Horario visible de la rejilla; se amplía si un bloque de la semana no cabe. */
export const HORARIO_VISIBLE = { inicio: 7 * 60, fin: 22 * 60 } as const

const MINUTOS_DEL_DIA = 24 * 60
const DIAS_DE_LA_SEMANA = 7

/** D4: `DISPONIBLE` de fondo, `BLOQUEO`/`MANTENIMIENTO` encima y `PARTIDO` hasta arriba. */
const CAPAS: Record<TipoBloqueOcupacion, number> = {
  DISPONIBLE: 0,
  BLOQUEO: 1,
  MANTENIMIENTO: 1,
  PARTIDO: 2,
}

export type SegmentoDia = {
  clave: string
  bloque: BloqueOcupacion
  capa: number
  /** Minutos desde la medianoche de ese día; `fin` llega a 1440 si sigue al día siguiente. */
  inicio: number
  fin: number
  /** Partido que cae dentro de un bloqueo o mantenimiento (PEN-007). */
  dentroDeBloqueo: boolean
}

export type DiaSemana = {
  fecha: string
  segmentos: SegmentoDia[]
}

export type SemanaOrganizada = {
  dias: DiaSemana[]
  /** Horario visible en minutos: 07:00–22:00, o más si un bloque lo necesita. */
  horario: { inicio: number; fin: number }
}

function seTraslapan(a: Intervalo, b: Intervalo): boolean {
  return Date.parse(a.desde) < Date.parse(b.hasta) && Date.parse(b.desde) < Date.parse(a.hasta)
}

function inicioDelDia(fecha: string): number {
  return Date.parse(aIsoConDesplazamiento(`${fecha}T00:00`))
}

/**
 * Reparte los bloques en los días que tocan: los recorta en medianoche y al rango
 * de la semana, y calcula su posición, su capa y si un partido quedó dentro de un
 * bloqueo. Dentro de cada día van ordenados por inicio y, a igual inicio, por capa.
 */
export function organizarSemana(bloques: readonly BloqueOcupacion[], rango: Intervalo): SemanaOrganizada {
  const limiteDesde = Date.parse(rango.desde)
  const limiteHasta = Date.parse(rango.hasta)
  const restricciones = bloques.filter(({ tipo }) => tipo === 'BLOQUEO' || tipo === 'MANTENIMIENTO')
  const primerDia = fechaEnZona(rango.desde)

  const dias: DiaSemana[] = Array.from({ length: DIAS_DE_LA_SEMANA }, (_, indice) => {
    const fecha = sumarDias(primerDia, indice)
    const abre = inicioDelDia(fecha)
    const cierra = inicioDelDia(sumarDias(fecha, 1))
    const segmentos: SegmentoDia[] = []

    bloques.forEach((bloque, posicion) => {
      const desde = Math.max(Date.parse(bloque.periodo.desde), limiteDesde, abre)
      const hasta = Math.min(Date.parse(bloque.periodo.hasta), limiteHasta, cierra)

      if (desde >= hasta) {
        return
      }

      segmentos.push({
        clave: `${posicion}-${fecha}`,
        bloque,
        capa: CAPAS[bloque.tipo],
        inicio: minutosEnZona(desde),
        fin: hasta === cierra ? MINUTOS_DEL_DIA : minutosEnZona(hasta),
        dentroDeBloqueo:
          bloque.tipo === 'PARTIDO' &&
          restricciones.some(({ periodo }) => seTraslapan(periodo, bloque.periodo)),
      })
    })

    segmentos.sort((a, b) => a.inicio - b.inicio || a.capa - b.capa)

    return { fecha, segmentos }
  })

  const todos = dias.flatMap(({ segmentos }) => segmentos)

  return {
    dias,
    horario: {
      inicio: Math.min(HORARIO_VISIBLE.inicio, ...todos.map(({ inicio }) => Math.floor(inicio / 60) * 60)),
      fin: Math.max(HORARIO_VISIBLE.fin, ...todos.map(({ fin }) => Math.ceil(fin / 60) * 60)),
    },
  }
}
