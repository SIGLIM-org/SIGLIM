import type { VarianteInsignia } from '@/components/ui/Insignia'

/** Estados de la cédula (DEC-19); el detalle del partido solo muestra en cuál va. */
export const estadoCedula: Record<string, { texto: string; variante: VarianteInsignia }> = {
  EN_CAPTURA: { texto: 'En captura', variante: 'informacion' },
  PENDIENTE_VALIDACION: { texto: 'Pendiente de validación', variante: 'advertencia' },
  DEVUELTA: { texto: 'Devuelta', variante: 'error' },
  APROBADA: { texto: 'Aprobada', variante: 'exito' },
}

export const etiquetaFuncionArbitro: Record<string, string> = {
  CENTRAL: 'Árbitro central',
  ASISTENTE: 'Asistente',
}
