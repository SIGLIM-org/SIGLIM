import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  CartaOficio,
  ContextoSolicitudesCancha,
  CrearSolicitudCanchaPeticion,
  FiltrosSolicitudesCancha,
  ResolverSolicitudCanchaPeticion,
  SolicitudCancha,
} from '@/features/canchas/tramites/tipos'
import { api } from '@/lib/api/cliente'
import { reintentarConsulta } from '@/lib/api/errores'
import type { Pagina } from '@/lib/api/tipos'

/** Filas por página de la bandeja (convenciones §4). */
export const TAMANO_TRAMITES = 20

export const clavesTramites = {
  todas: ['canchas', 'solicitudes'] as const,
  contexto: ['canchas', 'solicitudes', 'contexto'] as const,
  lista: (filtros: FiltrosSolicitudesCancha) => ['canchas', 'solicitudes', 'lista', filtros] as const,
  oficio: (id: string) => ['canchas', 'solicitudes', 'oficio', id] as const,
}

function consultaDeTramites({ formato, estado, pagina, tamano }: FiltrosSolicitudesCancha): string {
  const parametros = new URLSearchParams()

  if (formato === 1) {
    parametros.append('tipo', 'PRESTAMO')
    parametros.append('tipo', 'COMODATO')
  } else {
    parametros.append('tipo', 'OFERTA')
  }
  if (estado) {
    parametros.set('estado', estado)
  }
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))

  return parametros.toString()
}

/** Trámites del formato visibles para la sesión (B-14, ADR-0007). */
export function useSolicitudesCancha(filtros: FiltrosSolicitudesCancha) {
  return useQuery({
    queryKey: clavesTramites.lista(filtros),
    queryFn: () =>
      api.get<Pagina<SolicitudCancha>>(`solicitudes-cancha?${consultaDeTramites(filtros)}`),
    placeholderData: keepPreviousData,
    retry: reintentarConsulta,
  })
}

/** Clubes y canchas para capturar un trámite, y si la sesión captura ofertas de externos. */
export function useContextoTramites() {
  return useQuery({
    queryKey: clavesTramites.contexto,
    queryFn: () => api.get<ContextoSolicitudesCancha>('solicitudes-cancha/contexto'),
    retry: reintentarConsulta,
  })
}

/** Datos de la carta con número de oficio (RF-118). */
export function useCartaOficio(id: string) {
  return useQuery({
    queryKey: clavesTramites.oficio(id),
    queryFn: () => api.get<CartaOficio>(`solicitudes-cancha/${id}/oficio`),
    retry: reintentarConsulta,
  })
}

function useInvalidarTramites() {
  const clienteConsultas = useQueryClient()

  return () => clienteConsultas.invalidateQueries({ queryKey: clavesTramites.todas })
}

/** Captura un Formato 1 u oferta Formato 2 y le asigna número de oficio (RF-116, RF-117). */
export function useCrearSolicitudCancha() {
  const invalidar = useInvalidarTramites()

  return useMutation({
    mutationFn: (peticion: CrearSolicitudCanchaPeticion) =>
      api.post<SolicitudCancha>('solicitudes-cancha', peticion),
    onSuccess: invalidar,
  })
}

/** Dictamen del Presidente sobre un trámite pendiente. */
export function useResolverSolicitudCancha(id: string) {
  const invalidar = useInvalidarTramites()

  return useMutation({
    mutationFn: (peticion: ResolverSolicitudCanchaPeticion) =>
      api.post<SolicitudCancha>(`solicitudes-cancha/${id}/resolucion`, peticion),
    onSuccess: invalidar,
  })
}
