import { describe, expect, it } from 'vitest'
import { error } from '@/features/registros/datosPruebaRegistros'
import {
  MENSAJE_NO_ENCONTRADO,
  accionErrorBaja,
} from '@/features/registros/plantilla/erroresBaja'

describe('accionErrorBaja (DEC-129)', () => {
  it('DEC-129: 409 RF-017 muestra el detail en la página y recarga', () => {
    expect(accionErrorBaja(error(409, 'RF-017', 'El registro ya está dado de baja.'))).toEqual({
      mensaje: 'El registro ya está dado de baja.',
      lugar: 'pagina',
      recargar: true,
    })
  })

  it('DEC-129: 409 RF-003 muestra el detail en la página y recarga', () => {
    expect(accionErrorBaja(error(409, 'RF-003', 'La temporada está cerrada.'))).toEqual({
      mensaje: 'La temporada está cerrada.',
      lugar: 'pagina',
      recargar: true,
    })
  })

  it('DEC-129: 404 usa un texto propio, en la página, y recarga', () => {
    expect(accionErrorBaja(error(404, 'NO_ENCONTRADO', 'No existe.'))).toEqual({
      mensaje: MENSAJE_NO_ENCONTRADO,
      lugar: 'pagina',
      recargar: true,
    })
  })

  it('DEC-129: 403 muestra el detail en la página sin recargar', () => {
    expect(accionErrorBaja(error(403, 'VAL-015', 'No tienes permiso.'))).toEqual({
      mensaje: 'No tienes permiso.',
      lugar: 'pagina',
      recargar: false,
    })
  })

  it('DEC-129: 400 RN-021 deja el detail en el modal', () => {
    expect(accionErrorBaja(error(400, 'RN-021', 'Escribe un motivo más largo.'))).toEqual({
      mensaje: 'Escribe un motivo más largo.',
      lugar: 'modal',
      recargar: false,
    })
  })

  it('DEC-129: 400 VALIDACION deja el detail en el modal', () => {
    expect(accionErrorBaja(error(400, 'VALIDACION', 'El motivo es muy largo.'))).toEqual({
      mensaje: 'El motivo es muy largo.',
      lugar: 'modal',
      recargar: false,
    })
  })

  it('DEC-129: otro ApiError sin detail usa el texto genérico en el modal', () => {
    const accion = accionErrorBaja(error(500, 'ERROR_INTERNO', ''))
    expect(accion).toMatchObject({ lugar: 'modal', recargar: false })
    expect(accion.mensaje).toMatch(/No se pudo dar de baja el registro/)
    expect(accion.mensaje).not.toMatch(/ERROR_INTERNO/)
  })

  it('DEC-129: un error de red pide revisar la conexión, en el modal', () => {
    expect(accionErrorBaja(new TypeError('Failed to fetch'))).toEqual({
      mensaje: 'No se pudo dar de baja el registro. Revisa tu conexión e intenta de nuevo.',
      lugar: 'modal',
      recargar: false,
    })
  })
})
