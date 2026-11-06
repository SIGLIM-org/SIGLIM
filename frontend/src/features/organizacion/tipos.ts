/** DTO de clubes y equipos: los mismos nombres del contrato (convenciones de API §6.3). */

import type { EstadoTemporada, Referencia } from '@/features/temporadas/tipos'

export type EstadoClub = 'ACTIVO' | 'CERRADO'

/** Club afiliado a la liga (RF-008). `cerradoEn` y `motivoCierre` solo vienen si está cerrado. */
export type Club = {
  id: string
  nombre: string
  estado: EstadoClub
  creadoEn: string
  /** Día `AAAA-MM-DD` del cierre (RN-041). */
  cerradoEn?: string
  motivoCierre?: string
}

export type EstadoEquipo = 'ACTIVO' | 'BAJA'

/** Temporada del grupo del equipo, con su estado: decide si admite cambios (RF-003, RN-007). */
export type TemporadaEquipo = {
  id: string
  nombre: string
  estado: EstadoTemporada
}

/** Equipo de un club en un grupo de la temporada (RF-009). `bajaEn` y `motivoBaja` solo en BAJA. */
export type Equipo = {
  id: string
  nombre: string
  estado: EstadoEquipo
  club: Referencia
  grupo: Referencia
  categoria: Referencia
  temporada: TemporadaEquipo
  creadoEn: string
  /** Día `AAAA-MM-DD` de la baja (RN-005). */
  bajaEn?: string
  motivoBaja?: string
}

export type FiltrosClubes = {
  q?: string
  estado?: EstadoClub
  pagina: number
  tamano: number
}

export type FiltrosEquipos = {
  clubId: string
  estado?: EstadoEquipo
}

/** `POST /clubes` (RF-008). */
export type CrearClubPeticion = {
  nombre: string
}

/** `POST /equipos` (RF-009): la temporada es la del grupo (RN-007). */
export type CrearEquipoPeticion = {
  clubId: string
  grupoId: string
  nombre: string
}

/** `PATCH /equipos/{id}`: lo ausente conserva su valor (RF-009). */
export type ActualizarEquipoPeticion = {
  nombre?: string
  grupoId?: string
}

/** `POST /equipos/{id}/baja` (RF-009, RN-021). */
export type BajaEquipoPeticion = {
  motivo: string
}
