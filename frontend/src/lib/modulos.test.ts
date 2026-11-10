import { describe, expect, it } from 'vitest'
import { etiquetaModulo, nombreModulo } from '@/lib/modulos'

describe('módulos', () => {
  it('nombra un código conocido', () => {
    expect(nombreModulo('M-01')).toBe('Administración y seguridad')
    expect(nombreModulo('M-14')).toBe('Notificaciones')
  })

  it('un código desconocido se devuelve tal cual', () => {
    expect(nombreModulo('M-99')).toBe('M-99')
    expect(etiquetaModulo('M-99')).toBe('M-99')
    expect(etiquetaModulo('constructor')).toBe('constructor')
  })

  it('la etiqueta une código y nombre', () => {
    expect(etiquetaModulo('M-06')).toBe('M-06 · Canchas y recursos')
  })
})
