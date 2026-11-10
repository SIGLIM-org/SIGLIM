import { describe, expect, it } from 'vitest'
import {
  estadoDeUrl,
  FILTROS_VACIOS,
  hayFiltros,
  parametrosDeEstado,
  urlDeEstado,
} from '@/features/auditoria/filtros'

describe('filtros de auditoría', () => {
  it('sin parámetros toma los valores iniciales y no ensucia la URL', () => {
    const estado = estadoDeUrl(new URLSearchParams())

    expect(estado).toEqual({ ...FILTROS_VACIOS, pagina: 0, tamano: 50, orden: 'desc' })
    expect(urlDeEstado(estado).toString()).toBe('')
  })

  it('la URL guarda días, no instantes, y sobrevive a la lectura', () => {
    const url = new URLSearchParams(
      'modulo=M-06,M-07&accion=BLOQUEAR_CANCHA&desde=2026-10-01&hasta=2026-10-25&pagina=2&tamano=100&orden=asc',
    )
    const estado = estadoDeUrl(url)

    expect(estado.modulos).toEqual(['M-06', 'M-07'])
    expect(estado.desde).toBe('2026-10-01')
    expect(estadoDeUrl(urlDeEstado(estado))).toEqual(estado)
  })

  it('ignora en la URL valores que no reconoce', () => {
    const estado = estadoDeUrl(
      new URLSearchParams('desde=2026-02-31&hasta=ayer&pagina=-3&tamano=7&orden=otro'),
    )

    expect(estado).toEqual({ ...FILTROS_VACIOS, pagina: 0, tamano: 50, orden: 'desc' })
  })

  it('RF-134: convierte los días en instantes y omite lo vacío', () => {
    expect(
      parametrosDeEstado({
        ...FILTROS_VACIOS,
        hasta: '2026-12-31',
        pagina: 1,
        tamano: 100,
        orden: 'asc',
      }),
    ).toEqual({ hasta: '2027-01-01T00:00:00-06:00', pagina: 1, tamano: 100, orden: 'asc' })
  })

  it('el orden y la página no cuentan como filtros', () => {
    expect(hayFiltros(FILTROS_VACIOS)).toBe(false)
    expect(hayFiltros({ ...FILTROS_VACIOS, acciones: ['X'] })).toBe(true)
  })
})
