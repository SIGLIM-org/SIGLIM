import { Ban, CircleCheck, CircleX, Clock, CirclePause, type LucideIcon } from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type { EstadoRegistro, EstadoValidacionDocumento } from '@/features/registros/tipos'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Insignias del estado de un documento; ícono y texto, no solo color (RNF-008). */
export const etiquetaEstadoDocumento: Record<EstadoValidacionDocumento, EstadoVisible> = {
  PENDIENTE: { texto: 'Por revisar', variante: 'advertencia', icono: Clock },
  APROBADO: { texto: 'Aprobado', variante: 'exito', icono: CircleCheck },
  RECHAZADO: { texto: 'Rechazado', variante: 'error', icono: CircleX },
}

/** Insignias del estado de un registro de jugador (`estado_registro`). */
export const etiquetaEstadoRegistro: Record<EstadoRegistro, EstadoVisible> = {
  PENDIENTE_VALIDACION: { texto: 'Pendiente de validación', variante: 'advertencia', icono: Clock },
  ACTIVO: { texto: 'Activo', variante: 'exito', icono: CircleCheck },
  SUSPENDIDO: { texto: 'Suspendido', variante: 'error', icono: CirclePause },
  BAJA: { texto: 'Baja', variante: 'neutra', icono: Ban },
  RECHAZADO: { texto: 'Rechazado', variante: 'error', icono: CircleX },
}

/** Pestañas de la bandeja de validación (DEC-111); `TODOS` no viaja al API. */
export type FiltroEstadoRegistro = 'PENDIENTE_VALIDACION' | 'ACTIVO' | 'RECHAZADO' | 'TODOS'

export const filtrosRegistros: readonly { valor: FiltroEstadoRegistro; texto: string }[] = [
  { valor: 'PENDIENTE_VALIDACION', texto: 'Pendientes' },
  { valor: 'ACTIVO', texto: 'Activos' },
  { valor: 'RECHAZADO', texto: 'Rechazados' },
  { valor: 'TODOS', texto: 'Todos' },
]
