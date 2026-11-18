import {
  aIsoConDesplazamiento,
  fechaEnZona,
  formatearDiaLargo,
  formatearFechaCorta,
  formatearHora,
} from '@/features/canchas/fechas'
import type { DisponibilidadArbitral, PeriodoArbitral } from '@/features/arbitraje/tipos'

/** «Lunes 23 de noviembre de 2026» para una fecha `AAAA-MM-DD`. */
export function formatearDiaConAnio(fecha: string): string {
  return `${formatearDiaLargo(fecha)} de ${fecha.slice(0, 4)}`
}

/**
 * Fecha y horario legibles de un periodo `[desde, hasta)` en hora de Morelia. Si el periodo termina
 * otro día (p. ej. registrado por el API con un rango largo), el fin lleva su fecha.
 */
export function describirPeriodo({ desde, hasta }: PeriodoArbitral): {
  fecha: string
  horario: string
} {
  const dia = fechaEnZona(desde)
  const fin =
    fechaEnZona(hasta) === dia
      ? formatearHora(hasta)
      : `${formatearFechaCorta(hasta)}, ${formatearHora(hasta)}`

  return { fecha: formatearDiaConAnio(dia), horario: `${formatearHora(desde)} – ${fin}` }
}

/** Periodos agrupados por el día en que inician, conservando el orden del API (por inicio). */
export function agruparPorDia(
  periodos: readonly DisponibilidadArbitral[],
): { dia: string; periodos: DisponibilidadArbitral[] }[] {
  const grupos = new Map<string, DisponibilidadArbitral[]>()

  for (const periodo of periodos) {
    const dia = fechaEnZona(periodo.periodo.desde)
    grupos.set(dia, [...(grupos.get(dia) ?? []), periodo])
  }

  return [...grupos].map(([dia, lista]) => ({ dia, periodos: lista }))
}

/** Periodo del formulario: una fecha y dos horas `HH:mm` de Morelia, en ISO con desplazamiento. */
export function periodoDelFormulario(
  fecha: string,
  horaInicial: string,
  horaFinal: string,
): PeriodoArbitral {
  return {
    desde: aIsoConDesplazamiento(`${fecha}T${horaInicial}`),
    hasta: aIsoConDesplazamiento(`${fecha}T${horaFinal}`),
  }
}
