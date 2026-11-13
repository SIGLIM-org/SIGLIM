import { CircleCheck, CircleX, Clock, type LucideIcon } from 'lucide-react'
import type { EstadoRegistro } from '@/features/registros/tipos'

export type VarianteElegibilidad = 'exito' | 'advertencia' | 'error'

export type Elegibilidad = {
  texto: string
  variante: VarianteElegibilidad
  icono: LucideIcon
}

/*
 * [PROPUESTA] (DEC-117) RF-017 llama «estado de elegibilidad» al estado del registro; RF-019 y
 * RF-020 agregan la sanción vigente, que se revisará al capturar la alineación cuando entre M-09.
 * Por eso esto es un indicador calculado con el estado del registro, no un dato oficial (ERS 3.1.1).
 * El `Record` obliga a cubrir cada valor de `estado_registro`.
 */
const ELEGIBILIDAD: Record<EstadoRegistro, Elegibilidad> = {
  ACTIVO: { texto: 'Elegible', variante: 'exito', icono: CircleCheck },
  PENDIENTE_VALIDACION: { texto: 'En validación', variante: 'advertencia', icono: Clock },
  SUSPENDIDO: { texto: 'No elegible', variante: 'error', icono: CircleX },
  BAJA: { texto: 'No elegible', variante: 'error', icono: CircleX },
  RECHAZADO: { texto: 'No elegible', variante: 'error', icono: CircleX },
}

/** Indicador de elegibilidad de un registro de la plantilla (F-17, RF-017, DEC-117). */
export function elegibilidadDeRegistro(estado: EstadoRegistro): Elegibilidad {
  return ELEGIBILIDAD[estado]
}
