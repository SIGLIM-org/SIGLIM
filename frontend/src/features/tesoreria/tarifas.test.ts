import { describe, expect, it } from 'vitest'
import { indexarTarifas, leerMonto, temporadaInicial, textoDeMonto } from '@/features/tesoreria/tarifas'
import type { Tarifa, TemporadaTarifas } from '@/features/tesoreria/tipos'

const TARIFA: Tarifa = {
  id: 't-1',
  temporada: { id: 'tp-1', nombre: 'Temporada 2026' },
  categoria: { id: 'cat-1', nombre: 'Libre' },
  concepto: 'CANCHA',
  monto: 350,
  esDemo: true,
}

describe('leerMonto', () => {
  it('RF-135: acepta enteros, cero y hasta dos decimales, con o sin signo de pesos', () => {
    expect(leerMonto('350')).toEqual({ tipo: 'valido', monto: 350 })
    expect(leerMonto(' $1,250.5 ')).toEqual({ tipo: 'valido', monto: 1250.5 })
    expect(leerMonto('0')).toEqual({ tipo: 'valido', monto: 0 })
    expect(leerMonto('   ')).toEqual({ tipo: 'vacio' })
  })

  it('B-29 criterio 2 · RF-135: rechaza un monto negativo', () => {
    expect(leerMonto('-10')).toEqual({ tipo: 'invalido', mensaje: 'El monto no puede ser negativo.' })
  })

  it('RF-135: rechaza tres decimales, letras y más de diez dígitos enteros', () => {
    for (const texto of ['1.005', 'abc', '12345678901']) {
      expect(leerMonto(texto).tipo).toBe('invalido')
    }
  })
})

describe('tabla de tarifas', () => {
  it('RF-135: indexa por categoría y concepto y muestra el monto con dos decimales', () => {
    expect(indexarTarifas([TARIFA]).get('cat-1')).toEqual({ CANCHA: TARIFA })
    expect(textoDeMonto(TARIFA)).toBe('350.00')
    expect(textoDeMonto(undefined)).toBe('')
  })

  it('B-29.2: abre en la temporada activa o, sin ella, en la más reciente', () => {
    const cerrada: TemporadaTarifas = { id: 'a', nombre: 'A', estado: 'CERRADA', categorias: [] }
    const activa: TemporadaTarifas = { id: 'b', nombre: 'B', estado: 'ACTIVA', categorias: [] }

    expect(temporadaInicial([cerrada, activa])).toBe('b')
    expect(temporadaInicial([cerrada])).toBe('a')
    expect(temporadaInicial([])).toBe('')
  })
})
