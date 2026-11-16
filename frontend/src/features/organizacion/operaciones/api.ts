import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ContextoOperacionesEquipo,
  CrearOperacionEquipoPeticion,
  FiltrosOperacionesEquipo,
  OperacionEquipo,
  ResolverOperacionEquipoPeticion,
} from '@/features/organizacion/operaciones/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

/** Filas por página de la bandeja; máximo del API (convenciones §4). */
export const TAMANO_OPERACIONES = 20

export const clavesOperaciones = {
  todas: ['organizacion', 'operaciones-equipo'] as const,
  contexto: ['organizacion', 'operaciones-equipo', 'contexto'] as const,
  lista: (filtros: FiltrosOperacionesEquipo) =>
    ['organizacion', 'operaciones-equipo', 'lista', filtros] as const,
}

function consultaDeOperaciones({ estado, pagina, tamano }: FiltrosOperacionesEquipo): string {
  const parametros = new URLSearchParams()

  if (estado) {
    parametros.set('estado', estado)
  }
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))

  return parametros.toString()
}

/** Operaciones visibles para la sesión, filtradas por estado (B-05.3, ADR-0007). */
export function useOperacionesEquipo(filtros: FiltrosOperacionesEquipo) {
  return useQuery({
    queryKey: clavesOperaciones.lista(filtros),
    queryFn: () =>
      api.get<Pagina<OperacionEquipo>>(`operaciones-equipo?${consultaDeOperaciones(filtros)}`),
    placeholderData: keepPreviousData,
  })
}

/** Clubes ACTIVO de la liga para elegir el destino de una venta (B-05.3). */
export function useContextoOperaciones() {
  return useQuery({
    queryKey: clavesOperaciones.contexto,
    queryFn: () => api.get<ContextoOperacionesEquipo>('operaciones-equipo/contexto'),
  })
}

/**
 * Cualquier cambio en una operación invalida la bandeja y el contexto. El prefijo `todas` cubre la
 * lista que ya está en pantalla y el detalle que se agregue después.
 */
function useInvalidarOperaciones() {
  const clienteConsultas = useQueryClient()

  return () => clienteConsultas.invalidateQueries({ queryKey: clavesOperaciones.todas })
}

/** Registra una venta o una conservación y la deja SOLICITADA (RF-103, RF-104). */
export function useCrearOperacionEquipo() {
  const invalidar = useInvalidarOperaciones()

  return useMutation({
    mutationFn: (peticion: CrearOperacionEquipoPeticion) =>
      api.post<OperacionEquipo>('operaciones-equipo', peticion),
    onSuccess: invalidar,
  })
}

/** Dictamen del Presidente: autorizar o rechazar con observaciones (RN-040). */
export function useResolverOperacionEquipo(id: string) {
  const invalidar = useInvalidarOperaciones()

  return useMutation({
    mutationFn: (peticion: ResolverOperacionEquipoPeticion) =>
      api.post<OperacionEquipo>(`operaciones-equipo/${id}/resolucion`, peticion),
    onSuccess: invalidar,
  })
}
