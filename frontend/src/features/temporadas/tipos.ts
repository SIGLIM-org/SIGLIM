/** DTO de temporadas: los mismos nombres del contrato (convenciones de API §6.2). */

export type EstadoTemporada = 'CONFIGURACION' | 'ACTIVA' | 'CERRADA'

/** Punto de la lista de verificación que devuelve el API para una temporada en configuración. */
export type RequisitoActivacion = {
  /** Requisito del ERS: `RF-004`, `RF-005`, `RF-100` o `RN-058`. */
  codigo: string
  descripcion: string
  cumple: boolean
}

export type ActivacionTemporada = {
  puedeActivarse: boolean
  requisitos: RequisitoActivacion[]
}

export type Temporada = {
  id: string
  nombre: string
  fechaInicio: string
  fechaFin?: string
  estado: EstadoTemporada
  creadoEn: string
  categoriasAsignadas: number
  gruposCreados: number
  versionParametros?: number
  /** Solo viene para una temporada en `CONFIGURACION` (H-04.1). */
  activacion?: ActivacionTemporada
}

export type CrearTemporadaPeticion = {
  nombre: string
  fechaInicio: string
  fechaFin?: string
}

/** Un campo ausente conserva su valor (RF-001). */
export type ActualizarTemporadaPeticion = Partial<CrearTemporadaPeticion>

export type CerrarTemporadaPeticion = {
  motivo: string
}
