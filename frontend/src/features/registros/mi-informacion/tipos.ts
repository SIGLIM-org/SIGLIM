/**
 * Contrato de la consulta propia del jugador o tutor (convenciones de API §6.4, DEC-139 a DEC-144).
 * Solo trae lo que el titular puede ver: la CURP llega enmascarada y los documentos nunca traen URL,
 * MIME, tamaño ni nombre del archivo (RNF-018).
 */
import type { EstadoPartido } from '@/features/programacion/tipos'
import type {
  EstadoRegistro,
  EstadoValidacionDocumento,
  TipoDocumento,
} from '@/features/registros/tipos'
import type { EstadoTemporada, Referencia } from '@/features/temporadas/tipos'
import type { TipoSancion } from '@/features/disciplina/tipos'

/** Persona cuya información puede consultar la cuenta: la propia o un menor bajo su tutela vigente. */
export type Titular = {
  personaId: string
  nombre: string
  esPropia: boolean
  esMenor: boolean
}

/** `GET /mi-informacion` (DEC-139): primero la persona de la cuenta y luego sus menores. */
export type TitularesMiInformacion = {
  titulares: Titular[]
}

/** Causa por la que el registro no puede jugar: `RF-019` (estado) o `RF-020` (sanción), DEC-142. */
export type CausaElegibilidad = {
  codigo: string
  mensaje: string
}

export type Elegibilidad = {
  elegible: boolean
  causas: CausaElegibilidad[]
}

/** Documento vigente de un requisito; sin datos del archivo (DEC-141, RNF-018). */
export type DocumentoPropio = {
  id: string
  tipo: TipoDocumento
  estadoValidacion: EstadoValidacionDocumento
  observaciones: string | null
  cargadoEn: string
}

export type RequisitoPropio = {
  tipoDocumento: TipoDocumento
  cantidad: number
  soloMenores: boolean
  /** Valor de demostración: se rotula «No oficial» (DEC-74). */
  esDemo: boolean
  cargados: number
  aprobados: number
  faltan: number
  documentos: DocumentoPropio[]
}

export type DocumentosPropios = {
  requisitos: RequisitoPropio[]
  completo: boolean
  listoParaValidar: boolean
}

export type ResultadoPartidoPropio = {
  golesLocal: number
  golesVisitante: number
  penalesLocal: number | null
  penalesVisitante: number | null
}

/** Partido del equipo del registro, como local o visitante (DEC-144, [PROPUESTA]). */
export type PartidoPropio = {
  id: string
  jornada: number
  inicio: string
  cancha: string
  local: Referencia
  visitante: Referencia
  esLocal: boolean
  estado: EstadoPartido
  resultado: ResultadoPartidoPropio | null
}

export type RegistroPropio = {
  id: string
  temporada: { id: string; nombre: string; estado: EstadoTemporada }
  club: Referencia
  equipo: Referencia
  categoria: Referencia
  estado: EstadoRegistro
  altaEn: string
  bajaEn: string | null
  motivoBaja: string | null
  observaciones: string | null
  elegibilidad: Elegibilidad
  documentos: DocumentosPropios
  proximosPartidos: PartidoPropio[]
  partidosJugados: PartidoPropio[]
}

export type EstadoSancion = 'VIGENTE' | 'CUMPLIDA' | 'REVOCADA'

/** Sanción de la persona (no por registro), DEC-141. */
export type SancionPropia = {
  id: string
  tipo: TipoSancion
  estado: EstadoSancion
  /** Partidos de suspensión; `null` en los tipos que no los llevan. */
  partidos: number | null
  partidosCumplidos: number
  vigenteDesde: string
  vigenteHasta: string | null
  articulo: { numero: string; descripcion: string } | null
  folio: string
}

/** `GET /mi-informacion/{personaId}` (DEC-141). */
export type MiInformacion = {
  persona: {
    id: string
    nombreCompleto: string
    curpEnmascarada: string
    esMenor: boolean
  }
  /** De temporadas no cerradas, del más reciente al más antiguo. */
  registros: RegistroPropio[]
  sanciones: SancionPropia[]
}
