import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaComunicados } from '@/features/publicacion/PantallaComunicados'
import type { Comunicado } from '@/features/publicacion/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
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

const REDACTAR = 'publicacion.comunicado:REDACTAR'
const APROBAR = 'publicacion.comunicado:APROBAR'
const RUTA = '/app/administracion/comunicados'

const BORRADOR: Comunicado = {
  id: 'c-borrador',
  titulo: 'Aviso de mantenimiento de canchas',
  cuerpo: 'Las canchas 1 y 2 estarán cerradas el sábado por mantenimiento.',
  estadoPublicacion: 'BORRADOR',
  redactadoPor: 'perfil-1',
  creadoEn: '2026-11-01T10:00:00-06:00',
  actualizadoEn: '2026-11-02T09:15:00-06:00',
}

const REVISION: Comunicado = {
  id: 'c-revision',
  titulo: 'Suspensión preventiva de canchas',
  cuerpo: 'Se suspende el uso de las canchas 1 y 2 mientras se revisa el drenaje.',
  estadoPublicacion: 'REVISION',
  redactadoPor: 'perfil-2',
  creadoEn: '2026-11-01T11:00:00-06:00',
  actualizadoEn: '2026-11-02T10:30:00-06:00',
}

const PUBLICADO: Comunicado = {
  id: 'c-publicado',
  titulo: 'Convocatoria a asamblea de delegados',
  cuerpo: 'La asamblea se realizará el 10 de noviembre en la unidad deportiva.',
  estadoPublicacion: 'PUBLICADO',
  redactadoPor: 'perfil-2',
  aprobadoPor: 'perfil-3',
  aprobadoEn: '2026-11-02T12:00:00-06:00',
  publicadoEn: '2026-11-02T12:00:05-06:00',
  creadoEn: '2026-10-30T09:00:00-06:00',
  actualizadoEn: '2026-11-02T12:00:05-06:00',
}

const NUEVO: Comunicado = {
  ...BORRADOR,
  id: 'c-nuevo',
  titulo: 'Comunicado de prueba',
  cuerpo: 'Contenido del comunicado de prueba.',
  creadoEn: '2026-11-03T08:00:00-06:00',
  actualizadoEn: '2026-11-03T08:00:00-06:00',
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-11',
    ambito: 'LIGA',
    clubId: null,
  }))
}

function pagina(contenido: Comunicado[], extra: object = {}) {
  return {
    contenido,
    pagina: 0,
    tamano: 20,
    totalElementos: contenido.length,
    totalPaginas: contenido.length ? 1 : 0,
    ...extra,
  }
}

function error(status: number, codigo: string, detail: string) {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

type Respuesta = {
  lista?: unknown
  detalles?: Record<string, unknown>
}

function responder({ lista = pagina([BORRADOR, REVISION, PUBLICADO]), detalles = {} }: Respuesta = {}) {
  const porId: Record<string, unknown> = {
    [BORRADOR.id]: BORRADOR,
    [REVISION.id]: REVISION,
    [PUBLICADO.id]: PUBLICADO,
    [NUEVO.id]: NUEVO,
    ...detalles,
  }

  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('comunicados?')) {
      if (lista instanceof Error) {
        throw lista
      }
      return lista
    }

    if (ruta.startsWith('comunicados/')) {
      const id = decodeURIComponent(ruta.slice('comunicados/'.length))
      const respuesta = porId[id]

      if (respuesta instanceof Error) {
        throw respuesta
      }
      if (!respuesta) {
        throw error(404, 'NO_ENCONTRADO', 'No encontramos el comunicado.')
      }
      return respuesta
    }

    throw new Error(`Ruta inesperada: ${ruta}`)
  })
}

