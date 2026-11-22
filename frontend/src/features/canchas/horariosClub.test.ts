import { describe, expect, it } from 'vitest'
import {
  erroresDeServidor,
  filaDeHorario,
  filaVacia,
  mismasFilas,
  normalizarHora,
  peticionDeFilas,
  traslapes,
  validarFilas,
  type FilaHorario,
} from '@/features/canchas/horariosClub'
import type { DiaSemana, HorarioClub } from '@/features/canchas/tipos'

function fila(clave: string, diaSemana: DiaSemana | '', horaInicio: string, horaFin: string, extra: Partial<FilaHorario> = {}): FilaHorario {
  return {
    ...filaVacia(clave),
    categoriaId: 'cat-libre',
    categoriaNombre: 'Libre',
    diaSemana,
    horaInicio,
    horaFin,
    ...extra,
  }
}

describe('horariosClub (B-13.2, RF-119)', () => {
  it('normaliza HH:mm:ss a HH:mm y deja igual HH:mm', () => {
    expect(normalizarHora('10:00:00')).toBe('10:00')
    expect(normalizarHora('07:30')).toBe('07:30')
  })

  it('convierte un horario del API en fila con horas HH:mm', () => {
    const horario: HorarioClub = {
      id: 'h1',
      canchaId: 'c1',
      temporadaId: 't1',
      categoria: { id: 'cat-libre', nombre: 'Libre' },
      club: { id: 'club-1', nombre: 'Halcones' },
      diaSemana: 6,
      horaInicio: '10:00:00',
      horaFin: '12:00:00',
      esComodato: false,
    }

    expect(filaDeHorario(horario)).toEqual({
      clave: 'h1',
      categoriaId: 'cat-libre',
      categoriaNombre: 'Libre',
      diaSemana: 6,
      horaInicio: '10:00',
      horaFin: '12:00',
      clubId: 'club-1',
      clubNombre: 'Halcones',
    })
  })

  it('RF-119: 10–12 y 12–14 el mismo día no chocan (intervalo semiabierto)', () => {
    expect(traslapes([fila('a', 6, '10:00', '12:00'), fila('b', 6, '12:00', '14:00')])).toEqual([])
  })

  it('RF-119: la misma hora en otro día no choca', () => {
    expect(traslapes([fila('a', 6, '10:00', '12:00'), fila('b', 7, '10:00', '12:00')])).toEqual([])
  })

  it('RF-119: categorías distintas el mismo día sí chocan', () => {
    const filas = [
      fila('a', 6, '10:00', '12:00'),
      fila('b', 6, '11:00', '13:00', { categoriaId: 'cat-veteranos' }),
      fila('c', 6, '09:00', '09:30'),
    ]

    expect(traslapes(filas)).toEqual([[0, 1]])
  })

  it('un traslape marca las dos filas y cada una dice con cuál choca', () => {
    const errores = validarFilas([fila('a', 6, '10:00', '12:00'), fila('b', 6, '11:00', '13:00')], 'CLUB')

    expect(errores.a?.fila).toBe('Se traslapa con el horario 2 (Sábado 11:00–13:00).')
    expect(errores.b?.fila).toBe('Se traslapa con el horario 1 (Sábado 10:00–12:00).')
  })

  it('inicio = fin es error y no cuenta como traslape', () => {
    const errores = validarFilas([fila('a', 1, '10:00', '10:00'), fila('b', 1, '09:00', '11:00')], 'CLUB')

    expect(errores.a).toEqual({ horaFin: 'El fin debe ser posterior al inicio.' })
    expect(errores.b).toBeUndefined()
  })

  it('exige categoría, día y horas; el club solo en LIGA', () => {
    const vacia = filaVacia('n1')

    expect(validarFilas([vacia], 'CLUB').n1).toEqual({
      categoriaId: 'Elige la categoría.',
      diaSemana: 'Elige el día.',
      horaInicio: 'Indica la hora de inicio.',
      horaFin: 'Indica la hora de fin.',
    })
    expect(validarFilas([fila('a', 1, '10:00', '11:00')], 'LIGA').a).toEqual({
      clubId: 'Elige el club que usa la cancha.',
    })
    expect(validarFilas([fila('a', 1, '10:00', '11:00', { clubId: 'club-1' })], 'LIGA')).toEqual({})
  })

  it('arma la petición en orden, sin id ni esComodato; clubId solo en LIGA (DEC-33)', () => {
    const filas = [fila('h2', 7, '08:00', '09:00', { clubId: 'club-2' }), fila('h1', 6, '10:00', '12:00', { clubId: 'club-1' })]

    expect(peticionDeFilas(filas, 'CLUB')).toEqual([
      { categoriaId: 'cat-libre', diaSemana: 7, horaInicio: '08:00', horaFin: '09:00' },
      { categoriaId: 'cat-libre', diaSemana: 6, horaInicio: '10:00', horaFin: '12:00' },
    ])
    expect(peticionDeFilas(filas, 'LIGA')[0]).toEqual({
      categoriaId: 'cat-libre',
      diaSemana: 7,
      horaInicio: '08:00',
      horaFin: '09:00',
      clubId: 'club-2',
    })
  })

  it('mapea horarios[i].campo a la fila por posición y separa lo que no se puede mapear', () => {
    const filas = [fila('a', 1, '10:00', '11:00'), fila('b', 2, '10:00', '11:00')]

    expect(
      erroresDeServidor(
        [
          { campo: 'horarios[1].horaFin', mensaje: 'Fin inválido.' },
          { campo: 'horarios[5].horaFin', mensaje: 'Fuera de rango.' },
          { campo: 'temporadaId', mensaje: 'Falta la temporada.' },
        ],
        filas,
      ),
    ).toEqual({ porFila: { b: { horaFin: 'Fin inválido.' } }, sinMapear: ['Fuera de rango.', 'Falta la temporada.'] })
  })

  it('compara los datos de las filas, no sus claves locales', () => {
    expect(mismasFilas([fila('a', 1, '10:00', '11:00')], [fila('z', 1, '10:00', '11:00')])).toBe(true)
    expect(mismasFilas([fila('a', 1, '10:00', '11:00')], [fila('a', 1, '10:00', '11:30')])).toBe(false)
  })
})
