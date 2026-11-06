import { Ban, CircleCheck, Lock, type LucideIcon } from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type { EstadoClub, EstadoEquipo } from '@/features/organizacion/tipos'
import { ZONA_NEGOCIO } from '@/lib/zonaHoraria'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Insignias de los estados de club y equipo; la API no los traduce (convenciones §9.3). */
export const etiquetaEstadoClub: Record<EstadoClub, EstadoVisible> = {
  ACTIVO: { texto: 'Activo', variante: 'exito', icono: CircleCheck },
  CERRADO: { texto: 'Cerrado', variante: 'neutra', icono: Lock },
}

export const etiquetaEstadoEquipo: Record<EstadoEquipo, EstadoVisible> = {
  ACTIVO: { texto: 'Activo', variante: 'exito', icono: CircleCheck },
  BAJA: { texto: 'Baja', variante: 'neutra', icono: Ban },
}

const formatoInstante = new Intl.DateTimeFormat('es-MX', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: ZONA_NEGOCIO,
})

/** Día de un instante ISO en la zona de negocio, p. ej. «3 nov 2026». */
export function formatearDiaDeInstante(iso: string): string {
  const fecha = new Date(iso)

  return Number.isNaN(fecha.getTime()) ? 'Sin fecha' : formatoInstante.format(fecha).replace('.', '')
}
