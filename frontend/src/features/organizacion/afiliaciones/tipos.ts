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

/** Resumen de temporada que necesita la bandeja; el contexto añade fechas y categorías. */
export type TemporadaResumenAfiliacion = TemporadaAfiliacion

/** Grupo de una categoría en la temporada, con sus equipos activos. */
export type GrupoContextoAfiliacion = {
  id: string
  nombre: string
  equipos: number
}

/** Categoría que participa en la temporada, con sus grupos. */
export type CategoriaContextoAfiliacion = {
  id: string
  nombre: string
  grupos: GrupoContextoAfiliacion[]
}

/** Temporada del contexto de captura: la misma que usa el API en el tarjetón, con categorías. */
export type TemporadaContextoAfiliacion = TemporadaAfiliacion & {
  fechaInicio: string
  categorias: CategoriaContextoAfiliacion[]
}

/** `GET /afiliaciones/contexto`: temporadas de la liga con sus categorías y grupos (H-08.2). */
export type ContextoAfiliacion = {
  temporadas: TemporadaContextoAfiliacion[]
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
