import { CRITERIOS } from '@/features/temporadas/etiquetas'
import { moverElemento } from '@/features/temporadas/ordenamiento'
import type { CriterioDesempate } from '@/features/temporadas/tipos'

/*
 * Reglas de la lista de criterios de desempate (convenciones de API §6.2, RF-050): de 1 a 8,
 * PUNTOS primero, sin repetidos y SORTEO opcional, siempre al final. Son ayuda de la interfaz;
 * el servidor vuelve a validar.
 */

/** Criterios que aún no están en la lista, en el orden del catálogo. */
export function criteriosDisponibles(lista: readonly CriterioDesempate[]): CriterioDesempate[] {
  return CRITERIOS.filter((criterio) => !lista.includes(criterio))
}

/** Agrega un criterio antes de SORTEO; SORTEO va al final. Ignora los repetidos. */
export function agregarCriterio(
  lista: readonly CriterioDesempate[],
  criterio: CriterioDesempate,
): CriterioDesempate[] {
  if (lista.includes(criterio)) {
    return [...lista]
  }

  const indiceSorteo = lista.indexOf('SORTEO')

  if (criterio === 'SORTEO' || indiceSorteo === -1) {
    return [...lista, criterio]
  }

  return [...lista.slice(0, indiceSorteo), criterio, ...lista.slice(indiceSorteo)]
}

/** Quita un criterio; PUNTOS nunca se quita. */
export function quitarCriterio(
  lista: readonly CriterioDesempate[],
  criterio: CriterioDesempate,
): CriterioDesempate[] {
  if (criterio === 'PUNTOS') {
    return [...lista]
  }

  return lista.filter((actual) => actual !== criterio)
}

/** Criterios anclados: PUNTOS en la primera posición y SORTEO en la última. */
function anclado(criterio: CriterioDesempate | undefined): boolean {
  return criterio === undefined || criterio === 'PUNTOS' || criterio === 'SORTEO'
}

/** ¿El criterio de esa posición puede subir sin pasar por encima de PUNTOS? */
export function puedeSubir(lista: readonly CriterioDesempate[], indice: number): boolean {
  return !anclado(lista[indice]) && indice > 0 && lista[indice - 1] !== 'PUNTOS'
}

/** ¿El criterio de esa posición puede bajar sin pasar por debajo de SORTEO? */
export function puedeBajar(lista: readonly CriterioDesempate[], indice: number): boolean {
  const siguiente = lista[indice + 1]

  return !anclado(lista[indice]) && siguiente !== undefined && siguiente !== 'SORTEO'
}

/**
 * Mueve un criterio `delta` posiciones con PUNTOS fijo en 0 y SORTEO fijo al final. Un movimiento
 * que rompería esas anclas se acota; si no hay movimiento posible, devuelve una copia.
 */
export function moverCriterio(
  lista: readonly CriterioDesempate[],
  indice: number,
  delta: number,
): CriterioDesempate[] {
  if (anclado(lista[indice]) || delta === 0) {
    return [...lista]
  }

  const primero = lista[0] === 'PUNTOS' ? 1 : 0
  const ultimo = lista[lista.length - 1] === 'SORTEO' ? lista.length - 2 : lista.length - 1
  const destino = Math.min(Math.max(indice + delta, primero), ultimo)

  return moverElemento(lista, indice, destino)
}
