import type { Equipo, TemporadaEquipo } from '@/features/organizacion/tipos'

const comparar = (a: string, b: string) => a.localeCompare(b, 'es', { sensitivity: 'base' })

/**
 * Equipos de una temporada (`''` son todas). Conserva el orden del servidor: categoría por su
 * orden configurado → grupo → nombre (DEC-16), que el cliente no repite.
 */
export function filtrarPorTemporada(equipos: readonly Equipo[], temporadaId: string): Equipo[] {
  return temporadaId ? equipos.filter(({ temporada }) => temporada.id === temporadaId) : [...equipos]
}

const PRIORIDAD_ESTADO: Record<TemporadaEquipo['estado'], number> = {
  ACTIVA: 0,
  CONFIGURACION: 1,
  CERRADA: 2,
}

/**
 * Temporadas presentes en los equipos, sin repetir: activa, en configuración y cerradas, cada
 * grupo por nombre. Salen de los equipos y no de `GET /temporadas`, que la Directiva de club no
 * puede consultar (DEC-54).
 */
export function temporadasDeEquipos(equipos: readonly Equipo[]): TemporadaEquipo[] {
  const unicas = new Map<string, TemporadaEquipo>()

  for (const { temporada } of equipos) {
    unicas.set(temporada.id, temporada)
  }

  return [...unicas.values()].sort(
    (a, b) => PRIORIDAD_ESTADO[a.estado] - PRIORIDAD_ESTADO[b.estado] || comparar(a.nombre, b.nombre),
  )
}

/** Temporada elegida por omisión: la activa; si no hay, la de configuración; si no, todas (`''`). */
export function temporadaPorOmision(temporadas: readonly TemporadaEquipo[]): string {
  return (
    temporadas.find(({ estado }) => estado === 'ACTIVA')?.id ??
    temporadas.find(({ estado }) => estado === 'CONFIGURACION')?.id ??
    ''
  )
}
