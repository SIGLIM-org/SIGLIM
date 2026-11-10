import { CircleCheck, CircleX, Clock, Lock, Settings, type LucideIcon } from 'lucide-react'
import type { VarianteInsignia } from '@/components/ui/Insignia'
import type {
  CriterioDesempate,
  EstadoInterpretacion,
  EstadoTemporada,
  FiltroEstadoInterpretacion,
  FormatoLiguilla,
  TipoCategoria,
  ValoresParametros,
} from '@/features/temporadas/tipos'

type EstadoVisible = {
  texto: string
  variante: VarianteInsignia
  icono: LucideIcon
}

/** Textos e insignias de los estados de una temporada (ERS §4.3, RN-058). */
export const etiquetaEstadoTemporada: Record<EstadoTemporada, EstadoVisible> = {
  CONFIGURACION: { texto: 'Configuración', variante: 'informacion', icono: Settings },
  ACTIVA: { texto: 'Activa', variante: 'exito', icono: CircleCheck },
  CERRADA: { texto: 'Cerrada', variante: 'neutra', icono: Lock },
}

/** Textos e insignias de una interpretación de regla (RF-007). */
export const etiquetaEstadoInterpretacion: Record<EstadoInterpretacion, EstadoVisible> = {
  PENDIENTE: { texto: 'Pendiente', variante: 'advertencia', icono: Clock },
  AUTORIZADA: { texto: 'Autorizada', variante: 'exito', icono: CircleCheck },
  RECHAZADA: { texto: 'Rechazada', variante: 'error', icono: CircleX },
}

/** Filtros de la bandeja de interpretaciones; `TODOS` no se manda al API. */
export const filtrosInterpretaciones: readonly {
  valor: FiltroEstadoInterpretacion
  texto: string
}[] = [
  { valor: 'TODOS', texto: 'Todas' },
  { valor: 'PENDIENTE', texto: 'Pendientes' },
  { valor: 'AUTORIZADA', texto: 'Autorizadas' },
  { valor: 'RECHAZADA', texto: 'Rechazadas' },
]

/** Texto y descripción breve de cada criterio de desempate (RF-050). */
export const etiquetaCriterio: Record<CriterioDesempate, { texto: string; descripcion: string }> = {
  PUNTOS: { texto: 'Puntos', descripcion: 'Puntos acumulados en la tabla.' },
  DIFERENCIA_GOLES: {
    texto: 'Diferencia de goles',
    descripcion: 'Goles a favor menos goles en contra.',
  },
  GOLES_FAVOR: { texto: 'Goles a favor', descripcion: 'Total de goles anotados.' },
  MENOS_GOLES_CONTRA: {
    texto: 'Menos goles en contra',
    descripcion: 'Queda arriba el equipo que recibió menos goles.',
  },
  PARTIDOS_GANADOS: { texto: 'Partidos ganados', descripcion: 'Total de victorias.' },
  ENFRENTAMIENTO_DIRECTO: {
    texto: 'Enfrentamiento directo',
    descripcion: 'Resultado de los partidos entre los equipos empatados.',
  },
  MENOS_EXPULSIONES: {
    texto: 'Menos expulsiones',
    descripcion: 'Queda arriba el equipo con menos expulsiones.',
  },
  SORTEO: { texto: 'Sorteo', descripcion: 'Último recurso; siempre va al final.' },
}

/** Orden de los criterios en «Criterios disponibles». */
export const CRITERIOS: readonly CriterioDesempate[] = [
  'PUNTOS',
  'DIFERENCIA_GOLES',
  'GOLES_FAVOR',
  'MENOS_GOLES_CONTRA',
  'PARTIDOS_GANADOS',
  'ENFRENTAMIENTO_DIRECTO',
  'MENOS_EXPULSIONES',
  'SORTEO',
]

/** Tipos de categoría del catálogo (RF-099). */
export const etiquetaTipoCategoria: Record<TipoCategoria, string> = {
  INFANTIL: 'Infantil',
  LIBRE: 'Libre',
  VETERANOS: 'Veteranos',
}

/** Formatos de la liguilla (DEC-28). */
export const etiquetaFormatoLiguilla: Record<FormatoLiguilla, string> = {
  PARTIDO_UNICO: 'Partido único',
  IDA_Y_VUELTA: 'Ida y vuelta',
}

/** Campo de una versión de parámetros que puede aparecer en `camposCambiados`. */
export type CampoParametro = keyof ValoresParametros | 'esDemo' | 'categorias'

/**
 * Etiqueta de cada campo de una versión de parámetros, para el formulario, el resumen y los
 * `camposCambiados` del historial (RF-006). Incluye `esDemo` y `categorias`, que el historial
 * también reporta (convenciones de API §6.2).
 */
export const etiquetaCampoParametro: Record<CampoParametro, string> = {
  puntosVictoria: 'Puntos por victoria',
  puntosEmpate: 'Puntos por empate',
  puntosDerrota: 'Puntos por derrota',
  criteriosDesempate: 'Criterios de desempate',
  golesDefaultGanador: 'Goles del ganador por default',
  golesDefaultPerdedor: 'Goles del perdedor por default',
  plazoValidacionCedulaHoras: 'Plazo de validación de la cédula',
  duracionJuegoMin: 'Duración del juego',
  bloqueCanchaMin: 'Bloque de cancha',
  clasificadosLiguilla: 'Clasificados a la liguilla',
  formatoLiguilla: 'Formato de la liguilla',
  empateLiguillaPorPenales: 'Empate en liguilla por penales',
  equiposAscienden: 'Equipos que ascienden',
  equiposDescienden: 'Equipos que descienden',
  permiteCambioClub: 'Permite cambio de club',
  esDemo: 'Oficial / No oficial',
  categorias: 'Duración y bloque por categoría',
}

/** Etiqueta legible de un campo; un campo desconocido se muestra tal cual. */
export function etiquetaDeCampo(campo: string): string {
  return (etiquetaCampoParametro as Record<string, string>)[campo] ?? campo
}

/**
 * Campos [DEMO] que el formulario editable rotula «No oficial» mientras la versión sea demo.
 * Copia de la lista fija de convenciones de API §6.2 («La lista es fija») y de
 * `ParametrosService.CAMPOS_DEMO`: no incluye `duracionJuegoMin` (DEC-25) ni `permiteCambioClub`
 * (DEC-13). Las vistas de solo lectura usan los `camposDemo` de su propia versión.
 */
export const CAMPOS_DEMO: readonly string[] = [
  'puntosVictoria',
  'puntosEmpate',
  'puntosDerrota',
  'criteriosDesempate',
  'golesDefaultGanador',
  'golesDefaultPerdedor',
  'plazoValidacionCedulaHoras',
  'bloqueCanchaMin',
  'clasificadosLiguilla',
  'formatoLiguilla',
  'empateLiguillaPorPenales',
  'equiposAscienden',
  'equiposDescienden',
]
