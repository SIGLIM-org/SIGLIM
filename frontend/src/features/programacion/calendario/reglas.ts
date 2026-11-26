/**
 * Reglas puras del modal «Generar calendario» (H-32.4, DEC-197 a DEC-200). Sin React ni API, para
 * probarlas por separado.
 */

import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import type { Calendario, GrupoCalendario } from '@/features/programacion/calendario/tipos'
import type { CategoriaTemporada } from '@/features/temporadas/tipos'

/** Días ISO 8601 (1 = lunes … 7 = domingo). */
export const SABADO = 6
export const DOMINGO = 7

const UN_DIA_MS = 24 * 60 * 60 * 1000

function aFechaUtc(dia: string): Date {
  return new Date(`${dia}T00:00:00Z`)
}

function aTexto(fecha: Date): string {
  return fecha.toISOString().slice(0, 10)
}

/**
 * Días de la fecha 1 (DEC-186): el primer sábado en o después del inicio y el domingo siguiente. Si
 * el inicio cae en domingo, la fecha 1 empieza el sábado siguiente. Aritmética en UTC para que la
 * zona de la máquina no mueva el día.
 *
 * @param inicio `AAAA-MM-DD`
 * @returns `[sábado, domingo]` en `AAAA-MM-DD`
 */
export function primeraFecha(inicio: string): [string, string] {
  const fecha = aFechaUtc(inicio)
  // getUTCDay: 0 = domingo … 6 = sábado.
  const hastaSabado = (6 - fecha.getUTCDay() + 7) % 7
  const sabado = new Date(fecha.getTime() + hastaSabado * UN_DIA_MS)
  const domingo = new Date(sabado.getTime() + UN_DIA_MS)

  return [aTexto(sabado), aTexto(domingo)]
}

