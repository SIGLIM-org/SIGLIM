import { CircleCheck, CircleX, Clock, type LucideIcon } from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type {
  EstadoAfiliacion,
  FiltroEstadoAfiliacion,
  PerfilAfiliacion,
} from '@/features/organizacion/afiliaciones/tipos'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Textos e insignias de los estados del tarjetón; no se decide por color (RNF-008). */
export const etiquetaEstadoAfiliacion: Record<EstadoAfiliacion, EstadoVisible> = {
  SOLICITADA: { texto: 'Solicitada', variante: 'advertencia', icono: Clock },
  APROBADA: { texto: 'Aprobada', variante: 'exito', icono: CircleCheck },
  RECHAZADA: { texto: 'Rechazada', variante: 'error', icono: CircleX },
}

/** Filtros de la bandeja; `TODOS` no viaja al API. */
export const filtrosAfiliaciones: readonly {
  valor: FiltroEstadoAfiliacion
  texto: string
}[] = [
  { valor: 'TODOS', texto: 'Todas' },
  { valor: 'SOLICITADA', texto: 'Solicitadas' },
  { valor: 'APROBADA', texto: 'Aprobadas' },
  { valor: 'RECHAZADA', texto: 'Rechazadas' },
]

/**
 * Nombre que se puede mostrar del perfil. Si RLS no le permite ver el perfil a la sesión, el API
 * omite `nombreMostrado`; se usa una etiqueta honesta en lugar de inventar un nombre.
 */
export function nombrePerfil(perfil: PerfilAfiliacion | undefined): string {
  return perfil?.nombreMostrado ?? 'Usuario de la liga'
}
