import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { PartidoDetalle, TransicionPartidoPeticion } from '@/features/partidos/tipos'
import { clavesProgramacion } from '@/features/programacion/api'
import { api } from '@/lib/api/cliente'

export const clavesPartidos = {
  detalle: (id: string) => ['partidos', 'detalle', id] as const,
}

/** `GET /partidos/{id}`: estado, árbitros, cédula, historial y acciones permitidas (UI-010). */
export function usePartido(id: string) {
  return useQuery({
    queryKey: clavesPartidos.detalle(id),
    queryFn: () => api.get<PartidoDetalle>(`partidos/${encodeURIComponent(id)}`),
    enabled: Boolean(id),
  })
}

/**
 * `POST /partidos/{id}/transiciones` (RF-043, RN-021). La respuesta ya es el detalle actualizado,
 * así que se guarda tal cual; la jornada del partido se invalida porque su lista muestra el estado.
 */
export function useTransicionarPartido(id: string) {
  const cliente = useQueryClient()

  return useMutation({
    mutationFn: (peticion: TransicionPartidoPeticion) =>
      api.post<PartidoDetalle>(`partidos/${encodeURIComponent(id)}/transiciones`, peticion),
    onSuccess: (partido) => {
      cliente.setQueryData(clavesPartidos.detalle(id), partido)
      void cliente.invalidateQueries({ queryKey: clavesProgramacion.jornada(partido.jornada.id) })
    },
  })
}
