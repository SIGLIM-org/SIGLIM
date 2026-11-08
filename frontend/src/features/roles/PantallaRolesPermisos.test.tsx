import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaRolesPermisos } from '@/features/roles/PantallaRolesPermisos'
import type {
  CatalogoPermisos,
  RolCatalogo,
  RolDetalle,
  SolicitudCambioPermisos,
} from '@/features/roles/tipos'
import type { Permiso } from '@/features/seguridad/tipos'

const { get, patch, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  patch: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-admin', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [] as {
        id: string
        rol: { codigo: string; nombre: string }
        ambito: 'LIGA'
        club: null
      }[],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, patch, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'seguridad.rol:CONSULTAR'
const EDITAR = 'seguridad.rol:EDITAR'
const APROBAR_INCOMPATIBLE = 'seguridad.permisos:APROBAR_INCOMPATIBLE'
const JORNADA_EDITAR = 'programacion.jornada:EDITAR'
const JORNADA_APROBAR = 'programacion.jornada:APROBAR'
const CEDULA_CAPTURAR = 'partidos.cedula:CAPTURAR'
const RUTA = '/app/administracion/roles'

const PROGRAMADOR: RolCatalogo = {
  id: 'rol-programador',
  codigo: 'PROGRAMADOR',
  nombre: 'Programador de partidos',
  descripcion: 'Programa las jornadas de la Liga.',
  ambitoPredeterminado: 'LIGA',
  asignable: true,
}

const PRESIDENTE: RolCatalogo = {
  id: 'rol-presidente',
  codigo: 'PRESIDENTE',
  nombre: 'Presidente del Comité Ejecutivo',
  descripcion: 'Autoriza la programación y los pares incompatibles.',
  ambitoPredeterminado: 'LIGA',
  asignable: true,
}

const CATALOGO: CatalogoPermisos = {
  permisos: [
    {
      codigo: EDITAR,
      modulo: 'M-01',
      descripcion: 'Modificar la matriz de permisos',
    },
    {
      codigo: APROBAR_INCOMPATIBLE,
      modulo: 'M-01',
      descripcion: 'Aprobar o rechazar pares incompatibles',
    },
    {
      codigo: JORNADA_EDITAR,
      modulo: 'M-07',
      descripcion: 'Registrar y ajustar jornadas',
    },
    {
      codigo: JORNADA_APROBAR,
      modulo: 'M-07',
      descripcion: 'Aprobar o devolver la jornada',
    },
    {
      codigo: CEDULA_CAPTURAR,
      modulo: 'M-08',
      descripcion: 'Capturar la cédula del partido',
    },
  ],
  paresIncompatibles: [
    {
      permisoA: JORNADA_EDITAR,
      permisoB: JORNADA_APROBAR,
      motivo: 'Programar y aprobar la programación',
    },
  ],
}

const DETALLE: RolDetalle = {
  ...PROGRAMADOR,
  permisos: [CATALOGO.permisos[2]],
}

const SOLICITUD: SolicitudCambioPermisos = {
  id: 'solicitud-1',
  rol: PROGRAMADOR,
  permisosAgregados: [JORNADA_APROBAR],
  permisosQuitados: [],
  motivo: 'La Liga pidió concentrar la programación en una sola cuenta.',
  estado: 'SOLICITADO',
  solicitadoPor: { id: 'perfil-presidente', nombreMostrado: 'Luis Ramírez' },
  solicitadoEn: '2026-11-07T10:00:00-06:00',
}

const RESULTADO_APLICADO: RolDetalle = {
  ...DETALLE,
  permisos: [...DETALLE.permisos, CATALOGO.permisos[4]],
}

function pagina(contenido: SolicitudCambioPermisos[]) {
  return {
    contenido,
    pagina: 0,
    tamano: 20,
    totalElementos: contenido.length,
    totalPaginas: contenido.length ? 1 : 0,
  }
}

function permiso(codigo: string): Permiso {
  return { codigo, modulo: 'M-01', ambito: 'LIGA', clubId: null }
}

