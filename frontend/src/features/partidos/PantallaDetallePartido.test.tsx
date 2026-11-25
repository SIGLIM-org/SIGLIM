import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaDetallePartido } from '@/features/partidos/PantallaDetallePartido'
import type { AccionPartido, PartidoDetalle } from '@/features/partidos/tipos'
import type { EstadoJornada, EstadoPartido } from '@/features/programacion/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'partidos.partido:CONSULTAR'
const CAMBIAR_ESTADO = 'partidos.partido:CAMBIAR_ESTADO'
const MOTIVO = 'Tormenta eléctrica en la unidad deportiva'

function partido({
  estado = 'PROGRAMADO',
  acciones = ['REPROGRAMAR', 'CANCELAR'],
  estadoJornada = 'PUBLICADO',
}: { estado?: EstadoPartido; acciones?: AccionPartido[]; estadoJornada?: EstadoJornada } = {}): PartidoDetalle {
  return {
    id: 'p1',
    jornada: { id: 'j1', numero: 8, estadoPublicacion: estadoJornada },
    temporada: { id: 't1', nombre: 'Temporada 2026-2027' },
    categoria: { id: 'c1', nombre: 'Libre' },
    grupo: { id: 'g1', nombre: 'Grupo A' },
    equipoLocal: { id: 'e1', nombre: 'Halcones' },
    equipoVisitante: { id: 'e2', nombre: 'Toros' },
    cancha: { id: 'k1', nombre: 'Morelos 2' },
    inicio: '2031-03-09T10:00:00-06:00',
    finBloque: '2031-03-09T12:00:00-06:00',
    estado,
    arbitros: [
      { id: 'a1', nombre: 'Roberto Mendoza', funcion: 'CENTRAL' },
      { id: 'a2', nombre: null, funcion: 'ASISTENTE' },
    ],
    cedula: null,
    historial: [
      {
        estadoAnterior: null,
        estadoNuevo: 'PROGRAMADO',
        motivo: 'Programación del partido',
        cambiadoPor: { id: 'pr1', nombre: 'Programador' },
        cambiadoEn: '2031-03-01T10:00:00-06:00',
      },
    ],
    accionesPermitidas: acciones,
  }
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({ codigo, modulo: 'M-08', ambito: 'LIGA', clubId: null }))
}

function montar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const router = createMemoryRouter([{ path: '/app/partidos/:partidoId', element: <PantallaDetallePartido /> }], {
    initialEntries: ['/app/partidos/p1'],
  })

  return render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  sesion.yo.permisos = []
})

