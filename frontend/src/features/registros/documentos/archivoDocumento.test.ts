import { describe, expect, it } from 'vitest'
import { archivo } from '@/features/registros/datosPruebaRegistros'
import {
  TAMANO_MAXIMO_DOCUMENTO,
  esArchivoImagen,
  tamanoLegible,
  validarArchivoDocumento,
} from '@/features/registros/documentos/archivoDocumento'

describe('archivoDocumento (DEC-112)', () => {
  it('RF-108: acepta una imagen o un PDF de hasta 5 MB', () => {
    expect(validarArchivoDocumento(archivo('foto.jpg', 'image/jpeg', TAMANO_MAXIMO_DOCUMENTO))).toBeUndefined()
    expect(validarArchivoDocumento(archivo('acta.pdf', 'application/pdf'))).toBeUndefined()
  })

  it('RF-108: rechaza un archivo de más de 5 MB', () => {
    expect(validarArchivoDocumento(archivo('foto.png', 'image/png', TAMANO_MAXIMO_DOCUMENTO + 1))).toMatch(
      /más de 5 MB/,
    )
  })

  it('RF-108: rechaza un tipo no permitido', () => {
    expect(validarArchivoDocumento(archivo('acta.docx', 'application/msword'))).toMatch(
      /JPEG, PNG, WebP o PDF/,
    )
  })

  it('sin type, decide por la extensión', () => {
    expect(validarArchivoDocumento(archivo('foto.WEBP', ''))).toBeUndefined()
    expect(validarArchivoDocumento(archivo('nota.txt', ''))).toMatch(/JPEG, PNG, WebP o PDF/)
    expect(esArchivoImagen(archivo('foto.jpeg', ''))).toBe(true)
    expect(esArchivoImagen(archivo('acta.pdf', ''))).toBe(false)
  })

  it('rechaza un archivo vacío', () => {
    expect(validarArchivoDocumento(archivo('foto.jpg', 'image/jpeg', 0))).toMatch(/vacío/)
  })

  it('muestra el tamaño en KB o MB', () => {
    expect(tamanoLegible(183204)).toBe('179 KB')
    expect(tamanoLegible(2.5 * 1024 * 1024)).toBe('2.5 MB')
  })
})
