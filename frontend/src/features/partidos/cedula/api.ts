import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { clavesPartidos } from '@/features/partidos/api'
import type {
  Cedula,
  GuardarAlineacionesPeticion,
  GuardarMarcadorPeticion,
  JugadorDisponible,
  RegistrarEventoPeticion,
  RegistrarIncidenciaPeticion,
  TipoIncidencia,
} from '@/features/partidos/cedula/tipos'
import { api } from '@/lib/api/cliente'

export const clavesCedula = {
  dePartido: (partidoId: string) => ['partidos', 'cedula', partidoId] as const,
  jugadores: (cedulaId: string) => ['partidos', 'cedula', cedulaId, 'jugadores'] as const,
  tiposIncidencia: (cedulaId: string) => ['partidos', 'cedula', cedulaId, 'tipos-incidencia'] as const,
}

/** `GET /partidos/{id}/cedula`; `404` mientras el partido no tiene cédula. */
export function useCedula(partidoId: string) {
  return useQuery({
    queryKey: clavesCedula.dePartido(partidoId),
    queryFn: () => api.get<Cedula>(`partidos/${encodeURIComponent(partidoId)}/cedula`),
    enabled: Boolean(partidoId),
  })
}

/** `GET /cedulas/{id}/jugadores` (DEC-172): candidatos a la alineación con su elegibilidad. */
export function useJugadoresDisponibles(cedulaId?: string) {
  return useQuery({
    queryKey: clavesCedula.jugadores(cedulaId ?? ''),
    queryFn: () => api.get<JugadorDisponible[]>(`cedulas/${encodeURIComponent(cedulaId ?? '')}/jugadores`),
    enabled: Boolean(cedulaId),
  })
}

/** `GET /cedulas/{id}/tipos-incidencia` (DEC-172): tipos activos del catálogo. */
export function useTiposIncidencia(cedulaId?: string) {
  return useQuery({
    queryKey: clavesCedula.tiposIncidencia(cedulaId ?? ''),
    queryFn: () => api.get<TipoIncidencia[]>(`cedulas/${encodeURIComponent(cedulaId ?? '')}/tipos-incidencia`),
    enabled: Boolean(cedulaId),
  })
}

/**
 * Las operaciones de la cédula responden la cédula completa: se guarda tal cual y se invalida el
 * detalle del partido, que muestra el estado de la cédula (F-25).
 */
function useMutacionCedula<P>(partidoId: string, operacion: (peticion: P) => Promise<Cedula>) {
  const cliente = useQueryClient()

  return useMutation({
    mutationFn: operacion,
    onSuccess: (cedula) => {
      cliente.setQueryData(clavesCedula.dePartido(partidoId), cedula)
      void cliente.invalidateQueries({ queryKey: clavesPartidos.detalle(partidoId) })
    },
  })
}

const ruta = (cedulaId: string, sufijo: string) => `cedulas/${encodeURIComponent(cedulaId)}/${sufijo}`

/** `POST /partidos/{id}/cedula` (RF-025): abre la cédula del partido en curso. */
export function useAbrirCedula(partidoId: string) {
  return useMutacionCedula(partidoId, () => api.post<Cedula>(`partidos/${encodeURIComponent(partidoId)}/cedula`))
}

export function useGuardarAlineaciones(partidoId: string, cedulaId: string) {
  return useMutacionCedula(partidoId, (peticion: GuardarAlineacionesPeticion) =>
    api.put<Cedula>(ruta(cedulaId, 'alineaciones'), peticion))
}

export function useGuardarMarcador(partidoId: string, cedulaId: string) {
  return useMutacionCedula(partidoId, (peticion: GuardarMarcadorPeticion) =>
    api.put<Cedula>(ruta(cedulaId, 'marcador'), peticion))
}

export function useRegistrarEvento(partidoId: string, cedulaId: string) {
  return useMutacionCedula(partidoId, (peticion: RegistrarEventoPeticion) =>
    api.post<Cedula>(ruta(cedulaId, 'eventos'), peticion))
}

/** `DELETE /cedulas/{id}/eventos/{eventoId}` responde `204`: se vuelve a pedir la cédula. */
export function useEliminarEvento(partidoId: string, cedulaId: string) {
  const cliente = useQueryClient()

  return useMutation({
    mutationFn: (eventoId: string) => api.delete<void>(ruta(cedulaId, `eventos/${encodeURIComponent(eventoId)}`)),
    onSuccess: () => cliente.invalidateQueries({ queryKey: clavesCedula.dePartido(partidoId) }),
  })
}

export function useRegistrarIncidencia(partidoId: string, cedulaId: string) {
  return useMutacionCedula(partidoId, (peticion: RegistrarIncidenciaPeticion) =>
    api.post<Cedula>(ruta(cedulaId, 'incidencias'), peticion))
}

export function useGuardarObservaciones(partidoId: string, cedulaId: string) {
  return useMutacionCedula(partidoId, (observaciones: string) =>
    api.put<Cedula>(ruta(cedulaId, 'observaciones'), { observaciones }))
}

/** `POST /cedulas/{id}/envio` (RF-122): `422 VAL-020` con `faltantes` si está incompleta. */
export function useEnviarCedula(partidoId: string, cedulaId: string) {
  return useMutacionCedula(partidoId, () => api.post<Cedula>(ruta(cedulaId, 'envio')))
}
