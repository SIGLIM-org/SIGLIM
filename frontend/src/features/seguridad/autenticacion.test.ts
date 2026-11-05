import { describe, expect, it } from 'vitest'
import {
  correoValido,
  destinoSeguro,
  mensajeDeErrorDeAcceso,
  mensajeDeErrorDeContrasena,
  mensajeDeErrorDeRecuperacion,
} from '@/features/seguridad/autenticacion'

describe('autenticacion', () => {
  it('H-01.2: solo acepta destinos internos después de iniciar sesión', () => {
    expect(destinoSeguro('/app/partidos')).toBe('/app/partidos')
    expect(destinoSeguro(undefined)).toBe('/app')
    expect(destinoSeguro('https://malicioso.example')).toBe('/app')
    expect(destinoSeguro('//malicioso.example')).toBe('/app')
    expect(destinoSeguro('/\\malicioso.example')).toBe('/app')
  })

  it('H-01.2: valida el formato del correo antes de llamar a Supabase', () => {
    expect(correoValido('ana@ejemplo.mx')).toBe(true)
    expect(correoValido(' ana@ejemplo.mx ')).toBe(true)
    expect(correoValido('ana@ejemplo')).toBe(false)
    expect(correoValido('ana')).toBe(false)
    expect(correoValido('')).toBe(false)
  })

  it('H-01.2: no revela si falló el correo o la contraseña', () => {
    const mensaje = mensajeDeErrorDeAcceso({
      status: 400,
      code: 'invalid_credentials',
    })

    expect(mensaje).toBe('Correo o contraseña incorrectos.')
    expect(mensaje).not.toMatch(/correo no existe|contraseña incorrecta/i)
  })

  it('H-01.2: explica el límite de intentos y la falta de conexión', () => {
    expect(mensajeDeErrorDeAcceso({ status: 429 })).toContain('Demasiados intentos')
    expect(mensajeDeErrorDeAcceso({})).toContain('No se pudo conectar')
  })

  it('DEF-01: no atribuye a las credenciales las fallas del servidor ni de configuración', () => {
    const mensajeServidor = mensajeDeErrorDeAcceso({ status: 500 })

    expect(mensajeServidor).toContain('El servicio de acceso no está disponible')
    expect(mensajeServidor).not.toMatch(/correo o contraseña incorrectos/i)
    expect(mensajeDeErrorDeAcceso({ status: 503 })).toBe(mensajeServidor)
    expect(mensajeDeErrorDeAcceso({ status: 502 })).toBe(mensajeServidor)
    expect(mensajeDeErrorDeAcceso({ status: 404 })).toBe(mensajeServidor)
    expect(
      mensajeDeErrorDeAcceso({ status: 400, code: 'invalid_credentials' }),
    ).toBe('Correo o contraseña incorrectos.')
  })

  it('H-01.2: usa un mensaje genérico al fallar la recuperación', () => {
    expect(mensajeDeErrorDeRecuperacion({ status: 400 })).toBe(
      'No pudimos enviar el enlace en este momento. Intenta de nuevo más tarde.',
    )
  })

  it('H-01.2: traduce los errores al guardar la contraseña nueva', () => {
    expect(mensajeDeErrorDeContrasena({ code: 'same_password' })).toContain(
      'diferente a la actual',
    )
    expect(mensajeDeErrorDeContrasena({ code: 'weak_password' })).toContain(
      'al menos 15 caracteres',
    )
    expect(mensajeDeErrorDeContrasena({ status: 400 })).toContain(
      'No pudimos actualizar la contraseña',
    )
  })
})
