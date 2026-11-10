import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ActualizarCategoriaPeticion,
  ActualizarTemporadaPeticion,
  Categoria,
  CategoriaTemporada,
  CerrarTemporadaPeticion,
  Convocatoria,
  CrearCategoriaPeticion,
  CrearGrupoPeticion,
  CrearInterpretacionPeticion,
  CrearTemporadaPeticion,
  EstadoInterpretacion,
  Grupo,
  InterpretacionRegla,
  ParametrosTemporada,
  ReemplazarConvocatoriaPeticion,
  RegistrarParametrosPeticion,
  ResolverInterpretacionPeticion,
  Temporada,
  VersionParametros,
} from '@/features/temporadas/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

export const clavesTemporadas = {
  todas: ['temporadas'] as const,
  lista: (pagina: number, tamano: number) => ['temporadas', 'lista', pagina, tamano] as const,
}

function consultaDePagina(pagina: number, tamano: number): string {
  const parametros = new URLSearchParams({
    pagina: String(pagina),
    tamano: String(tamano),
  })

  return `temporadas?${parametros.toString()}`
}

export function useTemporadas(pagina: number, tamano: number) {
  return useQuery({
    queryKey: clavesTemporadas.lista(pagina, tamano),
    queryFn: () => api.get<Pagina<Temporada>>(consultaDePagina(pagina, tamano)),
    placeholderData: keepPreviousData,
  })
}

export function useCrearTemporada() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CrearTemporadaPeticion) =>
      api.post<Temporada>('temporadas', peticion),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: clavesTemporadas.todas }),
  })
}

export function useActualizarTemporada(id: string) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: ActualizarTemporadaPeticion) =>
      api.patch<Temporada>(`temporadas/${id}`, peticion),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: clavesTemporadas.todas }),
  })
}

export function useActivarTemporada() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => api.post<Temporada>(`temporadas/${id}/activacion`),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: clavesTemporadas.todas }),
  })
}

export function useCerrarTemporada() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: ({ id, motivo }: CerrarTemporadaPeticion & { id: string }) =>
      api.post<Temporada>(`temporadas/${id}/cierre`, { motivo }),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: clavesTemporadas.todas }),
  })
}

export function useReemplazarConvocatoria(id: string) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: ReemplazarConvocatoriaPeticion) =>
      api.put<Convocatoria>(`temporadas/${id}/convocatoria`, peticion),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: clavesTemporadas.todas }),
  })
}

/* ---------------------------------------------------------------------------------------------
 * Estructura de la temporada (F-11, H-05.3). Contrato: convenciones de API §6.2.
 * ------------------------------------------------------------------------------------------- */

export const clavesEstructura = {
  temporada: (id: string) => ['temporadas', 'detalle', id] as const,
  /** El catálogo es de la liga, no de una temporada: va fuera de `temporadas`. */
  catalogo: ['categorias'] as const,
  categoriasTemporada: (id: string) => ['temporadas', 'categorias', id] as const,
  parametros: (id: string) => ['temporadas', 'parametros', id] as const,
  version: (id: string, version: number) =>
    ['temporadas', 'parametros', id, 'version', version] as const,
}

/** `GET /temporadas/{id}`: la misma forma que una fila de la lista de F-10 (RF-001). */
export function useTemporada(id: string) {
  return useQuery({
    queryKey: clavesEstructura.temporada(id),
    queryFn: () => api.get<Temporada>(`temporadas/${id}`),
  })
}

/** Catálogo completo de categorías, activas e inactivas, por orden (RF-099). */
export function useCatalogoCategorias() {
  return useQuery({
    queryKey: clavesEstructura.catalogo,
    queryFn: () => api.get<Categoria[]>('categorias'),
  })
}

/**
 * Un cambio al catálogo cambia lo que muestran las categorías de la temporada (nombre, orden,
 * superior) y, con ellas, los contadores de F-10: se invalida todo lo de temporadas.
 */
function useInvalidarCatalogo() {
  const clienteConsultas = useQueryClient()

  return () =>
    Promise.all([
      clienteConsultas.invalidateQueries({ queryKey: clavesEstructura.catalogo }),
      clienteConsultas.invalidateQueries({ queryKey: clavesTemporadas.todas }),
    ])
}

export function useCrearCategoria() {
  const invalidar = useInvalidarCatalogo()

  return useMutation({
    mutationFn: (peticion: CrearCategoriaPeticion) =>
      api.post<Categoria>('categorias', peticion),
    onSuccess: invalidar,
  })
}

export function useActualizarCategoria() {
  const invalidar = useInvalidarCatalogo()

  return useMutation({
    mutationFn: ({ id, ...peticion }: ActualizarCategoriaPeticion & { id: string }) =>
      api.patch<Categoria>(`categorias/${id}`, peticion),
    onSuccess: invalidar,
  })
}

/** Lleva TODAS las categorías, activas e inactivas, en el orden nuevo (RF-099). */
export function useReordenarCategorias() {
  const invalidar = useInvalidarCatalogo()

  return useMutation({
    mutationFn: (ids: string[]) =>
      api.post<Categoria[]>('categorias/reordenamiento', { ids }),
    onSuccess: invalidar,
  })
}

/**
 * Categorías de la temporada con sus grupos anidados (RF-004, RF-005). `habilitada: false` evita
 * la petición cuando la pantalla no necesita la lista (B-13.2).
 */
