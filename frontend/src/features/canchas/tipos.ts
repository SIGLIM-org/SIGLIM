/** DTO de canchas: los mismos nombres del contrato (convenciones de API §6.6). */

export type UbicacionCancha = 'UNIDAD_DEPORTIVA' | 'EXTERNA'
export type PropietarioCancha = 'LIGA' | 'CLUB' | 'EXTERNO'
export type TipoDisponibilidad = 'DISPONIBLE' | 'BLOQUEO' | 'MANTENIMIENTO'
/** Tipos que se registran desde el modal de bloqueo: los dos impiden programar (RF-030, RF-031). */
export type TipoRestriccion = 'BLOQUEO' | 'MANTENIMIENTO'
export type TipoBloqueOcupacion = TipoDisponibilidad | 'PARTIDO'

/** `hasta` es exclusivo; las fechas son ISO 8601 con desplazamiento. */
export type Intervalo = {
  desde: string
  hasta: string
}

/** Agrupa canchas en una misma ubicación (RF-028); `direccion` se omite si no tiene. */
export type Sede = {
  id: string
  nombre: string
  direccion?: string
}

export type Cancha = {
  id: string
  nombre: string
  ubicacion: UbicacionCancha
  direccion?: string
  propietario: PropietarioCancha
  club?: { id: string; nombre: string }
  /** Se omite si la cancha no pertenece a una sede (RF-028). */
  sede?: { id: string; nombre: string }
  propietarioExterno?: string
  caracteristicas?: string
  activa: boolean
  creadoEn: string
}

export type Disponibilidad = {
  id: string
  tipo: TipoDisponibilidad
  periodo: Intervalo
  motivo?: string
  creadoEn: string
}

export type PartidoEnCancha = {
  id: string
  estado: string
  periodo: Intervalo
  equipoLocal: { id: string; nombre: string }
  equipoVisitante: { id: string; nombre: string }
}

export type RegistroDisponibilidad = {
  disponibilidad: Disponibilidad
  partidosAfectados: PartidoEnCancha[]
}

export type BloqueOcupacion = {
  tipo: TipoBloqueOcupacion
  periodo: Intervalo
  motivo?: string
  partido?: PartidoEnCancha
}

export type Ocupacion = {
  cancha: { id: string; nombre: string }
  activa: boolean
  periodo: Intervalo
  bloques: BloqueOcupacion[]
}

export type FiltrosCanchas = {
  q?: string
  ubicacion?: UbicacionCancha
  activa?: boolean
  sedeId?: string
  pagina: number
  tamano: number
}

export type CrearCanchaPeticion = {
  nombre: string
  ubicacion: UbicacionCancha
  direccion?: string
  propietario: PropietarioCancha
  propietarioExterno?: string
  caracteristicas?: string
  sedeId?: string
}

/** Un campo ausente conserva su valor; `direccion` y `caracteristicas` en blanco se vacían. */
export type ActualizarCanchaPeticion = Partial<CrearCanchaPeticion> & {
  activa?: boolean
  motivo?: string
  confirmarPartidosAfectados?: boolean
  /** Deja la cancha sin sede. Excluyente con `sedeId`: los dos juntos responden 400 (D2). */
  quitarSede?: boolean
}

export type CrearSedePeticion = {
  nombre: string
  direccion?: string
}

/** Un campo ausente conserva su valor; `direccion` en blanco se vacía (convenciones §6.6). */
export type ActualizarSedePeticion = Partial<CrearSedePeticion>

export type RegistrarDisponibilidadPeticion = {
  tipo: 'DISPONIBLE' | TipoRestriccion
  periodo: Intervalo
  motivo?: string
  confirmarPartidosAfectados?: boolean
}

/* ---------------------------------------------------------------------------------------------
 * Horarios de club y comodato por temporada (B-13, RF-119, DEC-33). Contrato: convenciones de
 * API §6.6.1.
 * ------------------------------------------------------------------------------------------- */

/** Día de la semana del horario: 1 es lunes y 7 domingo (convenciones §6.6.1). */
export type DiaSemana = 1 | 2 | 3 | 4 | 5 | 6 | 7

/**
 * Horario semanal de una categoría en la cancha durante una temporada (RF-119). Las horas llegan
 * como `HH:mm:ss` y el intervalo es semiabierto: `horaFin` es exclusiva. En una cancha `CLUB` el
 * club es su propietario y `esComodato` es `false`; en una `LIGA` todo horario es comodato (DEC-33).
 */
export type HorarioClub = {
  id: string
  canchaId: string
  temporadaId: string
  /** Categoría del catálogo (no la `CategoriaTemporada`). */
  categoria: { id: string; nombre: string }
  club: { id: string; nombre: string }
  diaSemana: DiaSemana
  horaInicio: string
  horaFin: string
  esComodato: boolean
}

/**
 * Un horario del cuerpo de `PUT /canchas/{id}/horarios-club` (§6.6.1). `categoriaId` es el id del
 * catálogo; `clubId` solo se manda en una cancha `LIGA`, donde es obligatorio. Nunca lleva `id`
 * ni `esComodato`: el servidor los decide.
 */
export type HorarioClubPeticion = {
  categoriaId: string
  diaSemana: DiaSemana
  /** `HH:mm`. */
  horaInicio: string
  /** `HH:mm`, exclusiva. */
  horaFin: string
  clubId?: string
}

/** `PUT /canchas/{id}/horarios-club`: reemplaza de forma atómica los horarios de UNA temporada; `[]` la vacía (§6.6.1). */
export type ReemplazarHorariosClubPeticion = {
  temporadaId: string
  horarios: HorarioClubPeticion[]
}
