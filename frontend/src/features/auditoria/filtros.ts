import { esDiaValido, rangoAInstantes } from '@/features/auditoria/fechas'
import type { DireccionOrden, ParametrosAuditoria } from '@/features/auditoria/tipos'

/** Lo que el usuario puede cambiar en el formulario; las fechas son días `AAAA-MM-DD`. */
export type ValoresFiltros = {
  perfilId: string
  modulos: string[]
  acciones: string[]
  desde: string
  hasta: string
}

/** Estado de la pantalla guardado en la URL. */
export type EstadoConsulta = ValoresFiltros & {
  pagina: number
  tamano: number
  orden: DireccionOrden
}

export const TAMANOS_PAGINA = [50, 100] as const
const TAMANO_INICIAL = TAMANOS_PAGINA[0]

export const FILTROS_VACIOS: ValoresFiltros = {
  perfilId: '',
  modulos: [],
  acciones: [],
  desde: '',
  hasta: '',
}

function lista(valor: string | null): string[] {
  return valor ? valor.split(',').filter(Boolean) : []
}

function dia(valor: string | null): string {
  return valor && esDiaValido(valor) ? valor : ''
}

function paginaDe(valor: string | null): number {
  const pagina = Number(valor)

  return Number.isSafeInteger(pagina) && pagina > 0 ? pagina : 0
}

function tamanoDe(valor: string | null): number {
  const tamano = Number(valor)

  return TAMANOS_PAGINA.some((opcion) => opcion === tamano) ? tamano : TAMANO_INICIAL
}

/** Lee la URL; lo que no se reconoce toma su valor inicial. `perfilId` lo valida el backend. */
export function estadoDeUrl(url: URLSearchParams): EstadoConsulta {
  return {
    perfilId: url.get('perfilId') ?? '',
    modulos: lista(url.get('modulo')),
    acciones: lista(url.get('accion')),
    desde: dia(url.get('desde')),
    hasta: dia(url.get('hasta')),
    pagina: paginaDe(url.get('pagina')),
    tamano: tamanoDe(url.get('tamano')),
    orden: url.get('orden') === 'asc' ? 'asc' : 'desc',
  }
}

/** Escribe en la URL solo lo que difiere del valor inicial. */
export function urlDeEstado(estado: EstadoConsulta): URLSearchParams {
  const url = new URLSearchParams()

  if (estado.perfilId) {
    url.set('perfilId', estado.perfilId)
  }
  if (estado.modulos.length > 0) {
    url.set('modulo', estado.modulos.join(','))
  }
  if (estado.acciones.length > 0) {
    url.set('accion', estado.acciones.join(','))
  }
  if (estado.desde) {
    url.set('desde', estado.desde)
  }
  if (estado.hasta) {
    url.set('hasta', estado.hasta)
  }
  if (estado.pagina > 0) {
    url.set('pagina', String(estado.pagina))
  }
  if (estado.tamano !== TAMANO_INICIAL) {
    url.set('tamano', String(estado.tamano))
  }
  if (estado.orden !== 'desc') {
    url.set('orden', estado.orden)
  }

  return url
}

/** Solo los filtros: el orden y la página no entran en el borrador del formulario. */
export function valoresDeEstado({
  perfilId,
  modulos,
  acciones,
  desde,
  hasta,
}: EstadoConsulta): ValoresFiltros {
  return { perfilId, modulos, acciones, desde, hasta }
}

export function hayFiltros({ perfilId, modulos, acciones, desde, hasta }: ValoresFiltros): boolean {
  return Boolean(perfilId || modulos.length || acciones.length || desde || hasta)
}

export function parametrosDeEstado(estado: EstadoConsulta): ParametrosAuditoria {
  return {
    ...(estado.perfilId ? { perfilId: estado.perfilId } : {}),
    ...(estado.modulos.length ? { modulo: estado.modulos } : {}),
    ...(estado.acciones.length ? { accion: estado.acciones } : {}),
    ...rangoAInstantes(estado.desde || undefined, estado.hasta || undefined),
    pagina: estado.pagina,
    tamano: estado.tamano,
    orden: estado.orden,
  }
}
