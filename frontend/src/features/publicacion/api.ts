import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type {
  ActualizarComunicadoPeticion,
  Comunicado,
  CrearComunicadoPeticion,
  EstadoPublicacion,
  ResolverComunicadoPeticion,
} from '@/features/publicacion/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

export const clavesComunicados = {
  todas: ['comunicados'] as const,
  lista: (pagina: number, tamano: number, estado?: EstadoPublicacion) =>
    ['comunicados', 'lista', { pagina, tamano, estado }] as const,
  detalle: (id: string) => ['comunicados', 'detalle', id] as const,
}

function consultaDePagina(
  pagina: number,
  tamano: number,
  estado?: EstadoPublicacion,
): string {
  const parametros = new URLSearchParams({
    pagina: String(pagina),
    tamano: String(tamano),
  })

  if (estado) {
    parametros.set('estado', estado)
  }

  return `comunicados?${parametros.toString()}`
}

export function useComunicados(
  pagina: number,
  tamano: number,
  estado?: EstadoPublicacion,
) {
  return useQuery({
    queryKey: clavesComunicados.lista(pagina, tamano, estado),
    queryFn: () => api.get<Pagina<Comunicado>>(consultaDePagina(pagina, tamano, estado)),
  })
}

export function useComunicado(id?: string) {
  return useQuery({
    queryKey: clavesComunicados.detalle(id ?? ''),
    queryFn: () => api.get<Comunicado>(`comunicados/${id}`),
    enabled: Boolean(id),
  })
}

export function useCrearComunicado() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CrearComunicadoPeticion) =>
      api.post<Comunicado>('comunicados', peticion),
    onSuccess: (comunicado) => {
      clienteConsultas.setQueryData(clavesComunicados.detalle(comunicado.id), comunicado)
      void clienteConsultas.invalidateQueries({ queryKey: ['comunicados', 'lista'] })
    },
  })
}

export function useActualizarComunicado(id: string) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: ActualizarComunicadoPeticion) =>
      api.patch<Comunicado>(`comunicados/${id}`, peticion),
    onSuccess: (comunicado) => {
      clienteConsultas.setQueryData(clavesComunicados.detalle(id), comunicado)
      void clienteConsultas.invalidateQueries({ queryKey: ['comunicados', 'lista'] })
    },
  })
}

export function useEnviarARevision() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => api.post<Comunicado>(`comunicados/${id}/envio`),
    onSuccess: (comunicado) => {
      clienteConsultas.setQueryData(clavesComunicados.detalle(comunicado.id), comunicado)
      void clienteConsultas.invalidateQueries({ queryKey: ['comunicados', 'lista'] })
    },
  })
}

export function useResolverComunicado() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: ({ id, ...peticion }: ResolverComunicadoPeticion & { id: string }) =>
      api.post<Comunicado>(`comunicados/${id}/resolucion`, peticion),
    onSuccess: (comunicado) => {
      clienteConsultas.setQueryData(clavesComunicados.detalle(comunicado.id), comunicado)
      void clienteConsultas.invalidateQueries({ queryKey: ['comunicados', 'lista'] })
    },
  })
}
