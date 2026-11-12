import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { clavesOrganizacion } from '@/features/organizacion/api'
import type {
  AvisoPrivacidad,
  Consentimiento,
  CrearRegistroPeticion,
  DocumentoRegistro,
  EnlaceDocumento,
  FiltrosRegistros,
  RegistrarConsentimientoPeticion,
  RegistrarTutelaPeticion,
  RegistroJugador,
  RegistroJugadorDetalle,
  RegistroJugadorFila,
  RequisitosRegistro,
  Tutela,
  ValidarDocumentoPeticion,
  ValidarRegistroPeticion,
} from '@/features/registros/tipos'
import { api } from '@/lib/api/cliente'
import { reintentarConsulta } from '@/lib/api/errores'
import type { Pagina } from '@/lib/api/tipos'

/** Filas por página de la bandeja de validación (DEC-111). */
export const TAMANO_REGISTROS = 20

/**
 * La lista y el detalle cuelgan de `registros`, que el alta ya invalida; los requisitos van aparte
 * para recargarlos solos cuando cambia un documento.
 */
export const clavesRegistros = {
  registros: ['registros', 'registros'] as const,
  lista: (filtros: FiltrosRegistros) => ['registros', 'registros', 'lista', filtros] as const,
  detalle: (id: string) => ['registros', 'registros', 'detalle', id] as const,
  requisitos: (id: string) => ['registros', 'requisitos', id] as const,
  avisoVigente: ['registros', 'aviso-vigente'] as const,
}

function consultaDeRegistros({ estado, clubId, orden, pagina, tamano }: FiltrosRegistros): string {
  const parametros = new URLSearchParams()

  if (estado) {
    parametros.set('estado', estado)
  }
  if (clubId) {
    parametros.set('clubId', clubId)
  }
  parametros.set('orden', orden)
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))

  return parametros.toString()
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

// ── Consulta (H-10.5, DEC-109) ──────────────────────────────────────────────────────────────────

/** Registros visibles para la sesión; el ámbito lo filtra el backend en el SQL (ADR-0007). */
export function useRegistros(filtros: FiltrosRegistros) {
  return useQuery({
    queryKey: clavesRegistros.lista(filtros),
    queryFn: () =>
      api.get<Pagina<RegistroJugadorFila>>(`registros-jugador?${consultaDeRegistros(filtros)}`),
    placeholderData: keepPreviousData,
    retry: reintentarConsulta,
  })
}

/** Un registro fuera de ámbito responde 404 igual que uno inexistente (VAL-024). */
export function useRegistro(id: string | undefined) {
  return useQuery({
    queryKey: clavesRegistros.detalle(id ?? 'sin-seleccion'),
    queryFn: () => api.get<RegistroJugadorDetalle>(`registros-jugador/${id}`),
    enabled: Boolean(id),
    retry: reintentarConsulta,
  })
}

/** Requisitos aplicables con sus documentos vigentes (DEC-92, DEC-96). */
export function useRequisitosRegistro(id: string | undefined) {
  return useQuery({
    queryKey: clavesRegistros.requisitos(id ?? 'sin-seleccion'),
    queryFn: () => api.get<RequisitosRegistro>(`registros-jugador/${id}/requisitos`),
    enabled: Boolean(id),
    retry: reintentarConsulta,
  })
}

/** Recargas que piden los errores de concurrencia y de estado (DEC-114). */
export function useRecargarRegistro() {
  const clienteConsultas = useQueryClient()

  return {
    requisitos: (id: string) =>
      clienteConsultas.invalidateQueries({ queryKey: clavesRegistros.requisitos(id) }),
    /** Detalle, lista y requisitos: el registro cambió de estado. */
    todo: (id: string) =>
      Promise.all([
        clienteConsultas.invalidateQueries({ queryKey: clavesRegistros.registros }),
        clienteConsultas.invalidateQueries({ queryKey: clavesRegistros.requisitos(id) }),
      ]),
  }
}

// ── Documentos (H-10.1, RF-108, RF-109) ────────────────────────────────────────────────────────

/** Carga un documento nuevo en un lugar libre del requisito (`multipart/form-data`). */
export function useCargarDocumento(registroId: string) {
  const recargar = useRecargarRegistro()

  return useMutation({
    mutationFn: ({ archivo, tipoDocumentoId }: { archivo: File; tipoDocumentoId: number }) => {
      const formulario = new FormData()
      formulario.append('archivo', archivo)
      formulario.append('tipoDocumentoId', String(tipoDocumentoId))
      return api.post<DocumentoRegistro>(`registros-jugador/${registroId}/documentos`, formulario)
    },
    onSuccess: () => recargar.requisitos(registroId),
  })
}

/** Reemplaza un documento vigente `PENDIENTE` o `RECHAZADO` (DEC-93). */
export function useReemplazarDocumento() {
  const recargar = useRecargarRegistro()

  return useMutation({
    mutationFn: ({ documentoId, archivo }: { documentoId: string; archivo: File }) => {
      const formulario = new FormData()
      formulario.append('archivo', archivo)
      return api.post<DocumentoRegistro>(`documentos/${documentoId}/reemplazo`, formulario)
    },
    onSuccess: (documento) => recargar.requisitos(documento.registroId),
  })
}

/**
 * Enlace prefirmado de 60 s (DEC-95, DEC-113). Es una mutación con `gcTime: 0` para que la URL
 * viva solo en el estado del visor: nunca entra a la caché de consultas ni a otro almacenamiento
 * (RNF-018). Cada petición se audita como `CONSULTAR_DOCUMENTO`.
 */
export function useEnlaceDocumento() {
  return useMutation({
    mutationFn: (documentoId: string) =>
      api.get<EnlaceDocumento>(`documentos/${documentoId}/enlace`),
    gcTime: 0,
  })
}

/** Aprueba o rechaza un documento; no cambia el estado del registro (DEC-96). */
export function useValidarDocumento() {
  const recargar = useRecargarRegistro()

  return useMutation({
    mutationFn: ({ documentoId, ...peticion }: ValidarDocumentoPeticion & { documentoId: string }) =>
      api.post<DocumentoRegistro>(`documentos/${documentoId}/validacion`, peticion),
    onSuccess: (documento) => recargar.requisitos(documento.registroId),
  })
}

/** Activa o rechaza el registro (DEC-97 a DEC-102); cambia el detalle, la lista y los requisitos. */
export function useValidarRegistro(id: string) {
  const recargar = useRecargarRegistro()

  return useMutation({
    mutationFn: (peticion: ValidarRegistroPeticion) =>
      api.post<RegistroJugador>(`registros-jugador/${id}/validacion`, peticion),
    onSuccess: () => recargar.todo(id),
  })
}
