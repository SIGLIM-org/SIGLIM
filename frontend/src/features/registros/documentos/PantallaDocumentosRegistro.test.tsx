import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ACTA,
  archivo,
  detalle,
  documento,
  error,
  permisos,
  requisito,
  requisitos,
} from '@/features/registros/datosPruebaRegistros'
import { PantallaDocumentosRegistro } from '@/features/registros/documentos/PantallaDocumentosRegistro'
import { RUTA_DOCUMENTOS_REGISTRO, rutaDocumentosRegistro } from '@/features/registros/rutas'
import type { RegistroJugadorDetalle, RequisitosRegistro } from '@/features/registros/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import type { ApiError } from '@/lib/api/ApiError'

const { get, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Directiva Halcones', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CREAR = 'registros.documento:CREAR'
const CONSULTAR = 'registros.documento:CONSULTAR'

let servidor: {
  registro: RegistroJugadorDetalle | ApiError
  requisitos: RequisitosRegistro
  carga: unknown
}

function responder(cambios: Partial<typeof servidor> = {}) {
  servidor = {
    registro: detalle(),
    requisitos: requisitos([
      requisito({ tipoDocumento: ACTA, documentos: [documento({ id: 'doc-acta', tipoDocumento: ACTA })] }),
      requisito({ cantidad: 2 }),
    ]),
    carga: documento({ id: 'doc-nuevo' }),
    ...cambios,
  }

  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'registros-jugador/reg-1') {
      if (servidor.registro instanceof Error) {
        throw servidor.registro
      }
      return servidor.registro
    }
    if (ruta === 'registros-jugador/reg-1/requisitos') {
      return servidor.requisitos
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })
  post.mockImplementation(async () => {
    if (servidor.carga instanceof Error) {
      throw servidor.carga
    }
    return servidor.carga
  })
}

