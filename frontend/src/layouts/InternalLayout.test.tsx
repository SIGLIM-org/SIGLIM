import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProveedorSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import type { Permiso, Yo } from '@/features/seguridad/tipos'
import { InternalLayout } from '@/layouts/InternalLayout'

const { obtenerYo, cerrarSesion } = vi.hoisted(() => ({
  obtenerYo: vi.fn(),
  cerrarSesion: vi.fn(),
}))

let resolverOrganizacion: (() => void) | null = null

vi.mock('@/lib/api/cliente', () => ({
  api: { get: obtenerYo },
}))

vi.mock('@/lib/supabase', () => ({
  supabase: { auth: { signOut: cerrarSesion } },
}))

function permiso(codigo: string, modulo: string): Permiso {
  return { codigo, modulo, ambito: 'LIGA', clubId: null }
}

const yoCapturista: Yo = {
  perfil: {
    id: 'perfil-capturista',
    nombreMostrado: 'Leonardo López',
    correo: 'capturista@ejemplo.mx',
  },
  asignaciones: [
    {
      id: 'asignacion-capturista',
      rol: { codigo: 'CAPTURISTA', nombre: 'Capturista' },
      ambito: 'LIGA',
      club: null,
    },
  ],
  permisos: [
    permiso('partidos.partido:CONSULTAR', 'M-08'),
    permiso('partidos.cedula:CONSULTAR', 'M-08'),
    permiso('partidos.cedula:CAPTURAR', 'M-08'),
    permiso('notificaciones.aviso:CONSULTAR', 'M-14'),
  ],
}

const yoPresidente: Yo = {
  perfil: {
    id: 'perfil-presidente',
    nombreMostrado: 'Ramón Medina',
    correo: 'presidente@ejemplo.mx',
  },
  asignaciones: [
    {
      id: 'asignacion-presidente',
      rol: { codigo: 'PRESIDENTE', nombre: 'Presidente del Comité Ejecutivo' },
      ambito: 'LIGA',
      club: null,
    },
  ],
  permisos: [
    permiso('seguridad.rol:CONSULTAR', 'M-01'),
    permiso('temporadas.temporada:CONSULTAR', 'M-02'),
    permiso('organizacion.club:CONSULTAR', 'M-03'),
    permiso('canchas.cancha:CONSULTAR', 'M-06'),
    permiso('programacion.jornada:CONSULTAR', 'M-07'),
    permiso('partidos.partido:CONSULTAR', 'M-08'),
    permiso('disciplina.expediente:CONSULTAR', 'M-09'),
    permiso('competencias.propuesta:APROBAR', 'M-10'),
    permiso('publicacion.comunicado:APROBAR', 'M-11'),
    permiso('reportes.reporte:CONSULTAR', 'M-12'),
    permiso('notificaciones.aviso:CONSULTAR', 'M-14'),
  ],
}

async function montar(yo: Yo = yoPresidente) {
  obtenerYo.mockResolvedValue(yo)

  const clienteConsultas = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  const enrutador = createMemoryRouter(
    [
      { path: '/login', element: <h1>Iniciar sesión</h1> },
      {
        element: (
          <ProveedorSesionActual>
            <InternalLayout />
          </ProveedorSesionActual>
        ),
        children: [
          { path: '/app', element: <p>Panel de inicio</p> },
          {
            path: '/app/organizacion',
            lazy: () =>
              new Promise((resolver) => {
                resolverOrganizacion = () =>
                  resolver({ Component: () => <p>Organización</p> })
              }),
          },
        ],
      },
    ],
    { initialEntries: ['/app'] },
  )

  render(
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={enrutador} useTransitions />
    </QueryClientProvider>,
  )

  await screen.findByRole('navigation', { name: 'Menú Ejecutivo' })
}

