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
  PublicacionPendiente,
  ResolverComunicadoPeticion,
} from '@/features/publicacion/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

export const clavesPublicaciones = {
  pendientes: (pagina: number, tamano: number) =>
    ['publicaciones', 'pendientes', { pagina, tamano }] as const,
  todas: ['publicaciones'] as const,
}

/**
 * Bandeja del Presidente (H-13.1): jornadas y comunicados en revisión, del envío más antiguo al
 * más reciente. Cada tipo llega solo si la sesión puede aprobarlo.
 */
export function usePublicacionesPendientes(
  pagina: number,
  tamano: number,
  { habilitada = true }: { habilitada?: boolean } = {},
) {
  return useQuery({
    queryKey: clavesPublicaciones.pendientes(pagina, tamano),
    queryFn: () =>
      api.get<Pagina<PublicacionPendiente>>(
        `publicaciones/pendientes?pagina=${pagina}&tamano=${tamano}`,
      ),
    enabled: habilitada,
  })
}

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
