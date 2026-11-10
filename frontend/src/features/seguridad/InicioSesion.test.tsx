import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InicioSesion } from '@/features/seguridad/InicioSesion'

const { obtenerSesion, iniciarSesionConContrasena, solicitarRestablecimiento } =
  vi.hoisted(() => ({
    obtenerSesion: vi.fn(),
    iniciarSesionConContrasena: vi.fn(),
    solicitarRestablecimiento: vi.fn(),
  }))

vi.mock('@/lib/supabase', () => ({
  supabase: {
    auth: {
      getSession: obtenerSesion,
      signInWithPassword: iniciarSesionConContrasena,
      resetPasswordForEmail: solicitarRestablecimiento,
    },
  },
}))

function montar(
  entrada: string | { pathname: string; state?: unknown } = '/login',
) {
  const enrutador = createMemoryRouter(
    [
      { path: '/', element: <h1>Portal público</h1> },
      { path: '/login', element: <InicioSesion /> },
      {
        path: '/restablecer-contrasena',
        element: <h1>Restablecer contraseña</h1>,
      },
      { path: '/app', element: <h1>Panel de inicio</h1> },
      { path: '/app/partidos', element: <h1>Partidos</h1> },
    ],
    { initialEntries: [entrada] },
  )

  render(<RouterProvider router={enrutador} />)
}

async function llenarAcceso(
  usuario: ReturnType<typeof userEvent.setup>,
  correo = 'ana@ejemplo.mx',
  contrasena = 'frase secreta larga',
) {
  await usuario.type(await screen.findByLabelText(/correo electrónico/i), correo)
  await usuario.type(screen.getByLabelText(/^contraseña/i), contrasena)
}

