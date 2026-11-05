import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ActualizarUsuarioPeticion,
  AsignacionPeticion,
  ClubResumen,
  CrearUsuarioPeticion,
  FiltrosUsuarios,
  PaginaClubes,
  RevocarAsignacionPeticion,
  RolCatalogo,
  Usuario,
} from '@/features/usuarios/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

export const clavesUsuarios = {
  todas: ['usuarios'] as const,
  lista: (filtros: FiltrosUsuarios) => ['usuarios', 'lista', filtros] as const,
  detalle: (id: string) => ['usuarios', 'detalle', id] as const,
  roles: ['catalogos', 'roles'] as const,
  clubes: ['catalogos', 'clubes'] as const,
}

/** Convierte los filtros en la cadena de consulta de `GET /usuarios` (convenciones §4). */
export function consultaDeUsuarios(filtros: FiltrosUsuarios): string {
  const parametros = new URLSearchParams({
    pagina: String(filtros.pagina),
    tamano: String(filtros.tamano),
  })

  if (filtros.q) {
    parametros.set('q', filtros.q)
  }
  if (filtros.rolId) {
    parametros.set('rolId', filtros.rolId)
  }
  if (filtros.ambito) {
    parametros.set('ambito', filtros.ambito)
  }
  if (filtros.clubId) {
    parametros.set('clubId', filtros.clubId)
  }
  if (filtros.activo !== undefined) {
    parametros.set('activo', String(filtros.activo))
  }

  return `usuarios?${parametros.toString()}`
}

export function useUsuarios(filtros: FiltrosUsuarios) {
  return useQuery({
    queryKey: clavesUsuarios.lista(filtros),
    queryFn: () => api.get<Pagina<Usuario>>(consultaDeUsuarios(filtros)),
    placeholderData: keepPreviousData,
  })
}

/** Detalle de una cuenta con todas sus asignaciones, incluidas las revocadas. */
export function useUsuario(id: string | null) {
  return useQuery({
    queryKey: clavesUsuarios.detalle(id ?? ''),
    queryFn: () => api.get<Usuario>(`usuarios/${id}`),
    enabled: id !== null,
  })
}

/** Catálogo de roles de la liga; se reutiliza mientras la pantalla está abierta. */
export function useRoles() {
  return useQuery({
    queryKey: clavesUsuarios.roles,
    queryFn: () => api.get<RolCatalogo[]>('roles'),
    staleTime: 5 * 60 * 1000,
  })
}

/**
 * Clubes para el selector de una asignación con ámbito `CLUB`. La Liga puede tener más de 100,
 * que es el máximo de una página (convenciones §4), así que se recorren las páginas.
 */
async function cargarClubes(): Promise<ClubResumen[]> {
  const clubes: ClubResumen[] = []
  let pagina = 0
  let totalPaginas = 1

  do {
    const datos = await api.get<PaginaClubes>(`clubes?pagina=${pagina}&tamano=100`)
    clubes.push(...datos.contenido)
    totalPaginas = datos.totalPaginas
    pagina += 1
  } while (pagina < totalPaginas)

  return clubes
}

export function useClubes() {
  return useQuery({
    queryKey: clavesUsuarios.clubes,
    queryFn: cargarClubes,
    staleTime: 5 * 60 * 1000,
  })
}

function useInvalidarUsuarios() {
  const clienteConsultas = useQueryClient()

  return () => {
    void clienteConsultas.invalidateQueries({ queryKey: clavesUsuarios.todas })
  }
}

export function useInvitarUsuario() {
  const invalidar = useInvalidarUsuarios()

  return useMutation({
    mutationFn: (peticion: CrearUsuarioPeticion) => api.post<Usuario>('usuarios', peticion),
    onSuccess: invalidar,
  })
}

export function useActualizarUsuario(id: string) {
  const invalidar = useInvalidarUsuarios()

  return useMutation({
    mutationFn: (peticion: ActualizarUsuarioPeticion) =>
      api.patch<Usuario>(`usuarios/${id}`, peticion),
    onSuccess: invalidar,
  })
}

export function useAsignarRol(usuarioId: string) {
  const invalidar = useInvalidarUsuarios()

  return useMutation({
    mutationFn: (peticion: AsignacionPeticion) =>
      api.post<Usuario['asignaciones'][number]>(`usuarios/${usuarioId}/asignaciones`, peticion),
    onSuccess: invalidar,
  })
}

export function useRevocarAsignacion() {
  const invalidar = useInvalidarUsuarios()

  return useMutation({
    mutationFn: ({ id, ...peticion }: RevocarAsignacionPeticion & { id: string }) =>
      api.post<Usuario['asignaciones'][number]>(`asignaciones-rol/${id}/revocacion`, {
        motivo: peticion.motivo,
      }),
    onSuccess: invalidar,
  })
}
