import { afterEach, describe, expect, it, vi } from 'vitest'
import { hoyEnZonaNegocio } from '@/features/organizacion/directiva'
import type { Equipo } from '@/features/organizacion/tipos'
import {
  ESTADO_INICIAL,
  accionDeError,
  agruparEquipos,
  curpCompleta,
  esMenorEnFecha,
  etiquetaFinalidad,
  huboEscrituras,
  normalizarCurp,
  parentescoFinal,
  personaExistenteDe,
  reducirAsistente,
  validarDatosPersona,
  DATOS_PERSONA_VACIOS,
} from '@/features/registros/asistenteAlta'
import { ApiError } from '@/lib/api/ApiError'

function error(status: number, codigo: string, detail = 'Detalle del servidor.', extra = {}) {
  return new ApiError({ title: 'Error', status, detail, codigo, ...extra })
}

function equipo(
  id: string,
  temporada: { id: string; estado: Equipo['temporada']['estado'] },
  categoria = 'Libre',
  estado: Equipo['estado'] = 'ACTIVO',
): Equipo {
  return {
    id,
    nombre: `Equipo ${id}`,
    estado,
    club: { id: 'c1', nombre: 'Club Halcones' },
    grupo: { id: 'g1', nombre: 'A' },
    categoria: { id: `cat-${categoria}`, nombre: categoria },
    temporada: { id: temporada.id, nombre: `Temporada ${temporada.id}`, estado: temporada.estado },
    creadoEn: '2026-11-01T10:00:00-06:00',
  }
}

describe('CURP (DEC-10)', () => {
  it('normaliza a mayúsculas y quita espacios antes de validar', () => {
    expect(normalizarCurp(' gode 840101hdfrns09 ')).toBe('GODE840101HDFRNS09')
    expect(curpCompleta('gode840101hdfrns09')).toBe(true)
    expect(curpCompleta('GODE84')).toBe(false)
  })
})

describe('Minoría con la fecha de Morelia (DEC-89, DEC-104)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('VAL-025: cumple 18 el día de su cumpleaños, no antes', () => {
    expect(esMenorEnFecha('2008-11-12', '2026-11-11')).toBe(true)
    expect(esMenorEnFecha('2008-11-12', '2026-11-12')).toBe(false)
    expect(esMenorEnFecha('1990-01-01', '2026-11-12')).toBe(false)
  })

  it('VAL-025: quien nació el 29 de febrero cumple el 1 de marzo en un año no bisiesto', () => {
    expect(esMenorEnFecha('2008-02-29', '2026-02-28')).toBe(true)
    expect(esMenorEnFecha('2008-02-29', '2026-03-01')).toBe(false)
  })

  it('una fecha incompleta no se considera menor', () => {
    expect(esMenorEnFecha('', '2026-11-12')).toBe(false)
  })

  it('DEC-89: a las 21:00 de Morelia (03:00 UTC del día siguiente) todavía es el día anterior', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-12T03:00:00Z'))

    const hoy = hoyEnZonaNegocio()

    expect(hoy).toBe('2026-11-11')
    expect(esMenorEnFecha('2008-11-12', hoy)).toBe(true)
  })
})

describe('Equipos del alta (DEC-108)', () => {
  it('RF-003: excluye las temporadas cerradas y los equipos de baja, y agrupa por temporada y categoría', () => {
    const grupos = agruparEquipos([
      equipo('e1', { id: '2026', estado: 'ACTIVA' }, 'Libre'),
      equipo('e2', { id: '2026', estado: 'ACTIVA' }, 'Juvenil'),
      equipo('e3', { id: '2026', estado: 'ACTIVA' }, 'Libre'),
      equipo('e4', { id: '2025', estado: 'CERRADA' }, 'Libre'),
      equipo('e5', { id: '2027', estado: 'CONFIGURACION' }, 'Libre'),
      equipo('e6', { id: '2026', estado: 'ACTIVA' }, 'Libre', 'BAJA'),
    ])

    expect(grupos.map(({ etiqueta }) => etiqueta)).toEqual([
      'Temporada 2026 · Libre',
      'Temporada 2026 · Juvenil',
      'Temporada 2027 · Libre',
    ])
    expect(grupos[0].equipos.map(({ id }) => id)).toEqual(['e1', 'e3'])
  })
})

describe('Parentesco y formulario (DEC-105, DEC-84)', () => {
  it('[PROPUESTA] «Otro» exige texto y se envía recortado hasta 40 caracteres', () => {
    expect(parentescoFinal('', '')).toBeUndefined()
    expect(parentescoFinal('Madre', '')).toBe('Madre')
    expect(parentescoFinal('Otro', '   ')).toBeUndefined()
    expect(parentescoFinal('Otro', '  Abuela  ')).toBe('Abuela')
    expect(parentescoFinal('Otro', 'x'.repeat(50))).toHaveLength(40)
  })

  it('DEC-62: exige CURP, nombre, apellido paterno y fecha no futura', () => {
    expect(validarDatosPersona('GODE', DATOS_PERSONA_VACIOS, '2026-11-12')).toEqual({
      curp: expect.any(String),
      nombre: expect.any(String),
      apellidoPaterno: expect.any(String),
      fechaNacimiento: expect.any(String),
    })
    expect(
      validarDatosPersona(
        'GODE840101HDFRNS09',
        { ...DATOS_PERSONA_VACIOS, nombre: 'Ana', apellidoPaterno: 'López', fechaNacimiento: '2026-11-13' },
        '2026-11-12',
      ),
    ).toEqual({ fechaNacimiento: 'La fecha de nacimiento no puede ser futura.' })
  })

  it('las finalidades se muestran legibles sin cambiar su código', () => {
    expect(etiquetaFinalidad('REGISTRO_DEPORTIVO')).toBe('Registro deportivo')
  })

  it('DEC-106: una finalidad conocida usa su nombre con acentos', () => {
    expect(etiquetaFinalidad('DIFUSION')).toBe('Difusión')
  })

  it('DEC-106: un código desconocido usa la regla general', () => {
    expect(etiquetaFinalidad('OTRA_FINALIDAD')).toBe('Otra finalidad')
  })
})

