import type { Referencia } from '@/features/temporadas/tipos'

/** DTO de árbitros: mismos nombres del contrato (convenciones de API §6.5). */

export type EstadoArbitro = 'PROPUESTO' | 'VALIDADO' | 'RECHAZADO' | 'BAJA'

/** Estados que muestran las pestañas de F-20; `BAJA` no se produce en esta historia. */
export type EstadoArbitroVisible = 'PROPUESTO' | 'VALIDADO' | 'RECHAZADO'

export type DecisionArbitro = 'VALIDADO' | 'RECHAZADO'

/** Perfil que propuso o validó; el nombre se omite si RLS no permite verlo. */
export type PerfilArbitro = {
  id: string
  nombreMostrado?: string
}

/** Identidad mínima de la persona que se presenta como árbitro, sin CURP ni contacto. */
export type PersonaArbitro = {
  id: string
  nombreCompleto: string
}

/** Árbitro presentado por un club (RF-021, RF-112, RF-113). */
export type Arbitro = {
  id: string
  persona: PersonaArbitro
  club: Referencia
  estado: EstadoArbitro
  propuestoPor: PerfilArbitro
  propuestoEn: string
  validadoPor?: PerfilArbitro
  validadoEn?: string
  observaciones?: string
}

/** `POST /arbitros`: la persona y el club que la presenta (RF-112). */
export type CrearArbitroPeticion = {
  personaId: string
  clubId: string
}

/** `POST /arbitros/{id}/validacion`: dictamen de la Comisión (RF-113). */
export type ValidarArbitroPeticion = {
  decision: DecisionArbitro
  observaciones?: string
}

export type FiltrosArbitros = {
  estado?: EstadoArbitroVisible
  pagina: number
  tamano: number
}

/** Club que la sesión puede usar para presentar árbitros. */
export type ClubParaArbitro = {
  id: string
  nombre: string
}
