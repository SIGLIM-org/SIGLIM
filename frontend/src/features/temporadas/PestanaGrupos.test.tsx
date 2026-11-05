import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CONSULTAR,
  EDITAR,
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

async function seccionDe(categoria: string): Promise<HTMLElement> {
  const titulo = await screen.findByRole('heading', { name: new RegExp(`^${categoria} ·`) })
  return titulo.closest('section') as HTMLElement
}

describe('PestanaGrupos', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    sesion.yo.permisos = permisos(CONSULTAR, EDITAR)
    get.mockImplementation(resolverGet(respuestasPorDefecto()))
  })

  it('RF-005: una sección por categoría con sus grupos y equipos', async () => {
    montarEstructura('?pestana=grupos')

    const libre = await seccionDe('Libre')
    expect(within(libre).getByRole('heading')).toHaveTextContent('Libre · 2 grupos · 14 equipos')
    expect(within(libre).getByText('8 equipos')).toBeInTheDocument()
    expect(await seccionDe('Veteranos')).toHaveTextContent('Veteranos · 1 grupo · 5 equipos')
  })

  it('RF-005: agregar grupo hace POST grupos con categoriaTemporadaId', async () => {
    post.mockResolvedValue({ id: 'g-c', nombre: 'C', equipos: 0, categoriaTemporadaId: 'ct-libre' })
    montarEstructura('?pestana=grupos')
    const libre = await seccionDe('Libre')

    await userEvent.click(within(libre).getByRole('button', { name: 'Agregar grupo a Libre' }))
    await userEvent.type(within(libre).getByLabelText(/Nombre del grupo nuevo de Libre/), ' C ')
    await userEvent.click(within(libre).getByRole('button', { name: 'Agregar' }))

    expect(post).toHaveBeenCalledWith('grupos', { categoriaTemporadaId: 'ct-libre', nombre: 'C' })
    expect(await screen.findByText('Se agregó el grupo «C» a Libre.')).toBeInTheDocument()
  })

  it('RF-005: renombrar hace PATCH con el nombre nuevo', async () => {
    patch.mockResolvedValue({ id: 'g-a', nombre: 'Norte', equipos: 8, categoriaTemporadaId: 'ct-libre' })
    montarEstructura('?pestana=grupos')
    const libre = await seccionDe('Libre')

    await userEvent.click(within(libre).getByRole('button', { name: 'Renombrar el grupo A' }))
    const campo = within(libre).getByLabelText(/Nuevo nombre del grupo A/)
    await userEvent.clear(campo)
    await userEvent.type(campo, 'Norte')
    await userEvent.click(within(libre).getByRole('button', { name: 'Guardar' }))

    expect(patch).toHaveBeenCalledWith('grupos/g-a', { nombre: 'Norte' })
    expect(await screen.findByText('El grupo «A» ahora se llama «Norte».')).toBeInTheDocument()
  })

  it('RF-005: en una temporada ACTIVA no aparece «Agregar grupo», pero sí «Renombrar»', async () => {
    get.mockImplementation(
      resolverGet(respuestasPorDefecto({ 'temporadas/t-1': { ...TEMPORADA, estado: 'ACTIVA' } })),
    )
    montarEstructura('?pestana=grupos')
    const libre = await seccionDe('Libre')

    expect(screen.queryByRole('button', { name: /Agregar grupo/ })).not.toBeInTheDocument()
    expect(within(libre).getByRole('button', { name: 'Renombrar el grupo A' })).toBeInTheDocument()
    expect(
      screen.getByText(/La temporada está activa: ya no se pueden agregar grupos \(RF-005\)/),
    ).toBeInTheDocument()
  })

  it('DUPLICADO: un 409 se muestra en línea, en el campo', async () => {
    post.mockRejectedValue(errorApi(409, 'DUPLICADO', 'Ya existe el grupo «B» en Libre.'))
    montarEstructura('?pestana=grupos')
    const libre = await seccionDe('Libre')

    await userEvent.click(within(libre).getByRole('button', { name: 'Agregar grupo a Libre' }))
    const campo = within(libre).getByLabelText(/Nombre del grupo nuevo de Libre/)
    await userEvent.type(campo, 'B')
    await userEvent.click(within(libre).getByRole('button', { name: 'Agregar' }))

    expect(await within(libre).findByRole('alert')).toHaveTextContent(
      'Ya existe el grupo «B» en Libre.',
    )
    expect(campo).toHaveAttribute('aria-invalid', 'true')
  })

  it('RF-004: sin categorías asignadas ofrece ir a la pestaña Categorías', async () => {
    get.mockImplementation(
      resolverGet(respuestasPorDefecto({ 'temporadas/t-1/categorias': [] })),
    )
    montarEstructura('?pestana=grupos')

    expect(await screen.findByText('La temporada aún no tiene categorías')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Ir a Categorías' }))
    expect(
      screen.getByRole('tab', { name: /Categorías/ }),
    ).toHaveAttribute('aria-selected', 'true')
  })
})
