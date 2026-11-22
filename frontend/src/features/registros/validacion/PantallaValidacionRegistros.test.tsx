import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ACTA,
  detalle,
  documento,
  enlace,
  error,
  fila,
  pagina,
  permisos,
  requisito,
  requisitos,
} from '@/features/registros/datosPruebaRegistros'
import type { RegistroJugadorDetalle, RequisitosRegistro } from '@/features/registros/tipos'
import { PantallaValidacionRegistros } from '@/features/registros/validacion/PantallaValidacionRegistros'
import type { Permiso } from '@/features/seguridad/tipos'

const { get, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Secretario', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const VALIDAR_REGISTRO = 'registros.registro:VALIDAR'
const CONSULTAR_REGISTRO = 'registros.registro:CONSULTAR'
const CONSULTAR_DOCUMENTO = 'registros.documento:CONSULTAR'
const VALIDAR_DOCUMENTO = 'registros.documento:VALIDAR'

/** Acta aprobada y fotografía por revisar: falta revisar una. */
const PENDIENTES = requisitos([
  requisito({ tipoDocumento: ACTA, documentos: [documento({ id: 'doc-acta', tipoDocumento: ACTA, estadoValidacion: 'APROBADO' })] }),
  requisito({ documentos: [documento()] }),
])

const LISTOS = requisitos([
  requisito({ tipoDocumento: ACTA, documentos: [documento({ id: 'doc-acta', tipoDocumento: ACTA, estadoValidacion: 'APROBADO' })] }),
  requisito({ documentos: [documento({ estadoValidacion: 'APROBADO' })] }),
])

let servidor: {
  registro: RegistroJugadorDetalle
  requisitos: RequisitosRegistro
  validacion: unknown
}

function responder(cambios: Partial<typeof servidor> = {}) {
  servidor = { registro: detalle(), requisitos: PENDIENTES, validacion: detalle({ estado: 'ACTIVO' }), ...cambios }

  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('clubes?')) {
      return { ...pagina([{ id: 'c1', nombre: 'Club Halcones' }]), tamano: 100 }
    }
    if (ruta.startsWith('registros-jugador?')) {
      return pagina([fila()])
    }
    if (ruta === 'registros-jugador/reg-1') {
      return servidor.registro
    }
    if (ruta === 'registros-jugador/reg-1/requisitos') {
      return servidor.requisitos
    }
    if (ruta.endsWith('/enlace')) {
      return enlace()
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })
  post.mockImplementation(async () => {
    if (servidor.validacion instanceof Error) {
      throw servidor.validacion
    }
    return servidor.validacion
  })
}

