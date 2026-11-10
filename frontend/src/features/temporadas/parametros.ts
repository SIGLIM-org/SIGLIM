import type { ApiError } from '@/lib/api/ApiError'
import type {
  ClasificadosLiguilla,
  CriterioDesempate,
  FormatoLiguilla,
  ParametrosCategoriaPeticion,
  ParametrosPredeterminados,
  RegistrarParametrosPeticion,
  ValoresParametros,
  VersionParametros,
} from '@/features/temporadas/tipos'

/** Campos numéricos enteros del juego de parámetros. */
export type CampoNumerico =
  | 'puntosVictoria'
  | 'puntosEmpate'
  | 'puntosDerrota'
  | 'golesDefaultGanador'
  | 'golesDefaultPerdedor'
  | 'plazoValidacionCedulaHoras'
  | 'duracionJuegoMin'
  | 'bloqueCanchaMin'
  | 'equiposAscienden'
  | 'equiposDescienden'

/** Duración y bloque propios de una categoría; vacío = usa los de la temporada (DEC-25). */
export type FilaCategoriaFormulario = {
  duracionJuegoMin: string
  bloqueCanchaMin: string
}

/**
 * Borrador del formulario de F-11. Los números se guardan como texto para que un campo pueda
 * quedar vacío mientras se escribe; `aPeticion` los convierte.
 */
export type FormularioParametros = Record<CampoNumerico, string> & {
  criteriosDesempate: CriterioDesempate[]
  clasificadosLiguilla: ClasificadosLiguilla
  formatoLiguilla: FormatoLiguilla
  empateLiguillaPorPenales: boolean
  permiteCambioClub: boolean
  /** «Valores confirmados por la Liga»: se envía como `esDemo = !oficial` (PEN-021). */
  oficial: boolean
  /** Filas por categoría, por `categoriaId`. */
  categorias: Record<string, FilaCategoriaFormulario>
}

export const CAMPOS_NUMERICOS: readonly CampoNumerico[] = [
  'puntosVictoria',
  'puntosEmpate',
  'puntosDerrota',
  'golesDefaultGanador',
  'golesDefaultPerdedor',
  'plazoValidacionCedulaHoras',
  'duracionJuegoMin',
  'bloqueCanchaMin',
  'equiposAscienden',
  'equiposDescienden',
]

/**
 * Rangos de convenciones de API §6.2. Los de puntos, plazo, duración, bloque y ascensos son
 * [PROPUESTA] del contrato; aquí solo se copian para avisar antes de enviar.
 */
export const RANGOS: Record<CampoNumerico, { minimo: number; maximo: number }> = {
  puntosVictoria: { minimo: 0, maximo: 100 },
  puntosEmpate: { minimo: 0, maximo: 100 },
  puntosDerrota: { minimo: 0, maximo: 100 },
  golesDefaultGanador: { minimo: 0, maximo: 99 },
  golesDefaultPerdedor: { minimo: 0, maximo: 99 },
  plazoValidacionCedulaHoras: { minimo: 1, maximo: 720 },
  duracionJuegoMin: { minimo: 1, maximo: 600 },
  bloqueCanchaMin: { minimo: 1, maximo: 600 },
  equiposAscienden: { minimo: 0, maximo: 32 },
  equiposDescienden: { minimo: 0, maximo: 32 },
}

/** Clave del error de una fila por categoría en el formulario. */
export function campoFila(
  categoriaId: string,
  campo: keyof FilaCategoriaFormulario,
): string {
  return `categorias.${categoriaId}.${campo}`
}

function desdeValores(valores: ValoresParametros, oficial: boolean): FormularioParametros {
  return {
    puntosVictoria: String(valores.puntosVictoria),
    puntosEmpate: String(valores.puntosEmpate),
    puntosDerrota: String(valores.puntosDerrota),
    golesDefaultGanador: String(valores.golesDefaultGanador),
    golesDefaultPerdedor: String(valores.golesDefaultPerdedor),
    plazoValidacionCedulaHoras: String(valores.plazoValidacionCedulaHoras),
    duracionJuegoMin: String(valores.duracionJuegoMin),
    bloqueCanchaMin: String(valores.bloqueCanchaMin),
    equiposAscienden: String(valores.equiposAscienden),
    equiposDescienden: String(valores.equiposDescienden),
    criteriosDesempate: [...valores.criteriosDesempate],
    clasificadosLiguilla: valores.clasificadosLiguilla,
    formatoLiguilla: valores.formatoLiguilla,
    empateLiguillaPorPenales: valores.empateLiguillaPorPenales,
    permiteCambioClub: valores.permiteCambioClub,
    oficial,
    categorias: {},
  }
}

/** Formulario desde la versión vigente, con sus filas por categoría (RF-006). */
export function desdeVersion(vigente: VersionParametros): FormularioParametros {
  return {
    ...desdeValores(vigente, !vigente.esDemo),
    categorias: Object.fromEntries(
      vigente.categorias.map((fila) => [
        fila.categoria.id,
        {
          duracionJuegoMin: String(fila.duracionJuegoMin),
          bloqueCanchaMin: String(fila.bloqueCanchaMin),
        },
      ]),
    ),
  }
}

