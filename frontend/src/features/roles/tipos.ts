/** DTO de roles y permisos: los mismos nombres del contrato (convenciones de API §6.1). */

export type Ambito = 'LIGA' | 'CLUB' | 'PROPIO'

/** Rol del catálogo de la liga (`GET /roles`, RF-095). */
export type RolCatalogo = {
  id: string
  codigo: string
  nombre: string
  descripcion?: string
  ambitoPredeterminado: Ambito
  asignable: boolean
}

/** Permiso del catálogo completo (`GET /permisos`). */
export type PermisoCatalogo = {
  codigo: string
  modulo: string
  descripcion: string
}

/** Par de permisos que no deben concentrarse en un rol sin aprobación (RF-096, VAL-023). */
export type ParIncompatible = {
  permisoA: string
  permisoB: string
  motivo: string
}

/** Catálogo que llena la matriz de F-13 (H-20.1). */
export type CatalogoPermisos = {
  permisos: PermisoCatalogo[]
  paresIncompatibles: ParIncompatible[]
}

/** Detalle del rol con sus permisos efectivos (`GET /roles/{id}`). */
export type RolDetalle = RolCatalogo & {
  permisos: PermisoCatalogo[]
}

export type EstadoSolicitud = 'SOLICITADO' | 'APROBADO' | 'RECHAZADO'

export type DecisionSolicitud = 'APROBADO' | 'RECHAZADO'

export type PerfilSolicitud = {
  id: string
  nombreMostrado: string
}

/** Solicitud de cambio pendiente del Presidente (RF-096, F-13). */
export type SolicitudCambioPermisos = {
  id: string
  rol: RolCatalogo
  permisosAgregados: string[]
  permisosQuitados: string[]
  motivo: string
  estado: EstadoSolicitud
  solicitadoPor: PerfilSolicitud
  solicitadoEn: string
  resueltoPor?: PerfilSolicitud
  resueltoEn?: string
  observaciones?: string
}

/** Cuerpo de `PATCH /roles/{id}/permisos`: el conjunto final, no una lista de altas. */
export type ActualizarPermisosPeticion = {
  permisos: string[]
  motivo: string
}

/** El `PATCH` aplica el cambio (`200`) o lo deja pendiente (`202`). */
export type ResultadoGuardado =
  | { tipo: 'aplicado'; rol: RolDetalle }
  | { tipo: 'pendiente'; solicitud: SolicitudCambioPermisos }

/** Cuerpo de `POST /solicitudes-cambio-permisos/{id}/resolucion`. */
export type ResolverSolicitudPeticion = {
  decision: DecisionSolicitud
  motivo: string
}

/** Cambios que aplica o descarta una solicitud. */
export type DiferenciaPermisos = {
  agregados: string[]
  quitados: string[]
}

/** Grupo de la matriz: los permisos de un módulo del ERS (§2.2). */
export type GrupoPermisos = {
  modulo: string
  permisos: PermisoCatalogo[]
}
