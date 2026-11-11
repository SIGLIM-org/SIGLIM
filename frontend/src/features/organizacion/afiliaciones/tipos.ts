/** DTO del tarjetón de afiliación: mismos nombres del contrato (convenciones de API §6.3). */

import type { EstadoTemporada, Referencia } from '@/features/temporadas/tipos'

export type EstadoAfiliacion = 'SOLICITADA' | 'APROBADA' | 'RECHAZADA'

export type DecisionAfiliacion = 'APROBADA' | 'RECHAZADA'

/** Perfil que capturó o resolvió. El nombre se omite si RLS no permite verlo (H-08.1). */
export type PerfilAfiliacion = {
  id: string
  nombreMostrado?: string
}

/** Temporada del tarjetón, con su estado para saber si es de solo lectura. */
export type TemporadaAfiliacion = {
  id: string
  nombre: string
  estado: EstadoTemporada
}

/** Tarjetón del club por temporada (RF-101, DEC-09). */
export type Afiliacion = {
  id: string
  folio: string
  club: Referencia
  temporada: TemporadaAfiliacion
  coloresUniformes: string
  estado: EstadoAfiliacion
  categorias: Referencia[]
  capturadaPor: PerfilAfiliacion
  capturadaEn: string
  resueltaPor?: PerfilAfiliacion
  resueltaEn?: string
  observaciones?: string
}

/** `POST /afiliaciones`: el folio, el estado y el perfil los pone el servidor. */
export type CrearAfiliacionPeticion = {
  clubId: string
  temporadaId: string
  coloresUniformes: string
  categoriaIds: string[]
}

/** `PATCH /afiliaciones/{id}`: lo ausente conserva su valor (solo en SOLICITADA). */
export type ActualizarAfiliacionPeticion = {
  coloresUniformes?: string
  categoriaIds?: string[]
}

/** `POST /afiliaciones/{id}/resolucion`. */
export type ResolverAfiliacionPeticion = {
  decision: DecisionAfiliacion
  observaciones?: string
}

export type FiltrosAfiliaciones = {
  temporadaId?: string
  estado?: EstadoAfiliacion
  pagina: number
  tamano: number
}

/** Filtro de la bandeja; `TODOS` no se manda al API. */
export type FiltroEstadoAfiliacion = 'TODOS' | EstadoAfiliacion
