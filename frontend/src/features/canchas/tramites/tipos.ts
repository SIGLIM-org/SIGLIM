/** DTO de los trámites de cancha: mismos nombres del contrato (convenciones §6.6, B-14). */

import type { Referencia } from '@/features/temporadas/tipos'

export type TipoSolicitudCancha = 'PRESTAMO' | 'COMODATO' | 'OFERTA'

export type EstadoSolicitudCancha =
  | 'PENDIENTE'
  | 'APROBADA'
  | 'NO_APROBADA'
  | 'PENDIENTE_NEGOCIACION'
  | 'ACEPTADA'
  | 'RECHAZADA'

export type DecisionSolicitudCancha = 'APROBADA' | 'NO_APROBADA' | 'ACEPTADA' | 'RECHAZADA'

/** Pestaña de la pantalla: Formato 1 (préstamo y comodato) o Formato 2 (oferta). */
export type FormatoTramite = 1 | 2

export type DestinatarioPrestamo = 'LIGA' | 'DUENO_EXTERNO'

/** Campos del Formato 1, carta de solicitud de préstamo (R8). */
export type DatosPrestamo = {
  lugar: string
  dirigidaA: DestinatarioPrestamo
  destinatarioNombre: string
  destinatarioCargo: string
  solicitanteNombre: string
  solicitanteCargo: string
  instalacion: string
  finalidad: string
  /** `AAAA-MM-DD` */
  fechaUtilizacion: string
  /** `HH:mm` o `HH:mm:ss` */
  horaInicio: string
  horaTermino: string
  actividad: string
  equiposParticipantes?: string
  asistentesAproximados?: number
  responsableActividad: string
  telefonoContacto: string
  correoContacto?: string
}

/** Campos del Formato 2, carta de oferta de cancha (R8). */
export type DatosOferta = {
  lugar: string
  oferenteNombre: string
  oferenteCaracter: string
  institucion?: string
  nombreCancha: string
  ubicacion: string
  tipoSuperficie: string
  disponibilidad: string
  capacidadAproximada?: number
  serviciosDisponibles?: string
  costoUso: string
  condicionesPago?: string
  personaResponsable: string
  telefonoContacto: string
  correoContacto?: string
}

export type PerfilTramite = {
  id: string
  nombreMostrado?: string
}

/** Trámite de cancha con su número de oficio (RF-116 a RF-118). */
export type SolicitudCancha = {
  id: string
  tipo: TipoSolicitudCancha
  formato: FormatoTramite
  estado: EstadoSolicitudCancha
  numeroOficio: string
  club?: Referencia
  solicitanteExterno?: string
  cancha?: Referencia
  prestamo?: DatosPrestamo
  oferta?: DatosOferta
  capturadaPor: PerfilTramite
  capturadaEn: string
  resueltaPor?: PerfilTramite
  resueltaEn?: string
  observaciones?: string
}

/** `POST /solicitudes-cancha`: el estado y el número de oficio los pone el servidor. */
export type CrearSolicitudCanchaPeticion = {
  tipo: TipoSolicitudCancha
  clubId?: string
  solicitanteExterno?: string
  canchaId?: string
  prestamo?: DatosPrestamo
  oferta?: DatosOferta
}

/** `POST /solicitudes-cancha/{id}/resolucion`. */
export type ResolverSolicitudCanchaPeticion = {
  decision: DecisionSolicitudCancha
  observaciones?: string
}

export type FiltrosSolicitudesCancha = {
  formato: FormatoTramite
  estado?: EstadoSolicitudCancha
  pagina: number
  tamano: number
}

/** Filtro de estado de la bandeja; `TODOS` no se manda al API. */
export type FiltroEstadoTramite = 'TODOS' | EstadoSolicitudCancha

/** `GET /solicitudes-cancha/contexto`. */
export type ContextoSolicitudesCancha = {
  clubes: Referencia[]
  canchas: Referencia[]
  capturaExternos: boolean
}

/** `GET /solicitudes-cancha/{id}/oficio`: datos de la carta (RF-118). */
export type CartaOficio = {
  numeroOficio: string
  tipo: TipoSolicitudCancha
  formato: FormatoTramite
  asunto: string
  liga: string
  /** `AAAA-MM-DD` */
  fechaEmision: string
  solicitante: string
  prestamo?: DatosPrestamo
  oferta?: DatosOferta
  estado: EstadoSolicitudCancha
  resueltaEn?: string
  resueltaPor?: string
  observaciones?: string
}
