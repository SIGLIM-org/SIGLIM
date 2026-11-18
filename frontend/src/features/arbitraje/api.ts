import {
  keepPreviousData,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type {
  Arbitro,
  CrearArbitroPeticion,
  CuotaArbitros,
  DisponibilidadArbitral,
  EstadoArbitroVisible,
  FiltrosArbitros,
  RegistrarDisponibilidadArbitralPeticion,
  ValidarArbitroPeticion,
} from '@/features/arbitraje/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

/** Filas por página de la bandeja; máximo del API (convenciones §4). */
export const TAMANO_ARBITROS = 20

export const clavesArbitros = {
  todas: ['arbitraje', 'arbitros'] as const,
  lista: (filtros: FiltrosArbitros) => ['arbitraje', 'arbitros', 'lista', filtros] as const,
  conteo: (estado: EstadoArbitroVisible) =>
    ['arbitraje', 'arbitros', 'conteo', estado] as const,
  cuota: (clubId: string) => ['arbitraje', 'arbitros', 'cuota', clubId] as const,
  disponibilidad: (arbitroId: string) =>
    ['arbitraje', 'arbitros', 'disponibilidad', arbitroId] as const,
}

function consultaDeArbitros({ estado, pagina, tamano }: FiltrosArbitros): string {
  const parametros = new URLSearchParams()

  if (estado) {
    parametros.set('estado', estado)
  }
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))

  return parametros.toString()
}

/** Árbitros visibles para la sesión; el backend filtra por club y estado (RF-021, ADR-0007). */
export function useArbitros(filtros: FiltrosArbitros) {
  return useQuery({
    queryKey: clavesArbitros.lista(filtros),
    queryFn: () => api.get<Pagina<Arbitro>>(`arbitros?${consultaDeArbitros(filtros)}`),
    placeholderData: keepPreviousData,
  })
}

/**
 * Conteo de una pestaña. Se pide una sola fila (`tamano=1`) y se usa `totalElementos`, que ya
 * respeta el ámbito del usuario. Comparte la misma clave que el listado cuando coincide el filtro.
 */
export function useConteoArbitros(estado: EstadoArbitroVisible) {
  return useQuery({
    queryKey: clavesArbitros.conteo(estado),
    queryFn: () => api.get<Pagina<Arbitro>>(`arbitros?${consultaDeArbitros({
      estado,
      pagina: 0,
      tamano: 1,
    })}`),
    select: (pagina) => pagina.totalElementos,
  })
}

function useInvalidarArbitros() {
  const clienteConsultas = useQueryClient()

  return () => clienteConsultas.invalidateQueries({ queryKey: clavesArbitros.todas })
}

/** La Directiva presenta al árbitro; nace PROPUESTO y queda auditado (RF-112). */
export function useCrearArbitro() {
  const invalidar = useInvalidarArbitros()

  return useMutation({
    mutationFn: (peticion: CrearArbitroPeticion) =>
      api.post<Arbitro>('arbitros', peticion),
    onSuccess: invalidar,
  })
}

/** La Comisión valida o rechaza con observaciones; el backend exige el estado PROPUESTO (RF-113). */
export function useValidarArbitro(id: string) {
  const invalidar = useInvalidarArbitros()

  return useMutation({
    mutationFn: (peticion: ValidarArbitroPeticion) =>
      api.post<Arbitro>(`arbitros/${id}/validacion`, peticion),
    onSuccess: invalidar,
  })
}

/**
 * Cuota de árbitros de un club en la temporada activa (RF-114, B-08.2). El backend calcula el
 * indicador con el ámbito del club; la Comisión (ámbito de liga) puede consultar el de cualquier
 * club. `habilitada` evita pedirla cuando la vista ya no muestra la cuota.
 */
export function useCuotaArbitros(clubId: string | undefined, habilitada = true) {
  return useQuery({
    queryKey: clavesArbitros.cuota(clubId ?? 'sin-club'),
    queryFn: () => consultarCuota(clubId),
    enabled: habilitada && Boolean(clubId),
  })
}

/**
 * Cuotas de varios clubes a la vez (B-08.2): el filtro «Cumple cuota» de la Comisión necesita el
 * indicador de cada club de la página. Comparte la clave con {@link useCuotaArbitros}, así que la
 * celda y el filtro no duplican peticiones.
 */
export function useCuotasDeClubes(clubIds: readonly string[], habilitada = true) {
  return useQueries({
    queries: clubIds.map((clubId) => ({
      queryKey: clavesArbitros.cuota(clubId),
      queryFn: () => consultarCuota(clubId),
      enabled: habilitada,
    })),
  })
}

function consultarCuota(clubId: string | undefined) {
  if (!clubId) {
    throw new Error('Falta el club de la cuota de árbitros.')
  }

  return api.get<CuotaArbitros>(`clubes/${clubId}/cuota-arbitros`)
}

/**
 * Periodos de disponibilidad del árbitro, ordenados por inicio (B-09.2, RF-022). Se piden todos,
 * sin rango: el backend exige `arbitraje.disponibilidad:EDITAR` sobre la persona del árbitro.
 */
export function useDisponibilidadArbitro(arbitroId: string) {
  return useQuery({
    queryKey: clavesArbitros.disponibilidad(arbitroId),
    queryFn: () =>
      api.get<DisponibilidadArbitral[]>(`arbitros/${arbitroId}/disponibilidad`),
  })
}

function useInvalidarDisponibilidad(arbitroId: string) {
  const clienteConsultas = useQueryClient()

  return () =>
    clienteConsultas.invalidateQueries({
      queryKey: clavesArbitros.disponibilidad(arbitroId),
    })
}

/** Registra un periodo; solo para un árbitro `VALIDADO` (`409 RF-022` si no lo está). */
export function useRegistrarDisponibilidadArbitro(arbitroId: string) {
  const invalidar = useInvalidarDisponibilidad(arbitroId)

  return useMutation({
    mutationFn: (peticion: RegistrarDisponibilidadArbitralPeticion) =>
      api.post<DisponibilidadArbitral>(`arbitros/${arbitroId}/disponibilidad`, peticion),
    onSuccess: invalidar,
  })
}

/** Retira un periodo (`204`); la disponibilidad no es historial deportivo (B-09.1). */
export function useRetirarDisponibilidadArbitro(arbitroId: string) {
  const invalidar = useInvalidarDisponibilidad(arbitroId)

  return useMutation({
    mutationFn: (disponibilidadId: string) =>
      api.delete<void>(`arbitros/${arbitroId}/disponibilidad/${disponibilidadId}`),
    onSuccess: invalidar,
  })
}
