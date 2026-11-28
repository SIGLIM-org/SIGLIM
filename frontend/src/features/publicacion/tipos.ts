/** DTO de comunicados: los mismos nombres del contrato (convenciones de API §6.11). */

/** Estado del flujo editorial de un comunicado (RF-090, ERS §4.3.6). */
export type EstadoPublicacion = 'BORRADOR' | 'REVISION' | 'PUBLICADO'

/** Filtro de la bandeja; `TODOS` no se envía al API. */
export type FiltroEstado = EstadoPublicacion | 'TODOS'

/**
 * Comunicado del flujo Borrador → Revisión → Publicado (RF-090).
 *
 * `redactadoPor` y `aprobadoPor` son `perfiles.id`; la pantalla solo conoce el
 * nombre de la sesión actual y usa el nombre de la oficina para las demás
 * personas (el contrato no devuelve el nombre del perfil).
 */
export type Comunicado = {
  id: string
  titulo: string
  cuerpo: string
  estadoPublicacion: EstadoPublicacion
  redactadoPor: string
  aprobadoPor?: string
  aprobadoEn?: string
  publicadoEn?: string
  observaciones?: string
  creadoEn: string
  actualizadoEn: string
}

export type CrearComunicadoPeticion = {
  titulo: string
  cuerpo: string
}

/** Un campo ausente conserva su valor (RF-090). */
export type ActualizarComunicadoPeticion = Partial<CrearComunicadoPeticion>

export type DecisionComunicado = 'PUBLICADO' | 'BORRADOR'

export type ResolverComunicadoPeticion = {
  decision: DecisionComunicado
  /** Obligatorio al devolver a borrador; opcional al aprobar. */
  observaciones?: string
}

/** `FECHA` agrupa las jornadas de una fecha de programación, que se aprueban completas (H-35.3). */
export type TipoPublicacion = 'JORNADA' | 'COMUNICADO' | 'FECHA'

/**
 * Fila de `GET /publicaciones/pendientes` (H-13.1, H-35.3): una fecha de programación, una jornada
 * suelta o un comunicado en `REVISION`. `contexto` y `partidos` solo vienen en fechas y jornadas.
 */
export type PublicacionPendiente = {
  tipo: TipoPublicacion
  id: string
  titulo: string
  contexto?: string
  partidos?: number
  enviadoPor: { id: string; nombre: string }
  enviadoEn: string
}
