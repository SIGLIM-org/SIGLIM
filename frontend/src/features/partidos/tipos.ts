/** DTO del partido: los mismos nombres del contrato (convenciones de API §6.8, H-15.1). */

import type { EstadoJornada, EstadoPartido } from '@/features/programacion/tipos'
import type { Referencia } from '@/features/temporadas/tipos'

/** Acción manual que admite el estado del partido para la sesión (RF-046, DEC-162). */
export type AccionPartido = 'SUSPENDER' | 'REPROGRAMAR' | 'CANCELAR'

/** Una transición del partido con usuario, fecha y motivo (RF-045). */
export type CambioEstadoPartido = {
  /** `null` en la programación del partido. */
  estadoAnterior: EstadoPartido | null
  estadoNuevo: EstadoPartido
  motivo: string
  /** `null` si no se guardó usuario; el nombre es `null` si la sesión no puede ver ese perfil. */
  cambiadoPor: { id: string; nombre: string | null } | null
  cambiadoEn: string
}

/** `GET /partidos/{id}` (UI-010). */
export type PartidoDetalle = {
  id: string
  jornada: { id: string; numero: number; estadoPublicacion: EstadoJornada }
  temporada: Referencia
  categoria: Referencia
  grupo: Referencia
  equipoLocal: Referencia
  equipoVisitante: Referencia
  cancha: Referencia
  inicio: string
  finBloque: string
  estado: EstadoPartido
  /** Designaciones vigentes, el central primero; el nombre es `null` si RLS oculta a la persona. */
  arbitros: { id: string; nombre: string | null; funcion: string }[]
  cedula: { id: string; estado: string } | null
  historial: CambioEstadoPartido[]
  accionesPermitidas: AccionPartido[]
}

/** `POST /partidos/{id}/transiciones`: `inicio` y `canchaId` solo al reprogramar (DEC-160). */
export type TransicionPartidoPeticion = {
  estadoNuevo: Extract<EstadoPartido, 'SUSPENDIDO' | 'REPROGRAMADO' | 'CANCELADO'>
  motivo: string
  inicio?: string
  canchaId?: string
}
