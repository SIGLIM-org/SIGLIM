import { describe, expect, it } from 'vitest'
import {
  agruparPorModulo,
  diferenciaPermisos,
  hayCambios,
  paresIncompatiblesNuevos,
} from '@/features/roles/reglas'
import type { ParIncompatible, PermisoCatalogo } from '@/features/roles/tipos'

const permiso = (codigo: string, modulo: string): PermisoCatalogo => ({
  codigo,
  modulo,
  descripcion: codigo,
})

const PAR: ParIncompatible = {
  permisoA: 'programacion.jornada:EDITAR',
  permisoB: 'programacion.jornada:APROBAR',
  motivo: 'Programar y aprobar la programación',
}

describe('reglas de la matriz de permisos', () => {
  it('H-20.2: agrupa por módulo en orden numérico M-01, M-02, M-10', () => {
    const grupos = agruparPorModulo([
      permiso('a', 'M-10'),
      permiso('b', 'M-02'),
      permiso('c', 'M-01'),
      permiso('d', 'M-02'),
    ])

    expect(grupos.map(({ modulo }) => modulo)).toEqual(['M-01', 'M-02', 'M-10'])
    expect(grupos[1].permisos.map(({ codigo }) => codigo)).toEqual(['b', 'd'])
  })

  it('VAL-023: solo señala el par que el cambio crea, no uno que el rol ya tenía', () => {
    expect(
      paresIncompatiblesNuevos(
        new Set([PAR.permisoA, PAR.permisoB]),
        new Set([PAR.permisoA]),
        [PAR],
      ),
    ).toEqual([PAR])

    expect(
      paresIncompatiblesNuevos(
        new Set([PAR.permisoA, PAR.permisoB]),
        new Set([PAR.permisoA, PAR.permisoB]),
        [PAR],
      ),
    ).toEqual([])
    expect(paresIncompatiblesNuevos(new Set([PAR.permisoA]), new Set(), [PAR])).toEqual([])
  })

  it('calcula altas y bajas entre la matriz vigente y el conjunto final', () => {
    expect(
      diferenciaPermisos(new Set(['b', 'c']), new Set(['a', 'c', 'd'])),
    ).toEqual({ agregados: ['a', 'd'], quitados: ['b'] })
    expect(hayCambios(new Set(['a']), new Set(['a']))).toBe(false)
    expect(hayCambios(new Set(['a']), new Set(['a', 'b']))).toBe(true)
  })
})
