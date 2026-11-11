import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  Arbitro,
  CrearArbitroPeticion,
  EstadoArbitroVisible,
  FiltrosArbitros,
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
