import type { VarianteInsignia } from '@/components/ui/Insignia'
import type { EstadoCobro, MedioPago, TipoCobro } from '@/features/tesoreria/tipos'

/** Textos de los tipos de cobro; la API no los traduce (convenciones §9.3). */
export const etiquetaTipoCobro: Record<TipoCobro, string> = {
  ARBITRAJE: 'Arbitraje',
  CANCHA: 'Cancha',
  MULTA: 'Multa',
  MANUAL: 'Manual',
}

/** Textos de los medios de un pago externo (RF-139). */
export const etiquetaMedioPago: Record<MedioPago, string> = {
  EFECTIVO: 'Efectivo',
  TRANSFERENCIA: 'Transferencia',
  OTRO: 'Otro',
}

/** Texto y variante visual de cada estado del cobro. */
export const etiquetaEstadoCobro: Record<
  EstadoCobro,
  { texto: string; variante: VarianteInsignia }
> = {
  PENDIENTE: { texto: 'Pendiente', variante: 'advertencia' },
  PAGADO: { texto: 'Pagado', variante: 'exito' },
  RECHAZADO: { texto: 'Rechazado', variante: 'error' },
  VENCIDO: { texto: 'Vencido', variante: 'error' },
}

/** Un estado desconocido se muestra tal cual, sin perder el dato. */
export function estadoCobro(estado: string) {
  return (
    etiquetaEstadoCobro[estado as EstadoCobro] ?? {
      texto: estado,
      variante: 'neutra' as const,
    }
  )
}
