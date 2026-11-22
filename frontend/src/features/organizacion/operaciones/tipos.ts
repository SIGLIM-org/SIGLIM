/** DTO de la venta o conservación de plaza: mismos nombres del contrato (convenciones §6.3). */

import type { EstadoTemporada, Referencia } from '@/features/temporadas/tipos'

export type TipoOperacionEquipo = 'VENTA' | 'CONSERVACION_PLAZA'

export type EstadoOperacionEquipo = 'SOLICITADA' | 'AUTORIZADA' | 'RECHAZADA' | 'APLICADA'

/** Dictamen del Presidente: solo se resuelve una operación SOLICITADA. */
export type DecisionOperacionEquipo = 'AUTORIZADA' | 'RECHAZADA'

/** Perfil que solicitó o resolvió; el nombre se omite si RLS no permite verlo. */
export type PerfilOperacion = {
  id: string
  nombreMostrado?: string
}

export type TemporadaOperacion = {
  id: string
  nombre: string
  estado: EstadoTemporada
}

/** Operación de venta o conservación de plaza (RF-103, RF-104, RN-040). */
export type OperacionEquipo = {
  id: string
  tipo: TipoOperacionEquipo
  estado: EstadoOperacionEquipo
  equipo: Referencia
  clubOrigen: Referencia
  /** Solo en una venta. */
  clubDestino?: Referencia
  temporada: TemporadaOperacion
  solicitadaPor: PerfilOperacion
  solicitadaEn: string
  autorizadaPor?: PerfilOperacion
  autorizadaEn?: string
  aplicadaEn?: string
  observaciones?: string
}

/** `POST /operaciones-equipo`: el estado y los perfiles los pone el servidor. */
export type CrearOperacionEquipoPeticion = {
  tipo: TipoOperacionEquipo
  equipoId: string
  clubDestinoId?: string
  observaciones?: string
}

/** `POST /operaciones-equipo/{id}/resolucion`. */
export type ResolverOperacionEquipoPeticion = {
  decision: DecisionOperacionEquipo
  observaciones?: string
}

export type FiltrosOperacionesEquipo = {
  estado?: EstadoOperacionEquipo
  pagina: number
  tamano: number
}

/** Filtro de la bandeja; `TODOS` no se manda al API. */
export type FiltroEstadoOperacionEquipo = 'TODOS' | EstadoOperacionEquipo

/** `GET /operaciones-equipo/contexto`: clubes ACTIVO que pueden recibir un equipo. */
export type ContextoOperacionesEquipo = {
  clubes: Referencia[]
}