function responder(solicitudes: SolicitudCambioPermisos[] = [SOLICITUD]) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'roles') {
      return [PROGRAMADOR, PRESIDENTE]
    }
    if (ruta === 'permisos') {
      return CATALOGO
    }
    if (ruta === `roles/${PROGRAMADOR.id}`) {
      return DETALLE
    }
    if (ruta.startsWith('solicitudes-cambio-permisos?')) {
      return pagina(solicitudes)
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })
}

function montar() {
  const enrutador = createMemoryRouter(
    [{ path: RUTA, element: <PantallaRolesPermisos /> }],
    { initialEntries: [RUTA] },
  )

  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

async function abrirMatriz() {
  await screen.findByRole('heading', { name: 'Programador de partidos' })
  return screen.getByRole('tabpanel')
}

async function escribirMotivo(texto: string) {
  const dialogo = screen.getByRole('dialog')
  await userEvent.type(within(dialogo).getByLabelText(/Motivo/), texto)
  return dialogo
}

describe('PantallaRolesPermisos', () => {
  beforeEach(() => {
    get.mockReset()
    patch.mockReset()
    post.mockReset()
    sesion.yo = {
      perfil: { id: 'perfil-admin', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [permiso(CONSULTAR), permiso(EDITAR)],
    }
    responder()
    patch.mockResolvedValue(RESULTADO_APLICADO)
  })

  it('RF-095: muestra el rol y su matriz agrupada por módulo con los permisos efectivos', async () => {
    montar()
    const panel = await abrirMatriz()

    expect(within(panel).getByRole('heading', { name: 'M-01 · Administración y seguridad' })).toBeInTheDocument()
    expect(within(panel).getByRole('heading', { name: 'M-07 · Programación manual' })).toBeInTheDocument()
    expect(within(panel).getByRole('heading', { name: 'M-08 · Partidos, cédula y resultados' })).toBeInTheDocument()
    expect(
      within(panel).getByRole('checkbox', { name: /Registrar y ajustar jornadas/ }),
    ).toBeChecked()
    expect(
      within(panel).getByRole('checkbox', { name: /Aprobar o devolver la jornada/ }),
    ).not.toBeChecked()
    expect(within(panel).getByText('1 de 5 permisos seleccionados')).toBeInTheDocument()
  })

  it('VAL-023: al crear un par incompatible avisa y envía una solicitud en lugar de aplicar', async () => {
    patch.mockResolvedValue(SOLICITUD)
    montar()
    const panel = await abrirMatriz()

    await userEvent.click(
      within(panel).getByRole('checkbox', { name: /Aprobar o devolver la jornada/ }),
    )

    expect(within(panel).getByText('Este cambio combina permisos incompatibles')).toBeInTheDocument()
    expect(within(panel).getByText(/Programar y aprobar la programación/)).toBeInTheDocument()
    await userEvent.click(
      within(panel).getByRole('button', { name: 'Solicitar aprobación' }),
    )

    expect(
      screen.getByRole('heading', { name: 'Solicitar aprobación' }),
    ).toBeInTheDocument()
    await escribirMotivo('La Liga pidió concentrar la programación en una sola cuenta.')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar solicitud' }))

    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch).toHaveBeenCalledWith(`roles/${PROGRAMADOR.id}/permisos`, {
      permisos: expect.arrayContaining([JORNADA_EDITAR, JORNADA_APROBAR]),
      motivo: 'La Liga pidió concentrar la programación en una sola cuenta.',
    })
    expect(
      await screen.findByText(/El cambio quedó pendiente/),
    ).toBeInTheDocument()
  })

  it('RF-095: un cambio compatible se guarda con su motivo y confirma la aplicación', async () => {
    montar()
    const panel = await abrirMatriz()

    await userEvent.click(
      within(panel).getByRole('checkbox', { name: /Capturar la cédula del partido/ }),
    )
    await userEvent.click(within(panel).getByRole('button', { name: 'Guardar cambios' }))

    expect(screen.getByRole('heading', { name: 'Guardar cambios' })).toBeInTheDocument()
    const dialogo = await escribirMotivo('Se requiere que el Programador también capture la cédula.')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch).toHaveBeenCalledWith(`roles/${PROGRAMADOR.id}/permisos`, {
      permisos: [JORNADA_EDITAR, CEDULA_CAPTURAR],
      motivo: 'Se requiere que el Programador también capture la cédula.',
    })
    expect(
      await screen.findByText(/Cambios aplicados a la matriz del rol/),
    ).toBeInTheDocument()
  })

  it('VAL-022: bloquea la edición del propio rol y explica el código', async () => {
    sesion.yo.asignaciones = [
      {
        id: 'asignacion-1',
        rol: { codigo: PROGRAMADOR.codigo, nombre: PROGRAMADOR.nombre },
        ambito: 'LIGA',
        club: null,
      },
    ]
    montar()
    const panel = await abrirMatriz()

    expect(
      within(panel).getByText(/No puedes cambiar los permisos de tu propio rol/),
    ).toBeInTheDocument()
    expect(
      within(panel).getByRole('checkbox', { name: /Aprobar o devolver la jornada/ }),
    ).toBeDisabled()
    expect(
      within(panel).queryByRole('button', { name: 'Guardar cambios' }),
    ).not.toBeInTheDocument()
  })

  it('RF-096: el Presidente aprueba una solicitud con motivo y ve el resultado', async () => {
    sesion.yo.permisos = [permiso(CONSULTAR), permiso(APROBAR_INCOMPATIBLE)]
    post.mockResolvedValue({
      ...SOLICITUD,
      estado: 'APROBADO',
      resueltoPor: { id: sesion.yo.perfil.id, nombreMostrado: 'Ana Pérez' },
      resueltoEn: '2026-11-07T11:00:00-06:00',
      observaciones: 'Procede por acuerdo del Comité.',
    })
    montar()
    await screen.findByRole('heading', { name: 'Programador de partidos' })

    await userEvent.click(
      screen.getByRole('tab', { name: /Solicitudes pendientes/ }),
    )

    const panel = screen.getByRole('tabpanel')
    expect(within(panel).getByText(/Luis Ramírez/)).toBeInTheDocument()
    expect(within(panel).getByText(/concentrar la programación/)).toBeInTheDocument()

    await userEvent.click(within(panel).getByRole('button', { name: 'Aprobar' }))
    expect(
      screen.getByRole('heading', { name: 'Aprobar cambio de permisos' }),
    ).toBeInTheDocument()
    const dialogo = screen.getByRole('dialog')
    await userEvent.type(
      within(dialogo).getByLabelText(/Motivo del dictamen/),
      'Procede por acuerdo del Comité.',
    )
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar cambio' }))

    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post).toHaveBeenCalledWith(
      `solicitudes-cambio-permisos/${SOLICITUD.id}/resolucion`,
      { decision: 'APROBADO', motivo: 'Procede por acuerdo del Comité.' },
    )
    expect(await screen.findByText(/Solicitud aprobada/)).toBeInTheDocument()
  })

  it('RF-096: quien solicitó el cambio no puede dictaminarlo', async () => {
    sesion.yo.permisos = [permiso(CONSULTAR), permiso(APROBAR_INCOMPATIBLE)]
    responder([{ ...SOLICITUD, solicitadoPor: sesion.yo.perfil }])
    montar()
    await screen.findByRole('heading', { name: 'Programador de partidos' })

    await userEvent.click(
      screen.getByRole('tab', { name: /Solicitudes pendientes/ }),
    )
    const panel = screen.getByRole('tabpanel')

    expect(within(panel).getByText(/Quien solicita no puede dictaminar/)).toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Aprobar' })).not.toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Rechazar' })).not.toBeInTheDocument()
  })

  it('VAL-015: sin permiso de aprobar no muestra la pestaña ni consulta solicitudes', async () => {
    montar()
    await screen.findByRole('heading', { name: 'Programador de partidos' })

    expect(
      screen.queryByRole('tab', { name: /Solicitudes pendientes/ }),
    ).not.toBeInTheDocument()
    expect(
      get.mock.calls.some(([ruta]) => String(ruta).startsWith('solicitudes-cambio-permisos')),
    ).toBe(false)
  })
})
