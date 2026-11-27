import type { AccionPartido } from '@/features/partidos/tipos'
import type { EstadoJornada, EstadoPartido } from '@/features/programacion/tipos'
import type { Referencia } from '@/features/temporadas/tipos'

/** DTO de designaciones: mismos nombres del contrato (convenciones de API §6.5, H-14.1). */

/** Función del árbitro en el partido; a lo más un central y dos asistentes vigentes (DEC-201). */
export type FuncionArbitral = 'CENTRAL' | 'ASISTENTE'

/** Árbitro con su club, sin CURP ni contacto; `nombre` es `null` si RLS oculta a la persona (DEC-78). */
export type ArbitroDesignado = {
  id: string
  nombre: string | null
  club: Referencia
}

/**
 * Designación vigente. En la vista por jornada solo llegan `id`, `arbitro`, `funcion`,
 * `automatica` y `designadoEn`; al designar y al retirar se agregan `partidoId`, `vigente` y, en el
 * retiro, `motivoCambio` (los campos sin valor se omiten).
 */
export type Designacion = {
  id: string
  partidoId?: string
  arbitro: ArbitroDesignado
  funcion: FuncionArbitral
  vigente?: boolean
  /** La hizo la asignación automática (DEC-182). */
  automatica: boolean
  designadoEn: string
  motivoCambio?: string
}

/** Jornada de `GET /designaciones?jornadaId=` con su grupo y categoría. */
export type JornadaDesignacion = {
  id: string
  numero: number
  estadoPublicacion: EstadoJornada
  grupo: Referencia
  categoria: Referencia
}

/** Avance de la designación de la jornada: partidos no cancelados, con central y con dos asistentes. */
export type AvanceDesignacion = {
  partidos: number
  conCentral: number
  asistentesCompletos: number
}

/** Partido de la jornada con sus designaciones vigentes (UI-025). */
export type PartidoDesignable = {
  id: string
  inicio: string
  finBloque: string
  estado: EstadoPartido
  cancha: Referencia
  equipoLocal: Referencia
  equipoVisitante: Referencia
  /** Verdadero si está `PROGRAMADO` o `REPROGRAMADO`: solo así se designa o retira (DEC-201). */
  designable: boolean
  central: Designacion | null
  asistentes: Designacion[]
}

/** `GET /designaciones?jornadaId=` (H-14.1, UI-025): excluye los partidos `CANCELADO` (DEC-181). */
export type DesignacionesJornada = {
  jornada: JornadaDesignacion
  avance: AvanceDesignacion
  partidos: PartidoDesignable[]
}

/** Causa que impide designar (`VAL-019`, `RF-039`, `YA_DESIGNADO`) o advertencia (`RF-022`). */
export type CausaCandidato = {
  codigo: string
  mensaje: string
}

/** `GET /partidos/{id}/arbitros-candidatos` (DEC-204): elegibilidad, advertencias y carga. */
export type CandidatoArbitral = {
  arbitro: ArbitroDesignado
  /** Verdadero si `causas` está vacía. */
  elegible: boolean
  causas: CausaCandidato[]
  /** No impiden designar; hoy solo `RF-022` (declaró que no está disponible). */
  advertencias: CausaCandidato[]
  /** Designaciones vigentes en partidos no cancelados de la temporada del partido. */
  carga: number
}

/** `POST /partidos/{id}/designaciones`; `motivo` solo al sustituir al central (DEC-202). */
export type DesignarArbitroPeticion = {
  arbitroId: string
  funcion: FuncionArbitral
  motivo?: string
}

/** `POST /designaciones/{id}/retiro` (DEC-203). */
export type RetirarDesignacionPeticion = {
  motivo: string
}

/** Fila de `GET /partidos` (convenciones de API §6.8). */
export type PartidoResumen = {
  id: string
  jornadaId: string
  jornadaNumero: number
  equipoLocal: Referencia
  equipoVisitante: Referencia
  cancha: Referencia
  inicio: string
  finBloque: string
  estado: EstadoPartido
  accionesPermitidas: AccionPartido[]
}

/** Jornada con partidos no cancelados en la semana, agrupada a partir de `GET /partidos`. */
export type JornadaDeSemana = {
  jornadaId: string
  numero: number
  /** Inicio del primer partido de la jornada en la semana. */
  inicio: string
  partidos: number
}

/** Jornadas de la semana; `truncado` avisa que se alcanzó el tope de páginas. */
export type JornadasDeSemana = {
  jornadas: JornadaDeSemana[]
  truncado: boolean
}

/** Elemento de `GET /mis-designaciones` (DEC-205): designación vigente en una jornada publicada. */
export type MiDesignacion = {
  designacionId: string
  funcion: FuncionArbitral
  partido: {
    id: string
    inicio: string
    finBloque: string
    estado: EstadoPartido
    cancha: Referencia
    equipoLocal: Referencia
    equipoVisitante: Referencia
    categoria: Referencia
    grupo: Referencia
    jornada: { id: string; numero: number }
  }
  cedula: { id: string; estado: string } | null
}

export type FiltrosMisDesignaciones = {
  /** ISO con desplazamiento; sin él se listan también las anteriores. */
  desde?: string
  pagina: number
}
