import { describe, expect, it } from 'vitest'
import {
  contadorVisible,
  enlaceInterno,
  presentacionDe,
  tiempoRelativo,
} from '@/features/notificaciones/presentacion'

describe('presentación de los avisos', () => {
  it('asigna categoría a cada evento conocido y un genérico a uno nuevo', () => {
    expect(presentacionDe('CEDULA_PENDIENTE').categoria).toBe('Cédulas y partidos')
    expect(presentacionDe('COBRO_VENCIDO').categoria).toBe('Tesorería')
    expect(presentacionDe('PROGRAMACION_PUBLICADA').categoria).toBe('Programación')
    expect(presentacionDe('EXPEDIENTE_ABIERTO').categoria).toBe('Aviso')
  })

  it('solo acepta enlaces de la aplicación interna', () => {
    expect(enlaceInterno('/app/partidos/1')).toBe('/app/partidos/1')
    expect(enlaceInterno('/app')).toBe('/app')
    expect(enlaceInterno('/resultados')).toBeNull()
    expect(enlaceInterno('//otro-sitio.example/app')).toBeNull()
    expect(enlaceInterno('https://otro-sitio.example/app/x')).toBeNull()
    expect(enlaceInterno('/application')).toBeNull()
    expect(enlaceInterno(null)).toBeNull()
  })

  it('describe la antigüedad del aviso', () => {
    const ahora = Date.parse('2026-11-27T12:00:00-06:00')

    expect(tiempoRelativo('2026-11-27T11:59:40-06:00', ahora)).toBe('Hace un momento')
    expect(tiempoRelativo('2026-11-27T11:45:00-06:00', ahora)).toBe('Hace 15 min')
    expect(tiempoRelativo('2026-11-27T09:00:00-06:00', ahora)).toBe('Hace 3 h')
    expect(tiempoRelativo('2026-11-26T09:00:00-06:00', ahora)).toBe('Ayer')
    expect(tiempoRelativo('2026-11-20T09:00:00-06:00', ahora)).toMatch(/20 nov/)
  })

  it('limita el contador de la campana a 99+', () => {
    expect(contadorVisible(7)).toBe('7')
    expect(contadorVisible(99)).toBe('99')
    expect(contadorVisible(100)).toBe('99+')
  })
})
