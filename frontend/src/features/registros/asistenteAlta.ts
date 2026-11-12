/**
 * Lógica pura del asistente de alta de jugador (F-18, H-09.3): pasos, reglas para avanzar, CURP,
 * minoría, equipos elegibles, parentesco y traducción de cada `ApiError` a la acción de DEC-107.
 * Sin React para probarla aparte.
 */

import { curpValida } from '@/features/organizacion/directiva'
import type { Equipo, PersonaDetalle } from '@/features/organizacion/tipos'
import type { PersonaConMinoria, RegistroJugador } from '@/features/registros/tipos'
import { ApiError } from '@/lib/api/ApiError'

// ── Pasos (DEC-103) ─────────────────────────────────────────────────────────────────────────────

export type NumeroPaso = 1 | 2 | 3 | 4

export type DescripcionPaso = {
  numero: NumeroPaso
  titulo: string
  descripcion: string
}

/**
 * Orden de F-18 según DEC-103: los documentos se cargan sobre un registro que ya existe, así que
 * van al final. Los pasos 1 a 3 son el asistente (H-09.3); el 4 es la página de documentos del
 * registro (H-10.3, DEC-111), que reutiliza esta lista en su indicador.
 */
export const PASOS: readonly DescripcionPaso[] = [
  { numero: 1, titulo: 'Identidad', descripcion: 'CURP del jugador' },
  { numero: 2, titulo: 'Datos y tutor', descripcion: 'Equipo y tutela' },
  { numero: 3, titulo: 'Consentimiento', descripcion: 'Aviso de privacidad' },
  { numero: 4, titulo: 'Documentos', descripcion: 'Expediente del registro' },
]

// ── CURP y minoría ──────────────────────────────────────────────────────────────────────────────

/** Mayúsculas y sin espacios, como la normaliza el backend antes de validar (DEC-10). */
export function normalizarCurp(valor: string): string {
  return valor.toUpperCase().replace(/\s/g, '')
}

/** CURP con el formato de `ck_personas_curp`, ya normalizada. */
export function curpCompleta(valor: string): boolean {
  return curpValida(normalizarCurp(valor))
}

/**
 * ¿Es menor de edad en `hoy` (`AAAA-MM-DD` en `America/Mexico_City`)? Misma regla que
 * `es_menor`: cumple 18 el día de su cumpleaños; quien nació el 29 de febrero los cumple el 1 de
 * marzo en un año no bisiesto. Una fecha incompleta no se considera menor.
 */
export function esMenorEnFecha(fechaNacimiento: string, hoy: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fechaNacimiento) || !/^\d{4}-\d{2}-\d{2}$/.test(hoy)) {
    return false
  }

  const limite = `${String(Number(hoy.slice(0, 4)) - 18).padStart(4, '0')}${hoy.slice(4)}`

  return fechaNacimiento > limite
}

// ── Equipos (DEC-108) ───────────────────────────────────────────────────────────────────────────

export type GrupoEquipos = {
  clave: string
  /** «Temporada · Categoría», etiqueta del `<optgroup>`. */
  etiqueta: string
  equipos: Equipo[]
}

/**
 * Equipos en los que se puede dar de alta: activos y de una temporada que no esté `CERRADA`
 * (RF-003, RF-013), agrupados por temporada y categoría en el orden en que llegan del backend.
 */
export function agruparEquipos(equipos: readonly Equipo[]): GrupoEquipos[] {
  const grupos = new Map<string, GrupoEquipos>()

  for (const equipo of equipos) {
    if (equipo.estado !== 'ACTIVO' || equipo.temporada.estado === 'CERRADA') {
      continue
    }

    const clave = `${equipo.temporada.id}|${equipo.categoria.id}`
    const grupo = grupos.get(clave) ?? {
      clave,
      etiqueta: `${equipo.temporada.nombre} · ${equipo.categoria.nombre}`,
      equipos: [],
    }

    grupo.equipos.push(equipo)
    grupos.set(clave, grupo)
  }

  return [...grupos.values()]
}

// ── Parentesco (DEC-105) ────────────────────────────────────────────────────────────────────────

/*
 * [PROPUESTA] El ERS no fija el catálogo de parentescos; DEC-105 propone estas cuatro opciones y,
 * con «Otro», un texto libre. La BD solo pide hasta 40 caracteres (DEC-82).
 */
export const PARENTESCOS = ['Madre', 'Padre', 'Tutor legal', 'Otro'] as const

export type OpcionParentesco = (typeof PARENTESCOS)[number]

