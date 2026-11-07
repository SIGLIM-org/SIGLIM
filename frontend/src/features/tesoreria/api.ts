import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  Adeudos,
  ClubResumen,
  Cobro,
  CrearCobroPeticion,
  EquipoResumen,
  FiltrosCobros,
  Pago,
  RegistrarPagoPeticion,
} from '@/features/tesoreria/tipos'
import { rangoDeCobros } from '@/features/tesoreria/fechas'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

export const clavesTesoreria = {
  todas: ['tesoreria'] as const,
  cobros: (filtros: FiltrosCobros) => ['tesoreria', 'cobros', filtros] as const,
  adeudos: (clubId?: string) => ['tesoreria', 'adeudos', clubId ?? 'todos'] as const,
  clubes: ['tesoreria', 'clubes'] as const,
  equipos: (clubId: string) => ['tesoreria', 'equipos', clubId] as const,
}

/** Convierte los filtros en la cadena de consulta de `GET /cobros` (convenciones §6.13). */
export function consultaDeCobros(filtros: FiltrosCobros): string {
  const parametros = new URLSearchParams({
    pagina: String(filtros.pagina),
    tamano: String(filtros.tamano),
  })

  if (filtros.clubId) {
    parametros.set('clubId', filtros.clubId)
  }
  if (filtros.equipoId) {
    parametros.set('equipoId', filtros.equipoId)
  }
  if (filtros.tipo) {
    parametros.set('tipo', filtros.tipo)
  }
  if (filtros.estado) {
    parametros.set('estado', filtros.estado)
  }

  const rango = rangoDeCobros(filtros.desde, filtros.hasta)

  if (rango.desde) {
    parametros.set('desde', rango.desde)
  }
  if (rango.hasta) {
    parametros.set('hasta', rango.hasta)
  }
  if (filtros.orden) {
    parametros.set('orden', filtros.orden)
  }

  return `cobros?${parametros.toString()}`
}

/** Listado paginado de cobros visibles para la sesión (RF-137, RF-140). */
export function useCobros(filtros: FiltrosCobros) {
  return useQuery({
    queryKey: clavesTesoreria.cobros(filtros),
    queryFn: () => api.get<Pagina<Cobro>>(consultaDeCobros(filtros)),
    placeholderData: keepPreviousData,
  })
}

/** Suma de adeudos por club y equipo; el ámbito lo aplica el backend (RF-140). */
export function useAdeudos(clubId?: string) {
  return useQuery({
    queryKey: clavesTesoreria.adeudos(clubId),
    queryFn: () =>
      api.get<Adeudos>(clubId ? `adeudos?clubId=${encodeURIComponent(clubId)}` : 'adeudos'),
  })
}

/** Recorre las páginas de un catálogo paginado sin repetir el tope de 100 de la API. */
async function cargarTodas<T>(ruta: (pagina: number) => string): Promise<T[]> {
  const elementos: T[] = []
  let pagina = 0
  let totalPaginas = 1

  do {
    const datos = await api.get<Pagina<T>>(ruta(pagina))
    elementos.push(...datos.contenido)
    totalPaginas = datos.totalPaginas
    pagina += 1
  } while (pagina < totalPaginas)

  return elementos
}

/** Clubes para el filtro y el alta de un cobro. */
export function useClubesTesoreria() {
  return useQuery({
    queryKey: clavesTesoreria.clubes,
    queryFn: () =>
      cargarTodas<ClubResumen>((pagina) => `clubes?pagina=${pagina}&tamano=100`),
    staleTime: 5 * 60 * 1000,
  })
}

/**
 * Equipos de un club para el alta de un cobro. Solo se consulta si la sesión puede verlos:
 * el Tesorero no tiene `organizacion.equipo:CONSULTAR` en la matriz inicial.
 */
export function useEquiposDeClub(clubId: string, habilitado: boolean) {
  return useQuery({
    queryKey: clavesTesoreria.equipos(clubId),
    queryFn: () =>
      cargarTodas<EquipoResumen>(
        (pagina) => `equipos?clubId=${encodeURIComponent(clubId)}&pagina=${pagina}&tamano=100`,
      ),
    enabled: habilitado && clubId.length > 0,
    staleTime: 5 * 60 * 1000,
  })
}

/** Crea un cobro manual `PENDIENTE` e invalida cobros y adeudos (RF-137). */
export function useCrearCobro() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CrearCobroPeticion) => api.post<Cobro>('cobros', peticion),
    onSuccess: () => {
      void clienteConsultas.invalidateQueries({ queryKey: clavesTesoreria.todas })
    },
  })
}

/**
 * Registra un pago externo con su comprobante en `multipart/form-data` (RF-139, VAL-028,
 * convenciones §6.13). El monto viaja con dos decimales; `fechaPago` y `referencia` solo si se
 * capturaron. Al terminar invalida cobros y adeudos, porque el cobro pasa a `PAGADO`.
 */
export function useRegistrarPago() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: ({ cobroId, medio, monto, fechaPago, referencia, archivo }: RegistrarPagoPeticion) => {
      const formulario = new FormData()
      formulario.append('medio', medio)
      formulario.append('monto', monto.toFixed(2))
      if (fechaPago) {
        formulario.append('fechaPago', fechaPago)
      }
      if (referencia) {
        formulario.append('referencia', referencia)
      }
      formulario.append('archivo', archivo)

      return api.post<Pago>(`cobros/${encodeURIComponent(cobroId)}/pagos`, formulario)
    },
    onSuccess: () => {
      void clienteConsultas.invalidateQueries({ queryKey: clavesTesoreria.todas })
    },
  })
}
