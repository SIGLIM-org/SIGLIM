import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaTramitesCancha } from '@/features/canchas/tramites/PantallaTramitesCancha'
import type {
  CartaOficio,
  ContextoSolicitudesCancha,
  SolicitudCancha,
} from '@/features/canchas/tramites/tipos'
import type { Ambito, Permiso } from '@/features/seguridad/tipos'

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

const CREAR = 'canchas.solicitud:CREAR'
const APROBAR = 'canchas.solicitud:APROBAR'
const GENERAR = 'canchas.oficio:GENERAR'

const PRESTAMO: SolicitudCancha = {
  id: 's-1',
  tipo: 'PRESTAMO',
  formato: 1,
  estado: 'PENDIENTE',
  numeroOficio: 'OF-2026-0001',
  club: { id: 'c1', nombre: 'Club Halcones' },
  prestamo: {
    lugar: 'Morelia, Michoacán',
    dirigidaA: 'DUENO_EXTERNO',
    destinatarioNombre: 'Ing. Laura Pérez',
    destinatarioCargo: 'Administración de la Unidad',
    solicitanteNombre: 'Mario Ruiz',
    solicitanteCargo: 'Presidente del club',
    instalacion: 'Cancha 2',
    finalidad: 'Jornada de liga',
    fechaUtilizacion: '2026-12-06',
    horaInicio: '10:00:00',
    horaTermino: '12:00:00',
    actividad: 'Partido oficial',
    responsableActividad: 'Mario Ruiz',
    telefonoContacto: '4431234567',
  },
  capturadaPor: { id: 'perfil-1', nombreMostrado: 'Ana Pérez' },
  capturadaEn: '2026-11-17T10:00:00-06:00',
}

const CONTEXTO: ContextoSolicitudesCancha = {
  clubes: [{ id: 'c1', nombre: 'Club Halcones' }],
  canchas: [{ id: 'k1', nombre: 'Cancha 2' }],
  capturaExternos: false,
}

const CARTA: CartaOficio = {
  numeroOficio: 'OF-2026-0001',
  tipo: 'PRESTAMO',
  formato: 1,
  asunto: 'Solicitud de préstamo de cancha deportiva.',
  liga: 'Liga Municipal de Futbol Amateur Morelia',
  fechaEmision: '2026-11-17',
  solicitante: 'Club Halcones',
  prestamo: PRESTAMO.prestamo,
  estado: 'PENDIENTE',
}

function otorgar(ambito: Ambito, ...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-06',
    ambito,
    clubId: ambito === 'CLUB' ? 'c1' : null,
  }))
}

function pagina<T>(contenido: T[]) {
  return { contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: contenido.length ? 1 : 0 }
}

function responder(lista: unknown = pagina([PRESTAMO])) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'solicitudes-cancha/contexto') {
      return CONTEXTO
    }
    if (ruta === 'solicitudes-cancha/s-1/oficio') {
      return CARTA
    }
    if (ruta.startsWith('solicitudes-cancha?')) {
      return lista
    }
    throw new Error(`Ruta inesperada: ${ruta}`)
  })
}