describe('InicioSesion', () => {
  beforeEach(() => {
    obtenerSesion.mockReset()
    iniciarSesionConContrasena.mockReset()
    solicitarRestablecimiento.mockReset()
    obtenerSesion.mockResolvedValue({ data: { session: null }, error: null })
    iniciarSesionConContrasena.mockResolvedValue({
      data: { user: null, session: { access_token: 'token' } },
      error: null,
    })
    solicitarRestablecimiento.mockResolvedValue({ data: {}, error: null })
  })

  it('H-01.2: muestra el formulario de F-05 sin folio, 2FA ni registro libre', async () => {
    montar()

    expect(
      await screen.findByRole('heading', { name: 'Iniciar sesión' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/correo electrónico/i)).toHaveAttribute(
      'type',
      'email',
    )
    expect(screen.getByLabelText(/^contraseña/i)).toHaveAttribute(
      'type',
      'password',
    )
    expect(
      screen.getByRole('button', { name: 'Iniciar sesión' }),
    ).toBeInTheDocument()
    expect(screen.getByText(/No hay registro libre/i)).toBeInTheDocument()
    expect(
      screen.getByText(/¿Eres tutor de un jugador menor/i),
    ).toBeInTheDocument()

    expect(screen.queryByText(/folio/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/2FA/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/mantener sesión|recordarme/i)).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /google|microsoft|apple/i }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: /crear cuenta|registrarse/i }),
    ).not.toBeInTheDocument()
  })

  it('H-01.2: inicia sesión con Supabase y llega a /app', async () => {
    const usuario = userEvent.setup()
    montar()

    await llenarAcceso(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(iniciarSesionConContrasena).toHaveBeenCalledWith({
      email: 'ana@ejemplo.mx',
      password: 'frase secreta larga',
    })
    expect(
      await screen.findByRole('heading', { name: 'Panel de inicio' }),
    ).toBeInTheDocument()
  })

  it('H-01.2: no revela si falló el correo o la contraseña', async () => {
    const usuario = userEvent.setup()
    iniciarSesionConContrasena.mockResolvedValue({
      data: { user: null, session: null },
      error: {
        message: 'Invalid login credentials',
        status: 400,
        code: 'invalid_credentials',
      },
    })
    montar()

    await llenarAcceso(usuario, 'ana@ejemplo.mx', 'incorrecta')
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Correo o contraseña incorrectos.',
    )
    expect(
      screen.getByRole('heading', { name: 'Iniciar sesión' }),
    ).toBeInTheDocument()
  })

  it('H-01.2: explica el límite de intentos de Supabase Auth', async () => {
    const usuario = userEvent.setup()
    iniciarSesionConContrasena.mockResolvedValue({
      data: { user: null, session: null },
      error: {
        message: 'Too many requests',
        status: 429,
        code: 'over_request_rate_limit',
      },
    })
    montar()

    await llenarAcceso(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Demasiados intentos',
    )
  })

  it('DEF-01: informa una falla del servicio de acceso en lugar de culpar a las credenciales', async () => {
    const usuario = userEvent.setup()
    iniciarSesionConContrasena.mockResolvedValue({
      data: { user: null, session: null },
      error: {
        message: 'Internal Server Error',
        status: 500,
        code: 'unexpected_failure',
      },
    })
    montar()

    await llenarAcceso(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('El servicio de acceso no está disponible')
    expect(alerta).not.toHaveTextContent(/correo o contraseña incorrectos/i)
  })

  it('H-01.2: muestra y oculta la contraseña', async () => {
    const usuario = userEvent.setup()
    montar()

    const campo = await screen.findByLabelText(/^contraseña/i)
    expect(campo).toHaveAttribute('type', 'password')

    await usuario.click(
      screen.getByRole('button', { name: 'Mostrar contraseña' }),
    )

    expect(campo).toHaveAttribute('type', 'text')
    expect(
      screen.getByRole('button', { name: 'Ocultar contraseña' }),
    ).toHaveAttribute('aria-pressed', 'true')

    await usuario.click(
      screen.getByRole('button', { name: 'Ocultar contraseña' }),
    )

    expect(campo).toHaveAttribute('type', 'password')
  })

  it('H-01.2: solicita el enlace de recuperación sin revelar si la cuenta existe', async () => {
    const usuario = userEvent.setup()
    montar()

    await usuario.click(
      await screen.findByRole('button', { name: '¿Olvidaste tu contraseña?' }),
    )
    expect(
      screen.getByRole('heading', { name: 'Recuperar acceso' }),
    ).toBeInTheDocument()

    await usuario.type(
      screen.getByLabelText(/correo electrónico/i),
      'ana@ejemplo.mx',
    )
    await usuario.click(screen.getByRole('button', { name: 'Enviar enlace' }))

    expect(solicitarRestablecimiento).toHaveBeenCalledWith('ana@ejemplo.mx', {
      redirectTo: `${window.location.origin}/restablecer-contrasena`,
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Si el correo está registrado',
    )
  })

  it('H-01.2: abre directamente la recuperación desde el enlace del correo', async () => {
    montar('/login?recuperar=1')

    expect(
      await screen.findByRole('heading', { name: 'Recuperar acceso' }),
    ).toBeInTheDocument()
  })

  it('H-01.2: redirige a /app cuando ya hay una sesión', async () => {
    obtenerSesion.mockResolvedValue({
      data: { session: { access_token: 'token' } },
      error: null,
    })

    montar()

    expect(
      await screen.findByRole('heading', { name: 'Panel de inicio' }),
    ).toBeInTheDocument()
  })

  it('H-01.2: respeta el destino interno que dejó RutaProtegida', async () => {
    const usuario = userEvent.setup()
    montar({ pathname: '/login', state: { desde: '/app/partidos' } })

    await llenarAcceso(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(
      await screen.findByRole('heading', { name: 'Partidos' }),
    ).toBeInTheDocument()
  })

  it('H-01.2: ignora un destino externo y vuelve a /app', async () => {
    const usuario = userEvent.setup()
    montar({
      pathname: '/login',
      state: { desde: 'https://malicioso.example' },
    })

    await llenarAcceso(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(
      await screen.findByRole('heading', { name: 'Panel de inicio' }),
    ).toBeInTheDocument()
  })

  it('H-01.2: avisa cuando la contraseña se restableció', async () => {
    montar({ pathname: '/login', state: { restablecida: true } })

    expect(
      await screen.findByText(
        'Contraseña actualizada. Inicia sesión con tu nueva contraseña.',
      ),
    ).toBeInTheDocument()
  })

  it('H-01.3: avisa cuando la sesión venció', async () => {
    montar({ pathname: '/login', state: { sesionExpirada: true } })

    expect(
      await screen.findByText('Tu sesión venció. Inicia sesión de nuevo.'),
    ).toBeInTheDocument()
  })
})
