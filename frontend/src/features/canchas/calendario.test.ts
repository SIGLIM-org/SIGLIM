import { describe, expect, it } from 'vitest'
import { HORARIO_VISIBLE, organizarSemana } from '@/features/canchas/calendario'
import type { BloqueOcupacion, Intervalo } from '@/features/canchas/tipos'

const RANGO: Intervalo = {
  desde: '2026-10-26T00:00:00-06:00',
  hasta: '2026-11-02T00:00:00-06:00',
}

function hora(dia: string, horaMinuto: string) {
  return `2026-${dia}T${horaMinuto}:00-06:00`
}

function bloque(tipo: BloqueOcupacion['tipo'], desde: string, hasta: string): BloqueOcupacion {
  return { tipo, periodo: { desde, hasta } }
}

describe('organizarSemana', () => {
  it('reparte en 7 días y calcula la posición en minutos', () => {
    const semana = organizarSemana(
      [bloque('DISPONIBLE', hora('10-28', '09:00'), hora('10-28', '12:30'))],
      RANGO,
    )

    expect(semana.dias).toHaveLength(7)
    expect(semana.dias[2].fecha).toBe('2026-10-28')
    expect(semana.dias[2].segmentos[0]).toMatchObject({ inicio: 540, fin: 750 })
  })

  it('D4: el bloqueo queda por encima de la disponibilidad y el partido hasta arriba', () => {
    const semana = organizarSemana(
      [
        bloque('PARTIDO', hora('10-26', '10:00'), hora('10-26', '12:00')),
        bloque('BLOQUEO', hora('10-26', '10:00'), hora('10-26', '11:00')),
        bloque('MANTENIMIENTO', hora('10-26', '10:00'), hora('10-26', '11:00')),
        bloque('DISPONIBLE', hora('10-26', '10:00'), hora('10-26', '11:00')),
      ],
      RANGO,
    )
    const capa = (tipo: BloqueOcupacion['tipo']) =>
      semana.dias[0].segmentos.find((segmento) => segmento.bloque.tipo === tipo)?.capa ?? -1

    expect(capa('BLOQUEO')).toBeGreaterThan(capa('DISPONIBLE'))
    expect(capa('MANTENIMIENTO')).toBe(capa('BLOQUEO'))
    expect(capa('PARTIDO')).toBeGreaterThan(capa('BLOQUEO'))
  })

  it('PEN-007: marca el partido que cae dentro de un bloqueo; [) no cuenta el contiguo', () => {
    const semana = organizarSemana(
      [
        bloque('BLOQUEO', hora('10-26', '10:00'), hora('10-26', '12:00')),
        bloque('PARTIDO', hora('10-26', '11:00'), hora('10-26', '13:00')),
        bloque('PARTIDO', hora('10-26', '12:00'), hora('10-26', '14:00')),
      ],
      RANGO,
    )
    const partidos = semana.dias[0].segmentos.filter(({ bloque: { tipo } }) => tipo === 'PARTIDO')

    expect(partidos.map(({ dentroDeBloqueo }) => dentroDeBloqueo)).toEqual([true, false])
  })

  it('un bloque que cruza medianoche se parte en los dos días', () => {
    const semana = organizarSemana(
      [bloque('BLOQUEO', hora('10-26', '22:00'), hora('10-27', '02:00'))],
      RANGO,
    )

    expect(semana.dias[0].segmentos[0]).toMatchObject({ inicio: 1320, fin: 1440 })
    expect(semana.dias[1].segmentos[0]).toMatchObject({ inicio: 0, fin: 120 })
  })

  it('recorta un bloque al rango de la semana', () => {
    const semana = organizarSemana(
      [bloque('BLOQUEO', hora('10-20', '08:00'), hora('10-26', '09:00'))],
      RANGO,
    )

    expect(semana.dias[0].segmentos[0]).toMatchObject({ inicio: 0, fin: 540 })
    expect(semana.dias.slice(1).every(({ segmentos }) => segmentos.length === 0)).toBe(true)
  })

  it('ignora un bloque fuera de la semana', () => {
    const semana = organizarSemana(
      [bloque('BLOQUEO', hora('11-02', '00:00'), hora('11-02', '09:00'))],
      RANGO,
    )

    expect(semana.dias.every(({ segmentos }) => segmentos.length === 0)).toBe(true)
  })

  it('mantiene el horario visible y lo amplía a horas completas si un bloque no cabe', () => {
    expect(organizarSemana([], RANGO).horario).toEqual(HORARIO_VISIBLE)

    const amplio = organizarSemana(
      [
        bloque('PARTIDO', hora('10-26', '05:30'), hora('10-26', '07:30')),
        bloque('PARTIDO', hora('10-27', '22:00'), hora('10-27', '23:10')),
      ],
      RANGO,
    )

    expect(amplio.horario).toEqual({ inicio: 5 * 60, fin: 24 * 60 })
  })
})
