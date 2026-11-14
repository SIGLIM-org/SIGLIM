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
 * por inicio.
 */
export type Jornada = {
  id: string
  grupoId: string
  faseId?: string
  numero: number
  estadoPublicacion: EstadoJornada
  partidos?: Partido[]
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
  /** Vacío hasta que el backlog B-15 implemente las reglas de localía (RF-120). */
  advertencias: AvisoVerificacion[]
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
}

/**
 * `PATCH /partidos/{id}` (RF-040): lo ausente conserva su valor. Si hay cambios, `motivo` es
 * obligatorio y queda en el historial y la auditoría (RN-018).
 */
export type ActualizarPartidoPeticion = {
  equipoLocalId?: string
  equipoVisitanteId?: string
  canchaId?: string
  inicio?: string
  motivo?: string
}

/** Grupo de la temporada activa, aplanado para el selector de F-22. */
export type GrupoProgramacion = {
  id: string
  nombre: string
  /** Nombre de la categoría a la que pertenece, para distinguir grupos homónimos. */
  categoria: string
  equipos: number
}
