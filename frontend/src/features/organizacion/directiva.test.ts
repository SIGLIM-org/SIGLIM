import { describe, expect, it } from 'vitest'
import {
  contarHistorial,
  contarPendientes,
  curpValida,
  diaAnterior,
  filasDeDirectiva,
  nombreCompletoPersona,
} from '@/features/organizacion/directiva'
import type { CargoDirectiva } from '@/features/organizacion/tipos'

function cargo(
  id: string,
  tipo: CargoDirectiva['cargo'],
  desde: string,
  extra: Partial<CargoDirectiva> = {},
): CargoDirectiva {
  return {
    id,
    clubId: 'c1',
    personaId: `persona-${id}`,
    nombrePersona: `Titular ${id}`,
    cargo: tipo,
    desde,
    estado: 'AUTORIZADO',
    ...extra,
  }
}

const VIGENTE_PRESIDENTE = cargo('p1', 'PRESIDENTE', '2026-01-01', { autorizadoPor: 'perfil-1' })
const PENDIENTE_SECRETARIO = cargo('s2', 'SECRETARIO', '2026-03-01', { estado: 'PENDIENTE' })
const ANTERIOR_DELEGADO = cargo('d0', 'DELEGADO', '2025-01-01', {
  hasta: '2025-12-31',
  autorizadoPor: 'perfil-1',
})

describe('directiva (F-15, RF-102)', () => {
  it('arma una fila por cargo y una vacía cuando no hay titular', () => {
    const filas = filasDeDirectiva([VIGENTE_PRESIDENTE, PENDIENTE_SECRETARIO], false)

    expect(filas.map(({ tipo }) => tipo)).toEqual([
      'vigente',
      'vacia',
      'pendiente',
      'vacia',
      'vacia',
    ])
    expect(filas[0]).toMatchObject({ tipo: 'vigente', cargo: { id: 'p1' } })
    expect(filas[1]).toMatchObject({ tipo: 'vacia', cargo: 'SECRETARIO' })
    expect(filas[2]).toMatchObject({ tipo: 'pendiente', cargo: { id: 's2' } })
  })

  it('conserva al titular vigente junto al cambio pendiente que lo reemplaza', () => {
    const pendiente = cargo('p2', 'PRESIDENTE', '2027-01-01', { estado: 'PENDIENTE' })
    const filas = filasDeDirectiva([VIGENTE_PRESIDENTE, pendiente], false)

    expect(filas[0]).toMatchObject({ tipo: 'vigente', cargo: { id: 'p1' } })
    expect(filas[1]).toMatchObject({ tipo: 'pendiente', cargo: { id: 'p2' } })
    expect(filas[1]).toMatchObject({ vigente: { id: 'p1' } })
  })

  it('el historial solo aparece cuando se pide y va después del cargo', () => {
    const sinHistorial = filasDeDirectiva([VIGENTE_PRESIDENTE, ANTERIOR_DELEGADO], false)
    expect(sinHistorial.some(({ tipo }) => tipo === 'historial')).toBe(false)

    const conHistorial = filasDeDirectiva([VIGENTE_PRESIDENTE, ANTERIOR_DELEGADO], true)
    expect(conHistorial.at(-1)).toMatchObject({ tipo: 'historial', cargo: { id: 'd0' } })
  })

  it('cuenta pendientes e historial para los avisos de la pestaña', () => {
    expect(contarPendientes([VIGENTE_PRESIDENTE, PENDIENTE_SECRETARIO])).toBe(1)
    expect(contarHistorial([VIGENTE_PRESIDENTE, ANTERIOR_DELEGADO])).toBe(1)
  })

  it('calcula el día anterior, incluso al cambiar de año', () => {
    expect(diaAnterior('2027-01-01')).toBe('2026-12-31')
    expect(diaAnterior('2026-03-01')).toBe('2026-02-28')
  })

  it('valida la CURP con el formato de la base antes de buscar', () => {
    expect(curpValida('GODE840101HDFRNS09')).toBe(true)
    expect(curpValida('GODE840101HDFRNS0')).toBe(false)
    expect(curpValida('GODE840101XDFRNS09')).toBe(false)
  })

  it('arma el nombre completo sin apellido materno vacío', () => {
    expect(
      nombreCompletoPersona({
        id: 'p1',
        curp: 'GODE840101HDFRNS09',
        nombre: 'Ana',
        apellidoPaterno: 'López',
      }),
    ).toBe('Ana López')
    expect(
      nombreCompletoPersona({
        id: 'p1',
        curp: 'GODE840101HDFRNS09',
        nombre: 'Ana',
        apellidoPaterno: 'López',
        apellidoMaterno: 'García',
      }),
    ).toBe('Ana López García')
  })
})
