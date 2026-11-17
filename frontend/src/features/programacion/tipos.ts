/** DTO de programación: los mismos nombres del contrato (convenciones de API §6.7). */

import type { Referencia } from '@/features/temporadas/tipos'

export type EstadoJornada = 'BORRADOR' | 'REVISION' | 'PUBLICADO'

export type EstadoPartido =
  | 'PROGRAMADO'
  | 'EN_CURSO'
  | 'FINALIZADO'
  | 'SUSPENDIDO'
  | 'REPROGRAMADO'
  | 'CANCELADO'

/**
 * Jornada de un grupo (RF-033, RF-121). El listado no trae `partidos`; el detalle sí, ordenados
 * por inicio. Los campos de publicación (H-13.1) se omiten mientras están vacíos; `enviadaPor` y
 * `aprobadaPor` son `perfiles.id`.
 */
export type Jornada = {
  id: string
  grupoId: string
  faseId?: string
  numero: number
  estadoPublicacion: EstadoJornada
  partidos?: Partido[]
  enviadaPor?: string
  enviadaEn?: string
  aprobadaPor?: string
  aprobadaEn?: string
  publicadaEn?: string
  /** Observaciones de la última devolución o nota de la aprobación. */
  observaciones?: string
  creadoEn: string
  actualizadoEn: string
}

/** Partido programado a mano en una jornada (RF-033, RF-036). */
export type Partido = {
  id: string
  jornadaId: string
  equipoLocal: Referencia
  equipoVisitante: Referencia
  cancha: Referencia
  /** ISO 8601 con desplazamiento, en la zona de negocio. */
  inicio: string
  /** Fin del bloque de cancha que calcula la base (DEC-25). */
  finBloque: string
  estado: EstadoPartido
  /** Solo cuando se aceptó una advertencia de localía (RF-120, backlog). */
  motivoLocalia?: string
  creadoEn: string
  actualizadoEn: string
}

/** Aviso de la verificación de la jornada; `codigo` identifica la regla (VAL-003…, RF-100). */
export type AvisoVerificacion = {
  partidoId: string
  codigo: string
  mensaje: string
}

/** `POST /jornadas/{id}/verificacion`: no guarda nada (H-12.3, RF-039). */
export type VerificacionJornada = {
  conflictos: AvisoVerificacion[]
  /**
   * Advertencias de localía (B-15.1, VAL-027): no bloquean el envío y se aceptan con motivo. Si el
   * partido ya se aceptó, el mensaje lo indica (B-15.2).
   */
  advertencias: AvisoVerificacion[]
}

/** Advertencia aceptable de un `409 VAL-027`; en un alta el `partidoId` todavía no existe. */
export type AdvertenciaAceptable = Pick<AvisoVerificacion, 'codigo' | 'mensaje'>

/** Dictamen del Presidente sobre una jornada en revisión (RF-121). */
export type DecisionJornada = 'PUBLICADO' | 'BORRADOR'

/**
 * `POST /jornadas/{id}/resolucion` (H-13.1): las observaciones son obligatorias al devolver
 * (mínimo 10 caracteres) y opcionales al aprobar.
 */
export type ResolverJornadaPeticion = {
  decision: DecisionJornada
  observaciones?: string
}

/** `POST /jornadas` (RF-033): la jornada nace en BORRADOR. */
export type CrearJornadaPeticion = {
  grupoId: string
  faseId?: string
  numero: number
}

/** `POST /jornadas/{id}/partidos` (RF-033, RF-036). */
export type CrearPartidoPeticion = {
  equipoLocalId: string
  equipoVisitanteId: string
  canchaId: string
  inicio: string
  /** Motivo con el que se acepta una advertencia de localía (VAL-027); solo si aplica. */
  motivoAceptacion?: string
}

/**
 * `PATCH /partidos/{id}` (RF-040): lo ausente conserva su valor. Si hay cambios, `motivo` es
 * obligatorio y queda en el historial y la auditoría (RN-018). `motivoAceptacion` acepta una
 * advertencia de localía; si llega solo, no cambia el partido y guarda el motivo (B-15.2, VAL-027).
 */
export type ActualizarPartidoPeticion = {
  equipoLocalId?: string
  equipoVisitanteId?: string
  canchaId?: string
  inicio?: string
  motivo?: string
  motivoAceptacion?: string
}

/** Grupo de la temporada activa, aplanado para el selector de F-22. */
export type GrupoProgramacion = {
  id: string
  nombre: string
  /** Nombre de la categoría a la que pertenece, para distinguir grupos homónimos. */
  categoria: string
  equipos: number
}
