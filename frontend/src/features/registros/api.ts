import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { clavesOrganizacion } from '@/features/organizacion/api'
import type {
  AvisoPrivacidad,
  Consentimiento,
  CrearRegistroPeticion,
  RegistrarConsentimientoPeticion,
  RegistrarTutelaPeticion,
  RegistroJugador,
  Tutela,
} from '@/features/registros/tipos'
import { api } from '@/lib/api/cliente'
import { reintentarConsulta } from '@/lib/api/errores'

export const clavesRegistros = {
  registros: ['registros', 'registros'] as const,
  avisoVigente: ['registros', 'aviso-vigente'] as const,
}

/**
 * Aviso de privacidad vigente con el tipo por defecto (`SIMPLIFICADO`, DEC-80, DEC-106). Un `404`
 * (sin aviso vigente) no se reintenta: el asistente se bloquea y pide contactar a la liga.
 */
export function useAvisoVigente({ habilitada = true }: { habilitada?: boolean } = {}) {
  return useQuery({
    queryKey: clavesRegistros.avisoVigente,
    queryFn: () => api.get<AvisoPrivacidad>('avisos-privacidad/vigente'),
    retry: reintentarConsulta,
    enabled: habilitada,
  })
}

/** Vincula a un menor con su tutor adulto (RF-106, DEC-82); la cuenta del tutor no se exige. */
export function useRegistrarTutela() {
  return useMutation({
    mutationFn: ({ menorId, ...peticion }: RegistrarTutelaPeticion & { menorId: string }) =>
      api.post<Tutela>(`personas/${menorId}/tutelas`, peticion),
  })
}

/** Consentimiento del titular adulto o del tutor de un menor (RF-097, DEC-76, DEC-81). */
export function useRegistrarConsentimiento() {
  return useMutation({
    mutationFn: ({
      personaId,
      ...peticion
    }: RegistrarConsentimientoPeticion & { personaId: string }) =>
      api.post<Consentimiento>(`personas/${personaId}/consentimientos`, peticion),
  })
}

/**
 * Alta del registro en `PENDIENTE_VALIDACION` (RF-013, RF-107, DEC-85 a DEC-90). Un registro nuevo
 * cambia los listados de registros y la plantilla del equipo.
 */
export function useRegistrarAltaJugador() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CrearRegistroPeticion) =>
      api.post<RegistroJugador>('registros-jugador', peticion),
    onSuccess: () =>
      Promise.all([
        clienteConsultas.invalidateQueries({ queryKey: clavesRegistros.registros }),
        clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.equipos }),
      ]),
  })
}
