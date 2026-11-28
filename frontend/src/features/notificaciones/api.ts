import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Aviso, LecturaMasiva, PestanaAvisos } from '@/features/notificaciones/tipos'
import { api } from '@/lib/api/cliente'
import { reintentarConsulta } from '@/lib/api/errores'
import type { Pagina } from '@/lib/api/tipos'

/**
 * [PROPUESTA] Cada cuánto se consulta el contador de no leídas (H-25, criterio 3). TanStack Query
 * no repite la consulta mientras la pestaña está oculta y la repite al volver a ella.
 */
export const INTERVALO_CONSULTA_MS = 60_000

/** Avisos sin leer que muestra el panel de la campana. */
export const TAMANO_PANEL = 5

export const TAMANO_PAGINA = 20

export const clavesAvisos = {
  todas: ['notificaciones'] as const,
  noLeidos: ['notificaciones', 'no-leidos'] as const,
  lista: (pestana: PestanaAvisos, pagina: number) =>
    ['notificaciones', 'lista', pestana, pagina] as const,
}

function consultaDeAvisos(leida: boolean | undefined, pagina: number, tamano: number): string {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamano: String(tamano) })

  if (leida !== undefined) {
    parametros.set('leida', String(leida))
  }

  return `notificaciones?${parametros.toString()}`
}

/**
 * Avisos sin leer más recientes y su total, que es el contador de la campana (UI-030). Se consulta al
 * cargar y cada {@link INTERVALO_CONSULTA_MS} mientras la pestaña está visible. El total sale de
 * `totalElementos`, igual al de la cabecera `X-No-Leidas`.
 */
export function useAvisosNoLeidos() {
  return useQuery({
    queryKey: clavesAvisos.noLeidos,
    queryFn: () => api.get<Pagina<Aviso>>(consultaDeAvisos(false, 0, TAMANO_PANEL)),
    refetchInterval: INTERVALO_CONSULTA_MS,
    refetchIntervalInBackground: false,
    retry: reintentarConsulta,
  })
}

/** Página de avisos de una pestaña de F-09, más recientes primero (RF-142). */
export function useAvisos(pestana: PestanaAvisos, pagina: number) {
  return useQuery({
    queryKey: clavesAvisos.lista(pestana, pagina),
    queryFn: () =>
      api.get<Pagina<Aviso>>(
        consultaDeAvisos(pestana === 'NO_LEIDAS' ? false : undefined, pagina, TAMANO_PAGINA),
      ),
    placeholderData: keepPreviousData,
    refetchInterval: INTERVALO_CONSULTA_MS,
    refetchIntervalInBackground: false,
    retry: reintentarConsulta,
  })
}

/** Marca un aviso como leído; refresca la campana y las listas. */
export function useMarcarLeido() {
  const cliente = useQueryClient()

  return useMutation({
    mutationFn: (id: string) =>
      api.post<Aviso>(`notificaciones/${encodeURIComponent(id)}/lectura`),
    onSettled: () => cliente.invalidateQueries({ queryKey: clavesAvisos.todas }),
  })
}

/** Marca como leídos todos los avisos sin leer del usuario. */
export function useMarcarTodos() {
  const cliente = useQueryClient()

  return useMutation({
    mutationFn: () => api.post<LecturaMasiva>('notificaciones/lectura'),
    onSettled: () => cliente.invalidateQueries({ queryKey: clavesAvisos.todas }),
  })
}