export const MAXIMO_PARENTESCO = 40

/** Texto que se envía como parentesco, recortado; `undefined` si falta. */
export function parentescoFinal(opcion: OpcionParentesco | '', otro: string): string | undefined {
  if (!opcion) {
    return undefined
  }
  if (opcion !== 'Otro') {
    return opcion
  }

  const texto = otro.trim()

  return texto ? texto.slice(0, MAXIMO_PARENTESCO) : undefined
}

// ── Formulario de persona (DEC-62, DEC-84) ─────────────────────────────────────────────────────

export type DatosPersona = {
  nombre: string
  apellidoPaterno: string
  apellidoMaterno: string
  fechaNacimiento: string
  correo: string
  telefono: string
}

export const DATOS_PERSONA_VACIOS: DatosPersona = {
  nombre: '',
  apellidoPaterno: '',
  apellidoMaterno: '',
  fechaNacimiento: '',
  correo: '',
  telefono: '',
}

export type CampoPersona = 'curp' | keyof DatosPersona

export type ErroresPersona = Partial<Record<CampoPersona, string>>

/** Mismo patrón que `ck_personas_correo`. */
const FORMATO_CORREO = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

/** Obligatorios y formatos que el backend vuelve a validar (DEC-84). */
export function validarDatosPersona(curp: string, datos: DatosPersona, hoy: string): ErroresPersona {
  const errores: ErroresPersona = {}

  if (!curpCompleta(curp)) {
    errores.curp = 'La CURP debe tener 18 caracteres y el formato oficial.'
  }
  if (!datos.nombre.trim()) {
    errores.nombre = 'Escribe el nombre.'
  }
  if (!datos.apellidoPaterno.trim()) {
    errores.apellidoPaterno = 'Escribe el apellido paterno.'
  }
  if (!datos.fechaNacimiento) {
    errores.fechaNacimiento = 'Indica la fecha de nacimiento.'
  } else if (datos.fechaNacimiento > hoy) {
    errores.fechaNacimiento = 'La fecha de nacimiento no puede ser futura.'
  }
  if (datos.correo.trim() && !FORMATO_CORREO.test(datos.correo.trim())) {
    errores.correo = 'Revisa el correo, por ejemplo: nombre@dominio.mx.'
  }

  return errores
}

/** Cuerpo de `POST /personas`: textos recortados y opcionales vacíos omitidos. */
export function peticionPersona(curp: string, datos: DatosPersona) {
  const opcional = (valor: string) => (valor.trim() ? valor.trim() : undefined)

  return {
    curp: normalizarCurp(curp),
    nombre: datos.nombre.trim(),
    apellidoPaterno: datos.apellidoPaterno.trim(),
    apellidoMaterno: opcional(datos.apellidoMaterno),
    fechaNacimiento: datos.fechaNacimiento,
    correo: opcional(datos.correo),
    telefono: opcional(datos.telefono),
  }
}

/** Persona recién creada como identidad del asistente; adulta porque así se validó antes. */
export function personaCreada(detalle: PersonaDetalle, esMenor: boolean): PersonaConMinoria {
  return {
    id: detalle.id,
    curp: detalle.curp,
    nombre: detalle.nombre,
    apellidoPaterno: detalle.apellidoPaterno,
    apellidoMaterno: detalle.apellidoMaterno,
    esMenor,
  }
}

/** Nombre completo con los apellidos que existan. */
export function nombreCompleto(
  persona: Pick<PersonaConMinoria, 'nombre' | 'apellidoPaterno' | 'apellidoMaterno'>,
): string {
  return [persona.nombre, persona.apellidoPaterno, persona.apellidoMaterno]
    .map((parte) => parte?.trim())
    .filter(Boolean)
    .join(' ')
}

// ── Finalidades del aviso (DEC-106) ────────────────────────────────────────────────────────────

/** Nombres de las finalidades conocidas, con su ortografía (los códigos no llevan acentos). */
const ETIQUETAS_FINALIDAD: Readonly<Record<string, string>> = {
  REGISTRO_DEPORTIVO: 'Registro deportivo',
  DIFUSION: 'Difusión',
}

/**
 * Nombre visible de una finalidad: el del diccionario si se conoce y, si no, el código legible
 * (`OTRA_FINALIDAD` → «Otra finalidad»). El código viaja tal cual al backend.
 */
