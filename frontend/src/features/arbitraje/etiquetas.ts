import { CircleCheck, CircleDot, CircleX, Clock, type LucideIcon } from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type {
  EstadoArbitro,
  EstadoArbitroVisible,
  PerfilArbitro,
} from '@/features/arbitraje/tipos'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Textos e insignias de los estados del árbitro; no se decide por color (RNF-008). */
export const etiquetaEstadoArbitro: Record<EstadoArbitro, EstadoVisible> = {
  PROPUESTO: { texto: 'Propuesto', variante: 'advertencia', icono: Clock },
  VALIDADO: { texto: 'Validado', variante: 'exito', icono: CircleCheck },
  RECHAZADO: { texto: 'Rechazado', variante: 'error', icono: CircleX },
  BAJA: { texto: 'Baja', variante: 'neutra', icono: CircleDot },
}

/** Pestañas de F-20: solo los estados que produce H-11. */
export const estadosArbitroVisibles: readonly {
  valor: EstadoArbitroVisible
  texto: string
  icono: LucideIcon
}[] = [
  { valor: 'PROPUESTO', texto: 'Por validar', icono: Clock },
  { valor: 'VALIDADO', texto: 'Validados', icono: CircleCheck },
  { valor: 'RECHAZADO', texto: 'Rechazados', icono: CircleX },
]

/**
 * Nombre que se puede mostrar del perfil. Si RLS no le permite ver el perfil a la sesión, el API
 * omite `nombreMostrado`; se usa una etiqueta honesta en lugar de inventar un nombre.
 */
export function nombrePerfilArbitro(perfil: PerfilArbitro | undefined): string {
  return perfil?.nombreMostrado ?? 'Usuario de la liga'
}

/** Iniciales para el avatar de la fila; no sustituyen el nombre completo. */
export function inicialesArbitro(nombreCompleto: string): string {
  const partes = nombreCompleto.trim().split(/\s+/).filter(Boolean)

  return (
    partes
      .slice(0, 2)
      .map((parte) => parte[0]?.toUpperCase() ?? '')
      .join('') || '?'
  )
}
