/** DTO de la cédula: los mismos nombres del contrato (convenciones de API §6.8, H-16.1 a H-16.3). */

import type { EstadoPartido } from '@/features/programacion/tipos'
import type { Referencia } from '@/features/temporadas/tipos'

export type EstadoCedula = 'EN_CAPTURA' | 'PENDIENTE_VALIDACION' | 'DEVUELTA' | 'APROBADA'

export type TipoEvento = 'GOL' | 'AUTOGOL' | 'TARJETA_AMARILLA' | 'TARJETA_ROJA' | 'SUSTITUCION'

export type JugadorAlineado = {
  /** Fila de `alineaciones`; es el `alineacionId` de los eventos. */
  id: string
  registroJugadorId: string
  /** Persona del registro: así se nombra al jugador como involucrado de una incidencia. */
  personaId: string
  nombre: string
  equipoId: string
  numeroPlayera: number | null
  titular: boolean
  categoriaSuperior: boolean
}

export type EventoCedula = {
  id: string
  tipo: TipoEvento
  minuto: number | null
  equipoId: string
  jugador: Referencia
  entra: Referencia | null
  observaciones: string | null
}

export type TipoIncidencia = { id: number; nombre: string; abreExpediente: boolean }

export type Incidencia = {
  id: string
  tipo: TipoIncidencia
  minuto: number | null
  descripcion: string
  involucrados: { personaId: string | null; equipoId: string | null; nombre: string | null }[]
}

export type Cedula = {
  id: string
  partido: {
    id: string
    equipoLocal: Referencia
    equipoVisitante: Referencia
    inicio: string
    estado: EstadoPartido
  }
  estado: EstadoCedula
  capturista: Referencia | null
  marcador: {
    golesLocal: number | null
    golesVisitante: number | null
    penalesLocal: number | null
    penalesVisitante: number | null
    noPresentado: Referencia | null
    porDefault: boolean
    /** El marcador por default sale de parámetros de demostración: se rotula «No oficial». */
    valorDemo: boolean
  }
  alineaciones: JugadorAlineado[]
  eventos: EventoCedula[]
  incidencias: Incidencia[]
  observaciones: string | null
  enviadaEn: string | null
  plazoVenceEn: string | null
}

/** `GET /cedulas/{id}/jugadores` (DEC-172): candidato a la alineación con su elegibilidad. */
export type JugadorDisponible = {
  registroJugadorId: string
  nombre: string
  /** Equipo del partido con el que jugaría. */
  equipoId: string
  equipoRegistro: Referencia
  categoriaSuperior: boolean
  elegible: boolean
  causa: { codigo: string; mensaje: string } | null
}

export type GuardarAlineacionesPeticion = {
  jugadores: { registroJugadorId: string; equipoId: string; numeroPlayera: number | null; titular: boolean }[]
}

export type GuardarMarcadorPeticion = {
  golesLocal?: number
  golesVisitante?: number
  penalesLocal?: number
  penalesVisitante?: number
  noPresentadoEquipoId?: string
}

export type RegistrarEventoPeticion = {
  tipo: TipoEvento
  minuto?: number
  alineacionId: string
  alineacionEntraId?: string
}

export type RegistrarIncidenciaPeticion = {
  tipoIncidenciaId: number
  minuto?: number
  descripcion: string
  involucrados: ({ personaId: string } | { equipoId: string })[]
}

/** Extensión `faltantes` del `422 VAL-020` (DEC-170). */
export type Faltante = { campo: string; mensaje: string }
