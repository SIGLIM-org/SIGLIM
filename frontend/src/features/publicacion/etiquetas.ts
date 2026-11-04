import { CircleCheck, Clock, FilePenLine, type LucideIcon } from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type { EstadoPublicacion, FiltroEstado } from '@/features/publicacion/tipos'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Textos e insignias de los estados del comunicado (RF-090, ERS §4.3.6). */
export const etiquetaEstadoPublicacion: Record<EstadoPublicacion, EstadoVisible> = {
  BORRADOR: { texto: 'Borrador', variante: 'neutra', icono: FilePenLine },
  REVISION: { texto: 'En revisión', variante: 'advertencia', icono: Clock },
  PUBLICADO: { texto: 'Publicado', variante: 'exito', icono: CircleCheck },
}

/** Pestañas de la bandeja; el valor `TODOS` no se manda al API. */
export const filtrosComunicados: readonly { valor: FiltroEstado; texto: string }[] = [
  { valor: 'TODOS', texto: 'Todos' },
  { valor: 'BORRADOR', texto: 'Borradores' },
  { valor: 'REVISION', texto: 'En revisión' },
  { valor: 'PUBLICADO', texto: 'Publicados' },
]
