import { describe, expect, it } from 'vitest'
import { ACTA, FOTO, documento, error, requisito } from '@/features/registros/datosPruebaRegistros'
import {
  faltantesDeError,
  faltantesDeRequisitos,
  motivoFaltante,
} from '@/features/registros/validacion/faltantes'

describe('faltantes (VAL-001, DEC-114)', () => {
  it('«Falta cargarlo» si hay menos cargados que la cantidad; un rechazado sin reemplazar cuenta', () => {
    expect(motivoFaltante({ tipoDocumento: FOTO, cantidad: 2, cargados: 1, aprobados: 1 })).toBe('cargar')
    const rechazado = requisito({ documentos: [documento({ estadoValidacion: 'RECHAZADO' })] })
    expect(motivoFaltante(rechazado)).toBe('cargar')
  })

  it('«Falta revisarlo» si están cargados pero no todos aprobados', () => {
    expect(motivoFaltante({ tipoDocumento: FOTO, cantidad: 1, cargados: 1, aprobados: 0 })).toBe('revisar')
  })

  it('un requisito completo y aprobado no falta', () => {
    expect(motivoFaltante({ tipoDocumento: FOTO, cantidad: 1, cargados: 1, aprobados: 1 })).toBeUndefined()
  })

  it('lista los faltantes de los requisitos en su orden', () => {
    const lista = [
      requisito({ tipoDocumento: ACTA, documentos: [documento({ estadoValidacion: 'APROBADO' })] }),
      requisito({ tipoDocumento: FOTO, documentos: [documento()] }),
    ]

    expect(faltantesDeRequisitos(lista)).toEqual([
      { tipoDocumentoId: FOTO.id, nombre: 'Fotografía', motivo: 'revisar' },
    ])
  })

  it('lee la extensión faltantes del 422 VAL-001 con guarda de tipo', () => {
    const conFaltantes = error(422, 'VAL-001', 'Faltan documentos.', {
      faltantes: [
        { tipoDocumento: ACTA, cantidad: 1, cargados: 0, aprobados: 0 },
        { tipoDocumento: FOTO, cantidad: 2, cargados: 2, aprobados: 1 },
        { basura: true },
      ],
    })

    expect(faltantesDeError(conFaltantes)).toEqual([
      { tipoDocumentoId: ACTA.id, nombre: 'Acta de nacimiento', motivo: 'cargar' },
      { tipoDocumentoId: FOTO.id, nombre: 'Fotografía', motivo: 'revisar' },
    ])
    expect(faltantesDeError(error(422, 'VAL-001', 'x', { faltantes: 'no es lista' }))).toEqual([])
    expect(faltantesDeError(error(409, 'RF-017'))).toEqual([])
  })
})