describe('PantallaDetallePartido (F-25, H-15.3)', () => {
  it('UI-010: muestra el encabezado, el estado, los árbitros y que la cédula aún no se abre', async () => {
    otorgar(CONSULTAR, CAMBIAR_ESTADO)
    get.mockResolvedValue(partido())
    montar()

    const titulo = await screen.findByRole('heading', { level: 1, name: 'Halcones vs. Toros' })
    expect(get).toHaveBeenCalledWith('partidos/p1')
    const encabezado = within(titulo.closest('header') as HTMLElement)
    expect(encabezado.getByText('Programado')).toBeInTheDocument()
    expect(encabezado.getByText('Morelos 2')).toBeInTheDocument()
    expect(encabezado.getByText('10:00 – 12:00')).toBeInTheDocument()
    expect(encabezado.getByText('Libre · Grupo A')).toBeInTheDocument()
    expect(screen.getByText('Roberto Mendoza')).toBeInTheDocument()
    expect(screen.getByText('Nombre reservado')).toBeInTheDocument()
    expect(screen.getByText('La cédula se abre cuando el partido está en curso.')).toBeInTheDocument()
  })

  it('RF-046: la barra muestra solo las acciones permitidas del API', async () => {
    otorgar(CONSULTAR, CAMBIAR_ESTADO)
    get.mockResolvedValue(partido({ estado: 'EN_CURSO', acciones: ['SUSPENDER'] }))
    montar()

    const barra = await screen.findByRole('region', { name: 'Acciones del partido' })
    expect(within(barra).getByRole('button', { name: 'Suspender' })).toBeInTheDocument()
    expect(within(barra).queryByRole('button', { name: 'Reprogramar' })).not.toBeInTheDocument()
    expect(within(barra).queryByRole('button', { name: 'Cancelar partido' })).not.toBeInTheDocument()
  })

  it('RF-046: sin acciones explica por qué a quien puede cambiar estados, y nada a quien solo consulta', async () => {
    otorgar(CONSULTAR, CAMBIAR_ESTADO)
    get.mockResolvedValue(partido({ estadoJornada: 'BORRADOR', acciones: [] }))
    const { unmount } = montar()

    expect(
      await screen.findByText(/La jornada 8 todavía no está publicada: ajusta el partido desde la programación/),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reprogramar' })).not.toBeInTheDocument()
    unmount()

    otorgar(CONSULTAR)
    get.mockResolvedValue(partido({ estado: 'CANCELADO', acciones: [] }))
    montar()

    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument()
    expect(screen.queryByText('Este estado ya no admite cambios manuales.')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Suspender|Reprogramar|Cancelar partido/ })).not.toBeInTheDocument()
  })

  it('RF-043: reprograma con nueva fecha, cancha y motivo y muestra el detalle actualizado', async () => {
    const usuario = userEvent.setup()
    otorgar(CONSULTAR, CAMBIAR_ESTADO)
    get.mockImplementation((ruta: string) =>
      Promise.resolve(
        ruta.startsWith('canchas')
          ? { contenido: [{ id: 'k2', nombre: 'Morelos 3' }], pagina: 0, tamano: 100, totalElementos: 1, totalPaginas: 1 }
          : partido(),
      ),
    )
    post.mockResolvedValue(partido({ estado: 'REPROGRAMADO', acciones: [] }))
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Reprogramar' }))
    const modal = await screen.findByRole('dialog', { name: 'Reprogramar partido' })
    const inicio = within(modal).getByLabelText(/Nueva fecha y hora/)
    await usuario.clear(inicio)
    await usuario.type(inicio, '2031-03-16T12:00')
    await usuario.selectOptions(await within(modal).findByLabelText('Cancha'), 'k2')
    await usuario.type(within(modal).getByLabelText(/Motivo/), MOTIVO)
    await usuario.click(within(modal).getByRole('button', { name: 'Reprogramar' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('partidos/p1/transiciones', {
        estadoNuevo: 'REPROGRAMADO',
        motivo: MOTIVO,
        inicio: '2031-03-16T12:00:00-06:00',
        canchaId: 'k2',
      }),
    )
    expect(await screen.findByText('Reprogramado')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('RN-021: no reprograma con un motivo corto ni sin fecha futura', async () => {
    const usuario = userEvent.setup()
    otorgar(CONSULTAR, CAMBIAR_ESTADO)
    get.mockImplementation((ruta: string) =>
      Promise.resolve(
        ruta.startsWith('canchas') ? { contenido: [], pagina: 0, tamano: 100, totalElementos: 0, totalPaginas: 0 } : partido(),
      ),
    )
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Reprogramar' }))
    const modal = await screen.findByRole('dialog', { name: 'Reprogramar partido' })
    const inicio = within(modal).getByLabelText(/Nueva fecha y hora/)
    await usuario.clear(inicio)
    await usuario.type(inicio, '2001-01-01T10:00')
    await usuario.type(within(modal).getByLabelText(/Motivo/), 'lluvia')
    await usuario.click(within(modal).getByRole('button', { name: 'Reprogramar' }))

    expect(await within(modal).findByText('La nueva fecha y hora debe ser posterior a este momento.')).toBeInTheDocument()
    expect(within(modal).getByText('Escribe un motivo de al menos 10 caracteres.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('VAL-004: el conflicto de la nueva fecha se muestra en el modal sin cerrarlo', async () => {
    const usuario = userEvent.setup()
    otorgar(CONSULTAR, CAMBIAR_ESTADO)
    get.mockImplementation((ruta: string) =>
      Promise.resolve(
        ruta.startsWith('canchas') ? { contenido: [], pagina: 0, tamano: 100, totalElementos: 0, totalPaginas: 0 } : partido(),
      ),
    )
    post.mockRejectedValue(
      new ApiError({
        title: 'Conflicto',
        status: 409,
        codigo: 'VAL-004',
        detail: 'La cancha Morelos 2 ya está ocupada de 10:00 a 12:00 por Pumas vs. Lobos.',
      }),
    )
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Reprogramar' }))
    const modal = await screen.findByRole('dialog', { name: 'Reprogramar partido' })
    const inicio = within(modal).getByLabelText(/Nueva fecha y hora/)
    await usuario.clear(inicio)
    await usuario.type(inicio, '2031-03-16T10:00')
    await usuario.type(within(modal).getByLabelText(/Motivo/), MOTIVO)
    await usuario.click(within(modal).getByRole('button', { name: 'Reprogramar' }))

    expect(await within(modal).findByRole('alert')).toHaveTextContent('ya está ocupada de 10:00 a 12:00')
    expect(post.mock.calls[0][1]).not.toHaveProperty('canchaId')
  })

  it('RF-043: suspende con motivo y la respuesta actualiza el estado y el historial', async () => {
    const usuario = userEvent.setup()
    otorgar(CONSULTAR, CAMBIAR_ESTADO)
    get.mockResolvedValue(partido({ estado: 'EN_CURSO', acciones: ['SUSPENDER'] }))
    const suspendido = partido({ estado: 'SUSPENDIDO', acciones: ['REPROGRAMAR', 'CANCELAR'] })
    suspendido.historial = [
      ...suspendido.historial,
      {
        estadoAnterior: 'PROGRAMADO',
        estadoNuevo: 'EN_CURSO',
        motivo: 'inicio automático por fecha y hora',
        cambiadoPor: { id: 's1', nombre: 'Sistema' },
        cambiadoEn: '2031-03-09T10:00:00-06:00',
      },
      {
        estadoAnterior: 'EN_CURSO',
        estadoNuevo: 'SUSPENDIDO',
        motivo: MOTIVO,
        cambiadoPor: { id: 'pr1', nombre: 'Programador' },
        cambiadoEn: '2031-03-09T10:40:00-06:00',
      },
    ]
    post.mockResolvedValue(suspendido)
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Suspender' }))
    const modal = await screen.findByRole('dialog', { name: 'Suspender partido' })
    await usuario.type(within(modal).getByLabelText(/Motivo/), MOTIVO)
    await usuario.click(within(modal).getByRole('button', { name: 'Suspender' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('partidos/p1/transiciones', { estadoNuevo: 'SUSPENDIDO', motivo: MOTIVO }),
    )
    expect(await screen.findByRole('button', { name: 'Reprogramar' })).toBeInTheDocument()

    const pestanaHistorial = screen.getByRole('tab', { name: /Historial/ })
    expect(pestanaHistorial).toHaveTextContent('3')
    await usuario.click(pestanaHistorial)
    const panel = screen.getByRole('tabpanel', { name: /Historial/ })
    const cambios = within(panel).getAllByRole('listitem')
    expect(cambios[0]).toHaveTextContent(MOTIVO)
    expect(cambios[0]).toHaveTextContent('Programador')
    expect(cambios[1]).toHaveTextContent('Sistema')
  })

  it('RF-046: una transición rechazada muestra la causa en el modal', async () => {
    const usuario = userEvent.setup()
    otorgar(CONSULTAR, CAMBIAR_ESTADO)
    get.mockResolvedValue(partido())
    post.mockRejectedValue(
      new ApiError({
        title: 'Conflicto',
        status: 409,
        codigo: 'RF-046',
        detail: 'Un partido EN_CURSO no puede pasar a CANCELADO.',
      }),
    )
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Cancelar partido' }))
    const modal = await screen.findByRole('dialog', { name: 'Cancelar partido' })
    await usuario.type(within(modal).getByLabelText(/Motivo/), MOTIVO)
    await usuario.click(within(modal).getByRole('button', { name: 'Cancelar partido' }))

    expect(await within(modal).findByRole('alert')).toHaveTextContent('no puede pasar a CANCELADO')
  })

  it('las pestañas se recorren con las flechas del teclado', async () => {
    const usuario = userEvent.setup()
    otorgar(CONSULTAR)
    get.mockResolvedValue(partido())
    montar()

    const resumen = await screen.findByRole('tab', { name: /Resumen/ })
    resumen.focus()
    await usuario.keyboard('{ArrowRight}')

    expect(screen.getByRole('tab', { name: /Historial/ })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: /Historial/ })).toHaveFocus()
  })

  it('H-16.3: con el partido en curso, el Capturista ve el enlace para capturar la cédula', async () => {
    otorgar(CONSULTAR, 'partidos.cedula:CAPTURAR')
    get.mockResolvedValue(partido({ estado: 'EN_CURSO', acciones: [] }))
    const { unmount } = montar()

    expect(await screen.findByRole('link', { name: 'Capturar cédula' })).toHaveAttribute('href', '/app/partidos/p1/cedula')
    unmount()

    otorgar(CONSULTAR)
    get.mockResolvedValue(partido({ estado: 'EN_CURSO', acciones: [] }))
    montar()

    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Capturar cédula' })).not.toBeInTheDocument()
  })

  it('VAL-015: sin permiso de consulta muestra la página 403 sin pedir el partido', () => {
    montar()

    expect(screen.getByRole('heading', { name: /permiso/i })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('VAL-024: un partido fuera de alcance muestra el error de la consulta', async () => {
    otorgar(CONSULTAR)
    get.mockRejectedValue(new ApiError({ title: 'No encontrado', status: 404, codigo: 'NO_ENCONTRADO', detail: 'El recurso no existe o no tienes acceso a él.' }))
    montar()

    expect(await screen.findByRole('alert')).toHaveTextContent('No encontramos esta información')
    expect(screen.queryByRole('region', { name: 'Acciones del partido' })).not.toBeInTheDocument()
  })
})