function montar() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <PantallaTramitesCancha />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('PantallaTramitesCancha (B-07, UI-028)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    responder()
  })

  it('RF-117: sin ninguno de los tres permisos no pide datos', () => {
    otorgar('LIGA', 'canchas.cancha:CONSULTAR')

    montar()

    expect(get).not.toHaveBeenCalled()
  })

  it('RF-117: la pestaña Formato 1 pide préstamos y comodatos y muestra el oficio y su estado', async () => {
    otorgar('CLUB', CREAR)

    montar()

    expect(await screen.findByText('OF-2026-0001')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith(expect.stringContaining('tipo=PRESTAMO&tipo=COMODATO'))
    expect(screen.getByText('Pendiente', { selector: 'span *, span' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Aprobar el oficio/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Generar la carta/ })).not.toBeInTheDocument()
  })

  it('RF-116: la pestaña Formato 2 pide las ofertas con sus estados', async () => {
    otorgar('LIGA', CREAR)
    const usuario = userEvent.setup()

    montar()
    await usuario.click(screen.getByRole('tab', { name: 'Ofertas de cancha (Formato 2)' }))

    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining('tipo=OFERTA')))
    expect(screen.getByRole('tab', { name: 'Pendiente de negociación' })).toBeInTheDocument()
  })

  it('RF-117: la Directiva captura un préstamo con los campos del Formato 1', async () => {
    otorgar('CLUB', CREAR)
    post.mockResolvedValue(PRESTAMO)
    const usuario = userEvent.setup()

    montar()
    await usuario.click(await screen.findByRole('button', { name: 'Nueva solicitud' }))
    const dialogo = await screen.findByRole('dialog')
    await within(dialogo).findByLabelText(/Club solicitante/)

    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar y asignar oficio' }))
    expect(post).not.toHaveBeenCalled()
    expect(within(dialogo).getByText('Revisa los campos marcados.')).toBeInTheDocument()

    const llenar = async (etiqueta: RegExp, valor: string) => {
      await usuario.type(within(dialogo).getByLabelText(etiqueta), valor)
    }
    await llenar(/^Destinatario/, 'Ing. Laura Pérez')
    await llenar(/Cargo o institución del destinatario/, 'Administración')
    await llenar(/Nombre de quien solicita/, 'Mario Ruiz')
    await llenar(/Cargo en el club/, 'Presidente')
    await llenar(/Instalación solicitada/, 'Cancha 2')
    await llenar(/^Finalidad/, 'Jornada de liga')
    await llenar(/Fecha de utilización/, '2026-12-06')
    await llenar(/Hora de inicio/, '10:00')
    await llenar(/Hora de término/, '12:00')
    await llenar(/Actividad deportiva/, 'Partido oficial')
    await llenar(/Responsable de la actividad/, 'Mario Ruiz')
    await llenar(/Teléfono de contacto/, '4431234567')
    await llenar(/Lugar de emisión/, 'Morelia, Michoacán')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar y asignar oficio' }))

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    const [ruta, cuerpo] = post.mock.calls[0]
    expect(ruta).toBe('solicitudes-cancha')
    expect(cuerpo).toMatchObject({
      tipo: 'PRESTAMO',
      clubId: 'c1',
      prestamo: { solicitanteNombre: 'Mario Ruiz', horaInicio: '10:00', dirigidaA: 'LIGA' },
    })
    expect(cuerpo.oferta).toBeUndefined()
    expect(await screen.findByRole('status')).toHaveTextContent('OF-2026-0001')
  })

  it('RN-021: la Presidencia no aprueba sin motivo y lo envía con él', async () => {
    otorgar('LIGA', APROBAR)
    post.mockResolvedValue({ ...PRESTAMO, estado: 'NO_APROBADA' })
    const usuario = userEvent.setup()

    montar()
    await usuario.click(await screen.findByRole('button', { name: /No aprobar el oficio OF-2026-0001/ }))
    const dialogo = await screen.findByRole('dialog')
    await usuario.click(within(dialogo).getByRole('button', { name: 'No aprobar trámite' }))
    expect(post).not.toHaveBeenCalled()
    expect(within(dialogo).getByRole('alert')).toHaveTextContent('al menos 10 caracteres')

    await usuario.type(within(dialogo).getByLabelText(/Motivo del dictamen/), 'La cancha ya está asignada')
    await usuario.click(within(dialogo).getByRole('button', { name: 'No aprobar trámite' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('solicitudes-cancha/s-1/resolucion', {
        decision: 'NO_APROBADA',
        observaciones: 'La cancha ya está asignada',
      }),
    )
  })

  it('RF-118: quien genera oficios ve la vista previa de la carta con su número', async () => {
    otorgar('LIGA', GENERAR)
    const usuario = userEvent.setup()

    montar()
    await usuario.click(await screen.findByRole('button', { name: /Generar la carta del oficio/ }))

    const carta = await screen.findByRole('article', { name: 'Carta del oficio OF-2026-0001' })
    expect(within(carta).getByText(/Solicitud de préstamo de cancha deportiva/)).toBeInTheDocument()
    expect(within(carta).getByText(/C\. Ing\. Laura Pérez/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Descargar PDF' })).toBeEnabled()
  })
})
