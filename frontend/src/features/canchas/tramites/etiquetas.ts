import {
  CircleCheck,
  CircleX,
  Clock,
  Handshake,
  KeyRound,
  MessagesSquare,
  type LucideIcon,
} from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import { formatearDiaDeInstante } from '@/features/organizacion/etiquetas'
import type {
  DecisionSolicitudCancha,
  EstadoSolicitudCancha,
  FiltroEstadoTramite,
  FormatoTramite,
  SolicitudCancha,
  TipoSolicitudCancha,
} from '@/features/canchas/tramites/tipos'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Insignias de los estados de los Formatos 1 y 2 (DEC-35): texto, ícono y color. */
export const etiquetaEstadoTramite: Record<EstadoSolicitudCancha, EstadoVisible> = {
  PENDIENTE: { texto: 'Pendiente', variante: 'advertencia', icono: Clock },
  APROBADA: { texto: 'Aprobada', variante: 'exito', icono: CircleCheck },
  NO_APROBADA: { texto: 'No aprobada', variante: 'error', icono: CircleX },
  PENDIENTE_NEGOCIACION: {
    texto: 'Pendiente de negociación',
    variante: 'advertencia',
    icono: MessagesSquare,
  },
  ACEPTADA: { texto: 'Aceptada', variante: 'exito', icono: CircleCheck },
  RECHAZADA: { texto: 'Rechazada', variante: 'error', icono: CircleX },
}

export const etiquetaTipoTramite: Record<
  TipoSolicitudCancha,
  { texto: string; icono: LucideIcon; descripcion: string }
> = {
  PRESTAMO: {
    texto: 'Préstamo',
    icono: KeyRound,
    descripcion: 'Uso de una cancha en una fecha y horario concretos.',
  },
  COMODATO: {
    texto: 'Comodato',
    icono: Handshake,
    descripcion: 'Uso prolongado de una cancha que el club cuida y conserva.',
  },
  OFERTA: {
    texto: 'Oferta',
    icono: Handshake,
    descripcion: 'Un club o un externo pone su cancha a disposición de la Liga.',
  },
}

/** Pestañas de la pantalla con los estados de cada formato. */
export const formatos: Record<
  FormatoTramite,
  { titulo: string; corto: string; estados: readonly EstadoSolicitudCancha[] }
> = {
  1: {
    titulo: 'Préstamo y comodato (Formato 1)',
    corto: 'Préstamo y comodato',
    estados: ['PENDIENTE', 'APROBADA', 'NO_APROBADA'],
  },
  2: {
    titulo: 'Ofertas de cancha (Formato 2)',
    corto: 'Ofertas de cancha',
    estados: ['PENDIENTE_NEGOCIACION', 'ACEPTADA', 'RECHAZADA'],
  },
}

/** Filtros de estado del formato; el primero, «Todos», no se manda al API. */
export function filtrosDeFormato(
  formato: FormatoTramite,
): readonly { valor: FiltroEstadoTramite; texto: string }[] {
  return [
    { valor: 'TODOS', texto: 'Todos' },
    ...formatos[formato].estados.map((estado) => ({
      valor: estado,
      texto: etiquetaEstadoTramite[estado].texto,
    })),
  ]
}

/** Las dos decisiones del Presidente para el formato: la favorable primero. */
export function decisionesDeFormato(
  formato: FormatoTramite,
): readonly [DecisionSolicitudCancha, DecisionSolicitudCancha] {
  return formato === 1 ? ['APROBADA', 'NO_APROBADA'] : ['ACEPTADA', 'RECHAZADA']
}

export const textoDecision: Record<DecisionSolicitudCancha, string> = {
  APROBADA: 'Aprobar',
  NO_APROBADA: 'No aprobar',
  ACEPTADA: 'Aceptar',
  RECHAZADA: 'Rechazar',
}

export function esPendiente(estado: EstadoSolicitudCancha): boolean {
  return estado === 'PENDIENTE' || estado === 'PENDIENTE_NEGOCIACION'
}

/** Quien pide u ofrece: el club o el oferente externo. */
export function solicitanteDe(solicitud: SolicitudCancha): string {
  return solicitud.club?.nombre ?? solicitud.solicitanteExterno ?? 'Sin solicitante'
}

/** Cancha a la que se refiere el trámite, según su formato. */
export function canchaDe(solicitud: SolicitudCancha): string {
  return (
    solicitud.cancha?.nombre ??
    solicitud.prestamo?.instalacion ??
    solicitud.oferta?.nombreCancha ??
    'Sin cancha indicada'
  )
}

/** Explica qué sigue para un trámite, según su estado. */
export function siguientePaso(solicitud: SolicitudCancha): string {
  switch (solicitud.estado) {
    case 'PENDIENTE':
      return 'Espera el dictamen de la Presidencia.'
    case 'PENDIENTE_NEGOCIACION':
      return 'En negociación: la Presidencia la acepta o la rechaza.'
    case 'APROBADA':
    case 'ACEPTADA':
      return solicitud.observaciones ?? 'Sin condiciones adicionales.'
    case 'NO_APROBADA':
    case 'RECHAZADA':
      return solicitud.observaciones ?? 'La Presidencia no dejó observaciones.'
  }
}

export function fechaDeTramite(iso: string | undefined): string {
  return iso ? formatearDiaDeInstante(iso) : 'Sin fecha'
}

const formatoFechaLarga = new Intl.DateTimeFormat('es-MX', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

/** Fecha `AAAA-MM-DD` como en la carta: «7 de septiembre de 2031». */
export function fechaLarga(fecha: string): string {
  const valor = new Date(`${fecha}T00:00:00Z`)

  return Number.isNaN(valor.getTime()) ? fecha : formatoFechaLarga.format(valor)
}

/** Hora `HH:mm[:ss]` del API como `HH:mm`. */
export function horaCorta(hora: string): string {
  return hora.slice(0, 5)
}
