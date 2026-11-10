import { describe, expect, it } from 'vitest'
import { moverElemento } from '@/features/temporadas/ordenamiento'

describe('moverElemento', () => {
  it('RF-099: mueve un elemento hacia abajo y devuelve una copia', () => {
    const lista = ['a', 'b', 'c', 'd']
    const resultado = moverElemento(lista, 0, 2)

    expect(resultado).toEqual(['b', 'c', 'a', 'd'])
    expect(lista).toEqual(['a', 'b', 'c', 'd'])
    expect(resultado).not.toBe(lista)
  })

  it('RF-099: mueve un elemento hacia arriba', () => {
    expect(moverElemento(['a', 'b', 'c', 'd'], 3, 1)).toEqual(['a', 'd', 'b', 'c'])
  })

  it('RF-099: mover a la misma posición deja el orden igual', () => {
    expect(moverElemento(['a', 'b', 'c'], 1, 1)).toEqual(['a', 'b', 'c'])
  })

  it('RF-099: acota los índices fuera de rango a los extremos', () => {
    expect(moverElemento(['a', 'b', 'c'], 0, 10)).toEqual(['b', 'c', 'a'])
    expect(moverElemento(['a', 'b', 'c'], 2, -5)).toEqual(['c', 'a', 'b'])
    expect(moverElemento(['a', 'b', 'c'], 9, 0)).toEqual(['c', 'a', 'b'])
  })

  it('RF-099: una lista vacía o de un elemento no falla', () => {
    expect(moverElemento([], 0, 1)).toEqual([])
    expect(moverElemento(['a'], 0, 3)).toEqual(['a'])
  })
})
