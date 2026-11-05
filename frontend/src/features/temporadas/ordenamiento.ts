/** Acota un índice al rango `[0, longitud - 1]`. */
function acotar(indice: number, longitud: number): number {
  return Math.min(Math.max(indice, 0), Math.max(longitud - 1, 0))
}

/**
 * Mueve un elemento de `desde` a `hasta` y devuelve una copia; la lista original no cambia. Los
 * índices fuera de rango se acotan a los extremos (F-11, RF-099, RF-050).
 */
export function moverElemento<T>(lista: readonly T[], desde: number, hasta: number): T[] {
  const copia = [...lista]

  if (copia.length === 0) {
    return copia
  }

  const origen = acotar(desde, copia.length)
  const destino = acotar(hasta, copia.length)
  const [elemento] = copia.splice(origen, 1)
  copia.splice(destino, 0, elemento)

  return copia
}
