import { describe, expect, it } from 'vitest'
import {
  describirAsignacion,
  formatearFechaAlta,
  iniciales,
} from '@/features/usuarios/etiquetas'
import type { AsignacionUsuario } from '@/features/usuarios/tipos'

describe('etiquetas de usuarios', () => {
  it('H-03.2: las iniciales ignoran el tratamiento y usan nombre y apellido', () => {
    expect(iniciales('Lic. Fernando Morales')).toBe('FM')
    expect(iniciales('Prof. Roberto Morales V.')).toBe('RM')
    expect(iniciales('Julio César Ortiz')).toBe('JC')
    expect(iniciales('   ')).toBe('?')
  })

  it('RF-094: describe la asignación con su club o su ámbito', () => {
    const deClub: AsignacionUsuario = {
      id: 'a-1',
      rol: { id: 'r-1', codigo: 'DIRECTIVA_CLUB', nombre: 'Directiva de club' },
      ambito: 'CLUB',
      club: { id: 'c-1', nombre: 'Halcones FC' },
      activa: true,
      creadoEn: '2026-01-10T10:00:00-06:00',
    }
    const deLiga: AsignacionUsuario = { ...deClub, club: undefined, ambito: 'LIGA' }

    expect(describirAsignacion(deClub)).toBe('Directiva de club · Halcones FC')
    expect(describirAsignacion(deLiga)).toBe('Directiva de club · Liga')
  })

  it('H-03.2: la fecha de alta se muestra en la zona de negocio', () => {
    expect(formatearFechaAlta('2026-01-10T10:00:00-06:00')).toBe('10 ene 2026')
    expect(formatearFechaAlta('fecha-invalida')).toBe('Sin fecha')
  })
})
