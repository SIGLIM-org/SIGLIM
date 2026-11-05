import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ActualizarArticuloPeticion,
  ActualizarTipoIncidenciaPeticion,
  Articulo,
  CrearArticuloPeticion,
  CrearTipoIncidenciaPeticion,
  TipoIncidencia,
} from '@/features/disciplina/tipos'
import { api } from '@/lib/api/cliente'

export const clavesDisciplina = {
  tipos: ['disciplina', 'tipos-incidencia'] as const,
  articulos: ['disciplina', 'articulos'] as const,
}

/** Catálogo completo de tipos de incidencia; es pequeño y no se pagina (convenciones §4). */
export function useTiposIncidencia() {
  return useQuery({
    queryKey: clavesDisciplina.tipos,
    queryFn: () => api.get<TipoIncidencia[]>('tipos-incidencia'),
  })
}

/** Catálogo completo de artículos del reglamento (RF-129, RF-060). */
export function useArticulos() {
  return useQuery({
    queryKey: clavesDisciplina.articulos,
    queryFn: () => api.get<Articulo[]>('articulos'),
  })
}

export function useCrearTipoIncidencia() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CrearTipoIncidenciaPeticion) =>
      api.post<TipoIncidencia>('tipos-incidencia', peticion),
    onSuccess: () => void clienteConsultas.invalidateQueries({ queryKey: clavesDisciplina.tipos }),
  })
}

export function useActualizarTipoIncidencia(id: number) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: ActualizarTipoIncidenciaPeticion) =>
      api.patch<TipoIncidencia>(`tipos-incidencia/${id}`, peticion),
    onSuccess: (tipo) => {
      clienteConsultas.setQueryData<TipoIncidencia[]>(clavesDisciplina.tipos, (actuales) =>
        actuales?.map((actual) => (actual.id === tipo.id ? tipo : actual)),
      )
      void clienteConsultas.invalidateQueries({ queryKey: clavesDisciplina.tipos })
    },
  })
}

export function useCrearArticulo() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CrearArticuloPeticion) =>
      api.post<Articulo>('articulos', peticion),
    onSuccess: () => void clienteConsultas.invalidateQueries({ queryKey: clavesDisciplina.articulos }),
  })
}

export function useActualizarArticulo(id: string) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: ActualizarArticuloPeticion) =>
      api.patch<Articulo>(`articulos/${id}`, peticion),
    onSuccess: (articulo) => {
      clienteConsultas.setQueryData<Articulo[]>(clavesDisciplina.articulos, (actuales) =>
        actuales?.map((actual) => (actual.id === articulo.id ? articulo : actual)),
      )
      void clienteConsultas.invalidateQueries({ queryKey: clavesDisciplina.articulos })
    },
  })
}
