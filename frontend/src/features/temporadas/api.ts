import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ActualizarTemporadaPeticion,
  CerrarTemporadaPeticion,
  Convocatoria,
  CrearTemporadaPeticion,
  ReemplazarConvocatoriaPeticion,
  Temporada,
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
