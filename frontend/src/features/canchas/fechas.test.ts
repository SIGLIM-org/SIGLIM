import { describe, expect, it } from 'vitest'
import {
  aIsoConDesplazamiento,
  fechaEnZona,
  formatearHorario,
  formatearRangoSemana,
  lunesDeSemana,
  minutosEnZona,
  rangoSemana,
  semanaAnterior,
  semanaDeParametro,
  semanaSiguiente,
} from '@/features/canchas/fechas'

describe('fechas de canchas', () => {
  it('calcula el lunes de la semana, también en domingo y al cambiar de mes', () => {
    expect(lunesDeSemana('2026-10-26')).toBe('2026-10-26')
    expect(lunesDeSemana('2026-10-30')).toBe('2026-10-26')
    expect(lunesDeSemana('2026-11-01')).toBe('2026-10-26')
    expect(lunesDeSemana('2026-03-01')).toBe('2026-02-23')
  })

  it('mueve la semana, cruzando el año', () => {
    expect(semanaSiguiente('2026-12-28')).toBe('2027-01-04')
    expect(semanaAnterior('2027-01-04')).toBe('2026-12-28')
  })

  it('el rango de la semana lleva desplazamiento y hasta es el lunes siguiente', () => {
    expect(rangoSemana('2026-10-26')).toEqual({
      desde: '2026-10-26T00:00:00-06:00',
      hasta: '2026-11-02T00:00:00-06:00',
    })
  })

  it('convierte un valor de datetime-local a ISO con desplazamiento de Morelia', () => {
    const iso = aIsoConDesplazamiento('2026-10-26T10:30')

    expect(iso).toBe('2026-10-26T10:30:00-06:00')
    expect(new Date(iso).toISOString()).toBe('2026-10-26T16:30:00.000Z')
  })

  it('el desplazamiento sale de la zona, no fijo: en 2020 aún había horario de verano', () => {
    expect(aIsoConDesplazamiento('2020-07-01T10:00')).toBe('2020-07-01T10:00:00-05:00')
  })

  it('lee fecha y minutos en la zona de negocio, sea cual sea el desplazamiento de entrada', () => {
    const instante = '2026-10-27T02:15:00Z'

    expect(fechaEnZona(instante)).toBe('2026-10-26')
    expect(minutosEnZona(instante)).toBe(20 * 60 + 15)
  })

  it('el parámetro semana se normaliza al lunes; uno inválido da la semana actual', () => {
    const ahora = Date.parse('2026-10-30T18:00:00Z')

    expect(semanaDeParametro('2026-11-01', ahora)).toBe('2026-10-26')
    expect(semanaDeParametro('otra-cosa', ahora)).toBe('2026-10-26')
    expect(semanaDeParametro(null, ahora)).toBe('2026-10-26')
  })

  it('formatea el rango de la semana y el horario', () => {
    expect(formatearRangoSemana('2026-10-26')).toBe(
      'Lunes 26 de octubre – Domingo 1 de noviembre de 2026',
    )
    expect(
      formatearHorario({ desde: '2026-10-26T16:00:00Z', hasta: '2026-10-26T18:00:00Z' }),
    ).toBe('10:00 – 12:00')
  })
})
