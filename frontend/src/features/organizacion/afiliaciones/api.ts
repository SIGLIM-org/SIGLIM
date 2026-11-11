import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ActualizarAfiliacionPeticion,
  Afiliacion,
  CrearAfiliacionPeticion,
  FiltrosAfiliaciones,
  ResolverAfiliacionPeticion,
} from '@/features/organizacion/afiliaciones/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

/** Filas por página de la bandeja; máximo del API (convenciones §4). */
export const TAMANO_AFILIACIONES = 20

export const clavesAfiliaciones = {
  todas: ['organizacion', 'afiliaciones'] as const,
  lista: (filtros: FiltrosAfiliaciones) =>
    ['organizacion', 'afiliaciones', 'lista', filtros] as const,
  detalle: (id: string) => ['organizacion', 'afiliaciones', 'detalle', id] as const,
}

function consultaDeAfiliaciones({
  temporadaId,
  estado,
  pagina,
  tamano,
}: FiltrosAfiliaciones): string {
  const parametros = new URLSearchParams()

  if (temporadaId) {
    parametros.set('temporadaId', temporadaId)
  }
  if (estado) {
    parametros.set('estado', estado)
  }
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))

  return parametros.toString()
}

/** Tarjetones visibles para la sesión, filtrados por temporada y estado (RF-101, ADR-0007). */
export function useAfiliaciones(filtros: FiltrosAfiliaciones) {
  return useQuery({
    queryKey: clavesAfiliaciones.lista(filtros),
    queryFn: () => api.get<Pagina<Afiliacion>>(`afiliaciones?${consultaDeAfiliaciones(filtros)}`),
    placeholderData: keepPreviousData,
  })
}

/** Un tarjetón fuera de ámbito responde 404 igual que uno inexistente (VAL-024). */
export function useAfiliacion(id: string | undefined) {
  return useQuery({
    queryKey: clavesAfiliaciones.detalle(id ?? 'sin-seleccion'),
    queryFn: () => api.get<Afiliacion>(`afiliaciones/${id}`),
    enabled: Boolean(id),
  })
}

/**
 * Cualquier cambio en un tarjetón invalida la lista y el detalle. El prefijo `todas` cubre ambas
 * consultas y también la bandeja que ya está en pantalla.
 */
function useInvalidarAfiliaciones() {
  const clienteConsultas = useQueryClient()

  return () => clienteConsultas.invalidateQueries({ queryKey: clavesAfiliaciones.todas })
}

export function useCrearAfiliacion() {
  const invalidar = useInvalidarAfiliaciones()

  return useMutation({
    mutationFn: (peticion: CrearAfiliacionPeticion) =>
      api.post<Afiliacion>('afiliaciones', peticion),
    onSuccess: invalidar,
  })
}

/** Solo se edita mientras está SOLICITADA; el backend responde 409 RF-101 si ya se resolvió. */
export function useActualizarAfiliacion(id: string) {
  const invalidar = useInvalidarAfiliaciones()

  return useMutation({
    mutationFn: (peticion: ActualizarAfiliacionPeticion) =>
      api.patch<Afiliacion>(`afiliaciones/${id}`, peticion),
    onSuccess: invalidar,
  })
}

/** Dictamen del Presidente: aprobar o rechazar con observaciones (RF-101). */
export function useResolverAfiliacion(id: string) {
  const invalidar = useInvalidarAfiliaciones()

  return useMutation({
    mutationFn: (peticion: ResolverAfiliacionPeticion) =>
      api.post<Afiliacion>(`afiliaciones/${id}/resolucion`, peticion),
    onSuccess: invalidar,
  })
}
