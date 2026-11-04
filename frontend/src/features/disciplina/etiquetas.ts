import type {
  Articulo,
  EfectoSancion,
  SujetoSancion,
  TipoSancion,
} from '@/features/disciplina/tipos'

/** Textos de los enums de disciplina; la API no los traduce (convenciones §9.3). */
export const etiquetaTipoSancion: Record<TipoSancion, string> = {
  SUSPENSION_PARTIDOS: 'Suspensión por partidos',
  MULTA: 'Multa',
  PERDIDA_PUNTOS: 'Pérdida de puntos',
  PERDIDA_PARTIDO: 'Pérdida de partido',
  BAJA: 'Baja',
}

/** Versión breve para tablas densas; el formulario usa el nombre completo. */
export const etiquetaTipoSancionCorta: Record<TipoSancion, string> = {
  SUSPENSION_PARTIDOS: 'Suspensión',
  MULTA: 'Multa',
  PERDIDA_PUNTOS: 'Pérdida de puntos',
  PERDIDA_PARTIDO: 'Pérdida de partido',
  BAJA: 'Baja',
}

export const etiquetaSujeto: Record<SujetoSancion, string> = {
  JUGADOR: 'Jugador',
  EQUIPO: 'Equipo',
  CLUB: 'Club',
}

export const etiquetaEfecto: Record<EfectoSancion, string> = {
  HACIA_ADELANTE: 'Hacia adelante',
  RETROACTIVO: 'Retroactivo',
}

/** Versión breve para tablas densas; el formulario usa el nombre completo. */
export const etiquetaEfectoCorta: Record<EfectoSancion, string> = {
  HACIA_ADELANTE: 'Adelante',
  RETROACTIVO: 'Retroactivo',
}

const formatoMoneda = new Intl.NumberFormat('es-MX', {
  style: 'currency',
  currency: 'MXN',
})

/** Valor por defecto de la sanción según su tipo; vacío para los que no llevan cantidad. */
export function valorSancion(articulo: Articulo): string {
  switch (articulo.tipoSancion) {
    case 'SUSPENSION_PARTIDOS':
      return articulo.partidosSuspension === undefined
        ? ''
        : `${articulo.partidosSuspension} ${
            articulo.partidosSuspension === 1 ? 'partido' : 'partidos'
          }`
    case 'MULTA':
      return articulo.montoMulta === undefined ? '' : formatoMoneda.format(articulo.montoMulta)
    case 'PERDIDA_PUNTOS':
      return articulo.puntos === undefined
        ? ''
        : `${articulo.puntos} ${articulo.puntos === 1 ? 'punto' : 'puntos'}`
    case 'PERDIDA_PARTIDO':
    case 'BAJA':
      return ''
  }
}
