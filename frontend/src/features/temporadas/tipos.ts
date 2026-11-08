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

/* ---------------------------------------------------------------------------------------------
 * Estructura de la temporada (F-11, H-05): catálogo de categorías, categorías de la temporada,
 * grupos y versiones de parámetros. Contrato de convenciones de API §6.2 (H-05.1 y H-05.2).
 * ------------------------------------------------------------------------------------------- */

/** Tipo de una categoría del catálogo (RF-099). */
export type TipoCategoria = 'INFANTIL' | 'LIBRE' | 'VETERANOS'

/** Referencia anidada `{ id, nombre }` (convenciones §9.1). */
export type Referencia = {
  id: string
  nombre: string
}

/** Categoría del catálogo de la liga, compartido por todas las temporadas (RF-099, DEC-16). */
export type Categoria = {
  id: string
  nombre: string
  tipo: TipoCategoria
  orden: number
  /** Se omite si la categoría es el tope de su rama. */
  categoriaSuperior?: Referencia
  activa: boolean
  creadoEn: string
}

/** `POST /categorias` (RF-099). */
export type CrearCategoriaPeticion = {
  nombre: string
  tipo: TipoCategoria
  categoriaSuperiorId?: string
}

/**
 * `PATCH /categorias/{id}`: lo ausente conserva su valor (RF-099, RF-110). `quitarSuperior` nunca
 * va junto con `categoriaSuperiorId`.
 */
export type ActualizarCategoriaPeticion = {
  nombre?: string
  tipo?: TipoCategoria
  categoriaSuperiorId?: string
  quitarSuperior?: boolean
  activa?: boolean
}

/** Grupo anidado en una categoría de la temporada, con el conteo de equipos (RF-005). */
export type GrupoResumen = {
  id: string
  nombre: string
  equipos: number
}

/**
 * Categoría que participa en la temporada (RF-004). `id` es el `categoriaTemporadaId` que usan
 * los grupos.
 */
export type CategoriaTemporada = {
  id: string
  categoria: Pick<Categoria, 'id' | 'nombre' | 'tipo' | 'orden' | 'categoriaSuperior'>
  grupos: GrupoResumen[]
}

/** `POST /grupos` (RF-005). */
export type CrearGrupoPeticion = {
  categoriaTemporadaId: string
  nombre: string
}

/** Respuesta de `POST /grupos` y `PATCH /grupos/{id}` (RF-005). */
export type Grupo = GrupoResumen & {
  categoriaTemporadaId: string
}

/** Criterio de desempate de la tabla; ninguno usa el nombre del equipo (RF-050). */
export type CriterioDesempate =
  | 'PUNTOS'
  | 'DIFERENCIA_GOLES'
  | 'GOLES_FAVOR'
  | 'MENOS_GOLES_CONTRA'
  | 'PARTIDOS_GANADOS'
  | 'ENFRENTAMIENTO_DIRECTO'
  | 'MENOS_EXPULSIONES'
  | 'SORTEO'

/** Formato de la liguilla (DEC-28). */
export type FormatoLiguilla = 'PARTIDO_UNICO' | 'IDA_Y_VUELTA'

/** Clasificados a la liguilla (DEC-28). */
export type ClasificadosLiguilla = 2 | 4 | 8 | 16 | 32

/** Juego completo de valores de una versión de parámetros (RF-006, RF-100, DEC-24 a DEC-29). */
export type ValoresParametros = {
  puntosVictoria: number
  puntosEmpate: number
  puntosDerrota: number
  criteriosDesempate: CriterioDesempate[]
  golesDefaultGanador: number
  golesDefaultPerdedor: number
  plazoValidacionCedulaHoras: number
  duracionJuegoMin: number
  bloqueCanchaMin: number
  clasificadosLiguilla: ClasificadosLiguilla
  formatoLiguilla: FormatoLiguilla
  empateLiguillaPorPenales: boolean
  equiposAscienden: number
  equiposDescienden: number
  permiteCambioClub: boolean
}

