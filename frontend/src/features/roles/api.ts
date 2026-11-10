import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ActualizarPermisosPeticion,
  CatalogoPermisos,
  ResultadoGuardado,
  ResolverSolicitudPeticion,
  RolCatalogo,
  RolDetalle,
  SolicitudCambioPermisos,
} from '@/features/roles/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

const TAMANO_SOLICITUDES = 20

export const clavesRoles = {
  todas: ['roles'] as const,
  lista: ['roles', 'lista'] as const,
  detalle: (id: string) => ['roles', 'detalle', id] as const,
  catalogo: ['permisos'] as const,
}

export const clavesSolicitudes = {
  todas: ['solicitudes-cambio-permisos'] as const,
  lista: (pagina: number) => ['solicitudes-cambio-permisos', pagina] as const,
}

/** Catálogo de roles para el selector de la matriz. */
export function useRoles() {
  return useQuery({
    queryKey: clavesRoles.lista,
    queryFn: () => api.get<RolCatalogo[]>('roles'),
    staleTime: 5 * 60 * 1000,
  })
}

/** Detalle del rol seleccionado; la matriz se arma con sus permisos efectivos. */
export function useRol(id: string | null) {
  return useQuery({
    queryKey: clavesRoles.detalle(id ?? ''),
    queryFn: () => api.get<RolDetalle>(`roles/${id}`),
    enabled: id !== null,
  })
}

/** Catálogo completo y pares incompatibles, que cambian poco. */
export function useCatalogoPermisos() {
  return useQuery({
    queryKey: clavesRoles.catalogo,
    queryFn: () => api.get<CatalogoPermisos>('permisos'),
    staleTime: 5 * 60 * 1000,
  })
}

/**
 * Solicitudes pendientes, de la más reciente a la más antigua. Solo se consultan si la sesión
 * puede dictaminarlas; el backend vuelve a exigir el permiso (VAL-015).
 */
export function useSolicitudes(pagina: number, habilitada: boolean) {
  return useQuery({
    queryKey: clavesSolicitudes.lista(pagina),
    queryFn: () =>
      api.get<Pagina<SolicitudCambioPermisos>>(
        `solicitudes-cambio-permisos?estado=SOLICITADO&pagina=${pagina}&tamano=${TAMANO_SOLICITUDES}`,
      ),
    enabled: habilitada,
  })
}

/**
 * `PATCH /roles/{id}/permisos`: el backend responde `200` con el rol aplicado o `202` con la
 * solicitud. Se normaliza aquí para que la pantalla no dependa del código HTTP.
 */
export function useGuardarPermisos(rolId: string) {
  const cliente = useQueryClient()

  return useMutation({
    mutationFn: async (
      peticion: ActualizarPermisosPeticion,
    ): Promise<ResultadoGuardado> => {
      const respuesta = await api.patch<RolDetalle | SolicitudCambioPermisos>(
        `roles/${rolId}/permisos`,
        peticion,
      )

      return 'estado' in respuesta
        ? { tipo: 'pendiente', solicitud: respuesta }
        : { tipo: 'aplicado', rol: respuesta }
    },
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: clavesRoles.todas })
      void cliente.invalidateQueries({ queryKey: clavesSolicitudes.todas })
    },
  })
}

/** Dictamen del Presidente: aplica o descarta la solicitud, sin reabrirla. */
export function useResolverSolicitud() {
  const cliente = useQueryClient()

  return useMutation({
    mutationFn: ({
      id,
      ...peticion
    }: ResolverSolicitudPeticion & { id: string }) =>
      api.post<SolicitudCambioPermisos>(
        `solicitudes-cambio-permisos/${id}/resolucion`,
        peticion,
      ),
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: clavesSolicitudes.todas })
      void cliente.invalidateQueries({ queryKey: clavesRoles.todas })
    },
  })
}
