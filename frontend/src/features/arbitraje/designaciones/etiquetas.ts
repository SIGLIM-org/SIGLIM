import type { Referencia } from '@/features/temporadas/tipos'
import type {
  CandidatoArbitral,
  FuncionArbitral,
} from '@/features/arbitraje/designaciones/tipos'

/** Nombre que se puede mostrar; si RLS oculta a la persona no se inventa uno (DEC-78). */
export function nombreArbitro(designacion: { arbitro: { nombre: string | null } }): string {
  return designacion.arbitro.nombre ?? 'Nombre reservado'
}

/** «Local vs Visitante» de un partido. */
export function nombrePartido({
  equipoLocal,
  equipoVisitante,
}: {
  equipoLocal: Referencia
  equipoVisitante: Referencia
}): string {
  return `${equipoLocal.nombre} vs ${equipoVisitante.nombre}`
}

/** Texto en minúsculas de la función, para títulos como «Designar árbitro central». */
export const textoFuncion: Record<FuncionArbitral, string> = {
  CENTRAL: 'árbitro central',
  ASISTENTE: 'asistente',
}

/** Insignia de la función en «Mis designaciones». */
export const etiquetaFuncion: Record<FuncionArbitral, string> = {
  CENTRAL: 'Central',
  ASISTENTE: 'Asistente',
}

/** «1 designación en la temporada» o «N designaciones en la temporada». */
export function textoCarga(carga: number): string {
  return carga === 1
    ? '1 designación en la temporada'
    : `${carga} designaciones en la temporada`
}

const comparadorNombres = new Intl.Collator('es-MX', { sensitivity: 'base' })

function compararNombre(primero: CandidatoArbitral, segundo: CandidatoArbitral): number {
  const a = primero.arbitro.nombre
  const b = segundo.arbitro.nombre

  if (a === null || b === null) {
    return a === b ? 0 : a === null ? 1 : -1
  }

  return comparadorNombres.compare(a, b)
}

/**
 * Orden del diálogo (DEC-210): primero los elegibles, de menor a mayor carga y por nombre, para
 * repartir el trabajo; después los no elegibles por nombre. Los nombres reservados van al final.
 */
export function ordenarCandidatos(candidatos: readonly CandidatoArbitral[]): CandidatoArbitral[] {
  return [...candidatos].sort((primero, segundo) => {
    if (primero.elegible !== segundo.elegible) {
      return primero.elegible ? -1 : 1
    }
    if (primero.elegible && primero.carga !== segundo.carga) {
      return primero.carga - segundo.carga
    }

    return compararNombre(primero, segundo)
  })
}

/** Búsqueda por nombre o club, sin distinguir mayúsculas ni acentos. */
export function coincideBusqueda(candidato: CandidatoArbitral, busqueda: string): boolean {
  const termino = normalizar(busqueda.trim())

  if (!termino) {
    return true
  }

  return [candidato.arbitro.nombre ?? '', candidato.arbitro.club.nombre].some((texto) =>
    normalizar(texto).includes(termino),
  )
}

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}
