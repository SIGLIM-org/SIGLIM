import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  contarCobros,
  diaSiguiente,
  formatearFecha,
  formatearMonto,
  hoyEnZonaNegocio,
  rangoDeCobros,
} from '@/features/tesoreria/fechas'

describe('fechas de tesorería', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('H-31.2: el día de hoy se toma en Ciudad de México, no en UTC', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-07T02:00:00Z'))

    expect(hoyEnZonaNegocio()).toBe('2026-11-06')
  })

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
})