function montar() {
  const enrutador = createMemoryRouter(
    [{ path: RUTA, element: <PantallaComunicados /> }],
    { initialEntries: [RUTA] },
  )

  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

async function bandeja(): Promise<HTMLElement> {
  return screen.findByRole('region', { name: 'Bandeja de comunicados' })
}

async function detalle(): Promise<HTMLElement> {
  return screen.findByRole('region', { name: /Comunicado|Editar comunicado|Redactar comunicado/ })
}

describe('PantallaComunicados', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    otorgar(REDACTAR)
    responder()
  })

  it('B-27.2: muestra la bandeja con los estados Borrador, En revisión y Publicado', async () => {
    montar()

    const lista = await bandeja()

    const filaBorrador = await within(lista).findByRole('button', {
      name: /Aviso de mantenimiento de canchas/,
    })
    const filaRevision = within(lista).getByRole('button', {
      name: /Suspensión preventiva de canchas/,
    })
    const filaPublicado = within(lista).getByRole('button', {
      name: /Convocatoria a asamblea de delegados/,
    })

    expect(within(filaBorrador).getByText('Borrador')).toBeInTheDocument()
    expect(within(filaRevision).getByText('En revisión')).toBeInTheDocument()
    expect(within(filaPublicado).getByText('Publicado')).toBeInTheDocument()
    expect(within(lista).getByText('3 comunicados')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('comunicados?pagina=0&tamano=20')
    expect(await detalle()).toHaveTextContent('Editar comunicado')
  })

  it('B-27.2: el Secretario redacta y guarda un borrador', async () => {
    responder({ lista: pagina([]) })
    post.mockResolvedValue(NUEVO)
    montar()

    expect(await screen.findByText('Aún no hay comunicados')).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: 'Redactar comunicado' })[0])

    const editor = await screen.findByRole('region', { name: 'Redactar comunicado' })
    await userEvent.type(within(editor).getByLabelText(/Título/), 'Comunicado de prueba')
    await userEvent.type(
      within(editor).getByLabelText(/Cuerpo del comunicado/),
      'Contenido del comunicado de prueba.',
    )
    await userEvent.click(within(editor).getByRole('button', { name: 'Guardar borrador' }))

    expect(post).toHaveBeenCalledWith('comunicados', {
      titulo: 'Comunicado de prueba',
      cuerpo: 'Contenido del comunicado de prueba.',
    })
    expect(
      await screen.findByText('«Comunicado de prueba» se guardó como borrador.'),
    ).toBeInTheDocument()
  })

  it('B-27.2: al editar envía solo los campos que cambiaron', async () => {
    const actualizado = { ...BORRADOR, titulo: 'Aviso de mantenimiento actualizado' }
    patch.mockResolvedValue(actualizado)
    montar()

    const editor = await screen.findByRole('region', { name: 'Editar comunicado' })
    const titulo = within(editor).getByLabelText(/Título/)

    await userEvent.clear(titulo)
    await userEvent.type(titulo, 'Aviso de mantenimiento actualizado')
    await userEvent.click(within(editor).getByRole('button', { name: 'Guardar cambios' }))

    expect(patch).toHaveBeenCalledWith('comunicados/c-borrador', {
      titulo: 'Aviso de mantenimiento actualizado',
    })
    expect(
      await screen.findByText('«Aviso de mantenimiento actualizado» se actualizó.'),
    ).toBeInTheDocument()
  })

  it('B-27.2: enviar a revisión pide confirmación y deja el comunicado en revisión', async () => {
    const enRevision = { ...BORRADOR, estadoPublicacion: 'REVISION' as const }
    post.mockResolvedValue(enRevision)
    montar()

    const editor = await screen.findByRole('region', { name: 'Editar comunicado' })
    await userEvent.click(within(editor).getByRole('button', { name: 'Enviar a revisión' }))

    const dialogo = screen.getByRole('dialog', { name: 'Enviar a revisión' })
    expect(within(dialogo).getByText(/dejará de ser editable/)).toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar a revisión' }))

    expect(post).toHaveBeenCalledWith('comunicados/c-borrador/envio')
    expect(
      await screen.findByText(
        '«Aviso de mantenimiento de canchas» se envió a revisión de la Presidencia.',
      ),
    ).toBeInTheDocument()
    expect(await screen.findByText(/En revisión por la Presidencia/)).toBeInTheDocument()
  })

  it('B-27.2: el Presidente aprueba y publica un comunicado en revisión', async () => {
    otorgar(APROBAR)
    responder({ lista: pagina([REVISION]) })
    post.mockResolvedValue({
      ...REVISION,
      estadoPublicacion: 'PUBLICADO',
      aprobadoPor: 'perfil-1',
      aprobadoEn: '2026-11-03T09:00:00-06:00',
      publicadoEn: '2026-11-03T09:00:05-06:00',
    })
    montar()

    const comunicado = await detalle()
    await userEvent.click(
      within(comunicado).getByRole('button', { name: 'Aprobar y publicar' }),
    )

    const dialogo = screen.getByRole('dialog', { name: 'Aprobar y publicar' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar y publicar' }))

    expect(post).toHaveBeenCalledWith('comunicados/c-revision/resolucion', {
      decision: 'PUBLICADO',
    })
    expect(
      await screen.findByText('«Suspensión preventiva de canchas» se aprobó y publicó.'),
    ).toBeInTheDocument()
  })

  it('B-27.2: devolver exige observaciones de al menos 10 caracteres', async () => {
    otorgar(APROBAR)
    responder({ lista: pagina([REVISION]) })
    post.mockResolvedValue({
      ...REVISION,
      estadoPublicacion: 'BORRADOR',
      observaciones: 'Falta explicar el alcance de la suspensión.',
    })
    montar()

    const comunicado = await detalle()
    await userEvent.click(
      within(comunicado).getByRole('button', { name: 'Devolver con observaciones' }),
    )

    const dialogo = screen.getByRole('dialog', { name: 'Devolver a borrador' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Devolver a borrador' }))

    expect(
      within(dialogo).getByText(
        'Escribe observaciones de al menos 10 caracteres para devolver el comunicado.',
      ),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()

    await userEvent.type(
      within(dialogo).getByLabelText(/Observaciones para la Secretaría/),
      'Falta explicar el alcance de la suspensión.',
    )
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Devolver a borrador' }))

    expect(post).toHaveBeenCalledWith('comunicados/c-revision/resolucion', {
      decision: 'BORRADOR',
      observaciones: 'Falta explicar el alcance de la suspensión.',
    })
    expect(
      await screen.findByText(
        '«Suspensión preventiva de canchas» se devolvió a borrador con observaciones.',
      ),
    ).toBeInTheDocument()
  })

  it('RF-090: quien redactó no puede aprobar su propio comunicado', async () => {
    otorgar(REDACTAR, APROBAR)
    responder({
      lista: pagina([{ ...REVISION, redactadoPor: 'perfil-1' }]),
      detalles: { [REVISION.id]: { ...REVISION, redactadoPor: 'perfil-1' } },
    })
    montar()

    const comunicado = await detalle()

    expect(
      within(comunicado).getByText('No puedes aprobar tu propio comunicado'),
    ).toBeInTheDocument()
    expect(
      within(comunicado).queryByRole('button', { name: 'Aprobar y publicar' }),
    ).not.toBeInTheDocument()
  })

  it('B-27.2: el borrador devuelto muestra las observaciones en el editor', async () => {
    const devuelto = {
      ...BORRADOR,
      observaciones: 'Falta explicar el alcance de la suspensión.',
    }
    responder({
      lista: pagina([devuelto]),
      detalles: { [devuelto.id]: devuelto },
    })
    montar()

    const editor = await screen.findByRole('region', { name: 'Editar comunicado' })

    expect(
      within(editor).getByText('Observaciones de la Presidencia'),
    ).toBeInTheDocument()
    expect(
      within(editor).getByText('Falta explicar el alcance de la suspensión.'),
    ).toBeInTheDocument()
  })

  it('B-27.2: la vista previa avisa que el comunicado aún no se publica', async () => {
    montar()

    const editor = await screen.findByRole('region', { name: 'Editar comunicado' })
    await userEvent.click(within(editor).getByRole('tab', { name: 'Vista previa' }))

    expect(
      within(editor).getByText(/Esta es una vista previa/),
    ).toBeInTheDocument()
    expect(
      within(editor).getByRole('heading', { name: 'Aviso de mantenimiento de canchas' }),
    ).toBeInTheDocument()
  })

  it('RF-090: un error del API al guardar se muestra en el editor', async () => {
    patch.mockRejectedValue(
      error(409, 'RF-090', 'Solo se edita un comunicado en borrador; este está en revisión.'),
    )
    montar()

    const editor = await screen.findByRole('region', { name: 'Editar comunicado' })
    const titulo = within(editor).getByLabelText(/Título/)

    await userEvent.clear(titulo)
    await userEvent.type(titulo, 'Otro título')
    await userEvent.click(within(editor).getByRole('button', { name: 'Guardar cambios' }))

    expect(await within(editor).findByRole('alert')).toHaveTextContent(
      'Solo se edita un comunicado en borrador; este está en revisión.',
    )
  })

  it('VAL-015: sin permiso muestra la 403 y no pide datos', () => {
    otorgar()
    montar()

    expect(
      screen.getByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })
})
