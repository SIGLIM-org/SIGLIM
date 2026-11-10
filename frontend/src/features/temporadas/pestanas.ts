/**
 * Pestañas de F-11, en orden de prioridad (D0): Parámetros, Categorías, Grupos e Interpretaciones.
 * La última solo se muestra con `temporadas.interpretacion:REGISTRAR` o `:APROBAR` (RF-007).
 */
export type PestanaEstructura = 'parametros' | 'categorias' | 'grupos' | 'interpretaciones'

export const PESTANAS_ESTRUCTURA: readonly PestanaEstructura[] = [
  'parametros',
  'categorias',
  'grupos',
  'interpretaciones',
]

/** Pestaña de `?pestana=`; un valor ausente o inválido abre Parámetros (D1). */
export function pestanaDeParametro(valor: string | null): PestanaEstructura {
  return PESTANAS_ESTRUCTURA.find((pestana) => pestana === valor) ?? 'parametros'
}

/** Ruta de F-11 para una temporada y, opcionalmente, una pestaña (D1). */
export function rutaEstructura(temporadaId: string, pestana?: PestanaEstructura): string {
  const base = `/app/administracion/temporadas/${temporadaId}/estructura`

  return pestana ? `${base}?pestana=${pestana}` : base
}

/** Pestaña que resuelve cada requisito de activación (RF-004, RF-005, RF-100); RN-058 no tiene. */
export const PESTANA_DE_REQUISITO: Readonly<Record<string, PestanaEstructura>> = {
  'RF-004': 'categorias',
  'RF-005': 'grupos',
  'RF-100': 'parametros',
}