/** Formulario de la primera versión desde los valores de la tabla; arranca como no oficial (D4). */
export function desdePredeterminados(predeterminados: ParametrosPredeterminados): FormularioParametros {
  return desdeValores(predeterminados, false)
}

function entero(texto: string): number {
  return Number(texto.trim())
}

/** Filas con ambos valores; las vacías o a medias no se envían. */
function filasCompletas(formulario: FormularioParametros): ParametrosCategoriaPeticion[] {
  return Object.entries(formulario.categorias)
    .filter(([, fila]) => fila.duracionJuegoMin.trim() !== '' && fila.bloqueCanchaMin.trim() !== '')
    .map(([categoriaId, fila]) => ({
      categoriaId,
      duracionJuegoMin: entero(fila.duracionJuegoMin),
      bloqueCanchaMin: entero(fila.bloqueCanchaMin),
    }))
}

/**
 * Cuerpo de `POST /temporadas/{id}/parametros`: el juego completo, `esDemo` y solo las filas por
 * categoría con ambos valores (RF-006, RF-100, convenciones §6.2).
 */
export function aPeticion(
  formulario: FormularioParametros,
  motivo: string,
): RegistrarParametrosPeticion {
  return {
    puntosVictoria: entero(formulario.puntosVictoria),
    puntosEmpate: entero(formulario.puntosEmpate),
    puntosDerrota: entero(formulario.puntosDerrota),
    criteriosDesempate: [...formulario.criteriosDesempate],
    golesDefaultGanador: entero(formulario.golesDefaultGanador),
    golesDefaultPerdedor: entero(formulario.golesDefaultPerdedor),
    plazoValidacionCedulaHoras: entero(formulario.plazoValidacionCedulaHoras),
    duracionJuegoMin: entero(formulario.duracionJuegoMin),
    bloqueCanchaMin: entero(formulario.bloqueCanchaMin),
    clasificadosLiguilla: formulario.clasificadosLiguilla,
    formatoLiguilla: formulario.formatoLiguilla,
    empateLiguillaPorPenales: formulario.empateLiguillaPorPenales,
    equiposAscienden: entero(formulario.equiposAscienden),
    equiposDescienden: entero(formulario.equiposDescienden),
    permiteCambioClub: formulario.permiteCambioClub,
    esDemo: !formulario.oficial,
    categorias: filasCompletas(formulario),
    motivo,
  }
}

/** ¿El borrador difiere de la vigente? Sin vigente siempre hay algo que guardar (RF-100). */
export function hayCambios(
  formulario: FormularioParametros,
  vigente: VersionParametros | null,
): boolean {
  if (!vigente) {
    return true
  }

  const camposDistintos = CAMPOS_NUMERICOS.some(
    (campo) => formulario[campo].trim() === '' || entero(formulario[campo]) !== vigente[campo],
  )
  const criteriosDistintos =
    formulario.criteriosDesempate.join() !== vigente.criteriosDesempate.join()
  const filas = Object.entries(formulario.categorias).filter(
    ([, fila]) => fila.duracionJuegoMin.trim() !== '' || fila.bloqueCanchaMin.trim() !== '',
  )
  const filasDistintas =
    filas.length !== vigente.categorias.length ||
    filas.some(([categoriaId, fila]) => {
      const actual = vigente.categorias.find((otra) => otra.categoria.id === categoriaId)

      return (
        !actual ||
        fila.duracionJuegoMin.trim() === '' ||
        fila.bloqueCanchaMin.trim() === '' ||
        entero(fila.duracionJuegoMin) !== actual.duracionJuegoMin ||
        entero(fila.bloqueCanchaMin) !== actual.bloqueCanchaMin
      )
    })

  return (
    camposDistintos ||
    criteriosDistintos ||
    filasDistintas ||
    formulario.clasificadosLiguilla !== vigente.clasificadosLiguilla ||
    formulario.formatoLiguilla !== vigente.formatoLiguilla ||
    formulario.empateLiguillaPorPenales !== vigente.empateLiguillaPorPenales ||
    formulario.permiteCambioClub !== vigente.permiteCambioClub ||
    formulario.oficial === vigente.esDemo
  )
}

/** Error de rango de un número entero, o `undefined` si es válido. */
function errorDeEntero(texto: string, minimo: number, maximo: number): string | undefined {
  if (texto.trim() === '') {
    return 'Escribe un número entero.'
  }

  const valor = entero(texto)

  if (!Number.isInteger(valor)) {
    return 'Escribe un número entero.'
  }

  if (valor < minimo || valor > maximo) {
    return `Debe estar entre ${minimo} y ${maximo}.`
  }

  return undefined
}

const MENSAJE_FILA_INCOMPLETA = 'Indica duración y bloque, o deja ambos vacíos.'
const MENSAJE_BLOQUE = 'El bloque de cancha no puede ser menor que la duración del juego.'

