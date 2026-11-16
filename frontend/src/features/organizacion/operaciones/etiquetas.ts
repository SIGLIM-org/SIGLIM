import {
  ArrowLeftRight,
  BadgeCheck,
  CircleCheck,
  CircleX,
  Clock,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type {
  EstadoOperacionEquipo,
  FiltroEstadoOperacionEquipo,
  OperacionEquipo,
  TipoOperacionEquipo,
} from '@/features/organizacion/operaciones/tipos'
import { formatearDiaDeInstante } from '@/features/organizacion/etiquetas'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Insignias de los estados de una operación de equipo (RN-040). */
export const etiquetaEstadoOperacion: Record<EstadoOperacionEquipo, EstadoVisible> = {
  SOLICITADA: { texto: 'Solicitada', variante: 'advertencia', icono: Clock },
  AUTORIZADA: { texto: 'Autorizada', variante: 'informacion', icono: CircleCheck },
  RECHAZADA: { texto: 'Rechazada', variante: 'error', icono: CircleX },
  APLICADA: { texto: 'Aplicada', variante: 'exito', icono: BadgeCheck },
}

/** Nombre, ícono y explicación de cada tipo de operación (RF-103, RF-104). */
export const etiquetaTipoOperacion: Record<
  TipoOperacionEquipo,
  { texto: string; icono: LucideIcon; descripcion: string }
> = {
  VENTA: {
    texto: 'Venta',
    icono: ArrowLeftRight,
    descripcion: 'Cambia el club propietario del equipo al cerrar la temporada.',
  },
  CONSERVACION_PLAZA: {
    texto: 'Conservación de plaza',
    icono: ShieldCheck,
    descripcion: 'Mantiene la plaza del equipo en su club, sin cambiar de propietario.',
  },
}

/** Filtros de la bandeja; `TODOS` no se manda al API. */
export const filtrosOperaciones: readonly {
  valor: FiltroEstadoOperacionEquipo
  texto: string
}[] = [
  { valor: 'TODOS', texto: 'Todas' },
  { valor: 'SOLICITADA', texto: 'Solicitadas' },
  { valor: 'AUTORIZADA', texto: 'Autorizadas' },
  { valor: 'RECHAZADA', texto: 'Rechazadas' },
  { valor: 'APLICADA', texto: 'Aplicadas' },
]

/**
 * Explica qué sigue para una operación, según su estado. La temporada cerrada se muestra aparte
 * porque impide dictaminar una solicitud pendiente (RF-003).
 */
export function siguientePaso(operacion: OperacionEquipo): string {
  if (operacion.temporada.estado === 'CERRADA' && operacion.estado === 'SOLICITADA') {
    return 'La temporada está cerrada: esta solicitud ya no se puede dictaminar.'
  }

  switch (operacion.estado) {
    case 'SOLICITADA':
      return 'Espera el dictamen de la Presidencia.'
    case 'AUTORIZADA':
      return 'Se aplicará al cerrar la temporada.'
    case 'RECHAZADA':
      return operacion.observaciones ?? 'La Presidencia la rechazó sin observaciones.'
    case 'APLICADA':
      return operacion.tipo === 'VENTA'
        ? 'El equipo ya pertenece al club destino.'
        : 'La plaza quedó conservada en su club.'
  }
}

/** Fecha legible de una marca de tiempo del API, o «Sin fecha» si falta. */
export function fechaDeOperacion(iso: string | undefined): string {
  return iso ? formatearDiaDeInstante(iso) : 'Sin fecha'
}
