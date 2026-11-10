import type { FiltrosCobros } from '@/features/tesoreria/tipos'

export const TAMANO_COBROS = 20

export const FILTROS_COBROS_INICIALES: FiltrosCobros = {
  pagina: 0,
  tamano: TAMANO_COBROS,
}

/** ¿La consulta tiene algún filtro además de la página? */
export function hayFiltrosCobros(filtros: FiltrosCobros): boolean {
  return Boolean(
    filtros.clubId ||
      filtros.equipoId ||
      filtros.tipo ||
      filtros.estado ||
      filtros.desde ||
      filtros.hasta,
  )
}
