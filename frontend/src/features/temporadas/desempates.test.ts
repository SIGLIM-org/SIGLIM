import { describe, expect, it } from 'vitest'
import {
  agregarCriterio,
  criteriosDisponibles,
  moverCriterio,
  puedeBajar,
  puedeSubir,
  quitarCriterio,
} from '@/features/temporadas/desempates'
import type { CriterioDesempate } from '@/features/temporadas/tipos'

const LISTA: CriterioDesempate[] = ['PUNTOS', 'DIFERENCIA_GOLES', 'GOLES_FAVOR', 'SORTEO']

describe('desempates', () => {
  it('RF-050: PUNTOS no se mueve ni se quita', () => {
    expect(moverCriterio(LISTA, 0, 1)).toEqual(LISTA)
    expect(quitarCriterio(LISTA, 'PUNTOS')).toEqual(LISTA)
    expect(puedeSubir(LISTA, 0)).toBe(false)
    expect(puedeBajar(LISTA, 0)).toBe(false)
  })

  it('RF-050: ningún criterio sube por encima de PUNTOS', () => {
    expect(puedeSubir(LISTA, 1)).toBe(false)
    expect(moverCriterio(LISTA, 1, -1)).toEqual(LISTA)
    expect(moverCriterio(LISTA, 2, -5)).toEqual([
      'PUNTOS',
      'GOLES_FAVOR',
      'DIFERENCIA_GOLES',
      'SORTEO',
    ])
  })

  it('RF-050: SORTEO queda anclado al final', () => {
    expect(puedeBajar(LISTA, 2)).toBe(false)
    expect(moverCriterio(LISTA, 2, 1)).toEqual(LISTA)
    expect(moverCriterio(LISTA, 3, -1)).toEqual(LISTA)
    expect(puedeSubir(LISTA, 3)).toBe(false)
  })

  it('RF-050: sube y baja los criterios intermedios', () => {
    expect(puedeSubir(LISTA, 2)).toBe(true)
    expect(puedeBajar(LISTA, 1)).toBe(true)
    expect(moverCriterio(LISTA, 1, 1)).toEqual([
      'PUNTOS',
      'GOLES_FAVOR',
      'DIFERENCIA_GOLES',
      'SORTEO',
    ])
  })

  it('RF-050: SORTEO queda al final al agregar otro criterio', () => {
    expect(agregarCriterio(LISTA, 'PARTIDOS_GANADOS')).toEqual([
      'PUNTOS',
      'DIFERENCIA_GOLES',
      'GOLES_FAVOR',
      'PARTIDOS_GANADOS',
      'SORTEO',
    ])
  })

  it('RF-050: SORTEO se agrega al final y sin él el criterio va al final', () => {
    expect(agregarCriterio(['PUNTOS', 'GOLES_FAVOR'], 'SORTEO')).toEqual([
      'PUNTOS',
      'GOLES_FAVOR',
      'SORTEO',
    ])
    expect(agregarCriterio(['PUNTOS'], 'GOLES_FAVOR')).toEqual(['PUNTOS', 'GOLES_FAVOR'])
  })

  it('RF-050: no agrega repetidos', () => {
    expect(agregarCriterio(LISTA, 'GOLES_FAVOR')).toEqual(LISTA)
    expect(agregarCriterio(LISTA, 'SORTEO')).toEqual(LISTA)
  })

  it('RF-050: quita un criterio que no es PUNTOS', () => {
    expect(quitarCriterio(LISTA, 'SORTEO')).toEqual(['PUNTOS', 'DIFERENCIA_GOLES', 'GOLES_FAVOR'])
  })

  it('RF-050: criteriosDisponibles lista los que faltan en el orden del catálogo', () => {
    expect(criteriosDisponibles(LISTA)).toEqual([
      'MENOS_GOLES_CONTRA',
      'PARTIDOS_GANADOS',
      'ENFRENTAMIENTO_DIRECTO',
      'MENOS_EXPULSIONES',
    ])
    expect(criteriosDisponibles(['PUNTOS'])).toHaveLength(7)
  })
})
