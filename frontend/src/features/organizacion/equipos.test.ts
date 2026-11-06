import { describe, expect, it } from 'vitest'
import { filtrarPorTemporada, temporadaPorOmision, temporadasDeEquipos } from '@/features/organizacion/equipos'
import type { Equipo, TemporadaEquipo } from '@/features/organizacion/tipos'

const ACTIVA: TemporadaEquipo = { id: 't1', nombre: 'Temporada 2026', estado: 'ACTIVA' }
const CONFIGURACION: TemporadaEquipo = { id: 't2', nombre: 'Temporada 2027', estado: 'CONFIGURACION' }
const CERRADA: TemporadaEquipo = { id: 't0', nombre: 'Temporada 2025', estado: 'CERRADA' }

function equipo(nombre: string, categoria: string, grupo: string, temporada = ACTIVA): Equipo {
  return {
    id: `${nombre}-${categoria}-${grupo}`,
    nombre,
    estado: 'ACTIVO',
    club: { id: 'c1', nombre: 'Club Halcones' },
    grupo: { id: grupo, nombre: grupo },
    categoria: { id: categoria, nombre: categoria },
    temporada,
    creadoEn: '2026-11-03T10:00:00-06:00',
  }
}

describe('equipos de F-15', () => {
  it('DEC-16: el filtro por temporada conserva el orden del servidor', () => {
    // Orden del backend: categoría por su orden configurado (Libre antes que Infantil), no alfabético.
    const delServidor = [
      equipo('Toros', 'Libre', 'A'),
      equipo('Futuro', 'Libre', 'A', CONFIGURACION),
      equipo('Águilas', 'Libre', 'B'),
      equipo('Zorros', 'Infantil', 'A'),
    ]

    expect(filtrarPorTemporada(delServidor, 't1').map(({ nombre }) => nombre)).toEqual(['Toros', 'Águilas', 'Zorros'])
    expect(filtrarPorTemporada(delServidor, '')).toEqual(delServidor)
  })

  it('DEC-54: deriva las temporadas de los equipos, sin repetir y con la activa primero', () => {
    const temporadas = temporadasDeEquipos([
      equipo('A', 'Libre', 'A', CERRADA),
      equipo('B', 'Libre', 'A', CONFIGURACION),
      equipo('C', 'Libre', 'A', ACTIVA),
      equipo('D', 'Libre', 'B', ACTIVA),
    ])

    expect(temporadas).toEqual([ACTIVA, CONFIGURACION, CERRADA])
  })

  it('por omisión elige la activa, luego la de configuración y si no, todas', () => {
    expect(temporadaPorOmision([CERRADA, CONFIGURACION, ACTIVA])).toBe('t1')
    expect(temporadaPorOmision([CERRADA, CONFIGURACION])).toBe('t2')
    expect(temporadaPorOmision([CERRADA])).toBe('')
  })
})
