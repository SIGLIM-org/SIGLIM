import { describe, expect, it } from 'vitest'
import {
  aPeticion,
  cambioDeCalendario,
  campoFila,
  categoriasInvalidasDe,
  desdePredeterminados,
  desdeVersion,
  erroresDeServidor,
  hayCambios,
  validarFormulario,
  type FormularioParametros,
} from '@/features/temporadas/parametros'
import type { ParametrosPredeterminados, VersionParametros } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

const PREDETERMINADOS: ParametrosPredeterminados = {
  puntosVictoria: 3,
  puntosEmpate: 1,
  puntosDerrota: 0,
  criteriosDesempate: ['PUNTOS', 'DIFERENCIA_GOLES', 'GOLES_FAVOR', 'SORTEO'],
  golesDefaultGanador: 3,
  golesDefaultPerdedor: 0,
  plazoValidacionCedulaHoras: 48,
  duracionJuegoMin: 90,
  bloqueCanchaMin: 120,
  clasificadosLiguilla: 8,
  formatoLiguilla: 'PARTIDO_UNICO',
  empateLiguillaPorPenales: true,
  equiposAscienden: 2,
  equiposDescienden: 2,
  permiteCambioClub: true,
  vueltas: 1,
  horaInicioJuego: '08:00:00',
  horaFinJuego: '18:00:00',
  esDemo: true,
}

const VIGENTE: VersionParametros = {
  ...PREDETERMINADOS,
  id: 'p-2',
  temporadaId: 't-1',
  version: 2,
  categorias: [{ categoria: { id: 'c-vet', nombre: 'Veteranos' }, duracionJuegoMin: 70, bloqueCanchaMin: 90 }],
  esDemo: true,
  camposDemo: ['puntosVictoria'],
  motivo: 'Ajuste de la asamblea',
  creadoEn: '2026-11-05T10:00:00-06:00',
}

function formulario(cambios: Partial<FormularioParametros> = {}): FormularioParametros {
  return { ...desdePredeterminados(PREDETERMINADOS), ...cambios }
}

