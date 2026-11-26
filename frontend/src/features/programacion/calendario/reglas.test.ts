import { describe, expect, it } from 'vitest'
import {
  conFinDeSemana,
  diasCambiados,
  esErrorDeFechaInicio,
  estadoGrupo,
  formatearDiasFecha,
  grupoIdsParaEnviar,
  primeraFecha,
  requiereMotivo,
  resumenCalendario,
  seleccionInicial,
  validarMotivo,
} from '@/features/programacion/calendario/reglas'
import type {
  Calendario,
  FechaCalendario,
  GrupoCalendario,
} from '@/features/programacion/calendario/tipos'
import type { CategoriaTemporada } from '@/features/temporadas/tipos'

const HALCONES = { id: 'e1', nombre: 'Halcones', estado: 'ACTIVO' }
const TOROS = { id: 'e2', nombre: 'Toros', estado: 'ACTIVO' }

function fecha(numero: number, vuelta: number, partidoId?: string): FechaCalendario {
  return {
    numero,
    vuelta,
    partidos: [
      {
        emparejamientoId: `em-${numero}`,
        local: HALCONES,
        visitante: TOROS,
        ...(partidoId ? { partidoId } : {}),
      },
    ],
  }
}

function grupo(grupoId: string, fechas: FechaCalendario[] = []): GrupoCalendario {
  return {
    grupoId,
    nombre: `Grupo ${grupoId}`,
    categoria: { id: 'cat1', nombre: 'Libre' },
    generado: fechas.length > 0,
    fechas,
  }
}

function calendario(...grupos: GrupoCalendario[]): Calendario {
  return { temporadaId: 't1', grupos }
}

function categoria(id: string, diasJuego: number[]): CategoriaTemporada {
  return {
    id,
    categoria: { id: `cat-${id}`, nombre: id, tipo: 'LIBRE', orden: 1 },
    diasJuego,
    grupos: [],
  }
}

describe('primeraFecha (DEC-186)', () => {
  it.each([
    ['lunes', '2026-11-30', ['2026-12-05', '2026-12-06']],
    ['viernes', '2026-12-04', ['2026-12-05', '2026-12-06']],
    ['sábado: la fecha 1 empieza ese mismo día', '2026-12-05', ['2026-12-05', '2026-12-06']],
    ['domingo: la fecha 1 es el sábado siguiente', '2026-12-06', ['2026-12-12', '2026-12-13']],
    ['jueves con cambio de mes', '2026-10-29', ['2026-10-31', '2026-11-01']],
    ['jueves con cambio de año', '2022-12-29', ['2022-12-31', '2023-01-01']],
    ['domingo que pasa al año siguiente', '2026-12-27', ['2027-01-02', '2027-01-03']],
  ])('DEC-186: inicio en %s', (_caso, inicio, esperado) => {
    expect(primeraFecha(inicio)).toEqual(esperado)
  })
})

describe('formatearDiasFecha', () => {
  it('mismo mes', () => {
    expect(formatearDiasFecha(['2026-12-05', '2026-12-06'])).toBe('sáb 5 y dom 6 dic 2026')
  })

  it('cambio de mes', () => {
    expect(formatearDiasFecha(['2026-10-31', '2026-11-01'])).toBe('sáb 31 oct y dom 1 nov 2026')
  })

  it('cambio de año', () => {
    expect(formatearDiasFecha(['2022-12-31', '2023-01-01'])).toBe(
      'sáb 31 dic 2022 y dom 1 ene 2023',
    )
  })
})

describe('resumenCalendario y estadoGrupo', () => {
  it('sin grupos', () => {
    expect(resumenCalendario(calendario())).toEqual({
      grupos: 0,
      generados: 0,
      totalFechas: 0,
      vueltas: null,
    })
  })

  it('toma la mayor fecha y la mayor vuelta de todos los grupos', () => {
    const resumen = resumenCalendario(
      calendario(grupo('a', [fecha(1, 1), fecha(2, 1), fecha(3, 2)]), grupo('b', [fecha(1, 1)]), grupo('c')),
    )

    expect(resumen).toEqual({ grupos: 3, generados: 2, totalFechas: 3, vueltas: 2 })
  })

  it('distingue sin calendario, con calendario y con partidos', () => {
    expect(estadoGrupo(grupo('a'))).toBe('SIN_CALENDARIO')
    expect(estadoGrupo(grupo('b', [fecha(1, 1)]))).toBe('CON_CALENDARIO')
    expect(estadoGrupo(grupo('c', [fecha(1, 1), fecha(2, 1, 'p1')]))).toBe('CON_PARTIDOS')
  })
})