/** Fila propia de una categoría en una versión de parámetros (RF-100, DEC-25). */
export type ParametrosCategoria = {
  categoria: Referencia
  duracionJuegoMin: number
  bloqueCanchaMin: number
}

/** Una versión inmutable de parámetros; la de número mayor es la vigente (RF-006, DP-002). */
export type VersionParametros = ValoresParametros & {
  id: string
  temporadaId: string
  version: number
  categorias: ParametrosCategoria[]
  /** `true` mientras la Liga no confirme los valores como oficiales (PEN-021). */
  esDemo: boolean
  /** Campos que la interfaz rotula «No oficial»; vacío si `esDemo` es `false`. */
  camposDemo: string[]
  motivo: string
  creadoPor?: Referencia
  creadoEn: string
}

/** Elemento del historial de versiones (RF-006). */
export type ResumenVersion = {
  id: string
  version: number
  esDemo: boolean
  motivo: string
  creadoPor?: Referencia
  creadoEn: string
  /** Campos que difieren de la versión anterior, en camelCase; la versión 1 los lista todos. */
  camposCambiados: string[]
}

/** Valores por defecto de la tabla, solo mientras la temporada no tiene versiones (RF-100). */
export type ParametrosPredeterminados = ValoresParametros & {
  esDemo: boolean
}

/** `GET /temporadas/{id}/parametros` (RF-006, RF-100). */
export type ParametrosTemporada = {
  vigente: VersionParametros | null
  historial: ResumenVersion[]
  predeterminados?: ParametrosPredeterminados
}

/** Fila por categoría que se envía al registrar una versión (DEC-25). */
export type ParametrosCategoriaPeticion = {
  categoriaId: string
  duracionJuegoMin: number
  bloqueCanchaMin: number
}

/** `POST /temporadas/{id}/parametros`: el juego completo, nada se hereda (RF-006, RN-021). */
export type RegistrarParametrosPeticion = ValoresParametros & {
  esDemo: boolean
  categorias: ParametrosCategoriaPeticion[]
  motivo: string
}

/* ---------------------------------------------------------------------------------------------
 * Interpretaciones de regla (F-11, B-03.2, RF-007, DEC-57 a DEC-59). Contrato de convenciones de
 * API §6.2.
 * ------------------------------------------------------------------------------------------- */

/** Nace PENDIENTE y solo el Presidente la deja AUTORIZADA o RECHAZADA (RF-007). */
export type EstadoInterpretacion = 'PENDIENTE' | 'AUTORIZADA' | 'RECHAZADA'

/** Filtro de la bandeja; `TODOS` no se manda al API. */
export type FiltroEstadoInterpretacion = 'TODOS' | EstadoInterpretacion

/**
 * Interpretación administrativa de una regla (RF-007, RN-006). No es una regla permanente ni toca
 * los parámetros versionados. El API solo expone `perfiles.id`, no nombres ni correos.
 */
export type InterpretacionRegla = {
  id: string
  temporadaId: string
  descripcion: string
  estado: EstadoInterpretacion
  registradaPor: string
  registradaEn: string
  /** Solo cuando ya está resuelta. */
  resueltaPor?: string
  resueltaEn?: string
  /** Observaciones del registro o de la resolución; el rechazo las exige (DEC-58). */
  observaciones?: string
}

/** `POST /interpretaciones-regla`: el alta siempre nace PENDIENTE (RF-007). */
export type CrearInterpretacionPeticion = {
  temporadaId: string
  /** Hasta 2000 caracteres [PROPUESTA]. */
  descripcion: string
}

/** `POST /interpretaciones-regla/{id}/resolucion`: resolución definitiva (RF-007). */
export type ResolverInterpretacionPeticion = {
  estado: Exclude<EstadoInterpretacion, 'PENDIENTE'>
  /** Obligatorias al rechazar (mínimo 10 caracteres, DEC-58); opcionales al autorizar. */
  observaciones?: string
}
