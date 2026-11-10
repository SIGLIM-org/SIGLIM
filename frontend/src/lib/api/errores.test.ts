import { describe, expect, it } from 'vitest'
import { ApiError } from '@/lib/api/ApiError'
import { esNoEncontrado, esSinPermiso, reintentarConsulta } from '@/lib/api/errores'

function error(status: number) {
  return new ApiError({ title: 'Error', status, detail: 'Detalle', codigo: 'CODIGO' })
}

describe('errores de la API', () => {
  it('H-02.4: reconoce 403 y 404 solo en un ApiError', () => {
    expect(esSinPermiso(error(403))).toBe(true)
    expect(esSinPermiso(error(404))).toBe(false)
    expect(esNoEncontrado(error(404))).toBe(true)
    expect(esNoEncontrado(new Error('otro'))).toBe(false)
  })

  it.each([400, 401, 403, 404, 409])('no reintenta un %i', (status) => {
    expect(reintentarConsulta(0, error(status))).toBe(false)
  })

  it('reintenta un 503 una sola vez', () => {
    expect(reintentarConsulta(0, error(503))).toBe(true)
    expect(reintentarConsulta(1, error(503))).toBe(false)
  })

  it('reintenta una vez un error que no viene de la API', () => {
    expect(reintentarConsulta(0, new Error('red'))).toBe(true)
    expect(reintentarConsulta(1, new Error('red'))).toBe(false)
  })
})
