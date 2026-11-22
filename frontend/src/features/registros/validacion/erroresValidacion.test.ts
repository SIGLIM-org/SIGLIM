import { describe, expect, it } from 'vitest'
import { FOTO, error } from '@/features/registros/datosPruebaRegistros'
import { accionErrorCarga } from '@/features/registros/documentos/erroresCarga'
import {
  MENSAJE_SEPARACION,
  MENSAJE_YA_RESUELTO,
  accionErrorValidacion,
} from '@/features/registros/validacion/erroresValidacion'

describe('accionErrorValidacion (DEC-114)', () => {
  it('RF-017: texto propio y recarga todo', () => {
    expect(accionErrorValidacion(error(409, 'RF-017'))).toMatchObject({
      mensaje: MENSAJE_YA_RESUELTO,
      recargar: 'todo',
      soloLectura: false,
    })
  })

  it('RF-109 del documento: detail y recarga los requisitos', () => {
    expect(accionErrorValidacion(error(409, 'RF-109', 'Ya no está pendiente.'))).toMatchObject({
      mensaje: 'Ya no está pendiente.',
      recargar: 'requisitos',
    })
  })

  it('RF-003: detail y la pantalla queda de solo lectura', () => {
    expect(accionErrorValidacion(error(409, 'RF-003', 'Temporada cerrada.'))).toMatchObject({
      mensaje: 'Temporada cerrada.',
      soloLectura: true,
    })
  })

  it('VAL-022: texto propio sin código', () => {
    const { mensaje } = accionErrorValidacion(error(403, 'VAL-022', 'No puede.'))
    expect(mensaje).toBe(MENSAJE_SEPARACION)
    expect(mensaje).not.toMatch(/VAL-/)
  })

  it.each(['RF-013', 'VAL-025'])('422 %s: detail sin recargar', (codigo) => {
    expect(accionErrorValidacion(error(422, codigo, 'Causa.'))).toMatchObject({
      mensaje: 'Causa.',
      recargar: 'nada',
    })
  })

  it('DUPLICADO: detail y recarga', () => {
    expect(accionErrorValidacion(error(409, 'DUPLICADO', 'Ya existe.'))).toMatchObject({
      mensaje: 'Ya existe.',
      recargar: 'todo',
    })
  })

  it('VAL-001: detail con los faltantes de la extensión', () => {
    const accion = accionErrorValidacion(
      error(422, 'VAL-001', 'Faltan documentos.', {
        faltantes: [{ tipoDocumento: FOTO, cantidad: 1, cargados: 1, aprobados: 0 }],
      }),
    )
    expect(accion.mensaje).toBe('Faltan documentos.')
    expect(accion.faltantes).toEqual([{ tipoDocumentoId: 5, nombre: 'Fotografía', motivo: 'revisar' }])
  })

  it('cualquier otro código: el detail; sin ApiError, un texto genérico', () => {
    expect(accionErrorValidacion(error(400, 'VALIDACION', 'Falta el motivo.')).mensaje).toBe('Falta el motivo.')
    expect(accionErrorValidacion(new Error('red')).mensaje).toMatch(/intenta de nuevo/)
  })
})

describe('accionErrorCarga (DEC-114)', () => {
  it.each(['RF-017', 'RF-003'])('409 %s: detail, recarga todo y solo lectura', (codigo) => {
    expect(accionErrorCarga(error(409, codigo, 'Ya no admite cargas.'))).toEqual({
      lugar: 'general',
      mensaje: 'Ya no admite cargas.',
      recargar: 'todo',
      soloLectura: true,
    })
  })

  it.each(['DUPLICADO', 'RF-109'])('409 %s: detail y recarga los requisitos', (codigo) => {
    expect(accionErrorCarga(error(409, codigo, 'Cupo lleno.'))).toMatchObject({
      lugar: 'general',
      recargar: 'requisitos',
    })
  })

  it.each(['RF-108', 'REFERENCIA_INVALIDA'])('422 %s: detail', (codigo) => {
    expect(accionErrorCarga(error(422, codigo, 'No lo pide.'))).toMatchObject({
      lugar: 'general',
      mensaje: 'No lo pide.',
      recargar: 'nada',
    })
  })

  it.each([
    [413, 'ARCHIVO_DEMASIADO_GRANDE'],
    [415, 'TIPO_ARCHIVO_NO_PERMITIDO'],
  ])('%i %s: error del archivo', (status, codigo) => {
    expect(accionErrorCarga(error(status, codigo, 'Archivo inválido.'))).toMatchObject({
      lugar: 'archivo',
      mensaje: 'Archivo inválido.',
    })
  })
})
