import type { VarianteInsignia } from '@/components/ui/Insignia'
import type {
  Cancha,
  PropietarioCancha,
  TipoBloqueOcupacion,
  UbicacionCancha,
} from '@/features/canchas/tipos'

/** Textos de los enums de canchas; la API no los traduce (convenciones §9.3). */
export const etiquetaUbicacion: Record<UbicacionCancha, string> = {
  UNIDAD_DEPORTIVA: 'Unidad deportiva',
  EXTERNA: 'Externa',
}

export const etiquetaPropietario: Record<PropietarioCancha, string> = {
  LIGA: 'Liga',
  CLUB: 'Club',
  EXTERNO: 'Externo',
}

export const etiquetaBloque: Record<TipoBloqueOcupacion, string> = {
  DISPONIBLE: 'Disponible',
  BLOQUEO: 'Bloqueo',
  MANTENIMIENTO: 'Mantenimiento',
  PARTIDO: 'Partido',
}

const estadosPartido: Record<string, { texto: string; variante: VarianteInsignia }> = {
  PROGRAMADO: { texto: 'Programado', variante: 'informacion' },
  EN_CURSO: { texto: 'En curso', variante: 'exito' },
  FINALIZADO: { texto: 'Finalizado', variante: 'neutra' },
  SUSPENDIDO: { texto: 'Suspendido', variante: 'error' },
  REPROGRAMADO: { texto: 'Reprogramado', variante: 'advertencia' },
  CANCELADO: { texto: 'Cancelado', variante: 'neutra' },
}

/** Un estado desconocido se muestra tal cual, sin perder el dato. */
export function estadoPartido(estado: string) {
  return estadosPartido[estado] ?? { texto: estado, variante: 'neutra' as const }
}

/** Quién es dueño de la cancha: Liga, el nombre del club o el del propietario externo. */
export function nombrePropietario(cancha: Cancha): string {
  if (cancha.propietario === 'CLUB') {
    return cancha.club?.nombre ?? etiquetaPropietario.CLUB
  }

  if (cancha.propietario === 'EXTERNO') {
    return cancha.propietarioExterno ?? etiquetaPropietario.EXTERNO
  }

  return etiquetaPropietario.LIGA
}
