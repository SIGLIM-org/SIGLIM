import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ActualizarPartidoPeticion,
  CrearJornadaPeticion,
  CrearPartidoPeticion,
  Jornada,
  Partido,
  ResolverJornadaPeticion,
  VerificacionJornada,
} from '@/features/programacion/tipos'
import { clavesPublicaciones } from '@/features/publicacion/api'
import type { Temporada } from '@/features/temporadas/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

export const clavesProgramacion = {
  todas: ['programacion'] as const,
  temporadaActiva: ['programacion', 'temporada-activa'] as const,
  jornadas: (grupoId: string) => ['programacion', 'jornadas', grupoId] as const,
  jornada: (id: string) => ['programacion', 'jornada', id] as const,
  verificacion: (jornadaId: string) =>
    ['programacion', 'verificacion', jornadaId] as const,
}

/**
 * Temporada `ACTIVA` de la liga, que es la que admite programación. [PROPUESTA] El API no tiene un
 * filtro por estado: se pide la primera página de 100 y se busca la activa, como en otras pantallas.
 */
export function useTemporadaActiva(
  { habilitada = true }: { habilitada?: boolean } = {},
) {
  return useQuery({
    queryKey: clavesProgramacion.temporadaActiva,
    queryFn: async () => {
      const pagina = await api.get<Pagina<Temporada>>('temporadas?pagina=0&tamano=100')

      return pagina.contenido.find((temporada) => temporada.estado === 'ACTIVA') ?? null
    },
    enabled: habilitada,
  })
}

/**
 * Jornadas del grupo, por número (RF-033). El listado exige `EDITAR` en el backend, así que la
 * pantalla lo deshabilita para una sesión de solo consulta.
 */
export function useJornadas(
  grupoId?: string,
  { habilitada = true }: { habilitada?: boolean } = {},
) {
  return useQuery({
    queryKey: clavesProgramacion.jornadas(grupoId ?? ''),
    queryFn: () =>
      api.get<Pagina<Jornada>>(
        `jornadas?grupoId=${encodeURIComponent(grupoId ?? '')}&pagina=0&tamano=100`,
      ),
    enabled: Boolean(grupoId) && habilitada,
  })
}

/** Detalle de la jornada con sus partidos ordenados por inicio (RF-033). */
export function useJornada(id?: string) {
  return useQuery({
    queryKey: clavesProgramacion.jornada(id ?? ''),
    queryFn: () => api.get<Jornada>(`jornadas/${id}`),
    enabled: Boolean(id),
  })
}

/**
 * Verificación en línea (H-12.3, RF-039): es un `POST` porque revisa toda la jornada, pero no
 * escribe nada. Se consulta como cualquier lectura y se refresca después de cada ajuste.
 */
export function useVerificacion(jornadaId?: string, { habilitada = true }: { habilitada?: boolean } = {}) {
  return useQuery({
    queryKey: clavesProgramacion.verificacion(jornadaId ?? ''),
    queryFn: () => api.post<VerificacionJornada>(`jornadas/${jornadaId}/verificacion`),
    enabled: Boolean(jornadaId) && habilitada,
  })
}

function useInvalidarProgramacion() {
  const clienteConsultas = useQueryClient()

  return () => clienteConsultas.invalidateQueries({ queryKey: clavesProgramacion.todas })
}

/**
 * Después de enviar o dictaminar, la jornada cambia de estado y entra o sale de la bandeja de
 * pendientes: se refrescan las dos.
 */
function useInvalidarPublicacion() {
  const clienteConsultas = useQueryClient()

  return () =>
    Promise.all([
      clienteConsultas.invalidateQueries({ queryKey: clavesProgramacion.todas }),
      clienteConsultas.invalidateQueries({ queryKey: clavesPublicaciones.todas }),
    ])
}

/**
 * `POST /jornadas/{id}/envio` (H-13.1, RF-121): `BORRADOR` → `REVISION`. Un `409 RF-039` trae los
 * conflictos que aparecieron después de la última verificación; se refresca para mostrarlos.
 */
export function useEnviarJornada() {
  const invalidar = useInvalidarPublicacion()

  return useMutation({
    mutationFn: (jornadaId: string) => api.post<Jornada>(`jornadas/${jornadaId}/envio`),
    onSettled: invalidar,
  })
}

/**
 * `POST /jornadas/{id}/resolucion` (H-13.1, RF-121, RF-090): aprobar y publicar, o devolver a
 * borrador con observaciones. Quien programa no puede dictaminar (`403 RF-121`).
 */
export function useResolverJornada() {
  const invalidar = useInvalidarPublicacion()

  return useMutation({
    mutationFn: ({ id, ...peticion }: ResolverJornadaPeticion & { id: string }) =>
      api.post<Jornada>(`jornadas/${id}/resolucion`, peticion),
    onSettled: invalidar,
  })
}

/** `POST /jornadas` (RF-033). */
export function useCrearJornada() {
  const invalidar = useInvalidarProgramacion()

  return useMutation({
    mutationFn: (peticion: CrearJornadaPeticion) =>
      api.post<Jornada>('jornadas', peticion),
    onSuccess: invalidar,
  })
}

/** `POST /jornadas/{id}/partidos` (RF-033, RF-036). */
export function useCrearPartido(jornadaId: string) {
  const invalidar = useInvalidarProgramacion()

  return useMutation({
    mutationFn: (peticion: CrearPartidoPeticion) =>
      api.post<Partido>(`jornadas/${jornadaId}/partidos`, peticion),
    onSuccess: invalidar,
  })
}

/** `PATCH /partidos/{id}` con motivo obligatorio cuando hay cambios (RF-040, RN-018). */
export function useActualizarPartido(partidoId: string) {
  const invalidar = useInvalidarProgramacion()

  return useMutation({
    mutationFn: (peticion: ActualizarPartidoPeticion) =>
      api.patch<Partido>(`partidos/${partidoId}`, peticion),
    onSuccess: invalidar,
  })
}