export function etiquetaFinalidad(codigo: string): string {
  const conocida = ETIQUETAS_FINALIDAD[codigo]
  if (conocida) {
    return conocida
  }

  const texto = codigo.replace(/_/g, ' ').trim().toLowerCase()

  return texto ? texto.charAt(0).toUpperCase() + texto.slice(1) : codigo
}

// ── Errores (DEC-107) ──────────────────────────────────────────────────────────────────────────

/** Escritura del asistente que falló; decide dónde se muestra el mensaje. */
export type ContextoEscritura = 'persona' | 'tutor' | 'tutela' | 'consentimiento' | 'alta'

export type AccionError =
  /** 400 con `errores`: se marcan los campos del formulario de la persona o del tutor. */
  | { tipo: 'campos'; contexto: ContextoEscritura; errores: Record<string, string>; mensaje: string }
  /** Sección del tutor del paso 2 (VAL-025 TUTOR, VAL-022 y RF-106 de la tutela). */
  | { tipo: 'tutor'; mensaje: string }
  /** Paso 3 con «Cambiar equipo» (VAL-017, VAL-026, RN-039, RF-101, RF-003, RF-013, 404). */
  | { tipo: 'cambiarEquipo'; mensaje: string; club?: string; equipo?: string }
  /** VAL-025 CONSENTIMIENTO: se vuelve a pedir en el paso 3. */
  | { tipo: 'consentimiento'; mensaje: string }
  /** RF-097: el aviso cambió; se recarga y se pide marcar de nuevo. */
  | { tipo: 'recargarAviso'; mensaje: string }
  /** `detail` en el paso donde ocurrió (VAL-015, RF-106 fuera de la tutela, el resto). */
  | { tipo: 'general'; contexto: ContextoEscritura; mensaje: string }

const CODIGOS_CAMBIAR_EQUIPO = new Set([
  'VAL-017',
  'VAL-026',
  'RN-039',
  'RF-101',
  'RF-003',
  'RF-013',
  'NO_ENCONTRADO',
])

/** Texto de una extensión del problema si es una cadena no vacía. */
function extensionTexto(error: ApiError, nombre: string): string | undefined {
  const valor = error.problema.extensiones?.[nombre]

  return typeof valor === 'string' && valor.trim() ? valor : undefined
}

/**
 * Qué hace la interfaz con un error de una escritura (DEC-107). Decide por `codigo` y estado, nunca
 * por el texto (DEC-63); el mensaje es el `detail` del backend, salvo VAL-022 de la tutela, que
 * tiene el texto propio aprobado. `RF-010` del alta de una persona no llega aquí: se reutiliza el
 * `personaId` de la extensión.
 */
export function accionDeError(contexto: ContextoEscritura, error: unknown): AccionError {
  if (!(error instanceof ApiError)) {
    return {
      tipo: 'general',
      contexto,
      mensaje: 'No se pudo completar la operación. Revisa tu conexión e intenta de nuevo.',
    }
  }

  const { codigo, status, detail } = error.problema

  if (status === 400 && error.errores.length > 0) {
    return {
      tipo: 'campos',
      contexto,
      errores: Object.fromEntries(error.errores.map(({ campo, mensaje }) => [campo, mensaje])),
      mensaje: detail,
    }
  }

  if (codigo === 'VAL-025') {
    if (contexto === 'alta' && extensionTexto(error, 'requisito') === 'CONSENTIMIENTO') {
      return { tipo: 'consentimiento', mensaje: detail }
    }
    return { tipo: 'tutor', mensaje: detail }
  }

  if (contexto === 'tutela' && codigo === 'VAL-022') {
    return {
      tipo: 'tutor',
      mensaje:
        'No puedes registrarte como tutor desde tu propia cuenta. Pide a otro integrante de la directiva o a la liga que registre la tutela.',
    }
  }

  if ((contexto === 'tutela' || contexto === 'tutor') && codigo === 'RF-106') {
    return { tipo: 'tutor', mensaje: detail }
  }

  if (contexto === 'consentimiento' && codigo === 'RF-097') {
    return { tipo: 'recargarAviso', mensaje: detail }
  }

  if (contexto === 'alta' && (CODIGOS_CAMBIAR_EQUIPO.has(codigo) || status === 404)) {
    return {
      tipo: 'cambiarEquipo',
      mensaje: detail,
      club: codigo === 'VAL-017' ? extensionTexto(error, 'club') : undefined,
      equipo: codigo === 'VAL-017' ? extensionTexto(error, 'equipo') : undefined,
    }
  }

  return { tipo: 'general', contexto, mensaje: detail }
}

