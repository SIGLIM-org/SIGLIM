/** DTO de los catálogos disciplinarios: los mismos nombres del contrato (convenciones de API §6.9). */

export type TipoSancion =
  | 'SUSPENSION_PARTIDOS'
  | 'MULTA'
  | 'PERDIDA_PUNTOS'
  | 'PERDIDA_PARTIDO'
  | 'BAJA'

export type SujetoSancion = 'JUGADOR' | 'EQUIPO' | 'CLUB'

export type EfectoSancion = 'HACIA_ADELANTE' | 'RETROACTIVO'

/** Tipo de incidencia del catálogo (RF-128); el `id` es `bigint` en la base. */
export type TipoIncidencia = {
  id: number
  codigo: string
  nombre: string
  abreExpediente: boolean
  activo: boolean
  esDemo: boolean
}

/** Artículo del reglamento con su sanción (RF-129, RF-060). */
export type Articulo = {
  id: string
  numero: string
  descripcion: string
  tipoSancion: TipoSancion
  sujeto: SujetoSancion
  efecto: EfectoSancion
  aplicacionAutomatica: boolean
  partidosSuspension?: number
  puntos?: number
  montoMulta?: number
  otorgaDefaultRestantes: boolean
  activo: boolean
  esDemo: boolean
}

export type CrearTipoIncidenciaPeticion = {
  codigo: string
  nombre: string
  abreExpediente: boolean
}

/** Un campo ausente conserva su valor; `activo: false` deja de ofrecer el tipo sin borrarlo. */
export type ActualizarTipoIncidenciaPeticion = Partial<CrearTipoIncidenciaPeticion> & {
  activo?: boolean
}

export type CrearArticuloPeticion = {
  numero: string
  descripcion: string
  tipoSancion: TipoSancion
  sujeto: SujetoSancion
  efecto?: EfectoSancion
  aplicacionAutomatica?: boolean
  partidosSuspension?: number
  puntos?: number
  montoMulta?: number
  otorgaDefaultRestantes?: boolean
}

/** Un campo ausente conserva su valor; `activo: false` deja de aplicar el artículo sin borrarlo. */
export type ActualizarArticuloPeticion = Partial<CrearArticuloPeticion> & {
  activo?: boolean
}
