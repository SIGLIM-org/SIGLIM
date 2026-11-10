import { etiquetaDia } from '@/features/canchas/etiquetas'
import type {
  DiaSemana,
  HorarioClub,
  HorarioClubPeticion,
  PropietarioCancha,
} from '@/features/canchas/tipos'
import type { ErrorCampo } from '@/lib/api/tipos'

/**
 * Lógica de la sección «Horarios por categoría» (B-13.2, RF-119, DEC-33), sin React: filas
 * editables, cuerpo del `PUT`, validación previa con la misma regla del servidor y mapeo de los
 * errores `horarios[i].campo` del 400 (convenciones de API §6.6.1).
 */

/** Campos editables de una fila; coinciden con los de `HorarioClubPeticion`. */
export type CampoHorario = 'categoriaId' | 'diaSemana' | 'horaInicio' | 'horaFin' | 'clubId'

/**
 * Fila editable. `clave` es local y estable (el id del horario o una clave nueva) y nunca se envía.
 * Los nombres se conservan para mostrar una categoría o un club que ya no esté en las listas.
 */
export type FilaHorario = {
  clave: string
  categoriaId: string
  categoriaNombre: string
  diaSemana: DiaSemana | ''
  /** `HH:mm`, el formato de `<input type="time">`. */
  horaInicio: string
  horaFin: string
  clubId: string
  clubNombre: string
}

/** Errores de una fila: por campo y, en `fila`, los traslapes con otras filas. */
export type ErroresFila = Partial<Record<CampoHorario | 'fila', string>>

/** Errores por `clave` de fila. */
export type ErroresFilas = Record<string, ErroresFila>

export const DIAS_SEMANA: readonly DiaSemana[] = [1, 2, 3, 4, 5, 6, 7]

const CAMPOS: readonly CampoHorario[] = ['categoriaId', 'diaSemana', 'horaInicio', 'horaFin', 'clubId']

/** `HH:mm:ss` → `HH:mm`; un valor que ya viene en `HH:mm` queda igual. */
export function normalizarHora(hora: string): string {
  return hora.slice(0, 5)
}

export function esDiaSemana(valor: number): valor is DiaSemana {
  return Number.isInteger(valor) && valor >= 1 && valor <= 7
}

export function filaDeHorario(horario: HorarioClub): FilaHorario {
  return {
    clave: horario.id,
    categoriaId: horario.categoria.id,
    categoriaNombre: horario.categoria.nombre,
    diaSemana: horario.diaSemana,
    horaInicio: normalizarHora(horario.horaInicio),
    horaFin: normalizarHora(horario.horaFin),
    clubId: horario.club.id,
    clubNombre: horario.club.nombre,
  }
}

export function filaVacia(clave: string): FilaHorario {
  return {
    clave,
    categoriaId: '',
    categoriaNombre: '',
    diaSemana: '',
    horaInicio: '',
    horaFin: '',
    clubId: '',
    clubNombre: '',
  }
}

/**
 * Cuerpo del `PUT` en el mismo orden de las filas, para que `horarios[i]` del 400 apunte a la fila
 * que se ve. `clubId` solo va en una cancha `LIGA` (DEC-33); nunca `id` ni `esComodato`. Llámala
 * con filas ya validadas.
 */
export function peticionDeFilas(
  filas: readonly FilaHorario[],
  propietario: PropietarioCancha,
): HorarioClubPeticion[] {
  return filas.map((fila) => {
    if (fila.diaSemana === '') {
      throw new Error('Horario sin día: valida las filas antes de armar la petición.')
    }

    return {
      categoriaId: fila.categoriaId,
      diaSemana: fila.diaSemana,
      horaInicio: fila.horaInicio,
      horaFin: fila.horaFin,
      ...(propietario === 'LIGA' ? { clubId: fila.clubId } : {}),
    }
  })
}

/** ¿Las dos listas guardarían lo mismo? Compara los datos, no las claves locales. */
export function mismasFilas(a: readonly FilaHorario[], b: readonly FilaHorario[]): boolean {
  const datos = (filas: readonly FilaHorario[]) =>
    JSON.stringify(
      filas.map(({ categoriaId, diaSemana, horaInicio, horaFin, clubId }) => [
        categoriaId,
        diaSemana,
        horaInicio,
        horaFin,
        clubId,
      ]),
    )

  return datos(a) === datos(b)
}

