/** DTO de canchas: los mismos nombres del contrato (convenciones de API §6.6). */

export type UbicacionCancha = 'UNIDAD_DEPORTIVA' | 'EXTERNA'
export type PropietarioCancha = 'LIGA' | 'CLUB' | 'EXTERNO'
export type TipoDisponibilidad = 'DISPONIBLE' | 'BLOQUEO' | 'MANTENIMIENTO'
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
  tipo: 'DISPONIBLE' | 'BLOQUEO'
  periodo: Intervalo
  motivo?: string
  confirmarPartidosAfectados?: boolean
}
