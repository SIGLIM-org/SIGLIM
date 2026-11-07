import { describe, expect, it } from 'vitest'
import {
  contarCobros,
  diaSiguiente,
  formatearFecha,
  formatearFechaHora,
  formatearMonto,
  rangoDeCobros,
} from '@/features/tesoreria/fechas'

describe('fechas de tesorería', () => {
  it('H-29.2: el último día del filtro se envía como el día siguiente, excluyente', () => {
    expect(rangoDeCobros('2026-11-01', '2026-11-30')).toEqual({
      desde: '2026-11-01',
      hasta: '2026-12-01',
    })
  })

  it('H-29.2: acepta un rango abierto y cruza fin de año', () => {
    expect(rangoDeCobros(undefined, undefined)).toEqual({})
    expect(rangoDeCobros('2026-12-31', undefined)).toEqual({ desde: '2026-12-31' })
    expect(diaSiguiente('2026-12-31')).toBe('2027-01-01')
    expect(diaSiguiente('2028-02-28')).toBe('2028-02-29')
  })

  it('formatea días y montos en la convención mexicana', () => {
    expect(formatearFecha('2026-11-15')).toBe('15 nov 2026')
    expect(formatearMonto(1500)).toBe('$1,500.00')
    expect(contarCobros(1)).toBe('1 cobro')
    expect(contarCobros(3)).toBe('3 cobros')
  })

  it('H-30.2: formatea el instante de resolución en la zona de negocio', () => {
    expect(formatearFechaHora('2026-11-06T12:02:00-06:00')).toBe('6 nov 2026, 12:02')
  })
})