function intervaloCompleto(fila: FilaHorario): boolean {
  return fila.diaSemana !== '' && fila.horaInicio !== '' && fila.horaFin !== '' && fila.horaInicio < fila.horaFin
}

/**
 * Pares de posiciones `[i, j]` (i < j) que se traslapan con la regla del servidor: mismo día, sin
 * importar la categoría, con intervalos semiabiertos (10–12 y 12–14 no chocan). Ignora las filas
 * incompletas o con inicio ≥ fin, que ya tienen su propio error.
 */
export function traslapes(filas: readonly FilaHorario[]): [number, number][] {
  const pares: [number, number][] = []

  filas.forEach((a, i) => {
    if (!intervaloCompleto(a)) {
      return
    }
    for (let j = i + 1; j < filas.length; j += 1) {
      const b = filas[j]
      if (
        intervaloCompleto(b) &&
        a.diaSemana === b.diaSemana &&
        a.horaInicio < b.horaFin &&
        b.horaInicio < a.horaFin
      ) {
        pares.push([i, j])
      }
    }
  })

  return pares
}

/** «Sábado 10:00–12:00» para decir con qué horario choca una fila. */
export function describirIntervalo(fila: FilaHorario): string {
  const dia = fila.diaSemana === '' ? '' : `${etiquetaDia[fila.diaSemana]} `

  return `${dia}${fila.horaInicio}–${fila.horaFin}`
}

function agregarTraslape(errores: ErroresFilas, fila: FilaHorario, otra: FilaHorario, posicionOtra: number) {
  const mensaje = `Se traslapa con el horario ${posicionOtra + 1} (${describirIntervalo(otra)}).`
  const actuales = errores[fila.clave] ?? {}

  errores[fila.clave] = { ...actuales, fila: actuales.fila ? `${actuales.fila} ${mensaje}` : mensaje }
}

/**
 * Validación en el navegador antes del `PUT` (B-13.2): obligatorios (el club solo en `LIGA`),
 * inicio < fin y traslapes. Un traslape marca las DOS filas y cada una dice con cuál choca.
 * Devuelve `{}` si no hay errores.
 */
export function validarFilas(filas: readonly FilaHorario[], propietario: PropietarioCancha): ErroresFilas {
  const errores: ErroresFilas = {}

  for (const fila of filas) {
    const propios: ErroresFila = {}

    if (!fila.categoriaId) {
      propios.categoriaId = 'Elige la categoría.'
    }
    if (fila.diaSemana === '') {
      propios.diaSemana = 'Elige el día.'
    }
    if (!fila.horaInicio) {
      propios.horaInicio = 'Indica la hora de inicio.'
    }
    if (!fila.horaFin) {
      propios.horaFin = 'Indica la hora de fin.'
    } else if (fila.horaInicio && fila.horaInicio >= fila.horaFin) {
      propios.horaFin = 'El fin debe ser posterior al inicio.'
    }
    if (propietario === 'LIGA' && !fila.clubId) {
      propios.clubId = 'Elige el club que usa la cancha.'
    }

    if (Object.keys(propios).length > 0) {
      errores[fila.clave] = propios
    }
  }

  for (const [i, j] of traslapes(filas)) {
    agregarTraslape(errores, filas[i], filas[j], j)
    agregarTraslape(errores, filas[j], filas[i], i)
  }

  return errores
}

const PATRON_CAMPO = /^horarios\[(\d+)\]\.(\w+)$/

function esCampoHorario(valor: string): valor is CampoHorario {
  return (CAMPOS as readonly string[]).includes(valor)
}

/**
 * Lleva los `errores` del 400 `VALIDACION` a su fila por la posición enviada (`horarios[i].campo`).
 * Lo que no apunta a una fila y campo conocidos queda en `sinMapear` con su mensaje.
 */
export function erroresDeServidor(
  errores: readonly ErrorCampo[],
  filas: readonly FilaHorario[],
): { porFila: ErroresFilas; sinMapear: string[] } {
  const porFila: ErroresFilas = {}
  const sinMapear: string[] = []

  for (const { campo, mensaje } of errores) {
    const coincidencia = PATRON_CAMPO.exec(campo)
    const fila = coincidencia ? filas[Number(coincidencia[1])] : undefined
    const nombre = coincidencia?.[2]

    if (fila && nombre && esCampoHorario(nombre)) {
      porFila[fila.clave] = { ...porFila[fila.clave], [nombre]: mensaje }
    } else {
      sinMapear.push(mensaje)
    }
  }

  return { porFila, sinMapear }
}
