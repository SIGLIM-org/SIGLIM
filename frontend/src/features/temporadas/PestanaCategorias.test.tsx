import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CATALOGO,
  CATEGORIAS_TEMPORADA,
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

async function catalogo(): Promise<HTMLElement> {
  return screen.findByRole('table', { name: 'Catálogo de categorías' })
}

function filaDe(tabla: HTMLElement, nombre: string): HTMLElement {
  return within(tabla).getByText(nombre, { selector: 'span.text-headline-sm' }).closest('tr') as HTMLElement
}

describe('PestanaCategorias', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    sesion.yo.permisos = permisos(CONSULTAR, EDITAR)
    get.mockImplementation(resolverGet(respuestasPorDefecto()))
  })

  it('RF-099: muestra el catálogo con superior, estado y participación en la temporada', async () => {
    montarEstructura('?pestana=categorias')
    const tabla = await catalogo()

    expect(
      screen.getByText(/Compartido por todas las temporadas\. .*participan en Temporada 2026–2027\./),
    ).toBeInTheDocument()
    expect(within(filaDe(tabla, 'Libre')).getByText('Tope de su rama')).toBeInTheDocument()
    expect(within(filaDe(tabla, 'Libre')).getByText('Asignada')).toBeInTheDocument()
    expect(within(filaDe(tabla, 'Juvenil')).getByRole('button', { name: /Asignar/ })).toBeInTheDocument()
    expect(within(filaDe(tabla, 'Infantil antigua')).getByText('Inactiva')).toBeInTheDocument()
    expect(within(filaDe(tabla, 'Infantil antigua')).queryByRole('button', { name: /Asignar/ })).not.toBeInTheDocument()
  })

  it('RF-099: reordenar y «Guardar orden» envía todos los ids, incluidas las inactivas', async () => {
    post.mockResolvedValue(CATALOGO)
    montarEstructura('?pestana=categorias')
    await catalogo()

    await userEvent.click(screen.getByRole('button', { name: 'Bajar Libre' }))
    expect(screen.getByText('Libre, posición 2 de 4')).toBeInTheDocument()
    expect(screen.getByText('Cambiaste el orden de las categorías.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Guardar orden' }))

    expect(post).toHaveBeenCalledWith('categorias/reordenamiento', {
      ids: ['cat-juv', 'cat-libre', 'cat-vet', 'cat-inf'],
    })
    expect(await screen.findByText('Se guardó el orden de las categorías.')).toBeInTheDocument()
  })

  it('RF-099: «Descartar» vuelve al orden del servidor', async () => {
    montarEstructura('?pestana=categorias')
    await catalogo()

    await userEvent.click(screen.getByRole('button', { name: 'Subir Veteranos' }))
    await userEvent.click(screen.getByRole('button', { name: 'Descartar' }))

    expect(screen.queryByText('Cambiaste el orden de las categorías.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Subir Libre' })).toBeDisabled()
  })

  it('RF-099: crear con superior hace el POST correcto', async () => {
    post.mockResolvedValue({ ...CATALOGO[1], id: 'cat-nueva', nombre: 'Sub-17' })
    montarEstructura('?pestana=categorias')
    await catalogo()

    await userEvent.click(screen.getByRole('button', { name: 'Nueva categoría' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nueva categoría' })
    await userEvent.type(within(dialogo).getByLabelText(/Nombre/), 'Sub-17')
    await userEvent.selectOptions(within(dialogo).getByLabelText(/^Tipo/), 'LIBRE')
    await userEvent.selectOptions(within(dialogo).getByLabelText(/Categoría superior/), 'cat-juv')
    // La inactiva no se ofrece como superior.
    expect(within(dialogo).queryByRole('option', { name: /Infantil antigua/ })).not.toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(post).toHaveBeenCalledWith('categorias', {
      nombre: 'Sub-17',
      tipo: 'LIBRE',
      categoriaSuperiorId: 'cat-juv',
    })
    expect(await screen.findByText('«Sub-17» se agregó al catálogo.')).toBeInTheDocument()
  })

  it('RF-099: editar a «Ninguna» envía quitarSuperior:true', async () => {
    patch.mockResolvedValue({ ...CATALOGO[1], categoriaSuperior: undefined })
    montarEstructura('?pestana=categorias')
    await catalogo()

    await userEvent.click(screen.getByRole('button', { name: 'Editar Juvenil' }))
    const dialogo = screen.getByRole('dialog', { name: 'Editar categoría' })
    expect(within(dialogo).queryByRole('option', { name: 'Juvenil' })).not.toBeInTheDocument()
    await userEvent.selectOptions(within(dialogo).getByLabelText(/Categoría superior/), '')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(patch).toHaveBeenCalledWith('categorias/cat-juv', { quitarSuperior: true })
  })

  it('RF-099: un 409 de ciclo muestra el detail con la cadena', async () => {
    patch.mockRejectedValue(
      errorApi(409, 'RF-099', 'La categoría formaría un ciclo: Libre → Juvenil → Libre.'),
    )
    montarEstructura('?pestana=categorias')
    await catalogo()

    await userEvent.click(screen.getByRole('button', { name: 'Editar Libre' }))
    const dialogo = screen.getByRole('dialog', { name: 'Editar categoría' })
    await userEvent.selectOptions(within(dialogo).getByLabelText(/Categoría superior/), 'cat-juv')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(patch).toHaveBeenCalledWith('categorias/cat-libre', { categoriaSuperiorId: 'cat-juv' })
    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(
      'La categoría formaría un ciclo: Libre → Juvenil → Libre.',
    )
  })

  it('RF-099: desactivar envía activa:false y un 409 se explica sobre la lista', async () => {
    patch.mockRejectedValue(
      errorApi(409, 'RF-099', '«Libre» participa en una temporada en configuración.'),
    )
    montarEstructura('?pestana=categorias')
    await catalogo()

    await userEvent.click(screen.getByRole('button', { name: 'Desactivar Libre' }))

    expect(patch).toHaveBeenCalledWith('categorias/cat-libre', { activa: false })
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '«Libre» participa en una temporada en configuración.',
    )
  })

  it('RF-004: «Asignar» hace POST temporadas/{id}/categorias', async () => {
    post.mockResolvedValue({
      id: 'ct-juv',
      categoria: { id: 'cat-juv', nombre: 'Juvenil', tipo: 'LIBRE', orden: 2 },
      grupos: [],
    })
    montarEstructura('?pestana=categorias')
    await catalogo()

    await userEvent.click(screen.getByRole('button', { name: 'Asignar Juvenil a la temporada' }))

    expect(post).toHaveBeenCalledWith('temporadas/t-1/categorias', { categoriaId: 'cat-juv' })
    expect(
      await screen.findByText('«Juvenil» se asignó a Temporada 2026–2027.'),
    ).toBeInTheDocument()
  })

  it('RF-004: en una temporada ACTIVA no aparece «Asignar» y se explica por qué', async () => {
    get.mockImplementation(
      resolverGet(
        respuestasPorDefecto({
          'temporadas/t-1': { ...TEMPORADA, estado: 'ACTIVA' },
          'temporadas/t-1/categorias': CATEGORIAS_TEMPORADA,
        }),
      ),
    )
    montarEstructura('?pestana=categorias')
    await catalogo()

    expect(screen.queryByRole('button', { name: /Asignar/ })).not.toBeInTheDocument()
    expect(
      screen.getByText(/La temporada está activa: ya no se pueden asignar categorías \(RF-004\)/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar Libre' })).toBeInTheDocument()
  })
})
