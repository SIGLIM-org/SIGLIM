import {
  keepPreviousData,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import type {
  CandidatoArbitral,
  Designacion,
  DesignacionesJornada,
  DesignarArbitroPeticion,
  FiltrosMisDesignaciones,
  JornadaDeSemana,
  JornadasDeSemana,
  MiDesignacion,
  PartidoResumen,
  RetirarDesignacionPeticion,
} from '@/features/arbitraje/designaciones/tipos'
import { rangoSemana } from '@/features/canchas/fechas'
import { clavesPartidos } from '@/features/partidos/api'
import { clavesProgramacion } from '@/features/programacion/api'
import { ApiError } from '@/lib/api/ApiError'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

/** Partidos por página al recorrer la semana; es el máximo del API (convenciones §4). */
const TAMANO_PARTIDOS_SEMANA = 100

/**
 * [PROPUESTA] Tope de páginas de la semana (500 partidos). Si se alcanza, la lista avisa que puede
 * omitir jornadas en lugar de hacer peticiones sin fin (DEC-209).
 */
export const MAXIMO_PAGINAS_SEMANA = 5

/** Elementos por página de «Mis designaciones». */
export const TAMANO_MIS_DESIGNACIONES = 20

export const clavesDesignaciones = {
  todas: ['arbitraje', 'designaciones'] as const,
  semana: (lunes: string) => ['arbitraje', 'designaciones', 'semana', lunes] as const,
  jornada: (id: string) => ['arbitraje', 'designaciones', 'jornada', id] as const,
  candidatos: (partidoId: string) =>
    ['arbitraje', 'designaciones', 'candidatos', partidoId] as const,
  mias: (filtros: FiltrosMisDesignaciones) =>
    ['arbitraje', 'designaciones', 'mias', filtros] as const,
}

/**
 * Agrupa los partidos de la semana por jornada conservando el inicio del primero (el API los ordena
 * por inicio). Descarta los `CANCELADO`, y con ellos las jornadas que se quedan sin partidos.
 */
export function agruparPorJornada(partidos: readonly PartidoResumen[]): JornadaDeSemana[] {
  const porJornada = new Map<string, JornadaDeSemana>()

  for (const partido of partidos) {
    if (partido.estado === 'CANCELADO') {
      continue
    }

    const existente = porJornada.get(partido.jornadaId)
    if (existente) {
      existente.partidos += 1
      continue
    }

    porJornada.set(partido.jornadaId, {
      jornadaId: partido.jornadaId,
      numero: partido.jornadaNumero,
      inicio: partido.inicio,
      partidos: 1,
    })
  }

  return [...porJornada.values()]
}

async function consultarJornadasDeSemana(lunes: string): Promise<JornadasDeSemana> {
  const { desde, hasta } = rangoSemana(lunes)
  const partidos: PartidoResumen[] = []
  let pagina = 0
  let totalPaginas = 1

  while (pagina < totalPaginas && pagina < MAXIMO_PAGINAS_SEMANA) {
    const parametros = new URLSearchParams({
      desde,
      hasta,
      pagina: String(pagina),
      tamano: String(TAMANO_PARTIDOS_SEMANA),
    })
    const respuesta = await api.get<Pagina<PartidoResumen>>(`partidos?${parametros.toString()}`)

    partidos.push(...respuesta.contenido)
    totalPaginas = respuesta.totalPaginas
    pagina += 1
  }

  return {
    jornadas: agruparPorJornada(partidos),
    truncado: totalPaginas > MAXIMO_PAGINAS_SEMANA,
  }
}

/**
 * Jornadas con partidos en la semana `[lunes, lunes siguiente)` en la zona de la Liga, a partir de
 * `GET /partidos` (H-14.2, DEC-209). El ámbito lo aplica el backend (DEC-161).
 */
export function useJornadasDeSemana(lunes: string, habilitada = true) {
  return useQuery({
    queryKey: clavesDesignaciones.semana(lunes),
    queryFn: () => consultarJornadasDeSemana(lunes),
    enabled: habilitada,
  })
}

function consultarDesignacionesJornada(id: string) {
  return api.get<DesignacionesJornada>(
    `designaciones?${new URLSearchParams({ jornadaId: id }).toString()}`,
  )
}

/** `GET /designaciones?jornadaId=`: partidos con su central, asistentes y el avance (UI-025). */
export function useDesignacionesJornada(id: string | undefined) {
  return useQuery({
    queryKey: clavesDesignaciones.jornada(id ?? ''),
    queryFn: () => consultarDesignacionesJornada(id ?? ''),
    enabled: Boolean(id),
  })
}

/** Resumen de cada jornada de la semana; comparte la clave con {@link useDesignacionesJornada}. */
export function useResumenesJornadas(ids: readonly string[]) {
  return useQueries({
    queries: ids.map((id) => ({
      queryKey: clavesDesignaciones.jornada(id),
      queryFn: () => consultarDesignacionesJornada(id),
    })),
  })
}

/**
 * `GET /partidos/{id}/arbitros-candidatos` (DEC-204). Solo con el diálogo abierto: exige
 * `arbitraje.designacion:EDITAR` y la elegibilidad cambia con cada designación.
 */
export function useCandidatos(partidoId: string, habilitada: boolean) {
  return useQuery({
    queryKey: clavesDesignaciones.candidatos(partidoId),
    queryFn: () =>
      api.get<CandidatoArbitral[]>(
        `partidos/${encodeURIComponent(partidoId)}/arbitros-candidatos`,
      ),
    enabled: habilitada,
  })
}

/** 409 o 422: otra persona cambió algo o la regla lo rechaza; la lista ya no está al día. */
function esConflicto(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.problema.status === 409 || error.problema.status === 422)
  )
}

