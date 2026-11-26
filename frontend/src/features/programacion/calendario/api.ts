import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { clavesProgramacion } from '@/features/programacion/api'
import type {
  Calendario,
  CalendarioGenerado,
  GenerarCalendarioPeticion,
} from '@/features/programacion/calendario/tipos'
import { ApiError } from '@/lib/api/ApiError'
import { api } from '@/lib/api/cliente'

export const clavesCalendario = {
  calendario: (temporadaId: string) =>
    [...clavesProgramacion.todas, 'calendario', temporadaId] as const,
}

/**
 * `GET /temporadas/{id}/calendario` (H-32.2, RF-034): todos los grupos, con `generado = false` los
 * que aún no tienen calendario. Exige `programacion.jornada:CONSULTAR`.
 */
export function useCalendario(
  temporadaId: string,
  { habilitada = true }: { habilitada?: boolean } = {},
) {
  return useQuery({
    queryKey: clavesCalendario.calendario(temporadaId),
    queryFn: () => api.get<Calendario>(`temporadas/${temporadaId}/calendario`),
    enabled: habilitada && Boolean(temporadaId),
  })
}

/**
 * `POST /temporadas/{id}/calendario` (H-32.4, RF-034, DEC-189): genera o regenera el calendario de
 * los grupos. Exige `programacion.jornada:EDITAR`. Al terminar se refresca todo lo de programación:
 * el calendario, las fechas y las jornadas.
 */
export function useGenerarCalendario(temporadaId: string) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: GenerarCalendarioPeticion) =>
      api.post<CalendarioGenerado>(`temporadas/${temporadaId}/calendario`, peticion),
    onSuccess: () =>
      clienteConsultas.invalidateQueries({ queryKey: clavesProgramacion.todas }),
  })
}

export type GrupoEnConflicto = { grupoId: string; nombre: string }

function esGrupoEnConflicto(valor: unknown): valor is GrupoEnConflicto {
  const grupo = valor as Partial<GrupoEnConflicto> | null

  return typeof grupo?.grupoId === 'string' && typeof grupo.nombre === 'string'
}

/**
 * Grupos de la extensión `grupos` de un `409 RN-020`: ya tienen partidos o jornadas de fecha y su
 * calendario no se puede regenerar (DEC-189). `[]` si el error no es ese.
 */
export function gruposDeConflicto(error: unknown): GrupoEnConflicto[] {
  if (
    !(error instanceof ApiError) ||
    error.problema.status !== 409 ||
    error.problema.codigo !== 'RN-020'
  ) {
    return []
  }
  const grupos = error.problema.extensiones?.grupos

  return Array.isArray(grupos) ? grupos.filter(esGrupoEnConflicto) : []
}