/**
 * Valida el borrador antes de abrir el modal (RF-006, RF-050, DEC-24, DEC-25). Devuelve un mensaje
 * por campo; las filas usan `campoFila`. Es solo ayuda: el servidor vuelve a validar y manda.
 */
export function validarFormulario(formulario: FormularioParametros): Record<string, string> {
  const errores: Record<string, string> = {}

  for (const campo of CAMPOS_NUMERICOS) {
    const { minimo, maximo } = RANGOS[campo]
    const error = errorDeEntero(formulario[campo], minimo, maximo)

    if (error) {
      errores[campo] = error
    }
  }

  const valido = (campo: CampoNumerico) => !errores[campo]
  const numero = (campo: CampoNumerico) => entero(formulario[campo])

  if (valido('puntosVictoria') && valido('puntosEmpate') && numero('puntosEmpate') > numero('puntosVictoria')) {
    errores.puntosEmpate = 'No puede ser mayor que los puntos por victoria.'
  }
  if (valido('puntosEmpate') && valido('puntosDerrota') && numero('puntosDerrota') > numero('puntosEmpate')) {
    errores.puntosDerrota = 'No puede ser mayor que los puntos por empate.'
  }
  if (
    valido('golesDefaultGanador') &&
    valido('golesDefaultPerdedor') &&
    numero('golesDefaultGanador') <= numero('golesDefaultPerdedor')
  ) {
    errores.golesDefaultPerdedor = 'Debe ser menor que los goles del ganador.'
  }
  if (valido('duracionJuegoMin') && valido('bloqueCanchaMin') && numero('bloqueCanchaMin') < numero('duracionJuegoMin')) {
    errores.bloqueCanchaMin = MENSAJE_BLOQUE
  }

  const criterios = formulario.criteriosDesempate
  if (
    criterios.length < 1 ||
    criterios.length > 8 ||
    criterios[0] !== 'PUNTOS' ||
    new Set(criterios).size !== criterios.length ||
    (criterios.includes('SORTEO') && criterios[criterios.length - 1] !== 'SORTEO')
  ) {
    errores.criteriosDesempate =
      'Los criterios empiezan por Puntos, no se repiten y el sorteo, si está, va al final.'
  }

  for (const [categoriaId, fila] of Object.entries(formulario.categorias)) {
    const duracionVacia = fila.duracionJuegoMin.trim() === ''
    const bloqueVacio = fila.bloqueCanchaMin.trim() === ''

    if (duracionVacia && bloqueVacio) {
      continue
    }

    if (duracionVacia || bloqueVacio) {
      errores[campoFila(categoriaId, duracionVacia ? 'duracionJuegoMin' : 'bloqueCanchaMin')] =
        MENSAJE_FILA_INCOMPLETA
      continue
    }

    const { minimo, maximo } = RANGOS.duracionJuegoMin
    const errorDuracion = errorDeEntero(fila.duracionJuegoMin, minimo, maximo)
    const errorBloque = errorDeEntero(fila.bloqueCanchaMin, minimo, maximo)

    if (errorDuracion) {
      errores[campoFila(categoriaId, 'duracionJuegoMin')] = errorDuracion
    }
    if (errorBloque) {
      errores[campoFila(categoriaId, 'bloqueCanchaMin')] = errorBloque
    } else if (!errorDuracion && entero(fila.bloqueCanchaMin) < entero(fila.duracionJuegoMin)) {
      errores[campoFila(categoriaId, 'bloqueCanchaMin')] = MENSAJE_BLOQUE
    }
  }

  return errores
}

const PATRON_FILA = /^categorias\[(\d+)\]\.(duracionJuegoMin|bloqueCanchaMin)$/

/**
 * Traduce los `errores` de un `400 VALIDACION` a las claves del formulario. Las filas llegan como
 * `categorias[i].campo`, con `i` = índice en el arreglo ENVIADO, así que se usa la petición para
 * saber a qué categoría corresponden (convenciones §6.2).
 */
export function erroresDeServidor(
  error: ApiError,
  peticion: RegistrarParametrosPeticion,
): Record<string, string> {
  const errores: Record<string, string> = {}

  for (const { campo, mensaje } of error.errores) {
    const fila = PATRON_FILA.exec(campo)

    if (fila) {
      const enviada = peticion.categorias[Number(fila[1])]

      if (enviada) {
        errores[campoFila(enviada.categoriaId, fila[2] as keyof FilaCategoriaFormulario)] = mensaje
      }
      continue
    }

    errores[campo] = mensaje
  }

  return errores
}

/** Ids de `extensiones.categoriasInvalidas` de un `422 REFERENCIA_INVALIDA`. */
export function categoriasInvalidasDe(error: ApiError): string[] {
  const valor = error.problema.extensiones?.categoriasInvalidas

  return Array.isArray(valor) ? valor.filter((id): id is string => typeof id === 'string') : []
}
