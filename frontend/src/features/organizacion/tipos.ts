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
  /** Club cerrado del que proviene por un cambio de nombre; solo viene si existe (DEC-15). */
  clubAnterior?: Referencia
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

/** Cargos de la directiva del club (RF-102, DEC-03). */
export type TipoCargoDirectiva = 'PRESIDENTE' | 'SECRETARIO' | 'TESORERO' | 'DELEGADO'

export type EstadoCargoDirectiva = 'PENDIENTE' | 'AUTORIZADO'

/**
 * Vigencia de un cargo de directiva (RF-102). `hasta` solo viene cuando otro cambio ya la
 * terminó; `autorizadoPor` solo cuando el cambio está autorizado.
 */
export type CargoDirectiva = {
  id: string
  clubId: string
  personaId: string
  nombrePersona: string
  cargo: TipoCargoDirectiva
  /** Día `AAAA-MM-DD` de inicio de la vigencia. */
  desde: string
  /** Día `AAAA-MM-DD` en que terminó; ausente si sigue vigente o está pendiente. */
  hasta?: string
  estado: EstadoCargoDirectiva
  autorizadoPor?: string
}

/** `POST /clubes/{id}/directiva`: el cambio nace pendiente de autorización (RF-102). */
export type RegistrarCargoDirectivaPeticion = {
  personaId: string
  cargo: TipoCargoDirectiva
  desde: string
}

/**
 * Identidad mínima que devuelve `GET /personas?curp=` (RF-010, RF-011). Se usa para confirmar a
 * quién se le registra el cargo antes de enviar su `personaId`; no incluye datos de contacto.
 */
export type Persona = {
  id: string
  curp: string
  nombre: string
  apellidoPaterno: string
  apellidoMaterno?: string
}

/** Detalle que devuelve el alta de una persona (H-09.1); incluye la fecha para reutilizarla. */
export type PersonaDetalle = Persona & {
  fechaNacimiento: string
  correo?: string
  telefono?: string
}

/** `POST /personas` (H-09.1, DEC-62): la identidad es única por CURP. */
export type CrearPersonaPeticion = {
  curp: string
  nombre: string
  apellidoPaterno: string
  apellidoMaterno?: string
  fechaNacimiento: string
  correo?: string
  telefono?: string
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

/** `POST /clubes/{id}/cambio-nombre`: registra un club nuevo y cierra el actual (RF-105, DEC-15). */
export type CambiarNombreClubPeticion = {
  nombre: string
  motivo: string
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
