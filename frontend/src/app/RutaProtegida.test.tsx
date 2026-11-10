import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RutaProtegida } from '@/app/RutaProtegida'

const { obtenerSesion, alCambiarSesion } = vi.hoisted(() => ({
  obtenerSesion: vi.fn(),
  alCambiarSesion: vi.fn(),
}))

let notificarCambioDeSesion:
  | ((evento: string, sesion: unknown) => void)
  | null = null

vi.mock('@/lib/supabase', () => ({
  supabase: {
    auth: {
      getSession: obtenerSesion,
      onAuthStateChange: alCambiarSesion,
    },
  },
}))

function montarRutaProtegida() {
  const clienteConsultas = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const enrutador = createMemoryRouter(
    [
      { path: '/login', element: <h1>Iniciar sesión</h1> },
      {
        element: <RutaProtegida />,
        children: [{ path: '/app', element: <h1>Panel de inicio</h1> }],
      },
    ],
    { initialEntries: ['/app'] },
  )

  render(
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return clienteConsultas
}

function sesionIniciada() {
  obtenerSesion.mockResolvedValue({
    data: { session: { access_token: 'token-de-prueba' } },
    error: null,
  })
}

describe('RutaProtegida', () => {
  beforeEach(() => {
    obtenerSesion.mockReset()
    alCambiarSesion.mockReset()
    notificarCambioDeSesion = null
    alCambiarSesion.mockImplementation(
      (callback: (evento: string, sesion: unknown) => void) => {
        notificarCambioDeSesion = callback

        return { data: { subscription: { unsubscribe: vi.fn() } } }
      },
    )
  })

  it('redirige al inicio de sesión cuando no hay sesión', async () => {
    obtenerSesion.mockResolvedValue({ data: { session: null }, error: null })

    montarRutaProtegida()

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })

  it('muestra la ruta hija cuando hay sesión', async () => {
    sesionIniciada()

    montarRutaProtegida()

    expect(await screen.findByRole('heading', { name: 'Panel de inicio' })).toBeInTheDocument()
  })

  it('DEF-02: redirige al inicio de sesión cuando otra pestaña cierra la sesión', async () => {
    sesionIniciada()
    const clienteConsultas = montarRutaProtegida()

    expect(await screen.findByRole('heading', { name: 'Panel de inicio' })).toBeInTheDocument()

    clienteConsultas.setQueryData(['yo'], { perfil: { id: 'perfil-1' } })
    expect(clienteConsultas.getQueryCache().getAll()).toHaveLength(1)

    act(() => {
      notificarCambioDeSesion?.('SIGNED_OUT', null)
    })

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(clienteConsultas.getQueryCache().getAll()).toHaveLength(0)
    expect(screen.queryByRole('heading', { name: 'Panel de inicio' })).not.toBeInTheDocument()
  })

  it('DEF-02: al recuperar el foco comprueba la sesión si no hubo aviso de otra pestaña', async () => {
    sesionIniciada()
    montarRutaProtegida()

    expect(await screen.findByRole('heading', { name: 'Panel de inicio' })).toBeInTheDocument()

    obtenerSesion.mockResolvedValue({ data: { session: null }, error: null })

    act(() => {
      window.dispatchEvent(new Event('focus'))
    })

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })
})
