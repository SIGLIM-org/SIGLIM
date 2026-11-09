import {
  Archive,
  Ban,
  CircleCheck,
  Clock,
  Crown,
  FileSignature,
  Flag,
  Lock,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type {
  CargoDirectiva,
  EstadoCargoDirectiva,
  EstadoClub,
  EstadoEquipo,
  TipoCargoDirectiva,
} from '@/features/organizacion/tipos'
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

/** Nombre e ícono de cada cargo de la directiva (RF-102, DEC-03). */
export const etiquetaCargoDirectiva: Record<
  TipoCargoDirectiva,
  { texto: string; icono: LucideIcon }
> = {
  PRESIDENTE: { texto: 'Presidente', icono: Crown },
  SECRETARIO: { texto: 'Secretario', icono: FileSignature },
  TESORERO: { texto: 'Tesorero', icono: Wallet },
  DELEGADO: { texto: 'Delegado', icono: Flag },
}

export const etiquetaEstadoCargoDirectiva: Record<EstadoCargoDirectiva, EstadoVisible> = {
  PENDIENTE: { texto: 'Pendiente', variante: 'advertencia', icono: Clock },
  AUTORIZADO: { texto: 'Vigente', variante: 'exito', icono: CircleCheck },
}

/**
 * Un cargo autorizado con `hasta` ya concluyó, pero la API lo sigue devolviendo `AUTORIZADO`
 * (RF-102): la interfaz lo distingue para no mostrar como vigente a quien ya dejó el cargo.
 */
export function estadoVisibleDeCargo(cargo: CargoDirectiva): EstadoVisible {
  if (cargo.estado === 'PENDIENTE') {
    return etiquetaEstadoCargoDirectiva.PENDIENTE
  }

  return cargo.hasta
    ? { texto: 'Concluido', variante: 'neutra', icono: Archive }
    : etiquetaEstadoCargoDirectiva.AUTORIZADO
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
