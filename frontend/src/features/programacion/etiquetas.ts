import type { VarianteInsignia } from '@/components/ui/Insignia'
import type { EstadoJornada, EstadoPartido } from '@/features/programacion/tipos'

/** Textos de estado para las personas (ERS §3.1.1). */
export const etiquetaEstadoJornada: Record<EstadoJornada, string> = {
  BORRADOR: 'Borrador',
  REVISION: 'En revisión',
  PUBLICADO: 'Publicado',
}

export const varianteEstadoJornada: Record<EstadoJornada, VarianteInsignia> = {
  BORRADOR: 'neutra',
  REVISION: 'informacion',
  PUBLICADO: 'exito',
}

export const etiquetaEstadoPartido: Record<EstadoPartido, string> = {
  PROGRAMADO: 'Programado',
  EN_CURSO: 'En curso',
  FINALIZADO: 'Finalizado',
  SUSPENDIDO: 'Suspendido',
  REPROGRAMADO: 'Reprogramado',
  CANCELADO: 'Cancelado',
}

export const varianteEstadoPartido: Record<EstadoPartido, VarianteInsignia> = {
  PROGRAMADO: 'informacion',
  EN_CURSO: 'advertencia',
  FINALIZADO: 'exito',
  SUSPENDIDO: 'advertencia',
  REPROGRAMADO: 'neutra',
  CANCELADO: 'error',
}

/**
 * Nombre legible de la regla que reportó la verificación. La pantalla nunca muestra el código
 * interno (DEC-63): el mensaje del API ya explica qué pasó.
 */
export function etiquetaConflicto(codigo: string): string {
  switch (codigo) {
    case 'VAL-003':
      return 'Equipo con otro partido'
    case 'VAL-004':
      return 'Cancha ocupada'
    case 'VAL-005':
      return 'Cancha no disponible'
    case 'VAL-006':
      return 'Equipo no elegible'
    case 'RF-100':
      return 'Temporada sin parámetros'
    default:
      return 'Conflicto de programación'
  }
}

/**
 * Nombre legible de una advertencia de la fecha (H-35): no bloquea el envío. Como en los conflictos,
 * el código no se muestra (DEC-63).
 */
export function etiquetaAdvertenciaFecha(codigo: string): string {
  switch (codigo) {
    case 'VAL-027':
      return 'Localía'
    case 'RF-023':
      return 'Sin árbitro'
    case 'VAL-007':
      return 'Sin horario libre'
    case 'VAL-006':
      return 'Equipo no elegible'
    case 'RF-033':
      return 'Pendiente de asignar'
    default:
      return 'Advertencia'
  }
}
