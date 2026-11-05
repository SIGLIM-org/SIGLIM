/**
 * Reglas y mensajes comunes de las pantallas de acceso (UI-001, RNF-013, RNF-014).
 *
 * Las contraseñas las administra Supabase Auth: SIGLIM nunca las recibe ni las
 * almacena (ADR-0003). Aquí solo se valida lo que el navegador puede comprobar
 * antes de enviar el formulario y se traducen los errores de Supabase a mensajes
 * en español.
 */

/** Política de contraseñas de ADR-0003 (PEN-030), configurada también en Supabase Auth. */
export const LONGITUD_MINIMA_CONTRASENA = 15
export const LONGITUD_MAXIMA_CONTRASENA = 72

/** Forma mínima de un error de Supabase Auth; evita depender de su clase interna. */
type ErrorAutenticacion = {
  status?: number
  code?: string
}

/** Destino interno permitido después de iniciar sesión; nunca acepta una URL externa. */
export function destinoSeguro(desde: unknown): string {
  if (typeof desde !== 'string') {
    return '/app'
  }

  if (!desde.startsWith('/') || desde.startsWith('//') || desde.startsWith('/\\')) {
    return '/app'
  }

  return desde
}

/** Valida el formato del correo antes de llamar a Supabase Auth. */
export function correoValido(correo: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo.trim())
}

/**
 * Fallas del servicio de autenticación: un 5xx o un 404 (URL de Supabase Auth
 * mal configurada) no son credenciales incorrectas (DEF-01).
 */
const MENSAJE_SERVICIO_NO_DISPONIBLE =
  'El servicio de acceso no está disponible en este momento. Intenta de nuevo más tarde; si continúa, contacta al administrador de la Liga.'

/**
 * Mensaje genérico de acceso fallido: no revela si el correo existe o si la
 * contraseña es la incorrecta (criterio de aceptación 2 de H-01, VAL-015). Los
 * errores del servidor o de configuración se informan como tales para no
 * culpar al usuario ni ocultar la causa real (DEF-01).
 */
export function mensajeDeErrorDeAcceso(error: ErrorAutenticacion): string {
  if (error.status === 429) {
    return 'Demasiados intentos. Espera unos minutos antes de volver a intentar.'
  }

  if (error.status === undefined || error.status === 0) {
    return 'No se pudo conectar con el servicio. Revisa tu conexión e intenta de nuevo.'
  }

  if (error.status === 404 || error.status >= 500) {
    return MENSAJE_SERVICIO_NO_DISPONIBLE
  }

  return 'Correo o contraseña incorrectos.'
}

/** Error al pedir el correo de recuperación; tampoco revela si la cuenta existe. */
export function mensajeDeErrorDeRecuperacion(error: ErrorAutenticacion): string {
  if (error.status === 429) {
    return 'Demasiados intentos. Espera unos minutos antes de volver a intentar.'
  }

  if (error.status === undefined || error.status === 0) {
    return 'No se pudo conectar con el servicio. Revisa tu conexión e intenta de nuevo.'
  }

  return 'No pudimos enviar el enlace en este momento. Intenta de nuevo más tarde.'
}

/** Error al guardar la contraseña nueva en Supabase Auth. */
export function mensajeDeErrorDeContrasena(error: ErrorAutenticacion): string {
  if (error.status === 429) {
    return 'Demasiados intentos. Espera unos minutos antes de volver a intentar.'
  }

  if (error.code === 'same_password') {
    return 'La nueva contraseña debe ser diferente a la actual.'
  }

  if (error.code === 'weak_password') {
    return `La contraseña no cumple la política de seguridad. Usa al menos ${LONGITUD_MINIMA_CONTRASENA} caracteres.`
  }

  if (error.status === undefined || error.status === 0) {
    return 'No se pudo conectar con el servicio. Revisa tu conexión e intenta de nuevo.'
  }

  return 'No pudimos actualizar la contraseña. Intenta de nuevo.'
}