describe('InternalLayout', () => {
  beforeEach(() => {
    obtenerYo.mockReset()
    cerrarSesion.mockReset()
    cerrarSesion.mockResolvedValue({ error: null })
    resolverOrganizacion = null
  })

  it('muestra el Menú Ejecutivo de F-07 y la identidad de la barra superior', async () => {
    await montar()

    const menu = screen.getByRole('navigation', { name: 'Menú Ejecutivo' })

    expect(within(menu).getByRole('link', { name: 'Inicio' })).toHaveAttribute(
      'href',
      '/app',
    )
    expect(
      within(menu).getByRole('link', { name: 'Organización' }),
    ).toHaveAttribute('href', '/app/organizacion')
    expect(
      within(menu).getByRole('link', { name: 'Programación' }),
    ).toHaveAttribute('href', '/app/programacion')
    expect(within(menu).getByRole('link', { name: 'Partidos' })).toHaveAttribute(
      'href',
      '/app/partidos',
    )
    expect(
      within(menu).getByRole('link', { name: 'Disciplina' }),
    ).toHaveAttribute('href', '/app/disciplina')
    expect(
      within(menu).getByRole('link', { name: 'Administración' }),
    ).toHaveAttribute('href', '/app/administracion')

    expect(
      screen.getByRole('link', { name: 'SIGLIM, aplicación interna' }),
    ).toHaveAttribute('href', '/app')
    expect(screen.getByText('Ramón Medina')).toBeInTheDocument()
    expect(
      screen.getByText('Presidente del Comité Ejecutivo · Liga'),
    ).toBeInTheDocument()
  })

  it('H-01.3: el Capturista solo ve Inicio y Partidos, sin notificaciones', async () => {
    const usuario = userEvent.setup()
    await montar(yoCapturista)

    expect(obtenerYo).toHaveBeenCalledWith('/yo')

    const menu = screen.getByRole('navigation', { name: 'Menú Ejecutivo' })

    expect(within(menu).getByRole('link', { name: 'Inicio' })).toBeInTheDocument()
    expect(within(menu).getByRole('link', { name: 'Partidos' })).toBeInTheDocument()
    expect(
      within(menu).queryByRole('link', { name: 'Organización' }),
    ).not.toBeInTheDocument()
    expect(
      within(menu).queryByRole('link', { name: 'Programación' }),
    ).not.toBeInTheDocument()
    expect(
      within(menu).queryByRole('link', { name: 'Disciplina' }),
    ).not.toBeInTheDocument()
    expect(
      within(menu).queryByRole('link', { name: 'Administración' }),
    ).not.toBeInTheDocument()
    expect(
      within(menu).queryByRole('link', { name: 'Notificaciones' }),
    ).not.toBeInTheDocument()
    expect(screen.getByText('Leonardo López')).toBeInTheDocument()
    expect(screen.getByText('Capturista · Liga')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Abrir menú' }))

    const dialogo = screen.getByRole('dialog', { name: 'Menú Ejecutivo' })
    expect(within(dialogo).getAllByRole('link')).toHaveLength(2)
    expect(
      within(dialogo).getByRole('link', { name: 'Partidos' }),
    ).toHaveAttribute('href', '/app/partidos')
  })

  it('H-01.3: cierra la sesión y vuelve al inicio de sesión', async () => {
    const usuario = userEvent.setup()
    await montar(yoCapturista)

    await usuario.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    expect(
      await screen.findByRole('heading', { name: 'Iniciar sesión' }),
    ).toBeInTheDocument()
    expect(cerrarSesion).toHaveBeenCalledTimes(1)
  })

  it('colapsa y expande la barra lateral', async () => {
    const usuario = userEvent.setup()
    await montar()

    const boton = screen.getByRole('button', { name: 'Colapsar menú' })
    expect(boton).toHaveAttribute('aria-expanded', 'true')

    await usuario.click(boton)

    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(boton).toHaveAccessibleName('Expandir menú')
    expect(
      screen.getByRole('navigation', { name: 'Menú Ejecutivo' }),
    ).toBeInTheDocument()

    await usuario.click(boton)

    expect(boton).toHaveAttribute('aria-expanded', 'true')
    expect(boton).toHaveAccessibleName('Colapsar menú')
  })

  it('abre el menú móvil, navega y devuelve el foco', async () => {
    const usuario = userEvent.setup()
    await montar()

    const abrir = screen.getByRole('button', { name: 'Abrir menú' })

    await usuario.click(abrir)

    const dialogo = screen.getByRole('dialog', { name: 'Menú Ejecutivo' })
    expect(
      within(dialogo).getByRole('link', { name: 'Administración' }),
    ).toHaveAttribute('href', '/app/administracion')

    await usuario.keyboard('{Escape}')

    expect(
      screen.queryByRole('dialog', { name: 'Menú Ejecutivo' }),
    ).not.toBeInTheDocument()
    expect(abrir).toHaveFocus()
  })

  it('RNF-008: el foco no sale del menú móvil al recorrerlo con Tab', async () => {
    const usuario = userEvent.setup()
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Abrir menú' }))

    const dialogo = screen.getByRole('dialog', { name: 'Menú Ejecutivo' })
    const cerrar = within(dialogo).getByRole('button', { name: 'Cerrar menú' })
    const ultimo = within(dialogo).getByRole('link', { name: 'Administración' })

    expect(cerrar).toHaveFocus()

    await usuario.tab({ shift: true })
    expect(ultimo).toHaveFocus()

    await usuario.tab()
    expect(cerrar).toHaveFocus()

    for (let paso = 0; paso < 10; paso += 1) {
      await usuario.tab()
      expect(dialogo).toContainElement(document.activeElement as HTMLElement)
    }
  })

  it('cierra el menú móvil con su botón', async () => {
    const usuario = userEvent.setup()
    await montar()

    await usuario.click(screen.getByRole('button', { name: 'Abrir menú' }))

    const dialogo = screen.getByRole('dialog', { name: 'Menú Ejecutivo' })
    await usuario.click(
      within(dialogo).getByRole('button', { name: 'Cerrar menú' }),
    )

    expect(
      screen.queryByRole('dialog', { name: 'Menú Ejecutivo' }),
    ).not.toBeInTheDocument()
  })

  it('DT-01: muestra el estado de carga al navegar a un módulo', async () => {
    const usuario = userEvent.setup()
    await montar()

    const menu = screen.getByRole('navigation', { name: 'Menú Ejecutivo' })
    await usuario.click(within(menu).getByRole('link', { name: 'Organización' }))

    expect(screen.getByRole('status')).toHaveTextContent('Cargando módulo…')

    resolverOrganizacion?.()

    expect(await screen.findByText('Organización')).toBeInTheDocument()
  })
})
