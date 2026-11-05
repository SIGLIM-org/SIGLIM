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

/** Referencia de la convocatoria de una temporada (RF-002, RN-002). */
export type Convocatoria = {
  id: string
  temporadaId: string
  referencia: string
  version?: string
  documentoUrl?: string
  /** Fecha de la primera asociación; el reemplazo no la actualiza. */
  creadoEn: string
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
  /** Solo viene cuando la temporada ya tiene convocatoria asociada (B-02.1). */
  convocatoria?: Convocatoria
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

/** `PUT /temporadas/{id}/convocatoria`: reemplazo completo (B-02.1). */
export type ReemplazarConvocatoriaPeticion = {
  referencia: string
  version?: string
  documentoUrl?: string
}
