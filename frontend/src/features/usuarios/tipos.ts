/** DTO de usuarios: los mismos nombres del contrato (convenciones de API §6.1). */

import type { Pagina } from '@/lib/api/tipos'

/** Ámbitos de una asignación de rol (ADR-0007). */
export type Ambito = 'LIGA' | 'CLUB' | 'PROPIO'

export type RolAsignado = {
  id: string
  codigo: string
  nombre: string
}

export type ClubAsignado = {
  id: string
  nombre: string
}

/** Asignación de rol de una cuenta; las revocadas solo aparecen en el detalle. */
export type AsignacionUsuario = {
  id: string
  rol: RolAsignado
  ambito: Ambito
  club?: ClubAsignado
  activa: boolean
  creadoEn: string
  revocadaEn?: string
}

/** Cuenta de SIGLIM con sus asignaciones (RF-094). */
export type Usuario = {
  id: string
  nombreMostrado: string
  correo?: string
  activo: boolean
  creadoEn: string
  asignaciones: AsignacionUsuario[]
}

/** Rol del catálogo de la liga para elegir al invitar o asignar (RF-094, RF-095). */
export type RolCatalogo = {
  id: string
  codigo: string
  nombre: string
  descripcion?: string
  ambitoPredeterminado: Ambito
  asignable: boolean
}

/** Club que puede recibir una asignación con ámbito `CLUB`. */
export type ClubResumen = {
  id: string
  nombre: string
}

/** Filtros y página de `GET /usuarios` (convenciones de API §4). */
export type FiltrosUsuarios = {
  q?: string
  rolId?: string
  ambito?: Ambito
  clubId?: string
  activo?: boolean
  pagina: number
  tamano: number
}

/** Una asignación dentro de una invitación o de un alta posterior (RF-094). */
export type AsignacionPeticion = {
  rolId: string
  /** Si se omite, el backend usa el ámbito predeterminado del rol. */
  ambito?: Ambito
  clubId?: string
}

export type CrearUsuarioPeticion = {
  nombreMostrado: string
  correo: string
  asignaciones: AsignacionPeticion[]
}

/** `motivo` solo es obligatorio al desactivar (H-03.1). */
export type ActualizarUsuarioPeticion = {
  activo: boolean
  motivo?: string
}

export type RevocarAsignacionPeticion = {
  motivo: string
}

/** Respuesta de `GET /clubes` para el selector de club. */
export type PaginaClubes = Pagina<ClubResumen>