describe('selección y envío (DEC-198)', () => {
  it('sin ningún calendario marca todos los grupos', () => {
    expect(seleccionInicial(calendario(grupo('a'), grupo('b')))).toEqual(['a', 'b'])
  })

  it('con calendario en un grupo marca solo los que no tienen', () => {
    expect(seleccionInicial(calendario(grupo('a', [fecha(1, 1)]), grupo('b')))).toEqual(['b'])
  })

  it('si todos tienen partidos no marca ninguno', () => {
    expect(
      seleccionInicial(calendario(grupo('a', [fecha(1, 1, 'p1')]), grupo('b', [fecha(1, 1, 'p2')]))),
    ).toEqual([])
  })

  it('pide motivo solo si se elige un grupo con calendario', () => {
    const conUno = calendario(grupo('a', [fecha(1, 1)]), grupo('b'))

    expect(requiereMotivo(conUno, ['b'])).toBe(false)
    expect(requiereMotivo(conUno, ['a', 'b'])).toBe(true)
  })

  it('omite grupoIds si van todos y si no manda los elegidos en orden', () => {
    const tres = calendario(grupo('a'), grupo('b'), grupo('c'))

    expect(grupoIdsParaEnviar(tres, ['c', 'a', 'b'])).toBeUndefined()
    expect(grupoIdsParaEnviar(tres, ['c', 'a'])).toEqual(['a', 'c'])
  })
})

describe('días de juego (DEC-199)', () => {
  it('conserva los días de lunes a viernes y pone o quita el fin de semana', () => {
    expect(conFinDeSemana([5, 7], true, true)).toEqual([5, 6, 7])
    expect(conFinDeSemana([5, 7], false, false)).toEqual([5])
    expect(conFinDeSemana([1, 3, 6], false, true)).toEqual([1, 3, 7])
  })

  it('quitar sábado y domingo sin otros días deja la categoría sin días', () => {
    expect(conFinDeSemana([6, 7], false, false)).toEqual([])
  })

  it('lista solo las categorías cuyo borrador difiere del servidor', () => {
    const libre = categoria('libre', [7])
    const veteranos = categoria('veteranos', [5, 7])
    const infantil = categoria('infantil', [6])

    expect(
      diasCambiados({ libre: [6], veteranos: [5, 7] }, [libre, veteranos, infantil]),
    ).toEqual([{ categoria: libre, diasJuego: [6] }])
  })
})

describe('esErrorDeFechaInicio', () => {
  it('reconoce los dos mensajes de la fecha de inicio del backend', () => {
    expect(
      esErrorDeFechaInicio(
        'La fecha de inicio 2026-11-01 es anterior al inicio de la temporada (2026-11-30).',
      ),
    ).toBe(true)
    expect(
      esErrorDeFechaInicio(
        'Ya hay jornadas en fechas de la temporada: sus días no se recalculan y las fechas nuevas siguen semana a semana desde la última. Omite fechaInicio.',
      ),
    ).toBe(true)
  })

  it('no confunde otro 422 con un error de la fecha', () => {
    expect(
      esErrorDeFechaInicio(
        'Ningún grupo tiene al menos 2 equipos activos; registra o activa equipos antes de generar el calendario.',
      ),
    ).toBe(false)
  })
})

describe('validarMotivo', () => {
  it('exige de 10 a 500 caracteres sin contar los espacios de los extremos', () => {
    expect(validarMotivo('   corto   ')).toBe('Escribe un motivo de al menos 10 caracteres.')
    expect(validarMotivo('Se sumaron dos equipos')).toBeNull()
    expect(validarMotivo('x'.repeat(501))).toBe('El motivo admite hasta 500 caracteres.')
  })
})