describe('parametros', () => {
  it('RF-100: desdePredeterminados prellena todo como texto y arranca no oficial', () => {
    const resultado = desdePredeterminados(PREDETERMINADOS)

    expect(resultado.puntosVictoria).toBe('3')
    expect(resultado.bloqueCanchaMin).toBe('120')
    expect(resultado.criteriosDesempate).toEqual(PREDETERMINADOS.criteriosDesempate)
    expect(resultado.oficial).toBe(false)
    expect(resultado.categorias).toEqual({})
  })

  it('RF-006: desdeVersion conserva las filas por categoría y el estado oficial', () => {
    const resultado = desdeVersion({ ...VIGENTE, esDemo: false })

    expect(resultado.oficial).toBe(true)
    expect(resultado.categorias).toEqual({
      'c-vet': { duracionJuegoMin: '70', bloqueCanchaMin: '90' },
    })
  })

  it('RF-006: aPeticion envía el juego completo, esDemo y omite las filas vacías', () => {
    const peticion = aPeticion(
      formulario({
        categorias: {
          'c-vet': { duracionJuegoMin: '70', bloqueCanchaMin: '90' },
          'c-libre': { duracionJuegoMin: '', bloqueCanchaMin: '' },
        },
      }),
      'Primera versión de la temporada',
    )

    expect(peticion).toEqual({
      puntosVictoria: 3,
      puntosEmpate: 1,
      puntosDerrota: 0,
      criteriosDesempate: ['PUNTOS', 'DIFERENCIA_GOLES', 'GOLES_FAVOR', 'SORTEO'],
      golesDefaultGanador: 3,
      golesDefaultPerdedor: 0,
      plazoValidacionCedulaHoras: 48,
      duracionJuegoMin: 90,
      bloqueCanchaMin: 120,
      clasificadosLiguilla: 8,
      formatoLiguilla: 'PARTIDO_UNICO',
      empateLiguillaPorPenales: true,
      equiposAscienden: 2,
      equiposDescienden: 2,
      permiteCambioClub: true,
      vueltas: 1,
      horaInicioJuego: '08:00',
      horaFinJuego: '18:00',
      esDemo: true,
      categorias: [{ categoriaId: 'c-vet', duracionJuegoMin: 70, bloqueCanchaMin: 90 }],
      motivo: 'Primera versión de la temporada',
    })
    expect(aPeticion(formulario({ oficial: true }), 'Motivo suficiente').esDemo).toBe(false)
  })

  it('RF-100: sin vigente siempre hay cambios', () => {
    expect(hayCambios(formulario(), null)).toBe(true)
  })

  it('RF-006: hayCambios es falso con el borrador igual a la vigente', () => {
    expect(hayCambios(desdeVersion(VIGENTE), VIGENTE)).toBe(false)
  })

  it('RF-006: hayCambios detecta valores, criterios, filas y el cambio de esDemo', () => {
    const base = desdeVersion(VIGENTE)

    expect(hayCambios({ ...base, puntosVictoria: '2' }, VIGENTE)).toBe(true)
    expect(hayCambios({ ...base, criteriosDesempate: ['PUNTOS', 'GOLES_FAVOR', 'DIFERENCIA_GOLES', 'SORTEO'] }, VIGENTE)).toBe(true)
    expect(hayCambios({ ...base, categorias: {} }, VIGENTE)).toBe(true)
    expect(hayCambios({ ...base, permiteCambioClub: false }, VIGENTE)).toBe(true)
    expect(hayCambios({ ...base, oficial: true }, VIGENTE)).toBe(true)
  })

  it('RF-006: validarFormulario acepta los predeterminados', () => {
    expect(validarFormulario(formulario())).toEqual({})
  })

  it('DEC-24: victoria ≥ empate ≥ derrota', () => {
    expect(validarFormulario(formulario({ puntosEmpate: '4' }))).toHaveProperty('puntosEmpate')
    expect(validarFormulario(formulario({ puntosDerrota: '2' }))).toHaveProperty('puntosDerrota')
  })

  it('DEC-24: el ganador por default anota más que el perdedor', () => {
    expect(validarFormulario(formulario({ golesDefaultPerdedor: '3' }))).toEqual({
      golesDefaultPerdedor: 'Debe ser menor que los goles del ganador.',
    })
  })

  it('DEC-25: el bloque no es menor que la duración, en la temporada y en cada fila', () => {
    expect(validarFormulario(formulario({ bloqueCanchaMin: '80' }))).toHaveProperty('bloqueCanchaMin')
    expect(
      validarFormulario(
        formulario({ categorias: { 'c-vet': { duracionJuegoMin: '70', bloqueCanchaMin: '60' } } }),
      ),
    ).toHaveProperty([campoFila('c-vet', 'bloqueCanchaMin')])
  })

  it('DEC-25: una fila lleva duración y bloque, o ninguno', () => {
    expect(
      validarFormulario(
        formulario({ categorias: { 'c-vet': { duracionJuegoMin: '70', bloqueCanchaMin: '' } } }),
      ),
    ).toEqual({
      [campoFila('c-vet', 'bloqueCanchaMin')]: 'Indica duración y bloque, o deja ambos vacíos.',
    })
  })

  it('RF-006: valida los rangos de §6.2 y los enteros', () => {
    const errores = validarFormulario(
      formulario({
        puntosVictoria: '101',
        plazoValidacionCedulaHoras: '0',
        duracionJuegoMin: '',
        equiposAscienden: '1.5',
      }),
    )

    expect(errores.puntosVictoria).toBe('Debe estar entre 0 y 100.')
    expect(errores.plazoValidacionCedulaHoras).toBe('Debe estar entre 1 y 720.')
    expect(errores.duracionJuegoMin).toBe('Escribe un número entero.')
    expect(errores.equiposAscienden).toBe('Escribe un número entero.')
  })

  it('RF-050: los criterios empiezan por PUNTOS y SORTEO va al final', () => {
    expect(
      validarFormulario(formulario({ criteriosDesempate: ['GOLES_FAVOR', 'PUNTOS'] })),
    ).toHaveProperty('criteriosDesempate')
    expect(
      validarFormulario(formulario({ criteriosDesempate: ['PUNTOS', 'SORTEO', 'GOLES_FAVOR'] })),
    ).toHaveProperty('criteriosDesempate')
  })

  it('RF-006: traduce categorias[i] del servidor a la categoría enviada', () => {
    const peticion = aPeticion(
      formulario({
        categorias: {
          'c-libre': { duracionJuegoMin: '', bloqueCanchaMin: '' },
          'c-vet': { duracionJuegoMin: '70', bloqueCanchaMin: '90' },
        },
      }),
      'Motivo suficiente',
    )
    const error = new ApiError({
      title: 'Error',
      status: 400,
      detail: 'Revisa los campos.',
      codigo: 'VALIDACION',
      errores: [
        { campo: 'categorias[0].bloqueCanchaMin', mensaje: 'Bloque inválido' },
        { campo: 'puntosVictoria', mensaje: 'Puntos inválidos' },
      ],
    })

    expect(erroresDeServidor(error, peticion)).toEqual({
      [campoFila('c-vet', 'bloqueCanchaMin')]: 'Bloque inválido',
      puntosVictoria: 'Puntos inválidos',
    })
  })

  describe('vueltas y horario de juego (H-32.3, DEC-194 a DEC-196)', () => {
    it('DEC-194: desdeVersion normaliza las horas HH:mm:ss a HH:mm', () => {
      const resultado = desdeVersion({ ...VIGENTE, vueltas: 2, horaInicioJuego: '09:30:00' })

      expect(resultado.vueltas).toBe('2')
      expect(resultado.horaInicioJuego).toBe('09:30')
      expect(resultado.horaFinJuego).toBe('18:00')
    })

    it('DEC-194: aPeticion manda las vueltas y las dos horas en HH:mm', () => {
      const peticion = aPeticion(
        formulario({ vueltas: '2', horaInicioJuego: '09:00', horaFinJuego: '17:00' }),
        'Motivo suficiente',
      )

      expect(peticion).toMatchObject({ vueltas: 2, horaInicioJuego: '09:00', horaFinJuego: '17:00' })
    })

    it('DEC-194: hayCambios detecta las vueltas y cada hora', () => {
      const base = desdeVersion(VIGENTE)

      expect(hayCambios({ ...base, vueltas: '2' }, VIGENTE)).toBe(true)
      expect(hayCambios({ ...base, horaInicioJuego: '09:00' }, VIGENTE)).toBe(true)
      expect(hayCambios({ ...base, horaFinJuego: '17:00' }, VIGENTE)).toBe(true)
    })

    it('DEC-195: las vueltas van de 1 a 4', () => {
      expect(validarFormulario(formulario({ vueltas: '0' })).vueltas).toBe('Debe estar entre 1 y 4.')
      expect(validarFormulario(formulario({ vueltas: '5' })).vueltas).toBe('Debe estar entre 1 y 4.')
      expect(validarFormulario(formulario({ vueltas: '4' }))).toEqual({})
    })

    it('DEC-195: las dos horas son obligatorias', () => {
      expect(validarFormulario(formulario({ horaInicioJuego: '' }))).toEqual({
        horaInicioJuego: 'Indica la hora de inicio del horario de juego.',
      })
      expect(validarFormulario(formulario({ horaFinJuego: '' }))).toEqual({
        horaFinJuego: 'Indica la hora de fin del horario de juego.',
      })
    })

    it('DEC-195: el fin va después del inicio', () => {
      const mensaje = 'El fin del horario de juego (08:00) debe ser posterior a su inicio (08:00).'

      expect(validarFormulario(formulario({ horaFinJuego: '08:00' }))).toEqual({ horaFinJuego: mensaje })
      expect(validarFormulario(formulario({ horaFinJuego: '07:00' }))).toHaveProperty('horaFinJuego')
    })

    it('DEC-195: el horario alcanza para el bloque de la temporada y el de cada fila', () => {
      expect(
        validarFormulario(formulario({ horaInicioJuego: '08:00', horaFinJuego: '09:00' })),
      ).toEqual({
        horaFinJuego:
          'El horario de juego (de 08:00 a 09:00, 60 min) no alcanza para un bloque de cancha de 120 min.',
      })
      expect(
        validarFormulario(
          formulario({
            horaFinJuego: '10:00',
            categorias: { 'c-vet': { duracionJuegoMin: '90', bloqueCanchaMin: '150' } },
          }),
        ),
      ).toEqual({
        horaFinJuego:
          'El horario de juego (de 08:00 a 10:00, 120 min) no alcanza para un bloque de cancha de 150 min.',
      })
    })

    it('DEC-196: cambioDeCalendario avisa solo si cambian las vueltas o el horario', () => {
      const base = desdeVersion(VIGENTE)

      expect(cambioDeCalendario(base, null)).toBe(false)
      expect(cambioDeCalendario(base, VIGENTE)).toBe(false)
      expect(cambioDeCalendario({ ...base, puntosVictoria: '2' }, VIGENTE)).toBe(false)
      expect(cambioDeCalendario({ ...base, vueltas: '2' }, VIGENTE)).toBe(true)
      expect(cambioDeCalendario({ ...base, horaFinJuego: '17:00' }, VIGENTE)).toBe(true)
    })
  })

  it('RF-100: lee categoriasInvalidas de las extensiones del 422', () => {
    const error = new ApiError({
      title: 'Error',
      status: 422,
      detail: 'Categoría no asignada.',
      codigo: 'REFERENCIA_INVALIDA',
      extensiones: { categoriasInvalidas: ['c-vet', 3] },
    })

    expect(categoriasInvalidasDe(error)).toEqual(['c-vet'])
  })
})
