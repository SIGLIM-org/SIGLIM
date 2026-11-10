/** DTO de la auditoría: los mismos nombres del contrato (convenciones de API §6.1). */

export type PerfilAuditoria = {
  id: string
  nombreMostrado: string
}

/** Los campos opcionales vacíos no vienen en la respuesta (convenciones de API §1). */
export type RegistroAuditoria = {
  id: number
  ocurridoEn: string
  perfil?: PerfilAuditoria
  modulo: string
  accion: string
  entidad: string
  entidadId?: string
  motivo?: string
  detalle?: Record<string, unknown>
}

export type OpcionesFiltroAuditoria = {
  perfiles: PerfilAuditoria[]
  modulos: string[]
  acciones: string[]
}

export type DireccionOrden = 'asc' | 'desc'

/** Parámetros de `GET /auditoria`; `desde` (incluido) y `hasta` (excluido) son instantes ISO. */
export type ParametrosAuditoria = {
  perfilId?: string
  modulo?: string[]
  accion?: string[]
  desde?: string
  hasta?: string
  pagina: number
  tamano: number
  orden: DireccionOrden
}
