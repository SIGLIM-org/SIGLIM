import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Permiso } from '@/features/seguridad/tipos'
import { PantallaUsuarios } from '@/features/usuarios/PantallaUsuarios'
import type {
  AsignacionUsuario,
  ClubResumen,
  RolCatalogo,
  Usuario,
} from '@/features/usuarios/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, patch, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, patch } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'seguridad.usuario:CONSULTAR'
const CREAR = 'seguridad.usuario:CREAR'
const EDITAR = 'seguridad.usuario:EDITAR'
const RUTA = '/app/administracion/usuarios'
const MOTIVO = 'Cambio de funciones en la Liga'

const ROL_ADMIN: RolCatalogo = {
  id: 'r-admin',
  codigo: 'ADMINISTRADOR_GENERAL',
  nombre: 'Administrador general',
  ambitoPredeterminado: 'LIGA',
  asignable: true,
}

const ROL_CLUB: RolCatalogo = {
  id: 'r-club',
  codigo: 'DIRECTIVA_CLUB',
  nombre: 'Directiva de club',
  ambitoPredeterminado: 'CLUB',
  asignable: true,
}

const ROL_SISTEMA: RolCatalogo = {
  id: 'r-sistema',
  codigo: 'SISTEMA',
  nombre: 'Sistema',
  ambitoPredeterminado: 'LIGA',
  asignable: false,
}

const CLUB: ClubResumen = { id: 'c-1', nombre: 'Club Halcones' }

const ASIGNACION: AsignacionUsuario = {
  id: 'a-1',
  rol: { id: ROL_CLUB.id, codigo: ROL_CLUB.codigo, nombre: ROL_CLUB.nombre },
  ambito: 'CLUB',
  club: CLUB,
  activa: true,
  creadoEn: '2026-01-10T10:00:00-06:00',
}

const REVOCADA: AsignacionUsuario = {
  id: 'a-2',
  rol: { id: ROL_ADMIN.id, codigo: ROL_ADMIN.codigo, nombre: ROL_ADMIN.nombre },
  ambito: 'LIGA',
  activa: false,
  creadoEn: '2025-08-01T10:00:00-06:00',
  revocadaEn: '2026-02-01T10:00:00-06:00',
}

const USUARIO: Usuario = {
  id: 'u-1',
  nombreMostrado: 'Ana López',
  correo: 'ana@ejemplo.mx',
  activo: true,
  creadoEn: '2026-01-10T10:00:00-06:00',
  asignaciones: [ASIGNACION],
}

const DETALLE: Usuario = { ...USUARIO, asignaciones: [ASIGNACION, REVOCADA] }

const INACTIVO: Usuario = {
  ...USUARIO,
  id: 'u-2',
  nombreMostrado: 'Beto Ruiz',
  correo: 'beto@ejemplo.mx',
  activo: false,
}

let lista: unknown
let detalle: unknown

function error(status: number, codigo: string, detail: string) {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

function pagina(contenido: Usuario[]) {
  return {
    contenido,
    pagina: 0,
    tamano: 20,
    totalElementos: contenido.length,
    totalPaginas: contenido.length ? 1 : 0,
  }
}

function responder(nuevaLista: unknown = pagina([USUARIO, INACTIVO])) {
  lista = nuevaLista
  detalle = DETALLE
  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('usuarios?')) {
      if (lista instanceof Error) {
        throw lista
      }
      return lista
    }
    if (ruta === 'usuarios/u-1') {
      if (detalle instanceof Error) {
        throw detalle
      }
      return detalle
    }
    if (ruta === 'roles') {
      return [ROL_ADMIN, ROL_CLUB, ROL_SISTEMA]
    }
    if (ruta.startsWith('clubes?')) {
      return {
        contenido: [CLUB],
        pagina: 0,
        tamano: 100,
        totalElementos: 1,
        totalPaginas: 1,
      }
    }
    throw new Error(`Ruta inesperada: ${ruta}`)
  })
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-01',
    ambito: 'LIGA',
    clubId: null,
  }))
}