const partesFecha = new Intl.DateTimeFormat('es-MX', {
  timeZone: 'UTC',
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

function partes(dia: string) {
  const valor = (tipo: Intl.DateTimeFormatPartTypes) =>
    (partesFecha.formatToParts(aFechaUtc(dia)).find((parte) => parte.type === tipo)?.value ?? '')
      .replace('.', '')
      .toLowerCase()

  return {
    semana: valor('weekday'),
    dia: valor('day'),
    mes: valor('month'),
    anio: valor('year'),
  }
}

/**
 * «sáb 5 y dom 6 dic 2026»; si cambia el mes, «sáb 31 oct y dom 1 nov 2026»; si cambia el año,
 * «sáb 31 dic 2026 y dom 1 ene 2027».
 */
export function formatearDiasFecha([sabado, domingo]: readonly [string, string]): string {
  const primero = partes(sabado)
  const segundo = partes(domingo)
  const final = `${segundo.semana} ${segundo.dia} ${segundo.mes} ${segundo.anio}`

  if (primero.anio !== segundo.anio) {
    return `${primero.semana} ${primero.dia} ${primero.mes} ${primero.anio} y ${final}`
  }
  if (primero.mes !== segundo.mes) {
    return `${primero.semana} ${primero.dia} ${primero.mes} y ${final}`
  }

  return `${primero.semana} ${primero.dia} y ${final}`
}

export type ResumenCalendario = {
  grupos: number
  generados: number
  /** El mayor número de fecha; 0 si no hay calendario. */
  totalFechas: number
  /** La mayor vuelta; `null` si no hay calendario. */
  vueltas: number | null
}

export function resumenCalendario(calendario: Calendario): ResumenCalendario {
  const fechas = calendario.grupos.flatMap((grupo) => grupo.fechas)

  return {
    grupos: calendario.grupos.length,
    generados: calendario.grupos.filter((grupo) => grupo.generado).length,
    totalFechas: fechas.reduce((mayor, { numero }) => Math.max(mayor, numero), 0),
    vueltas: fechas.length > 0 ? Math.max(...fechas.map(({ vuelta }) => vuelta)) : null,
  }
}

export type EstadoGrupoCalendario = 'SIN_CALENDARIO' | 'CON_CALENDARIO' | 'CON_PARTIDOS'

/** «Con partidos» si algún enfrentamiento ya tiene partido: no se regenera (RN-020). */
export function estadoGrupo(grupo: GrupoCalendario): EstadoGrupoCalendario {
  if (grupo.fechas.some((fecha) => fecha.partidos.some(({ partidoId }) => Boolean(partidoId)))) {
    return 'CON_PARTIDOS'
  }

  return grupo.generado ? 'CON_CALENDARIO' : 'SIN_CALENDARIO'
}

/**
 * Selección inicial (DEC-198): los grupos sin calendario; si ninguno tiene calendario, todos los que
 * no tienen partidos (en la práctica, todos).
 */
export function seleccionInicial(calendario: Calendario): string[] {
  const ningunoGenerado = calendario.grupos.every((grupo) => !grupo.generado)

  return calendario.grupos
    .filter((grupo) => {
      const estado = estadoGrupo(grupo)

      return ningunoGenerado ? estado !== 'CON_PARTIDOS' : estado === 'SIN_CALENDARIO'
    })
    .map(({ grupoId }) => grupoId)
}

/** El motivo solo se pide si algún grupo elegido ya tiene calendario y se reemplaza (RN-021). */
export function requiereMotivo(calendario: Calendario, seleccion: readonly string[]): boolean {
  return calendario.grupos.some(
    (grupo) => grupo.generado && seleccion.includes(grupo.grupoId),
  )
}

/**
 * `grupoIds` del `POST` (DEC-198): se omite si están todos los grupos de la temporada; si no, los
 * elegidos en el orden del calendario.
 */
export function grupoIdsParaEnviar(
  calendario: Calendario,
  seleccion: readonly string[],
): string[] | undefined {
  const elegidos = calendario.grupos
    .map(({ grupoId }) => grupoId)
    .filter((grupoId) => seleccion.includes(grupoId))

  return elegidos.length === calendario.grupos.length ? undefined : elegidos
}

/**
 * Pone o quita sábado y domingo y conserva los días de lunes a viernes que ya tenga la categoría
 * (DEC-199). El resultado va ordenado.
 */
export function conFinDeSemana(
  dias: readonly number[],
  sabado: boolean,
  domingo: boolean,
): number[] {
  const resultado = new Set(diasEntreSemana(dias))

  if (sabado) {
    resultado.add(SABADO)
  }
  if (domingo) {
    resultado.add(DOMINGO)
  }

  return [...resultado].sort((primero, segundo) => primero - segundo)
}

const NOMBRES_DIA: Readonly<Record<number, string>> = {
  1: 'lunes',
  2: 'martes',
  3: 'miércoles',
  4: 'jueves',
  5: 'viernes',
  6: 'sábado',
  7: 'domingo',
}

const conjuncion = new Intl.ListFormat('es-MX', { type: 'conjunction' })

/** «A», «A y B» o «A, B y C». */
export function enumerar(textos: readonly string[]): string {
  return conjuncion.format(textos)
}

/** «sábado y domingo»; los días van en el orden recibido. */
export function nombresDias(dias: readonly number[]): string {
  return enumerar(dias.map((dia) => NOMBRES_DIA[dia] ?? String(dia)))
}

/** Días de lunes a viernes que también juega la categoría (DEC-199). */
export function diasEntreSemana(dias: readonly number[]): number[] {
  return dias.filter((dia) => dia >= 1 && dia <= 5)
}

function mismosDias(primero: readonly number[], segundo: readonly number[]): boolean {
  return (
    primero.length === segundo.length && primero.every((dia, indice) => dia === segundo[indice])
  )
}

/** Categorías cuyo borrador difiere de lo que tiene el servidor, en el orden recibido. */
export function diasCambiados(
  borrador: Readonly<Record<string, readonly number[]>>,
  categorias: readonly CategoriaTemporada[],
): { categoria: CategoriaTemporada; diasJuego: number[] }[] {
  return categorias.flatMap((categoria) => {
    const dias = borrador[categoria.id]

    return dias && !mismosDias(dias, categoria.diasJuego)
      ? [{ categoria, diasJuego: [...dias] }]
      : []
  })
}

/**
 * Un `422` es de la fecha de inicio si su mensaje habla de ella. Los dos casos de
 * `CalendarioService.exigirFechaInicioValida` (fecha anterior al inicio de la temporada, o fechas que
 * ya tienen jornadas) contienen «fecha de inicio» o «fechaInicio». Los demás `422` (p. ej. ningún
 * grupo con 2 equipos activos) no lo son. [PROPUESTA] El backend no distingue estos `422` por
 * `codigo` ni por campo, así que se reconoce por el texto mientras el contrato no lo precise.
 */
export function esErrorDeFechaInicio(detail: string): boolean {
  const texto = detail.toLowerCase()

  return texto.includes('fecha de inicio') || texto.includes('fechainicio')
}

/**
 * Motivo del reemplazo: de 10 a 500 caracteres tras recortarlo. [PROPUESTA] El máximo es el de los
 * demás motivos del sistema (`ModalMotivo`); el backend solo exige el mínimo.
 *
 * @returns el error para la persona, o `null` si es válido
 */
export function validarMotivo(texto: string): string | null {
  const longitud = texto.trim().length

  if (longitud < MINIMO_MOTIVO) {
    return `Escribe un motivo de al menos ${MINIMO_MOTIVO} caracteres.`
  }
  if (longitud > MAXIMO_MOTIVO) {
    return `El motivo admite hasta ${MAXIMO_MOTIVO} caracteres.`
  }

  return null
}