export function useCategoriasTemporada(
  temporadaId: string,
  { habilitada = true }: { habilitada?: boolean } = {},
) {
  return useQuery({
    queryKey: clavesEstructura.categoriasTemporada(temporadaId),
    queryFn: () => api.get<CategoriaTemporada[]>(`temporadas/${temporadaId}/categorias`),
    enabled: habilitada && Boolean(temporadaId),
  })
}

/** Las categorías y los grupos cambian los contadores y la lista de verificación de F-10. */
function useInvalidarTemporadas() {
  const clienteConsultas = useQueryClient()

  return () => clienteConsultas.invalidateQueries({ queryKey: clavesTemporadas.todas })
}

export function useAsignarCategoria(temporadaId: string) {
  const invalidar = useInvalidarTemporadas()

  return useMutation({
    mutationFn: (categoriaId: string) =>
      api.post<CategoriaTemporada>(`temporadas/${temporadaId}/categorias`, { categoriaId }),
    onSuccess: invalidar,
  })
}

export function useCrearGrupo() {
  const invalidar = useInvalidarTemporadas()

  return useMutation({
    mutationFn: (peticion: CrearGrupoPeticion) => api.post<Grupo>('grupos', peticion),
    onSuccess: invalidar,
  })
}

export function useRenombrarGrupo() {
  const invalidar = useInvalidarTemporadas()

  return useMutation({
    mutationFn: ({ id, nombre }: { id: string; nombre: string }) =>
      api.patch<Grupo>(`grupos/${id}`, { nombre }),
    onSuccess: invalidar,
  })
}

/** Vigente, historial y, sin versiones, los predeterminados (RF-006, RF-100). */
export function useParametros(temporadaId: string) {
  return useQuery({
    queryKey: clavesEstructura.parametros(temporadaId),
    queryFn: () => api.get<ParametrosTemporada>(`temporadas/${temporadaId}/parametros`),
  })
}

/** Instantánea de una versión; solo consulta cuando hay versión elegida (RF-006). */
export function useVersionParametros(temporadaId: string, version: number | null) {
  return useQuery({
    queryKey: clavesEstructura.version(temporadaId, version ?? 0),
    queryFn: () =>
      api.get<VersionParametros>(`temporadas/${temporadaId}/parametros/${version}`),
    enabled: version !== null,
  })
}

/** Registra una versión nueva; nunca edita la vigente (RF-006, RN-021). */
export function useRegistrarParametros(temporadaId: string) {
  const invalidar = useInvalidarTemporadas()

  return useMutation({
    mutationFn: (peticion: RegistrarParametrosPeticion) =>
      api.post<VersionParametros>(`temporadas/${temporadaId}/parametros`, peticion),
    onSuccess: invalidar,
  })
}

/* ---------------------------------------------------------------------------------------------
 * Interpretaciones de regla (F-11, B-03.2, RF-007). Contrato: convenciones de API §6.2.
 * ------------------------------------------------------------------------------------------- */

export const clavesInterpretaciones = {
  todas: ['interpretaciones-regla'] as const,
  lista: (
    temporadaId: string,
    pagina: number,
    tamano: number,
    estado?: EstadoInterpretacion,
  ) => ['interpretaciones-regla', 'lista', { temporadaId, pagina, tamano, estado }] as const,
}

function consultaDeInterpretaciones(
  temporadaId: string,
  pagina: number,
  tamano: number,
  estado?: EstadoInterpretacion,
): string {
  const parametros = new URLSearchParams({
    temporadaId,
    pagina: String(pagina),
    tamano: String(tamano),
  })

  if (estado) {
    parametros.set('estado', estado)
  }

  return `interpretaciones-regla?${parametros.toString()}`
}

/**
 * Interpretaciones de la temporada, de la más reciente a la más antigua (RF-007). El `GET` acepta
 * cualquiera de los dos permisos del recurso; el backend decide el ámbito.
 */
export function useInterpretaciones(
  temporadaId: string,
  pagina: number,
  tamano: number,
  estado?: EstadoInterpretacion,
) {
  return useQuery({
    queryKey: clavesInterpretaciones.lista(temporadaId, pagina, tamano, estado),
    queryFn: () =>
      api.get<Pagina<InterpretacionRegla>>(
        consultaDeInterpretaciones(temporadaId, pagina, tamano, estado),
      ),
    placeholderData: keepPreviousData,
  })
}

/** El alta nace PENDIENTE y toma la autoría de la sesión (RF-007). */
export function useRegistrarInterpretacion() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CrearInterpretacionPeticion) =>
      api.post<InterpretacionRegla>('interpretaciones-regla', peticion),
    onSuccess: () =>
      clienteConsultas.invalidateQueries({ queryKey: clavesInterpretaciones.todas }),
  })
}

/** La resolución es definitiva: AUTORIZADA o RECHAZADA, sin reapertura (RF-007). */
export function useResolverInterpretacion() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: ({ id, ...peticion }: ResolverInterpretacionPeticion & { id: string }) =>
      api.post<InterpretacionRegla>(`interpretaciones-regla/${id}/resolucion`, peticion),
    onSuccess: () =>
      clienteConsultas.invalidateQueries({ queryKey: clavesInterpretaciones.todas }),
  })
}