describe('Traducción de errores (DEC-107)', () => {
  it('VAL-017: lleva a «Cambiar equipo» con el club y el equipo de las extensiones', () => {
    const accion = accionDeError(
      'alta',
      error(409, 'VAL-017', 'Ya está registrada en otro club.', {
        extensiones: { club: 'Club Águilas', equipo: 'Águilas' },
      }),
    )

    expect(accion).toEqual({
      tipo: 'cambiarEquipo',
      mensaje: 'Ya está registrada en otro club.',
      club: 'Club Águilas',
      equipo: 'Águilas',
    })
  })

  it('VAL-017 sin extensiones (carrera, DEC-90) basta con detail', () => {
    expect(accionDeError('alta', error(409, 'VAL-017', 'Ya registrada.'))).toEqual({
      tipo: 'cambiarEquipo',
      mensaje: 'Ya registrada.',
      club: undefined,
      equipo: undefined,
    })
  })

  it.each(['VAL-026', 'RN-039', 'RF-101', 'RF-003', 'RF-013'])(
    '%s del alta lleva a «Cambiar equipo» con detail',
    (codigo) => {
      expect(accionDeError('alta', error(409, codigo, 'Causa.'))).toMatchObject({
        tipo: 'cambiarEquipo',
        mensaje: 'Causa.',
      })
    },
  )

  it('404 del alta lleva a «Cambiar equipo»', () => {
    expect(accionDeError('alta', error(404, 'NO_ENCONTRADO'))).toMatchObject({ tipo: 'cambiarEquipo' })
  })

  it('VAL-025: TUTOR va a la sección del tutor y CONSENTIMIENTO al paso 3', () => {
    expect(
      accionDeError('alta', error(422, 'VAL-025', 'Falta tutor.', { extensiones: { requisito: 'TUTOR' } })),
    ).toEqual({ tipo: 'tutor', mensaje: 'Falta tutor.' })
    expect(
      accionDeError(
        'alta',
        error(422, 'VAL-025', 'Falta consentimiento.', { extensiones: { requisito: 'CONSENTIMIENTO' } }),
      ),
    ).toEqual({ tipo: 'consentimiento', mensaje: 'Falta consentimiento.' })
  })

  it('VAL-022 de la tutela usa el texto propio, sin código', () => {
    const accion = accionDeError('tutela', error(403, 'VAL-022'))

    expect(accion.tipo).toBe('tutor')
    expect(accion.mensaje).toMatch(/^No puedes registrarte como tutor desde tu propia cuenta/)
    expect(accion.mensaje).not.toMatch(/VAL-/)
  })

  it('RF-097 del consentimiento recarga el aviso', () => {
    expect(accionDeError('consentimiento', error(422, 'RF-097', 'Aviso viejo.'))).toEqual({
      tipo: 'recargarAviso',
      mensaje: 'Aviso viejo.',
    })
  })

  it('400 con errores marca los campos', () => {
    const accion = accionDeError(
      'persona',
      error(400, 'VALIDACION', 'Revisa los datos.', { errores: [{ campo: 'nombre', mensaje: 'Obligatorio' }] }),
    )

    expect(accion).toEqual({
      tipo: 'campos',
      contexto: 'persona',
      errores: { nombre: 'Obligatorio' },
      mensaje: 'Revisa los datos.',
    })
  })

  it('403 VAL-015 y RF-106 fuera de la tutela muestran detail', () => {
    expect(accionDeError('alta', error(403, 'VAL-015', 'Sin permiso.'))).toEqual({
      tipo: 'general',
      contexto: 'alta',
      mensaje: 'Sin permiso.',
    })
    expect(accionDeError('tutela', error(422, 'RF-106', 'El tutor es menor.'))).toEqual({
      tipo: 'tutor',
      mensaje: 'El tutor es menor.',
    })
  })

  it('un fallo sin problema RFC 9457 da un mensaje propio', () => {
    expect(accionDeError('alta', new Error('red'))).toMatchObject({ tipo: 'general' })
  })

  it('RF-010: toma el personaId de la extensión para reutilizarlo', () => {
    expect(personaExistenteDe(error(409, 'RF-010', 'Ya existe.', { extensiones: { personaId: 'p9' } }))).toBe('p9')
    expect(personaExistenteDe(error(409, 'DUPLICADO'))).toBeUndefined()
  })
})

describe('Estado del asistente (DEC-103)', () => {
  it('una escritura cierra el regreso al paso 1; cambiar de paso limpia el bloqueo', () => {
    expect(huboEscrituras(ESTADO_INICIAL)).toBe(false)
    expect(huboEscrituras({ ...ESTADO_INICIAL, tutelaRegistrada: true })).toBe(true)

    const conBloqueo = { ...ESTADO_INICIAL, bloqueo: { tipo: 'tutor' as const, mensaje: 'x' } }
    expect(reducirAsistente(conBloqueo, { tipo: 'ir', paso: 2 })).toMatchObject({
      paso: 2,
      navego: true,
      bloqueo: null,
    })
  })
})