function useInvalidarTrasDesignar() {
  const cliente = useQueryClient()

  return {
    alTerminar: (partidoId: string) =>
      Promise.all([
        cliente.invalidateQueries({ queryKey: clavesDesignaciones.todas }),
        cliente.invalidateQueries({ queryKey: clavesPartidos.detalle(partidoId) }),
        cliente.invalidateQueries({ queryKey: clavesProgramacion.todas }),
      ]),
    alFallar: (error: unknown) => {
      if (esConflicto(error)) {
        void cliente.invalidateQueries({ queryKey: clavesDesignaciones.todas })
      }
    },
  }
}

/** Designa un central o asistente; con central vigente lo sustituye con motivo (DEC-201, DEC-202). */
export function useDesignar(partidoId: string) {
  const { alTerminar, alFallar } = useInvalidarTrasDesignar()

  return useMutation({
    mutationFn: (peticion: DesignarArbitroPeticion) =>
      api.post<Designacion>(
        `partidos/${encodeURIComponent(partidoId)}/designaciones`,
        peticion,
      ),
    onSuccess: () => alTerminar(partidoId),
    onError: alFallar,
  })
}

type RetiroVariables = RetirarDesignacionPeticion & {
  designacionId: string
  /** Partido de la designación, para refrescar su detalle (F-25). */
  partidoId: string
}

/** Retira la designación con motivo; queda sin vigencia, nada se borra (DEC-203, RN-005). */
export function useRetirarDesignacion() {
  const { alTerminar, alFallar } = useInvalidarTrasDesignar()

  return useMutation({
    mutationFn: ({ designacionId, motivo }: RetiroVariables) =>
      api.post<Designacion>(`designaciones/${encodeURIComponent(designacionId)}/retiro`, {
        motivo,
      }),
    onSuccess: (_designacion, { partidoId }) => alTerminar(partidoId),
    onError: alFallar,
  })
}

/**
 * `GET /mis-designaciones` (DEC-205): designaciones vigentes del árbitro de la sesión en jornadas
 * publicadas, por inicio del partido.
 */
export function useMisDesignaciones(filtros: FiltrosMisDesignaciones) {
  return useQuery({
    queryKey: clavesDesignaciones.mias(filtros),
    queryFn: () => {
      const parametros = new URLSearchParams()
      if (filtros.desde) {
        parametros.set('desde', filtros.desde)
      }
      parametros.set('pagina', String(filtros.pagina))
      parametros.set('tamano', String(TAMANO_MIS_DESIGNACIONES))

      return api.get<Pagina<MiDesignacion>>(`mis-designaciones?${parametros.toString()}`)
    },
    placeholderData: keepPreviousData,
  })
}
