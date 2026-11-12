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