function montar(estado?: unknown) {
  const enrutador = createMemoryRouter(
    [{ path: RUTA_DOCUMENTOS_REGISTRO, element: <PantallaDocumentosRegistro /> }],
    { initialEntries: [{ pathname: rutaDocumentosRegistro('reg-1'), state: estado }] },
  )

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

describe('PantallaDocumentosRegistro (F-18 paso 4, H-10.3)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    sesion.yo.permisos = permisos(CREAR, CONSULTAR)
    responder()
    // jsdom no implementa las URL de objetos locales.
    URL.createObjectURL = vi.fn(() => 'blob:vista-previa')
    URL.revokeObjectURL = vi.fn()
  })

  it('VAL-015: sin registros.documento:CREAR muestra la 403 y no pide datos', () => {
    sesion.yo.permisos = permisos(CONSULTAR)
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('DEC-112: muestra los requisitos con sus documentos, «No oficial» y los lugares libres', async () => {
    montar()

    const foto = await screen.findByRole('region', { name: 'Fotografía' })
    expect(screen.getByRole('heading', { name: 'Ana López Ruiz' })).toBeInTheDocument()
    expect(screen.getByText('LORA900501MMNPZN05')).toBeInTheDocument()
    expect(within(foto).getAllByText('Sin cargar')).toHaveLength(2)
    expect(within(foto).getByText('No oficial')).toBeInTheDocument()
    const acta = screen.getByRole('region', { name: 'Acta de nacimiento' })
    expect(within(acta).getByText('Por revisar')).toBeInTheDocument()
    expect(within(acta).getByRole('button', { name: 'Reemplazar Acta de nacimiento' })).toBeInTheDocument()
    expect(within(acta).getByRole('button', { name: 'Ver Acta de nacimiento' })).toBeInTheDocument()
    expect(within(acta).queryByRole('button', { name: /^Cargar/ })).not.toBeInTheDocument()
  })

  it('RF-108: Cargar → elegir imagen → vista previa → Subir envía archivo y tipoDocumentoId', async () => {
    const user = userEvent.setup()
    montar()

    await user.click((await screen.findAllByRole('button', { name: 'Cargar Fotografía' }))[0])
    const foto = archivo('foto.jpg', 'image/jpeg', 200 * 1024)
    await user.upload(screen.getByLabelText('Archivo de Fotografía'), foto)

    expect(screen.getByRole('img', { name: 'Vista previa de Fotografía' })).toHaveAttribute(
      'src',
      'blob:vista-previa',
    )
    expect(URL.createObjectURL).toHaveBeenCalledWith(foto)
    await user.click(screen.getByRole('button', { name: 'Subir' }))

    expect(post).toHaveBeenCalledTimes(1)
    const [ruta, cuerpo] = post.mock.calls[0] as [string, FormData]
    expect(ruta).toBe('registros-jugador/reg-1/documentos')
    expect(cuerpo.get('archivo')).toBe(foto)
    expect(cuerpo.get('tipoDocumentoId')).toBe('5')
    expect(await screen.findByText(/el archivo se subió/)).toBeInTheDocument()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:vista-previa')
  })

  it('RF-108: un archivo de más de 5 MB no se envía', async () => {
    const user = userEvent.setup()
    montar()

    await user.click((await screen.findAllByRole('button', { name: 'Cargar Fotografía' }))[0])
    await user.upload(
      screen.getByLabelText('Archivo de Fotografía'),
      archivo('grande.png', 'image/png', 6 * 1024 * 1024),
    )

    expect(screen.getByRole('alert')).toHaveTextContent(/más de 5 MB/)
    expect(screen.queryByRole('button', { name: 'Subir' })).not.toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('RF-108: el reemplazo de un PDF muestra nombre y tamaño y usa /reemplazo', async () => {
    const user = userEvent.setup()
    montar()

    await user.click(await screen.findByRole('button', { name: 'Reemplazar Acta de nacimiento' }))
    await user.upload(
      screen.getByLabelText('Archivo nuevo de Acta de nacimiento'),
      archivo('acta.pdf', 'application/pdf', 300 * 1024),
    )
    expect(screen.getByText('acta.pdf')).toBeInTheDocument()
    expect(screen.getByText('300 KB')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Subir' }))

    expect(post.mock.calls[0][0]).toBe('documentos/doc-acta/reemplazo')
  })

  it('DEC-114: un 409 DUPLICADO muestra el detail y recarga los requisitos', async () => {
    const user = userEvent.setup()
    responder({ carga: error(409, 'DUPLICADO', 'Ya se cargaron las fotografías que pide la categoría.') })
    montar()

    await user.click((await screen.findAllByRole('button', { name: 'Cargar Fotografía' }))[0])
    await user.upload(screen.getByLabelText('Archivo de Fotografía'), archivo('foto.jpg', 'image/jpeg'))
    const llamadasAntes = get.mock.calls.length
    await user.click(screen.getByRole('button', { name: 'Subir' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Ya se cargaron las fotografías que pide la categoría.',
    )
    expect(get.mock.calls.slice(llamadasAntes).map(([ruta]) => ruta)).toContain(
      'registros-jugador/reg-1/requisitos',
    )
  })

  it('DEC-112: un 413 del servidor se muestra junto al archivo', async () => {
    const user = userEvent.setup()
    responder({ carga: error(413, 'ARCHIVO_DEMASIADO_GRANDE', 'El archivo supera 5 MB.') })
    montar()

    await user.click((await screen.findAllByRole('button', { name: 'Cargar Fotografía' }))[0])
    await user.upload(screen.getByLabelText('Archivo de Fotografía'), archivo('foto.jpg', 'image/jpeg'))
    await user.click(screen.getByRole('button', { name: 'Subir' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El archivo supera 5 MB.')
    expect(screen.getByLabelText('Archivo de Fotografía')).toHaveAttribute('aria-invalid', 'true')
  })

  it('DEC-112: un registro ACTIVO es de solo lectura, con observaciones y «Ver»', async () => {
    responder({
      registro: detalle({ estado: 'ACTIVO', observaciones: 'Documentación completa.' }),
    })
    montar()

    expect(await screen.findByText(/La liga ya validó este registro/)).toBeInTheDocument()
    expect(screen.getByText('Documentación completa.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Cargar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Reemplazar/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver Acta de nacimiento' })).toBeInTheDocument()
  })

  it('DEC-112: una categoría sin requisitos lo dice', async () => {
    responder({ requisitos: requisitos([]) })
    montar()

    expect(await screen.findByText('Esta categoría no pide documentos')).toBeInTheDocument()
  })

  it('DEC-111: con desdeAlta se ve el paso 4 y «Dar de alta a otro jugador»', async () => {
    montar({ desdeAlta: true })

    await screen.findByRole('region', { name: 'Fotografía' })
    expect(screen.getByRole('listitem', { current: 'step' })).toHaveTextContent('Documentos')
    expect(screen.getByRole('link', { name: 'Dar de alta a otro jugador' })).toHaveAttribute(
      'href',
      '/app/organizacion/jugadores/alta',
    )
    expect(screen.getByRole('link', { name: 'Volver' })).toHaveAttribute('href', '/app/organizacion')
  })

  it('DEC-111: sin desdeAlta (recarga o entrada posterior) no hay indicador de pasos', async () => {
    montar()

    await screen.findByRole('region', { name: 'Fotografía' })
    expect(screen.queryByRole('navigation', { name: 'Pasos del alta' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Dar de alta a otro jugador' })).not.toBeInTheDocument()
  })

  it('VAL-024: un registro fuera de ámbito muestra el 404 sin revelar si existe', async () => {
    responder({ registro: error(404, 'NO_ENCONTRADO') })
    montar()

    expect(await screen.findByText(/No encontramos esta información/)).toBeInTheDocument()
  })
})
