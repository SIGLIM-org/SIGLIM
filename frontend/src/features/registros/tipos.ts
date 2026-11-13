/**
 * DTO de registros de jugador, aviso de privacidad, consentimiento y tutela (M-04, M-01, M-03).
 * Mismos nombres del contrato (convenciones de API §6.1, §6.3 y §6.4). La identidad de la persona y
 * los equipos se reutilizan de `features/organizacion/tipos`.
 */

import type { Persona } from '@/features/organizacion/tipos'
import type { EstadoTemporada, Referencia } from '@/features/temporadas/tipos'

/** `GET /personas?curp=` con la minoría calculada en `America/Mexico_City` (DEC-104). */
export type PersonaConMinoria = Persona & { esMenor: boolean }

export type EstadoRegistro =
  | 'PENDIENTE_VALIDACION'
  | 'ACTIVO'
  | 'SUSPENDIDO'
  | 'BAJA'
  | 'RECHAZADO'

/** Respuesta `201` de `POST /registros-jugador` (§6.4, DEC-90); nunca trae la CURP. */
export type RegistroJugador = {
  id: string
  persona: { id: string; nombreCompleto: string }
  equipo: Referencia
  club: Referencia
  categoria: Referencia
  temporada: { id: string; nombre: string; estado: EstadoTemporada }
  estado: EstadoRegistro
  /** Día `AAAA-MM-DD` del alta en la zona de negocio (DEC-89). */
  altaEn: string
  creadoEn: string
  validadoPor: { id: string; nombre: string } | null
  validadoEn: string | null
  observaciones: string | null
}

/**
 * Fila de `GET /registros-jugador` (§6.4, DEC-109, DEC-110): solo la CURP enmascarada y `esMenor`
 * en la fecha de alta; sin datos de validación ni avance de documentos.
 */
export type RegistroJugadorFila = {
  id: string
  persona: { id: string; nombreCompleto: string; curpEnmascarada: string }
  equipo: Referencia
  club: Referencia
  categoria: Referencia
  temporada: { id: string; nombre: string; estado: EstadoTemporada }
  estado: EstadoRegistro
  esMenor: boolean
  /** Día `AAAA-MM-DD` del alta en la zona de negocio (DEC-89). */
  altaEn: string
  creadoEn: string
}

/** Tutela vigente del menor, solo con nombre y parentesco (DEC-109). */
export type TutorRegistro = {
  nombreCompleto: string
  parentesco: string
}

/**
 * `GET /registros-jugador/{id}` (DEC-109): los campos del alta y la validación, `esMenor` y los
 * tutores. `curp` solo llega completa con `organizacion.persona:CONSULTAR` sobre el club (DEC-110).
 */
export type RegistroJugadorDetalle = Omit<RegistroJugador, 'persona'> & {
  persona: { id: string; nombreCompleto: string; curpEnmascarada: string; curp: string | null }
  esMenor: boolean
  tutores: TutorRegistro[]
}

export type OrdenRegistros = 'creadoEn,asc' | 'creadoEn,desc' | 'nombre,asc'

/** Filtros de `GET /registros-jugador` que usa la bandeja (DEC-111). */
export type FiltrosRegistros = {
  estado?: EstadoRegistro
  clubId?: string
  orden: OrdenRegistros
  pagina: number
  tamano: number
}

export type EstadoValidacionDocumento = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO'

/** Tipo de documento del catálogo (`tipos_documento`). */
export type TipoDocumento = {
  id: number
  codigo: string
  nombre: string
}

/** Documento de un registro (§6.4); nunca trae bucket, clave, URL ni SHA-256. */
export type DocumentoRegistro = {
  id: string
  registroId: string
  tipoDocumento: TipoDocumento
  tipoMime: string
  tamanoBytes: number
  estadoValidacion: EstadoValidacionDocumento
  observaciones: string | null
  validadoPor: { id: string; nombre: string } | null
  validadoEn: string | null
  reemplazaA: string | null
  cargadoEn: string
}

/** Requisito de un tipo de documento con sus documentos vigentes (DEC-92, DEC-96). */
export type Requisito = {
  tipoDocumento: TipoDocumento
  cantidad: number
  soloMenores: boolean
  /** Valor de demostración: se rotula «No oficial» (DEC-74). */
  esDemo: boolean
  /** Vigentes no rechazados. */
  cargados: number
  aprobados: number
  faltan: number
  /** Vigentes de cualquier estado, por fecha de carga. */
  documentos: DocumentoRegistro[]
}

/** `GET /registros-jugador/{id}/requisitos`. */
export type RequisitosRegistro = {
  registroId: string
  estadoRegistro: EstadoRegistro
  categoria: { id: string; nombre: string; tipo: string }
  esMenor: boolean
  requisitos: Requisito[]
  completo: boolean
  listoParaValidar: boolean
}

/** `GET /documentos/{id}/enlace` (DEC-95): URL prefirmada de 60 s que nunca se guarda (RNF-018). */
export type EnlaceDocumento = {
  url: string
  expiraEn: string
}

/** Elemento de la extensión `faltantes` del `422 VAL-001` (DEC-99). */
export type DocumentoFaltante = {
  tipoDocumento: TipoDocumento
  cantidad: number
  cargados: number
  aprobados: number
}

/** `POST /documentos/{id}/validacion` (DEC-96). */
export type ValidarDocumentoPeticion = {
  decision: 'APROBADO' | 'RECHAZADO'
  observaciones?: string
}

/** `POST /registros-jugador/{id}/validacion` (DEC-97, DEC-98). */
export type ValidarRegistroPeticion = {
  decision: 'ACTIVO' | 'RECHAZADO'
  observaciones?: string
}

/** `POST /registros-jugador`: la temporada es la del grupo del equipo (DEC-85). */
export type CrearRegistroPeticion = {
  personaId: string
  equipoId: string
}

export type TipoAvisoPrivacidad = 'INTEGRAL' | 'SIMPLIFICADO'

/** `GET /avisos-privacidad/vigente` (DEC-80). `esDemo` se rotula «No oficial» (DEC-74). */
export type AvisoPrivacidad = {
  id: string
  tipo: TipoAvisoPrivacidad
  version: string
  /** Texto del aviso que define la Liga (D-08). */
  contenido: string
  finalidades: string[]
  /** Día `AAAA-MM-DD`. */
  vigenteDesde: string
  esDemo: boolean
}

/**
 * `POST /personas/{id}/consentimientos` (DEC-81). En un menor, `otorgadoPorId` es su tutor vigente;
 * en un adulto se omite y lo otorga el titular (DEC-76).
 */
export type RegistrarConsentimientoPeticion = {
  avisoId: string
  finalidades: string[]
  otorgadoPorId?: string
}

export type Consentimiento = {
  id: string
  personaId: string
  otorgadoPorId: string
  avisoId: string
  finalidades: string[]
  otorgadoEn: string
}

/** `POST /personas/{id}/tutelas` (DEC-82): parentesco de hasta 40 caracteres tras recortarlo. */
export type RegistrarTutelaPeticion = {
  tutorId: string
  parentesco: string
}

export type Tutela = {
  id: string
  menorId: string
  tutorId: string
  parentesco: string
  vigente: boolean
  creadoEn: string
}