function montar(ruta = '/app/organizacion/registros') {
  const enrutador = createMemoryRouter(
    [{ path: '/app/organizacion/registros/:registroId?', element: <PantallaValidacionRegistros /> }],
    { initialEntries: [ruta] },
  )

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

const rutasGet = () => get.mock.calls.map(([ruta]) => String(ruta))

describe('PantallaValidacionRegistros (F-19, H-10.3)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    sesion.yo.permisos = permisos(VALIDAR_REGISTRO, CONSULTAR_REGISTRO, CONSULTAR_DOCUMENTO, VALIDAR_DOCUMENTO)
    responder()
  })

  it('VAL-015: sin registros.registro:VALIDAR muestra la 403 y no pide datos', () => {
    sesion.yo.permisos = permisos(CONSULTAR_REGISTRO)
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('DEC-111: la primera petición pide los pendientes del más antiguo al más reciente', async () => {
    montar()

    expect(await screen.findByText('Ana López Ruiz')).toBeInTheDocument()
    const lista = rutasGet().find((ruta) => ruta.startsWith('registros-jugador?'))
    const parametros = new URLSearchParams(lista?.split('?')[1])
    expect(parametros.get('estado')).toBe('PENDIENTE_VALIDACION')
    expect(parametros.get('orden')).toBe('creadoEn,asc')
    expect(parametros.get('tamano')).toBe('20')
    expect(screen.getByText('LORA******MMN***05')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Pendientes' })).toHaveAttribute('aria-selected', 'true')
  })

  it('VAL-001: sin listoParaValidar, «Activar registro» está deshabilitado y lista lo que falta', async () => {
    montar('/app/organizacion/registros/reg-1')

    const activar = await screen.findByRole('button', { name: 'Activar registro' })
    expect(activar).toBeDisabled()
    const faltantes = await screen.findByRole('list', { name: 'Documentos pendientes' })
    expect(faltantes).toHaveTextContent('Fotografía: falta revisarlo')
    expect(within(screen.getByRole('region', { name: 'Fotografía' })).getByText('Falta revisarlo')).toBeInTheDocument()
  })

  it('VAL-001: un 422 con faltantes muestra el detail y lo que falta', async () => {
    const user = userEvent.setup()
    responder({
      requisitos: LISTOS,
      validacion: error(422, 'VAL-001', 'El registro no se puede activar: faltan documentos aprobados de «Fotografía».', {
        faltantes: [{ tipoDocumento: { id: 5, codigo: 'FOTOGRAFIA', nombre: 'Fotografía' }, cantidad: 1, cargados: 0, aprobados: 0 }],
      }),
    })
    montar('/app/organizacion/registros/reg-1')

    await user.click(await screen.findByRole('button', { name: 'Activar registro' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Activar registro' })
    await user.type(within(dialogo).getByLabelText('Observaciones (opcional)'), '  Todo en orden  ')
    await user.click(within(dialogo).getByRole('button', { name: 'Activar registro' }))

    const alerta = await within(dialogo).findByRole('alert')
    expect(alerta).toHaveTextContent('faltan documentos aprobados de «Fotografía»')
    expect(alerta).toHaveTextContent('Fotografía: falta cargarlo')
    expect(post).toHaveBeenCalledWith('registros-jugador/reg-1/validacion', {
      decision: 'ACTIVO',
      observaciones: 'Todo en orden',
    })
  })

  it('RF-017: si otra persona ya lo resolvió, muestra el texto propio y recarga', async () => {
    const user = userEvent.setup()
    responder({ requisitos: LISTOS, validacion: error(409, 'RF-017', 'El registro ya no está pendiente.') })
    montar('/app/organizacion/registros/reg-1')

    await user.click(await screen.findByRole('button', { name: 'Activar registro' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Activar registro' })
    const antes = rutasGet().length
    await user.click(within(dialogo).getByRole('button', { name: 'Activar registro' }))

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent('Otra persona ya resolvió este registro.')
    await waitFor(() => {
      const nuevas = rutasGet().slice(antes)
      expect(nuevas).toContain('registros-jugador/reg-1')
      expect(nuevas).toContain('registros-jugador/reg-1/requisitos')
      expect(nuevas.some((ruta) => ruta.startsWith('registros-jugador?'))).toBe(true)
    })
  })

  it('DEC-100: rechazar el registro explica el criterio y envía RECHAZADO con el motivo', async () => {
    const user = userEvent.setup()
    responder({ validacion: detalle({ estado: 'RECHAZADO' }) })
    montar('/app/organizacion/registros/reg-1')

    await user.click(await screen.findByRole('button', { name: 'Rechazar registro' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Rechazar registro' })
    expect(dialogo).toHaveTextContent(/se rechaza como documento y el club lo reemplaza/)
    await user.type(within(dialogo).getByLabelText(/Motivo/), '  La CURP no corresponde a la persona.  ')
    await user.click(within(dialogo).getByRole('button', { name: 'Rechazar registro' }))

    expect(post).toHaveBeenCalledWith('registros-jugador/reg-1/validacion', {
      decision: 'RECHAZADO',
      observaciones: 'La CURP no corresponde a la persona.',
    })
    expect(await screen.findByText(/se rechazó con observaciones/)).toBeInTheDocument()
  })

  it('DEC-114: aprobar un documento se confirma en el visor y envía APROBADO', async () => {
    const user = userEvent.setup()
    responder({ validacion: documento({ estadoValidacion: 'APROBADO' }) })
    montar('/app/organizacion/registros/reg-1')

    const foto = await screen.findByRole('region', { name: 'Fotografía' })
    await user.click(within(foto).getByRole('button', { name: 'Ver Fotografía' }))
    const visor = await screen.findByRole('dialog', { name: 'Fotografía' })
    await user.click(within(visor).getByRole('button', { name: 'Aprobar' }))
    expect(visor).toHaveTextContent('Aprobar es definitivo: ya no se podrá reemplazar.')
    await user.click(within(visor).getByRole('button', { name: 'Confirmar' }))

    expect(post).toHaveBeenCalledWith('documentos/doc-1/validacion', { decision: 'APROBADO' })
    expect(await screen.findByText('Fotografía: aprobado.')).toBeInTheDocument()
  })

  it('VAL-022: quien cargó el documento ve el texto de separación de funciones', async () => {
    const user = userEvent.setup()
    responder({ validacion: error(403, 'VAL-022', 'No puede.') })
    montar('/app/organizacion/registros/reg-1')

    const foto = await screen.findByRole('region', { name: 'Fotografía' })
    await user.click(within(foto).getByRole('button', { name: 'Ver Fotografía' }))
    const visor = await screen.findByRole('dialog', { name: 'Fotografía' })
    await user.click(within(visor).getByRole('button', { name: 'Aprobar' }))
    await user.click(within(visor).getByRole('button', { name: 'Confirmar' }))

    expect(await within(visor).findByRole('alert')).toHaveTextContent(
      'No puedes validar un registro o documento que tú mismo diste de alta o cargaste.',
    )
  })

  it('un registro ya resuelto muestra quién y cuándo, sin acciones', async () => {
    responder({
      registro: detalle({
        estado: 'ACTIVO',
        validadoPor: { id: 'u1', nombre: 'Secretaria Liga' },
        validadoEn: '2026-11-12T10:30:00-06:00',
        observaciones: 'Expediente completo.',
      }),
      requisitos: LISTOS,
    })
    montar('/app/organizacion/registros/reg-1')

    expect(await screen.findByText('Secretaria Liga')).toBeInTheDocument()
    expect(screen.getByText('Expediente completo.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Activar registro' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Rechazar registro' })).not.toBeInTheDocument()
  })
})