function montar() {
  const enrutador = createMemoryRouter([{ path: RUTA, element: <PantallaUsuarios /> }], {
    initialEntries: [RUTA],
  })

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

async function abrirPanel() {
  await userEvent.click(
    await screen.findByRole('button', { name: 'Gestionar a Ana López' }),
  )
  return screen.findByRole('dialog', { name: 'Detalle de usuario' })
}

describe('PantallaUsuarios', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    otorgar(CONSULTAR, CREAR, EDITAR)
    responder()
    post.mockImplementation(async (ruta: string) => {
      if (ruta === 'usuarios') {
        return {
          ...USUARIO,
          id: 'u-nuevo',
          nombreMostrado: 'Carla Díaz',
          correo: 'carla@ejemplo.mx',
        }
      }
      if (ruta === 'usuarios/u-1/asignaciones') {
        return {
          ...ASIGNACION,
          id: 'a-nueva',
          rol: { id: ROL_ADMIN.id, codigo: ROL_ADMIN.codigo, nombre: ROL_ADMIN.nombre },
          ambito: 'LIGA',
          club: undefined,
        }
      }
      if (ruta === 'asignaciones-rol/a-1/revocacion') {
        return { ...ASIGNACION, activa: false, revocadaEn: '2026-03-01T10:00:00-06:00' }
      }
      throw new Error(`Ruta inesperada: ${ruta}`)
    })
    patch.mockResolvedValue({ ...USUARIO, activo: false })
  })

  it('H-03.2: muestra la tabla con roles, ámbitos, estado y alta', async () => {
    montar()

    const fila = await screen.findByText('Ana López')
    const contenedor = fila.closest('tr') as HTMLElement

    expect(within(contenedor).getByText('ana@ejemplo.mx')).toBeInTheDocument()
    expect(within(contenedor).getByText('Directiva de club · Club Halcones')).toBeInTheDocument()
    expect(within(contenedor).getByText('Activo')).toBeInTheDocument()
    expect(within(contenedor).getByText('10 ene 2026')).toBeInTheDocument()
    expect(screen.getByText('2 cuentas')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('usuarios?pagina=0&tamano=20')
  })

  it('H-03.2: aplica los filtros de rol, ámbito, club, estado y texto', async () => {
    montar()
    await screen.findByText('Ana López')

    await userEvent.type(screen.getByLabelText('Buscar'), 'ana')
    await userEvent.selectOptions(screen.getByLabelText('Rol'), 'r-club')
    await userEvent.selectOptions(screen.getByLabelText('Ámbito'), 'CLUB')
    await userEvent.selectOptions(screen.getByLabelText('Club'), 'c-1')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'true')
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith(
        'usuarios?pagina=0&tamano=20&q=ana&rolId=r-club&ambito=CLUB&clubId=c-1&activo=true',
      )
    })
  })

  it('H-03.2: invita una cuenta con rol y ámbito de club', async () => {
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Invitar usuario' }))
    const dialogo = screen.getByRole('dialog', { name: 'Invitar usuario' })

    await userEvent.type(within(dialogo).getByLabelText(/Nombre completo/), 'Carla Díaz')
    await userEvent.type(within(dialogo).getByLabelText(/Correo electrónico/), 'carla@ejemplo.mx')
    await userEvent.selectOptions(within(dialogo).getByLabelText(/Rol/), 'r-club')
    await userEvent.selectOptions(within(dialogo).getByLabelText(/Club/), 'c-1')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar invitación' }))

    expect(post).toHaveBeenCalledWith('usuarios', {
      nombreMostrado: 'Carla Díaz',
      correo: 'carla@ejemplo.mx',
      asignaciones: [{ rolId: 'r-club', ambito: 'CLUB', clubId: 'c-1' }],
    })
    expect(
      await screen.findByText('Invitación enviada a carla@ejemplo.mx.'),
    ).toBeInTheDocument()
  })

  it('H-03 criterio 2: la invitación con ámbito de club exige elegir el club', async () => {
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Invitar usuario' }))
    const dialogo = screen.getByRole('dialog', { name: 'Invitar usuario' })

    await userEvent.type(within(dialogo).getByLabelText(/Nombre completo/), 'Carla Díaz')
    await userEvent.type(within(dialogo).getByLabelText(/Correo electrónico/), 'carla@ejemplo.mx')
    await userEvent.selectOptions(within(dialogo).getByLabelText(/Rol/), 'r-club')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar invitación' }))

    expect(within(dialogo).getByText('Elige el club de la asignación.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('H-03.2: el panel muestra las asignaciones vigentes y revocadas, y desactiva con motivo', async () => {
    montar()
    const panel = await abrirPanel()

    expect(await within(panel).findByText('Directiva de club')).toBeInTheDocument()
    expect(within(panel).getByText('Vigente')).toBeInTheDocument()
    expect(within(panel).getByText('Revocada')).toBeInTheDocument()

    await userEvent.click(within(panel).getByRole('switch', { name: 'Cuenta activa' }))

    const confirmacion = await screen.findByRole('dialog', { name: 'Desactivar cuenta' })
    await userEvent.type(within(confirmacion).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(
      within(confirmacion).getByRole('button', { name: 'Desactivar cuenta' }),
    )

    expect(patch).toHaveBeenCalledWith('usuarios/u-1', { activo: false, motivo: MOTIVO })
    expect(await screen.findByText('Cuenta «Ana López» desactivada.')).toBeInTheDocument()
  })

  it('H-03.2: revoca una asignación con motivo', async () => {
    montar()
    const panel = await abrirPanel()

    await userEvent.click(await within(panel).findByRole('button', { name: 'Revocar' }))

    const confirmacion = await screen.findByRole('dialog', { name: 'Revocar asignación' })
    await userEvent.type(within(confirmacion).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(
      within(confirmacion).getByRole('button', { name: 'Revocar asignación' }),
    )

    expect(post).toHaveBeenCalledWith('asignaciones-rol/a-1/revocacion', { motivo: MOTIVO })
    expect(
      await screen.findByText('Asignación «Directiva de club · Club Halcones» revocada; la cuenta conserva el historial.'),
    ).toBeInTheDocument()
  })

  it('H-03.2: agrega una asignación desde el panel', async () => {
    montar()
    const panel = await abrirPanel()

    await userEvent.click(
      await within(panel).findByRole('button', { name: 'Agregar asignación' }),
    )
    const dialogo = screen.getByRole('dialog', { name: 'Agregar asignación' })

    await userEvent.selectOptions(within(dialogo).getByLabelText(/Rol/), 'r-admin')
    await userEvent.click(
      within(dialogo).getByRole('button', { name: 'Agregar asignación' }),
    )

    expect(post).toHaveBeenCalledWith('usuarios/u-1/asignaciones', {
      rolId: 'r-admin',
      ambito: 'LIGA',
    })
    expect(
      await screen.findByText('Asignación «Administrador general · Liga» agregada.'),
    ).toBeInTheDocument()
  })

  it('H-02.4: con solo consulta no ofrece invitar, revocar ni agregar asignaciones', async () => {
    otorgar(CONSULTAR)
    montar()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Ver detalle de Ana López' }),
    )
    const panel = await screen.findByRole('dialog', { name: 'Detalle de usuario' })

    expect(screen.queryByRole('button', { name: 'Invitar usuario' })).not.toBeInTheDocument()
    expect(within(panel).getByText('Solo consulta')).toBeInTheDocument()
    expect(within(panel).queryByRole('switch', { name: 'Cuenta activa' })).not.toBeInTheDocument()
    expect(
      within(panel).queryByRole('button', { name: 'Agregar asignación' }),
    ).not.toBeInTheDocument()
    expect(within(panel).queryByRole('button', { name: 'Revocar' })).not.toBeInTheDocument()
  })

  it('H-02.4: sin CONSULTAR muestra la 403 y no pide datos', () => {
    otorgar()
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('VAL-015: un 403 de la API muestra la página 403 completa', async () => {
    responder(error(403, 'VAL-015', 'No tienes el permiso seguridad.usuario:CONSULTAR.'))
    montar()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
  })

  it('estado vacío: no hay cuentas e invita a crear la primera', async () => {
    responder(pagina([]))
    montar()

    expect(await screen.findByText('Aún no hay cuentas registradas')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Invitar usuario' })).not.toHaveLength(0)
  })
})
