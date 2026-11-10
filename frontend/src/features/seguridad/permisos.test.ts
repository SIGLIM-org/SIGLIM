import { describe, expect, it } from 'vitest'
import {
  puedeVerModulo,
  tieneAlgunPermiso,
  tienePermiso,
} from '@/features/seguridad/permisos'
import { seccionDeRuta } from '@/layouts/navegacion'

const permisos = [
  { codigo: 'partidos.partido:CONSULTAR', modulo: 'M-08' },
  { codigo: 'notificaciones.aviso:CONSULTAR', modulo: 'M-14' },
]

describe('permisos', () => {
  it('H-02.4: compara el código exacto del permiso', () => {
    expect(tienePermiso(permisos, 'partidos.partido:CONSULTAR')).toBe(true)
    expect(tienePermiso(permisos, 'partidos.partido:CREAR')).toBe(false)
  })

  it('H-02.4: basta con uno de los permisos indicados', () => {
    expect(
      tieneAlgunPermiso(permisos, [
        'partidos.partido:CREAR',
        'partidos.partido:CONSULTAR',
      ]),
    ).toBe(true)
    expect(
      tieneAlgunPermiso(permisos, [
        'partidos.partido:CREAR',
        'partidos.partido:EDITAR',
      ]),
    ).toBe(false)
  })

  it('H-02.4: una sección sin módulos siempre es visible', () => {
    expect(puedeVerModulo([], [])).toBe(true)
  })

  it('H-02.4: da acceso cuando hay un permiso de alguno de sus módulos', () => {
    expect(puedeVerModulo(permisos, ['M-03', 'M-08'])).toBe(true)
    expect(puedeVerModulo(permisos, ['M-03', 'M-04'])).toBe(false)
  })

  it('H-01.3: las notificaciones no dan acceso a una sección del menú', () => {
    expect(
      puedeVerModulo(
        [{ codigo: 'notificaciones.aviso:CONSULTAR', modulo: 'M-14' }],
        ['M-14'],
      ),
    ).toBe(false)
  })

  it('H-02.4: asocia la URL con su sección y deja libres las desconocidas', () => {
    expect(seccionDeRuta('/app')?.nombre).toBe('Inicio')
    expect(seccionDeRuta('/app/organizacion')?.nombre).toBe('Organización')
    expect(seccionDeRuta('/app/organizacion/clubes')?.nombre).toBe(
      'Organización',
    )
    expect(seccionDeRuta('/app/inexistente')).toBeUndefined()
    expect(seccionDeRuta('/app/organizacionx')).toBeUndefined()
  })
})
