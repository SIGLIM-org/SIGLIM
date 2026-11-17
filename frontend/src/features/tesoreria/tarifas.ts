import type { ConceptoTarifa, Tarifa, TemporadaTarifas } from '@/features/tesoreria/tipos'

/** Columnas de la tabla de tarifas, en el orden en que se muestran (RF-135). */
export const CONCEPTOS_TARIFA: readonly { valor: ConceptoTarifa; texto: string }[] = [
  { valor: 'ARBITRAJE', texto: 'Arbitraje' },
  { valor: 'CANCHA', texto: 'Cancha' },
]

/** Hasta diez dígitos enteros y dos decimales, como la columna `numeric(12,2)`. */
const MONTO_VALIDO = /^\d{1,10}(\.\d{1,2})?$/

export type MontoCapturado =
  | { tipo: 'vacio' }
  | { tipo: 'valido'; monto: number }
  | { tipo: 'invalido'; mensaje: string }

/**
 * Interpreta el monto escrito en una celda. Se valida aquí para explicar el error antes de
 * enviar, pero el backend vuelve a validar (B-29 criterio 2): un negativo nunca se guarda.
 */
export function leerMonto(texto: string): MontoCapturado {
  const limpio = texto.trim().replace(/^\$/, '').replace(/,/g, '').trim()

  if (limpio === '') {
    return { tipo: 'vacio' }
  }
  if (limpio.startsWith('-')) {
    return { tipo: 'invalido', mensaje: 'El monto no puede ser negativo.' }
  }
  if (!MONTO_VALIDO.test(limpio)) {
    return {
      tipo: 'invalido',
      mensaje: 'Escribe un monto en pesos con hasta dos decimales, por ejemplo 350.00.',
    }
  }

  return { tipo: 'valido', monto: Number(limpio) }
}

/** Texto inicial de la celda: el monto vigente con dos decimales o vacío si no hay tarifa. */
export function textoDeMonto(tarifa?: Tarifa): string {
  return tarifa ? tarifa.monto.toFixed(2) : ''
}

/** Índice `categoríaId → concepto → tarifa` para pintar la tabla categoría × concepto. */
export function indexarTarifas(
  tarifas: readonly Tarifa[],
): Map<string, Partial<Record<ConceptoTarifa, Tarifa>>> {
  const indice = new Map<string, Partial<Record<ConceptoTarifa, Tarifa>>>()

  for (const tarifa of tarifas) {
    const fila = indice.get(tarifa.categoria.id) ?? {}
    fila[tarifa.concepto] = tarifa
    indice.set(tarifa.categoria.id, fila)
  }

  return indice
}

/** Temporada que se muestra al abrir la pestaña: la activa o, si no hay, la más reciente. */
export function temporadaInicial(temporadas: readonly TemporadaTarifas[]): string {
  return (temporadas.find(({ estado }) => estado === 'ACTIVA') ?? temporadas[0])?.id ?? ''
}
