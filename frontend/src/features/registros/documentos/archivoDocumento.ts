/*
 * Límites de un documento de registro (convenciones de API §6.4 y §7, DEC-112). El backend los
 * vuelve a validar por firma binaria y su 413/415 manda; aquí se revisan antes de enviar porque, con
 * un archivo mucho mayor, el servidor corta la conexión antes de que se pueda leer el 413. Es una
 * copia propia de M-04: tesorería tiene la suya y no se modifica.
 */

export const TAMANO_MAXIMO_DOCUMENTO = 5 * 1024 * 1024

const TIPOS_DOCUMENTO = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']

const EXTENSIONES_DOCUMENTO = ['.jpg', '.jpeg', '.png', '.webp', '.pdf']

/** Valor del `accept` del input (DEC-112). */
export const ACEPTA_DOCUMENTO = TIPOS_DOCUMENTO.join(',')

export const AYUDA_ARCHIVO = 'JPEG, PNG, WebP o PDF de hasta 5 MB.'

/** Revisa el archivo en el navegador; devuelve el mensaje de error o `undefined`. */
export function validarArchivoDocumento(archivo: File): string | undefined {
  if (archivo.size === 0) {
    return 'El archivo está vacío. Elige otro.'
  }
  if (archivo.size > TAMANO_MAXIMO_DOCUMENTO) {
    return 'El archivo pesa más de 5 MB. Elige uno más ligero o toma la foto con menor resolución.'
  }

  // Si el navegador no informa el tipo, se decide por la extensión.
  const nombre = archivo.name.toLowerCase()
  const permitido = archivo.type
    ? TIPOS_DOCUMENTO.includes(archivo.type)
    : EXTENSIONES_DOCUMENTO.some((extension) => nombre.endsWith(extension))

  return permitido ? undefined : 'El archivo debe ser JPEG, PNG, WebP o PDF.'
}

/** ¿Se puede previsualizar como imagen? Por tipo o, si falta, por extensión. */
export function esArchivoImagen(archivo: File): boolean {
  if (archivo.type) {
    return archivo.type.startsWith('image/')
  }
  return /\.(jpe?g|png|webp)$/i.test(archivo.name)
}

/** ¿El documento guardado es una imagen? Lo decide el `tipoMime` detectado por el backend. */
export function esMimeImagen(tipoMime: string): boolean {
  return tipoMime.startsWith('image/')
}

const formatoTamano = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 1 })

/** «820 KB» o «2.4 MB». */
export function tamanoLegible(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${formatoTamano.format(Math.max(1, Math.round(bytes / 1024)))} KB`
  }

  return `${formatoTamano.format(bytes / (1024 * 1024))} MB`
}