/** `personaId` de un `409 RF-010` (la CURP ya existe, DEC-84), para reutilizarlo. */
export function personaExistenteDe(error: unknown): string | undefined {
  if (error instanceof ApiError && error.codigo === 'RF-010') {
    return extensionTexto(error, 'personaId')
  }
  return undefined
}

/** La tutela ya existía (`409 DUPLICADO`, DEC-103): se sigue sin repetirla. */
export function esTutelaDuplicada(error: unknown): boolean {
  return error instanceof ApiError && error.codigo === 'DUPLICADO'
}

// ── Estado del asistente (DEC-103) ─────────────────────────────────────────────────────────────

/** Errores de los campos y faltantes que se marcan antes o después de escribir. */
export type ErroresAsistente = {
  /** CURP del paso 1 y datos del jugador nuevo. */
  jugador: ErroresPersona
  /** CURP y datos del tutor. */
  tutor: ErroresPersona
  equipo?: string
  parentesco?: string
  /** El menor no tiene tutor identificado o el tutor no sirve (menor de edad, misma persona). */
  tutorFaltante?: string
}

export const SIN_ERRORES: ErroresAsistente = { jugador: {}, tutor: {} }

export type EstadoAsistente = {
  paso: NumeroPaso
  /** Tras el primer cambio de paso, el título del paso recibe el foco. */
  navego: boolean
  curp: string
  /** Identidad conocida del jugador: encontrada por CURP o ya creada. */
  jugador: PersonaConMinoria | null
  /** La CURP no existe (404): se capturan sus datos en el paso 2. */
  jugadorNuevo: boolean
  datosJugador: DatosPersona
  equipoId: string
  tutorCurp: string
  tutor: PersonaConMinoria | null
  tutorNuevo: boolean
  datosTutor: DatosPersona
  parentesco: OpcionParentesco | ''
  parentescoOtro: string
  // Escrituras hechas: no se repiten al volver o reintentar.
  personaCreada: boolean
  tutorCreado: boolean
  tutelaRegistrada: boolean
  consentimientoId: string | null
  registro: RegistroJugador | null
  /** Mensaje de bloqueo vigente (DEC-107). */
  bloqueo: AccionError | null
  errores: ErroresAsistente
}

export const ESTADO_INICIAL: EstadoAsistente = {
  paso: 1,
  navego: false,
  curp: '',
  jugador: null,
  jugadorNuevo: false,
  datosJugador: DATOS_PERSONA_VACIOS,
  equipoId: '',
  tutorCurp: '',
  tutor: null,
  tutorNuevo: false,
  datosTutor: DATOS_PERSONA_VACIOS,
  parentesco: '',
  parentescoOtro: '',
  personaCreada: false,
  tutorCreado: false,
  tutelaRegistrada: false,
  consentimientoId: null,
  registro: null,
  bloqueo: null,
  errores: SIN_ERRORES,
}

export type AccionAsistente =
  | { tipo: 'actualizar'; cambios: Partial<EstadoAsistente> }
  | { tipo: 'ir'; paso: NumeroPaso; bloqueo?: AccionError | null }
  | { tipo: 'reiniciar' }

export function reducirAsistente(
  estado: EstadoAsistente,
  accion: AccionAsistente,
): EstadoAsistente {
  switch (accion.tipo) {
    case 'actualizar':
      return { ...estado, ...accion.cambios }
    case 'ir':
      return {
        ...estado,
        paso: accion.paso,
        navego: true,
        bloqueo: accion.bloqueo ?? null,
        errores: SIN_ERRORES,
      }
    case 'reiniciar':
      return { ...ESTADO_INICIAL, navego: true }
  }
}

/** ¿Ya se escribió algo? Entonces «Cancelar» confirma y el paso 1 queda cerrado. */
export function huboEscrituras(estado: EstadoAsistente): boolean {
  return (
    estado.personaCreada ||
    estado.tutorCreado ||
    estado.tutelaRegistrada ||
    estado.consentimientoId !== null ||
    estado.registro !== null
  )
}

/**
 * ¿El jugador es menor hoy? La persona conocida trae `esMenor` del backend (DEC-104); una nueva se
 * calcula con su fecha en la zona de negocio y se confirma tras crearla.
 */
export function jugadorEsMenor(estado: EstadoAsistente, hoy: string): boolean {
  if (estado.jugador) {
    return estado.jugador.esMenor
  }
  return estado.jugadorNuevo && esMenorEnFecha(estado.datosJugador.fechaNacimiento, hoy)
}
