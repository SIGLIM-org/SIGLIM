import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { rutas } from '@/app/router'

const { obtenerSesion, obtenerYo, obtenerCatalogo } = vi.hoisted(() => ({
  obtenerSesion: vi.fn(),
  obtenerYo: vi.fn(),
  obtenerCatalogo: vi.fn(),
}))

vi.mock('@/lib/supabase', () => ({
  supabase: {
    auth: {
      getSession: obtenerSesion,
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({
  api: {
    get: (ruta: string) => (ruta === '/yo' ? obtenerYo() : obtenerCatalogo(ruta)),
  },
}))

function montar(ruta: string) {
  const clienteConsultas = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const enrutador = createMemoryRouter(rutas, { initialEntries: [ruta] })

  render(
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

function permiso(codigo: string, modulo: string) {
  return { codigo, modulo, ambito: 'LIGA' as const, clubId: null }
}

const yoCapturista = {
  perfil: {
    id: 'perfil-capturista',
    nombreMostrado: 'Leonardo López',
    correo: null,
  },
  asignaciones: [],
  permisos: [permiso('partidos.partido:CONSULTAR', 'M-08')],
}

function iniciarSesion() {
  obtenerSesion.mockResolvedValue({
    data: { session: { access_token: 'token-de-prueba' } },
    error: null,
  })
  obtenerYo.mockResolvedValue(yoCapturista)
}

describe('router', () => {
  beforeEach(() => {
    obtenerSesion.mockReset()
    obtenerYo.mockReset()
    obtenerCatalogo.mockReset()
    obtenerCatalogo.mockResolvedValue([])
    obtenerSesion.mockResolvedValue({ data: { session: null }, error: null })
    obtenerYo.mockResolvedValue({
      perfil: {
        id: 'perfil-1',
        nombreMostrado: 'Ana Pérez',
        correo: null,
      },
      asignaciones: [],
      permisos: [],
    })
  })

  it('muestra el portal público en /', () => {
    montar('/')

    expect(
      screen.getByRole('heading', { name: 'Programación de la semana' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'SIGLIM, portal público' }),
    ).toBeInTheDocument()
  })

  it('muestra los resultados del portal en /resultados', () => {
    montar('/resultados')

    expect(
      screen.getByRole('heading', { name: 'Resultados oficiales' }),
    ).toBeInTheDocument()
  })

  it('muestra la tabla de posiciones del portal en /tabla', () => {
    montar('/tabla')

    expect(
      screen.getByRole('heading', { name: 'Tabla de posiciones' }),
    ).toBeInTheDocument()
  })

  it('muestra el aviso de privacidad en /aviso-de-privacidad', () => {
    montar('/aviso-de-privacidad')

    expect(
      screen.getByRole('heading', { name: 'Aviso de privacidad' }),
    ).toBeInTheDocument()
  })

  it('H-07.2: la sección Programación enlaza a Canchas solo con su permiso', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('canchas.cancha:CONSULTAR', 'M-06')],
    })

    montar('/app/programacion')

    expect(await screen.findByRole('link', { name: /Canchas/ })).toHaveAttribute(
      'href',
      '/app/programacion/canchas',
    )
  })

  it('H-07.2: con otro permiso del módulo no enlaza a Canchas y su URL responde 403', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('programacion.jornada:CONSULTAR', 'M-07')],
    })

    montar('/app/programacion/canchas')

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Canchas/ })).not.toBeInTheDocument()
  })

  it('H-06.2: con organizacion.club:CONSULTAR, la URL de Clubes muestra la pantalla', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('organizacion.club:CONSULTAR', 'M-03')],
    })
    obtenerCatalogo.mockImplementation(async (ruta: string) =>
      ruta.startsWith('clubes?')
        ? { contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 }
        : [],
    )

    montar('/app/organizacion/clubes')

    expect(await screen.findByRole('heading', { name: 'Clubes' })).toBeInTheDocument()
    expect(await screen.findByText('Aún no hay clubes registrados')).toBeInTheDocument()
  })

  it('H-06.2: la sección Organización enlaza a Clubes con su permiso', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('organizacion.club:CONSULTAR', 'M-03')],
    })

    montar('/app/organizacion')

    expect(await screen.findByRole('link', { name: /Clubes/ })).toHaveAttribute(
      'href',
      '/app/organizacion/clubes',
    )
  })

  it('H-21.2: la sección Administración enlaza a Auditoría solo con su permiso', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('auditoria.registro:CONSULTAR', 'M-01')],
    })

    montar('/app/administracion')

    expect(await screen.findByRole('link', { name: /Auditoría/ })).toHaveAttribute(
      'href',
      '/app/administracion/auditoria',
    )
  })

  it('H-21.2: con otro permiso del módulo no enlaza a Auditoría y su URL responde 403', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('seguridad.rol:CONSULTAR', 'M-01')],
    })

    montar('/app/administracion/auditoria')

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Auditoría/ })).not.toBeInTheDocument()
  })

  it('H-03.2: la sección Administración enlaza a Usuarios solo con su permiso', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('seguridad.usuario:CONSULTAR', 'M-01')],
    })

    montar('/app/administracion')

    expect(await screen.findByRole('link', { name: /Usuarios/ })).toHaveAttribute(
      'href',
      '/app/administracion/usuarios',
    )
  })

  it('H-03.2: con otro permiso del módulo no enlaza a Usuarios y su URL responde 403', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('seguridad.rol:CONSULTAR', 'M-01')],
    })

    montar('/app/administracion/usuarios')

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Usuarios/ })).not.toBeInTheDocument()
  })

  it('H-03.2: con permiso, la URL de Usuarios muestra la pantalla', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('seguridad.usuario:CONSULTAR', 'M-01')],
    })
    obtenerCatalogo.mockImplementation(async (ruta: string) => {
      if (ruta.startsWith('usuarios?')) {
        return { contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 }
      }
      if (ruta.startsWith('clubes?')) {
        return { contenido: [], pagina: 0, tamano: 100, totalElementos: 0, totalPaginas: 0 }
      }
      return []
    })

    montar('/app/administracion/usuarios')

    expect(await screen.findByRole('heading', { name: 'Usuarios' })).toBeInTheDocument()
    expect(await screen.findByText('Aún no hay cuentas registradas')).toBeInTheDocument()
  })

  it('B-27.2: la sección Administración enlaza a Comunicados con permiso de aprobar', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('publicacion.comunicado:APROBAR', 'M-11')],
    })

    montar('/app/administracion')

    expect(await screen.findByRole('link', { name: /Comunicados/ })).toHaveAttribute(
      'href',
      '/app/administracion/comunicados',
    )
  })

  it('B-27.2: con otro permiso del módulo no enlaza a Comunicados y su URL responde 403', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('seguridad.rol:CONSULTAR', 'M-01')],
    })

    montar('/app/administracion/comunicados')

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Comunicados/ })).not.toBeInTheDocument()
  })

  it('H-26.2: la sección Disciplina enlaza a Catálogos solo con su permiso', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('disciplina.catalogo:EDITAR', 'M-09')],
    })

    montar('/app/disciplina')

    expect(await screen.findByRole('link', { name: /Catálogos/ })).toHaveAttribute(
      'href',
      '/app/disciplina/catalogos',
    )
  })

  it('H-26.2: con otro permiso del módulo no enlaza a Catálogos', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('disciplina.expediente:CONSULTAR', 'M-09')],
    })

    montar('/app/disciplina')

    expect(
      await screen.findByRole('heading', { name: 'Disciplina' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Catálogos/ })).not.toBeInTheDocument()
  })

  it('H-26.2: con permiso, la URL de Catálogos muestra la pantalla', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('disciplina.catalogo:EDITAR', 'M-09')],
    })

    montar('/app/disciplina/catalogos')

    expect(
      await screen.findByRole('heading', { name: 'Catálogos disciplinarios' }),
    ).toBeInTheDocument()
  })

  it('H-26.2: sin el permiso de catálogos, su URL responde 403', async () => {
    iniciarSesion()
    obtenerYo.mockResolvedValue({
      ...yoCapturista,
      permisos: [permiso('disciplina.expediente:CONSULTAR', 'M-09')],
    })

    montar('/app/disciplina/catalogos')

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
  })

  it('muestra la página 404 en una ruta inexistente', () => {
    montar('/ruta-que-no-existe')

    expect(
      screen.getByRole('heading', { name: 'No encontramos lo que buscas' }),
    ).toBeInTheDocument()
  })

  it('envía /app al inicio de sesión cuando no hay sesión', async () => {
    montar('/app')

    expect(
      await screen.findByRole('heading', { name: 'Iniciar sesión' }),
    ).toBeInTheDocument()
  })

  it('muestra la aplicación interna en /app cuando hay sesión', async () => {
    obtenerSesion.mockResolvedValue({
      data: { session: { access_token: 'token-de-prueba' } },
      error: null,
    })

    montar('/app')

    expect(
      await screen.findByRole('heading', { name: 'Inicio' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: 'Menú Ejecutivo' }),
    ).toBeInTheDocument()
  })

  it('con sesión, /app/inexistente muestra la 404 dentro de la aplicación interna', async () => {
    obtenerSesion.mockResolvedValue({
      data: { session: { access_token: 'token-de-prueba' } },
      error: null,
    })

    montar('/app/inexistente')

    expect(
      await screen.findByRole('heading', { name: 'No encontramos lo que buscas' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: 'Menú Ejecutivo' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute(
      'href',
      '/app',
    )
  })

  it('H-02.4: sin permiso del módulo, la URL directa muestra 403 y no la página', async () => {
    iniciarSesion()

    montar('/app/organizacion')

    expect(
      await screen.findByRole('heading', {
        name: 'No tienes permiso para esta acción',
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('navigation', { name: 'Menú Ejecutivo' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Organización' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByText('Módulo de la aplicación interna en construcción.'),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toHaveAttribute(
      'href',
      '/app',
    )
  })

  it('H-02.4: con permiso del módulo, la URL directa muestra la página', async () => {
    iniciarSesion()

    montar('/app/partidos')

    expect(
      await screen.findByRole('heading', { name: 'Partidos' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', {
        name: 'No tienes permiso para esta acción',
      }),
    ).not.toBeInTheDocument()
  })

  it('H-02.4: una subruta de un módulo sin permiso también muestra 403', async () => {
    iniciarSesion()

    montar('/app/organizacion/clubes')

    expect(
      await screen.findByRole('heading', {
        name: 'No tienes permiso para esta acción',
      }),
    ).toBeInTheDocument()
  })

  it('H-02.4: con permiso, una subruta inexistente muestra 404 y no 403', async () => {
    iniciarSesion()

    montar('/app/partidos/inexistente')

    expect(
      await screen.findByRole('heading', { name: 'No encontramos lo que buscas' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', {
        name: 'No tienes permiso para esta acción',
      }),
    ).not.toBeInTheDocument()
  })
})
