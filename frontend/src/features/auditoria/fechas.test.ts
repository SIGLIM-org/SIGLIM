import { describe, expect, it } from 'vitest'
import { esDiaValido, formatearOcurridoEn, rangoAInstantes } from '@/features/auditoria/fechas'

describe('fechas de auditoría', () => {
  it('RF-134: desde incluye su día y hasta excluye el día siguiente', () => {
    expect(rangoAInstantes('2026-10-01', '2026-10-25')).toEqual({
      desde: '2026-10-01T00:00:00-06:00',
      hasta: '2026-10-26T00:00:00-06:00',
    })
  })

  it('el mismo día en ambos extremos abarca ese día completo', () => {
    expect(rangoAInstantes('2026-10-25', '2026-10-25')).toEqual({
      desde: '2026-10-25T00:00:00-06:00',
      hasta: '2026-10-26T00:00:00-06:00',
    })
  })

  it('cruza el fin de mes, también en febrero bisiesto', () => {
    expect(rangoAInstantes(undefined, '2026-10-31').hasta).toBe('2026-11-01T00:00:00-06:00')
    expect(rangoAInstantes(undefined, '2028-02-28').hasta).toBe('2028-02-29T00:00:00-06:00')
    expect(rangoAInstantes(undefined, '2026-02-28').hasta).toBe('2026-03-01T00:00:00-06:00')
  })

  it('cruza el fin de año', () => {
    expect(rangoAInstantes(undefined, '2026-12-31').hasta).toBe('2027-01-01T00:00:00-06:00')
  })

  it('con solo desde o solo hasta omite el otro extremo', () => {
    expect(rangoAInstantes('2026-10-01')).toEqual({ desde: '2026-10-01T00:00:00-06:00' })
    expect(rangoAInstantes(undefined, '2026-10-01')).toEqual({ hasta: '2026-10-02T00:00:00-06:00' })
    expect(rangoAInstantes()).toEqual({})
  })

  it('reconoce solo días reales', () => {
    expect(esDiaValido('2026-10-31')).toBe(true)
    expect(esDiaValido('2026-02-31')).toBe(false)
    expect(esDiaValido('2026-13-01')).toBe(false)
    expect(esDiaValido('ayer')).toBe(false)
  })

  it('formatea en la zona de negocio, sea cual sea el desplazamiento de entrada', () => {
    const esperado = formatearOcurridoEn('2026-10-25T12:30:00-06:00')

    expect(esperado).toMatch(/^25 oct 2026, 12:30:00$/)
    expect(formatearOcurridoEn('2026-10-25T18:30:00Z')).toBe(esperado)
  })
})
