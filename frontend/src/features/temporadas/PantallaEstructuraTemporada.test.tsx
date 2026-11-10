import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  APROBAR_INTERPRETACION,
  CONSULTAR,
  EDITAR,
  EDITAR_PARAMETROS,
  TEMPORADA,
  errorApi,
  montarEstructura,
  permisos,
  resolverGet,
  respuestasPorDefecto,
} from '@/features/temporadas/datosPruebaEstructura'
import type { Permiso } from '@/features/seguridad/tipos'

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

function pestanaSeleccionada(): string | null {
  const estructura = screen.getByRole('tablist', { name: 'Estructura de la temporada' })

  return (
    within(estructura)
      .getAllByRole('tab')
      .find((pestana) => pestana.getAttribute('aria-selected') === 'true')
      ?.textContent ?? null
  )
}

describe('PantallaEstructuraTemporada', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    sesion.yo.permisos = permisos(CONSULTAR, EDITAR, EDITAR_PARAMETROS)
    get.mockImplementation(resolverGet(respuestasPorDefecto()))
  })

  it('VAL-015: sin CONSULTAR muestra la 403 y no pide datos', () => {
    sesion.yo.permisos = []
    montarEstructura()

    expect(
      screen.getByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('D1: la pestaña sale de ?pestana=', async () => {
    montarEstructura('?pestana=categorias')

    await screen.findByRole('tab', { name: /Categorías/ })
    expect(pestanaSeleccionada()).toMatch(/^Categorías/)
    expect(
      await screen.findByRole('heading', { name: 'Catálogo de categorías de la liga' }),
    ).toBeInTheDocument()
  })

  it('D1: un valor inválido de ?pestana= abre Parámetros', async () => {
    montarEstructura('?pestana=desconocida')

    await screen.findByRole('tab', { name: /Parámetros/ })
    expect(pestanaSeleccionada()).toMatch(/^Parámetros/)
  })

  it('UI-004: las flechas, Inicio y Fin cambian de pestaña y la URL', async () => {
    const enrutador = montarEstructura()
    const parametros = await screen.findByRole('tab', { name: /Parámetros/ })

    parametros.focus()
    await userEvent.keyboard('{ArrowRight}')
    expect(pestanaSeleccionada()).toMatch(/^Categorías/)
    expect(screen.getByRole('tab', { name: /Categorías/ })).toHaveFocus()
    expect(enrutador.state.location.search).toBe('?pestana=categorias')

    await userEvent.keyboard('{End}')
    expect(pestanaSeleccionada()).toMatch(/^Grupos/)

    await userEvent.keyboard('{ArrowRight}')
    expect(pestanaSeleccionada()).toMatch(/^Parámetros/)

    await userEvent.keyboard('{ArrowLeft}')
    expect(pestanaSeleccionada()).toMatch(/^Grupos/)

    await userEvent.keyboard('{Home}')
    expect(pestanaSeleccionada()).toMatch(/^Parámetros/)
  })

  it('RF-004, RF-005, RF-100: el encabezado muestra los contadores reales', async () => {
    montarEstructura()

    expect(
      await screen.findByRole('heading', { level: 1, name: /Estructura y parámetros/ }),
    ).toHaveTextContent('Temporada 2026–2027')
    const contadores = screen.getByText('Categorías asignadas:').closest('dl') as HTMLElement
    expect(within(contadores).getByText('2')).toBeInTheDocument()
    expect(within(contadores).getByText('3')).toBeInTheDocument()
    expect(within(contadores).getByText('versión 2')).toBeInTheDocument()
    expect(screen.getByText('Configuración')).toBeInTheDocument()
  })

  it('RF-100: sin parámetros el encabezado dice «Sin parámetros»', async () => {
    get.mockImplementation(
      resolverGet(
        respuestasPorDefecto({ 'temporadas/t-1': { ...TEMPORADA, versionParametros: undefined } }),
      ),
    )
    montarEstructura()

    expect(await screen.findByText('Sin parámetros')).toBeInTheDocument()
  })

  it('VAL-024: un 404 muestra ErrorConsulta con el regreso a Temporadas', async () => {
    get.mockImplementation(
      resolverGet(
        respuestasPorDefecto({
          'temporadas/t-1': errorApi(404, 'NO_ENCONTRADO', 'No existe.'),
        }),
      ),
    )
    montarEstructura()

    expect(
      await screen.findByText(/No encontramos esta información/),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver a temporadas' })).toHaveAttribute(
      'href',
      '/app/administracion/temporadas',
    )
  })

  it('RF-003: una temporada cerrada avisa y no ofrece edición en ninguna pestaña', async () => {
    get.mockImplementation(
      resolverGet(respuestasPorDefecto({ 'temporadas/t-1': { ...TEMPORADA, estado: 'CERRADA' } })),
    )
    montarEstructura()

    expect(
      await screen.findByText('Temporada cerrada: solo lectura (RF-003).'),
    ).toBeInTheDocument()

    // Parámetros: resumen sin formulario.
    expect(await screen.findByRole('heading', { name: 'Puntuación' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Guardar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: /Categorías/ }))
    await screen.findByRole('heading', { name: 'Catálogo de categorías de la liga' })
    expect(screen.queryByRole('button', { name: 'Nueva categoría' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^(Subir|Bajar|Editar|Desactivar|Reactivar|Asignar)/ })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: /Grupos/ }))
    await screen.findByRole('heading', { name: 'Grupos por categoría' })
    await waitFor(() => expect(screen.getByText(/Libre/, { selector: 'h3' })).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /Renombrar|Agregar grupo/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Solo consulta')).not.toBeInTheDocument()
  })

  it('D8: sin EDITAR las pestañas dicen «Solo consulta»', async () => {
    sesion.yo.permisos = permisos(CONSULTAR)
    montarEstructura('?pestana=grupos')

    expect(await screen.findByText('Solo consulta')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Renombrar/ })).not.toBeInTheDocument()
  })

  it('B-03.2: sin permiso de interpretación la pestaña no existe y la URL abre Parámetros', async () => {
    montarEstructura('?pestana=interpretaciones')

    await screen.findByRole('tab', { name: /Parámetros/ })
    expect(screen.queryByRole('tab', { name: /Interpretaciones/ })).not.toBeInTheDocument()
    expect(pestanaSeleccionada()).toMatch(/^Parámetros/)
    expect(get).not.toHaveBeenCalledWith(
      expect.stringContaining('interpretaciones-regla'),
    )
  })

  it('B-03.2: con APROBAR la pestaña aparece al final y la URL la abre', async () => {
    sesion.yo.permisos = permisos(CONSULTAR, APROBAR_INTERPRETACION)
    montarEstructura('?pestana=interpretaciones')

    expect(await screen.findByRole('tab', { name: /Interpretaciones/ })).toBeInTheDocument()
    expect(pestanaSeleccionada()).toMatch(/^Interpretaciones/)
    expect(
      await screen.findByRole('heading', { name: 'Interpretaciones de regla' }),
    ).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith(
      'interpretaciones-regla?temporadaId=t-1&pagina=0&tamano=10&estado=PENDIENTE',
    )
  })
})
