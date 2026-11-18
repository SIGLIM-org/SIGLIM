import { Ban, CircleCheck, Clock, type LucideIcon } from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type { TipoSancion } from '@/features/disciplina/tipos'
import type { EstadoSancion } from '@/features/registros/mi-informacion/tipos'

/** Textos del tipo de sanción para el jugador o tutor (DEC-150). */
export const etiquetaTipoSancionPropia: Record<TipoSancion, string> = {
  SUSPENSION_PARTIDOS: 'Suspensión por partidos',
  MULTA: 'Multa',
  PERDIDA_PUNTOS: 'Pérdida de puntos',
  PERDIDA_PARTIDO: 'Pérdida de partido',
  BAJA: 'Baja',
}

/** Insignia del estado de una sanción; ícono y texto, no solo color (RNF-008). */
export const etiquetaEstadoSancion: Record<
  EstadoSancion,
  { texto: string; variante: VarianteInsignia; icono: LucideIcon }
> = {
  VIGENTE: { texto: 'Vigente', variante: 'error', icono: Clock },
  CUMPLIDA: { texto: 'Cumplida', variante: 'exito', icono: CircleCheck },
  REVOCADA: { texto: 'Revocada', variante: 'neutra', icono: Ban },
}
