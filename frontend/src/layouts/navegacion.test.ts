import { describe, expect, it } from 'vitest'
import { seccionDeRuta, seccionesApp, seccionesPermitidas } from '@/layouts/navegacion'

describe('subsecciones', () => {
  it('H-07.2: Programación ofrece Canchas con su permiso y la ruta queda en la sección', () => {
    const programacion = seccionesApp.find(({ nombre }) => nombre === 'Programación')

    expect(programacion?.subsecciones).toEqual([
      expect.objectContaining({
        nombre: 'Canchas',
        ruta: '/app/programacion/canchas',
        permiso: 'canchas.cancha:CONSULTAR',
      }),
    ])
    expect(seccionDeRuta('/app/programacion/canchas/c1')?.nombre).toBe('Programación')
  })

  it('H-06.2: Organización ofrece Clubes con su permiso y la ruta queda en la sección', () => {
    const organizacion = seccionesApp.find(({ nombre }) => nombre === 'Organización')

    expect(organizacion?.subsecciones).toEqual([
      expect.objectContaining({
        nombre: 'Clubes',
        ruta: '/app/organizacion/clubes',
        permiso: 'organizacion.club:CONSULTAR',
      }),
    ])
    expect(seccionDeRuta('/app/organizacion/clubes/c1')?.nombre).toBe('Organización')
  })

  it('H-21.2: Administración ofrece Auditoría con su permiso y la ruta queda en la sección', () => {
    const administracion = seccionesApp.find(({ nombre }) => nombre === 'Administración')

    expect(administracion?.subsecciones).toContainEqual(
      expect.objectContaining({
        nombre: 'Auditoría',
        ruta: '/app/administracion/auditoria',
        permiso: 'auditoria.registro:CONSULTAR',
      }),
    )
    expect(seccionDeRuta('/app/administracion/auditoria')?.nombre).toBe('Administración')
  })

  it('H-03.2: Administración ofrece Usuarios con su permiso y la ruta queda en la sección', () => {
    const administracion = seccionesApp.find(({ nombre }) => nombre === 'Administración')

    expect(administracion?.subsecciones).toContainEqual(
      expect.objectContaining({
        nombre: 'Usuarios',
        ruta: '/app/administracion/usuarios',
        permiso: 'seguridad.usuario:CONSULTAR',
      }),
    )
    expect(seccionDeRuta('/app/administracion/usuarios')?.nombre).toBe('Administración')
  })

  it('H-04.2: Administración ofrece Temporadas con su permiso y la ruta queda en la sección', () => {
    const administracion = seccionesApp.find(({ nombre }) => nombre === 'Administración')

    expect(administracion?.subsecciones).toContainEqual(
      expect.objectContaining({
        nombre: 'Temporadas',
        ruta: '/app/administracion/temporadas',
        permiso: 'temporadas.temporada:CONSULTAR',
      }),
    )
    expect(seccionDeRuta('/app/administracion/temporadas')?.nombre).toBe('Administración')
  })

  it('H-29.2: Tesorería es una sección propia de M-13 y su ruta queda en ella', () => {
    const tesoreria = seccionesApp.find(({ nombre }) => nombre === 'Tesorería')
    const administracion = seccionesApp.find(({ nombre }) => nombre === 'Administración')

    expect(tesoreria).toEqual(
      expect.objectContaining({
        ruta: '/app/tesoreria',
        modulos: ['M-13'],
      }),
    )
    expect(administracion?.modulos).not.toContain('M-13')
    expect(seccionDeRuta('/app/tesoreria')?.nombre).toBe('Tesorería')
  })
})

const permiso = (codigo: string, modulo: string) => ({ codigo, modulo })

describe('seccionesPermitidas', () => {
  it('H-01.3: el Capturista solo ve Inicio y Partidos', () => {
    const secciones = seccionesPermitidas([
      permiso('partidos.partido:CONSULTAR', 'M-08'),
      permiso('partidos.cedula:CONSULTAR', 'M-08'),
      permiso('partidos.cedula:CAPTURAR', 'M-08'),
      permiso('notificaciones.aviso:CONSULTAR', 'M-14'),
    ])

    expect(secciones.map((seccion) => seccion.nombre)).toEqual([
      'Inicio',
      'Partidos',
    ])
  })

  it('H-01.3: un segundo rol ve las secciones de sus módulos', () => {
    const secciones = seccionesPermitidas([
      permiso('seguridad.rol:CONSULTAR', 'M-01'),
      permiso('temporadas.temporada:CONSULTAR', 'M-02'),
      permiso('organizacion.club:CONSULTAR', 'M-03'),
      permiso('canchas.cancha:CONSULTAR', 'M-06'),
      permiso('programacion.jornada:CONSULTAR', 'M-07'),
      permiso('partidos.partido:CONSULTAR', 'M-08'),
      permiso('disciplina.expediente:CONSULTAR', 'M-09'),
      permiso('competencias.propuesta:APROBAR', 'M-10'),
      permiso('publicacion.comunicado:APROBAR', 'M-11'),
      permiso('reportes.reporte:CONSULTAR', 'M-12'),
      permiso('tesoreria.cobro:CONSULTAR', 'M-13'),
    ])

    expect(secciones.map((seccion) => seccion.nombre)).toEqual([
      'Inicio',
      'Organización',
      'Programación',
      'Partidos',
      'Disciplina',
      'Tesorería',
      'Administración',
    ])
  })

  it('H-29.2: un permiso de M-13 muestra Inicio y Tesorería', () => {
    const secciones = seccionesPermitidas([
      permiso('tesoreria.cobro:CONSULTAR', 'M-13'),
    ])

    expect(secciones.map((seccion) => seccion.nombre)).toEqual([
      'Inicio',
      'Tesorería',
    ])
  })

  it('H-01.3: las notificaciones no crean una sección aunque el módulo esté mapeado', () => {
    // M-08 pertenece a Partidos; el prefijo `notificaciones.` debe excluirse igual.
    const secciones = seccionesPermitidas([
      permiso('notificaciones.aviso:CONSULTAR', 'M-08'),
    ])

    expect(secciones.map((seccion) => seccion.nombre)).toEqual(['Inicio'])
  })

  it('H-01.3: Inicio siempre está y un módulo desconocido no agrega secciones', () => {
    expect(seccionesPermitidas([]).map((seccion) => seccion.nombre)).toEqual([
      'Inicio',
    ])
    expect(
      seccionesPermitidas([permiso('otro.recurso:CONSULTAR', 'M-99')]).map(
        (seccion) => seccion.nombre,
      ),
    ).toEqual(['Inicio'])
  })
})
