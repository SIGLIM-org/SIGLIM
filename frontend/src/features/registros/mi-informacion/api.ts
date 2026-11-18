import { useQuery } from '@tanstack/react-query'
import type {
  MiInformacion,
  TitularesMiInformacion,
} from '@/features/registros/mi-informacion/tipos'
import { api } from '@/lib/api/cliente'
import { reintentarConsulta } from '@/lib/api/errores'

/** Claves propias: la consulta personal no se mezcla con las listas administrativas de M-04. */
export const clavesMiInformacion = {
  titulares: ['registros', 'mi-informacion', 'titulares'] as const,
  detalle: (personaId: string) => ['registros', 'mi-informacion', 'detalle', personaId] as const,
}

/**
 * Datos personales con `Cache-Control: no-store`: se descartan de la caché en cuanto la pantalla
 * deja de usarlos (`gcTime: 0`) y nunca se persisten.
 */
const SIN_CACHE = { gcTime: 0 } as const

/**
 * Titulares que la cuenta puede consultar (DEC-139). Lista vacía = cuenta sin persona vinculada.
 *
 * Refs: RF-144, DEC-139, DEC-147
 */
export function useTitularesMiInformacion() {
  return useQuery({
    queryKey: clavesMiInformacion.titulares,
    queryFn: () => api.get<TitularesMiInformacion>('mi-informacion'),
    retry: reintentarConsulta,
    ...SIN_CACHE,
  })
}

/**
 * Detalle de un titular (DEC-141). Solo se pide con un `personaId` de la lista de titulares
 * (DEC-147): la pantalla pasa `undefined` en cualquier otro caso.
 *
 * Refs: RF-144, RF-019, RF-020, DEC-141, DEC-147
 */
export function useMiInformacion(personaId: string | undefined) {
  return useQuery({
    queryKey: clavesMiInformacion.detalle(personaId ?? 'sin-seleccion'),
    queryFn: () => api.get<MiInformacion>(`mi-informacion/${personaId}`),
    enabled: Boolean(personaId),
    retry: reintentarConsulta,
    ...SIN_CACHE,
  })
}
