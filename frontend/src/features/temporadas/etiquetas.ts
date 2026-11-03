import { CircleCheck, Lock, Settings, type LucideIcon } from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type { EstadoTemporada } from '@/features/temporadas/tipos'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Textos e insignias de los estados de una temporada (ERS §4.3, RN-058). */
export const etiquetaEstadoTemporada: Record<EstadoTemporada, EstadoVisible> = {
  CONFIGURACION: { texto: 'Configuración', variante: 'informacion', icono: Settings },
  ACTIVA: { texto: 'Activa', variante: 'exito', icono: CircleCheck },
  CERRADA: { texto: 'Cerrada', variante: 'neutra', icono: Lock },
}
