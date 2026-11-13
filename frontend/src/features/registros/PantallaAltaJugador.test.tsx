import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useLocation, useParams } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Equipo, Persona, PersonaDetalle } from '@/features/organizacion/tipos'
import {
  PantallaAltaJugador,
  RUTA_ALTA_JUGADOR,
} from '@/features/registros/PantallaAltaJugador'
import { RUTA_DOCUMENTOS_REGISTRO, vieneDelAlta } from '@/features/registros/rutas'
import type { AvisoPrivacidad, RegistroJugador } from '@/features/registros/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

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

const EDITAR = 'registros.registro:EDITAR'

const CURP_ADULTO = 'GODE840101HDFRNS09'
const CURP_MENOR = 'LOMA100202HMNRRN01'
const CURP_TUTOR = 'RUPL800101MMNZRR02'

function equipo(
  id: string,
  nombre: string,
  categoria: string,
  estadoTemporada: Equipo['temporada']['estado'] = 'ACTIVA',
): Equipo {
  return {
    id,
    nombre,
    estado: 'ACTIVO',
    club: { id: 'c1', nombre: 'Club Halcones' },
    grupo: { id: 'g1', nombre: 'A' },
    categoria: { id: `cat-${categoria}`, nombre: categoria },
    temporada: {
      id: estadoTemporada === 'CERRADA' ? 't-2025' : 't-2026',
      nombre: estadoTemporada === 'CERRADA' ? 'Temporada 2025' : 'Temporada 2026',
      estado: estadoTemporada,
    },
    creadoEn: '2026-11-01T10:00:00-06:00',
  }
}

const HALCONES = equipo('e1', 'Halcones', 'Libre')
const VIEJO = equipo('e2', 'Halcones Veteranos', 'Libre', 'CERRADA')
const HALCONES_B = equipo('e3', 'Halcones B', 'Juvenil')

const AVISO: AvisoPrivacidad = {
  id: 'aviso-1',
  tipo: 'SIMPLIFICADO',
  version: 'v1',
  contenido: 'AVISO DE PRIVACIDAD DE DEMOSTRACIÓN. NO OFICIAL.',
  finalidades: ['REGISTRO_DEPORTIVO', 'DIFUSION'],
  vigenteDesde: '2026-11-11',
  esDemo: true,
}

const ADULTO_EXISTENTE: Persona = {
  id: 'p-adulto',
  curp: CURP_ADULTO,
  nombre: 'Ana',
  apellidoPaterno: 'López',
  apellidoMaterno: 'Ruiz',
  esMenor: false,
}

const MENOR_EXISTENTE: Persona = {
  id: 'p-menor',
  curp: CURP_MENOR,
  nombre: 'Mateo',
  apellidoPaterno: 'Luna',
  esMenor: true,
}

const TUTOR_EXISTENTE: Persona = {
  id: 'p-tutor',
  curp: CURP_TUTOR,
  nombre: 'Laura',
  apellidoPaterno: 'Ruiz',
  esMenor: false,
}

function registro(persona: string, eq: Equipo = HALCONES): RegistroJugador {
  return {
    id: 'reg-1',
    persona: { id: 'p', nombreCompleto: persona },
    equipo: { id: eq.id, nombre: eq.nombre },
    club: eq.club,
    categoria: eq.categoria,
    temporada: eq.temporada,
    estado: 'PENDIENTE_VALIDACION',
    altaEn: '2026-11-12',
    creadoEn: '2026-11-12T10:00:00-06:00',
    validadoPor: null,
    validadoEn: null,
    observaciones: null,
  }
}

function error(status: number, codigo: string, detail = 'Detalle del servidor.', extensiones?: Record<string, unknown>) {
  return new ApiError({ title: 'Error', status, detail, codigo, ...(extensiones ? { extensiones } : {}) })
}

const noEncontrada = () => error(404, 'NO_ENCONTRADO')

type Respuesta<T> = T | ApiError

/** Respuestas del servidor simulado; una lista se consume en orden y repite la última. */
type Servidor = {
  personas: Record<string, Respuesta<Persona>[]>
  altasPersona: Record<string, Respuesta<PersonaDetalle>>
  tutela: Respuesta<unknown>
  consentimiento: Respuesta<unknown>
  altas: Respuesta<RegistroJugador>[]
  aviso: Respuesta<AvisoPrivacidad>
}

let servidor: Servidor

function siguiente<T>(lista: T[]): T {
  return lista.length > 1 ? (lista.shift() as T) : lista[0]
}

function resolver<T>(valor: Respuesta<T>): T {
  if (valor instanceof ApiError) {
    throw valor
  }
  return valor
}

function responder(cambios: Partial<Servidor> = {}) {
  servidor = {
    personas: {},
    altasPersona: {},
    tutela: { id: 'tutela-1' },
    consentimiento: { id: 'cons-1' },
    altas: [registro('Ana López')],
    aviso: AVISO,
    ...cambios,
  }

  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('personas?curp=')) {
      const curp = decodeURIComponent(ruta.slice('personas?curp='.length))
      return resolver(siguiente(servidor.personas[curp] ?? [noEncontrada()]))
    }
    if (ruta.startsWith('equipos?')) {
      const contenido = [HALCONES, VIEJO, HALCONES_B]
      return { contenido, pagina: 0, tamano: 100, totalElementos: 3, totalPaginas: 1 }
    }
    if (ruta === 'avisos-privacidad/vigente') {
      return resolver(servidor.aviso)
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })

  post.mockImplementation(async (ruta: string, cuerpo: Record<string, unknown>) => {
    if (ruta === 'personas') {
      const curp = String(cuerpo.curp)
      const detalle = resolver(servidor.altasPersona[curp])
      // Tras crearla, la consulta por CURP ya la encuentra con su minoría (DEC-104).
      servidor.personas[curp] = [
        {
          id: detalle.id,
          curp: detalle.curp,
          nombre: detalle.nombre,
          apellidoPaterno: detalle.apellidoPaterno,
          esMenor: detalle.fechaNacimiento > '2008-11-12',
        },
      ]
      return detalle
    }
    if (ruta.endsWith('/tutelas')) {
      return resolver(servidor.tutela)
    }
    if (ruta.endsWith('/consentimientos')) {
      return resolver(servidor.consentimiento)
    }
    if (ruta === 'registros-jugador') {
      return resolver(siguiente(servidor.altas))
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-04',
    ambito: 'CLUB' as const,
    clubId: 'c1',
  }))
}

/** Sustituto de la página de documentos: muestra el registro y si llegó desde el alta (DEC-111). */
function DocumentosSimulados() {
  const { registroId } = useParams()
  const { state } = useLocation()

  return (
    <p>
      Documentos de {registroId}
      {vieneDelAlta(state) ? ' desde el alta' : ''}
    </p>
  )
}

function montar() {
  const enrutador = createMemoryRouter(
    [
      { path: RUTA_ALTA_JUGADOR, element: <PantallaAltaJugador /> },
      { path: RUTA_DOCUMENTOS_REGISTRO, element: <DocumentosSimulados /> },
      { path: '/app/organizacion/jugadores', element: <p>Plantilla de jugadores</p> },
    ],
    { initialEntries: [RUTA_ALTA_JUGADOR] },
  )

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

const usuario = () => userEvent.setup()

async function identificar(user: ReturnType<typeof userEvent.setup>, curp: string) {
  await user.type(screen.getByLabelText(/^CURP del jugador/), curp)
  await user.click(screen.getByRole('button', { name: 'Buscar CURP' }))
  await screen.findByText(/Persona nueva|Persona encontrada/)
  await user.click(screen.getByRole('button', { name: 'Continuar' }))
  await screen.findByRole('heading', { name: 'Datos y equipo' })
}

async function capturarJugador(
  user: ReturnType<typeof userEvent.setup>,
  { nombre, apellido, nacimiento }: { nombre: string; apellido: string; nacimiento: string },
) {
  const seccion = screen.getByRole('region', { name: 'Jugador' })
  await user.type(within(seccion).getByLabelText(/^Nombre/), nombre)
  await user.type(within(seccion).getByLabelText(/^Apellido paterno/), apellido)
  fireEvent.change(within(seccion).getByLabelText(/^Fecha de nacimiento/), {
    target: { value: nacimiento },
  })
}

async function elegirEquipo(user: ReturnType<typeof userEvent.setup>, eq: Equipo = HALCONES) {
  await screen.findByRole('option', { name: new RegExp(`^${eq.nombre} ·`) })
  await user.selectOptions(screen.getByRole('combobox', { name: /^Equipo/ }), eq.id)
}

async function otorgarConsentimiento(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('checkbox', { name: 'Registro deportivo' }))
  await user.click(screen.getByRole('checkbox', { name: /otorga su consentimiento expreso/ }))
  await user.click(screen.getByRole('button', { name: 'Enviar a validación' }))
}

/** Rutas de los POST en el orden en que se hicieron. */
function rutasPost(): string[] {
  return post.mock.calls.map(([ruta]) => ruta as string)
}

const ALTA_ADULTO: PersonaDetalle = {
  id: 'p-nuevo',
  curp: CURP_ADULTO,
  nombre: 'Ana',
  apellidoPaterno: 'López',
  fechaNacimiento: '1990-05-01',
}

const ALTA_MENOR: PersonaDetalle = {
  id: 'p-menor',
  curp: CURP_MENOR,
  nombre: 'Mateo',
  apellidoPaterno: 'Luna',
  fechaNacimiento: '2010-02-02',
}

const ALTA_TUTOR: PersonaDetalle = {
  id: 'p-tutor',
  curp: CURP_TUTOR,
  nombre: 'Laura',
  apellidoPaterno: 'Ruiz',
  fechaNacimiento: '1980-01-01',
}

/** Menor y tutor ya registrados, listos para llegar al paso 3. */
async function llegarAlConsentimientoDeMenor(user: ReturnType<typeof userEvent.setup>) {
  await identificar(user, CURP_MENOR)
  await elegirEquipo(user)
  const seccionTutor = screen.getByRole('region', { name: 'Tutor del menor' })
  await user.type(within(seccionTutor).getByLabelText(/^CURP del tutor/), CURP_TUTOR)
  await user.click(within(seccionTutor).getByRole('button', { name: 'Buscar tutor' }))
  await within(seccionTutor).findByText('Laura Ruiz')
  await user.selectOptions(within(seccionTutor).getByLabelText(/^Parentesco/), 'Madre')
  await user.click(screen.getByRole('button', { name: 'Continuar' }))
  await screen.findByRole('heading', { name: /Consentimiento para el tratamiento/ })
}

describe('PantallaAltaJugador (F-18, F-06, H-09.3)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    otorgar(EDITAR)
    responder()
  })

  it('VAL-015: sin registros.registro:EDITAR muestra la página 403 y no pide datos', () => {
    otorgar('registros.registro:CONSULTAR')
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('H-09 criterio 1: una CURP nueva de un adulto crea la persona, registra su consentimiento sin otorgadoPorId y navega a sus documentos (DEC-111)', async () => {
    const user = usuario()
    responder({ altasPersona: { [CURP_ADULTO]: ALTA_ADULTO } })
    montar()

    expect(screen.getByRole('listitem', { current: 'step' })).toHaveTextContent('Identidad')
    await identificar(user, CURP_ADULTO.toLowerCase())
    expect(screen.queryByRole('region', { name: 'Tutor del menor' })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Halcones Veteranos/ })).not.toBeInTheDocument()
    await capturarJugador(user, { nombre: 'Ana', apellido: 'López', nacimiento: '1990-05-01' })
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    expect(
      await screen.findByRole('heading', { name: /Consentimiento para el tratamiento/ }),
    ).toHaveFocus()
    await otorgarConsentimiento(user)

    expect(await screen.findByText('Documentos de reg-1 desde el alta')).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('personas', expect.objectContaining({
      curp: CURP_ADULTO,
      nombre: 'Ana',
      apellidoPaterno: 'López',
      fechaNacimiento: '1990-05-01',
    }))
    expect(post).toHaveBeenCalledWith('personas/p-nuevo/consentimientos', {
      avisoId: 'aviso-1',
      finalidades: ['REGISTRO_DEPORTIVO'],
    })
    expect(post).toHaveBeenLastCalledWith('registros-jugador', { personaId: 'p-nuevo', equipoId: 'e1' })
    expect(rutasPost()).toEqual(['personas', 'personas/p-nuevo/consentimientos', 'registros-jugador'])

  })

  it('DEC-107: un 400 de POST /personas sobre un campo que el paso 2 no dibuja muestra el detail', async () => {
    const user = usuario()
    responder({
      altasPersona: {
        [CURP_ADULTO]: new ApiError({
          title: 'Datos inválidos',
          status: 400,
          detail: 'La CURP debe tener 18 caracteres y el formato oficial.',
          codigo: 'VALIDACION',
          errores: [{ campo: 'curp', mensaje: 'Formato inválido' }],
        }),
      },
    })
    montar()

    await identificar(user, CURP_ADULTO)
    await capturarJugador(user, { nombre: 'Ana', apellido: 'López', nacimiento: '1990-05-01' })
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La CURP debe tener 18 caracteres y el formato oficial.',
    )
    expect(screen.getByRole('heading', { name: 'Datos y equipo' })).toBeInTheDocument()
    expect(rutasPost()).toEqual(['personas'])
  })

  it('H-09 criterio 4: una persona existente no se vuelve a crear y se usa su id', async () => {
    const user = usuario()
    responder({ personas: { [CURP_ADULTO]: [ADULTO_EXISTENTE] } })
    montar()

    await identificar(user, CURP_ADULTO)
    expect(screen.getByText('Ana López Ruiz')).toBeInTheDocument()
    expect(screen.queryByLabelText(/^Nombre/)).not.toBeInTheDocument()
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await otorgarConsentimiento(user)

    await screen.findByText('Documentos de reg-1 desde el alta')
    expect(rutasPost()).not.toContain('personas')
    expect(post).toHaveBeenLastCalledWith('registros-jugador', { personaId: 'p-adulto', equipoId: 'e1' })
  })

  it('RF-010: un 409 de POST /personas reutiliza el personaId de la extensión', async () => {
    const user = usuario()
    responder({
      personas: { [CURP_ADULTO]: [noEncontrada(), { ...ADULTO_EXISTENTE, id: 'p-existe' }] },
      altasPersona: {
        [CURP_ADULTO]: error(409, 'RF-010', 'Ya está registrada.', { personaId: 'p-existe' }),
      },
    })
    montar()

    await identificar(user, CURP_ADULTO)
    await capturarJugador(user, { nombre: 'Ana', apellido: 'López', nacimiento: '1990-05-01' })
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await otorgarConsentimiento(user)

    await screen.findByText('Documentos de reg-1 desde el alta')
    expect(post).toHaveBeenCalledWith('personas/p-existe/consentimientos', expect.anything())
    expect(post).toHaveBeenLastCalledWith('registros-jugador', { personaId: 'p-existe', equipoId: 'e1' })
  })

  it('VAL-025: un menor nuevo con tutor nuevo escribe persona, tutor, tutela, consentimiento del tutor y alta, en ese orden', async () => {
    const user = usuario()
    responder({
      altasPersona: { [CURP_MENOR]: ALTA_MENOR, [CURP_TUTOR]: ALTA_TUTOR },
      altas: [registro('Mateo Luna')],
    })
    montar()

    await identificar(user, CURP_MENOR)
    await capturarJugador(user, { nombre: 'Mateo', apellido: 'Luna', nacimiento: '2010-02-02' })
    await elegirEquipo(user)
    const seccionTutor = screen.getByRole('region', { name: 'Tutor del menor' })
    await user.type(within(seccionTutor).getByLabelText(/^CURP del tutor/), CURP_TUTOR)
    await user.click(within(seccionTutor).getByRole('button', { name: 'Buscar tutor' }))
    await within(seccionTutor).findByText(/No hay nadie con esa CURP/)
    await user.type(within(seccionTutor).getByLabelText(/^Nombre/), 'Laura')
    await user.type(within(seccionTutor).getByLabelText(/^Apellido paterno/), 'Ruiz')
    fireEvent.change(within(seccionTutor).getByLabelText(/^Fecha de nacimiento/), {
      target: { value: '1980-01-01' },
    })
    expect(within(seccionTutor).getByText(/Servirá para invitarlo después/)).toBeInTheDocument()
    await user.selectOptions(within(seccionTutor).getByLabelText(/^Parentesco/), 'Otro')
    await user.type(within(seccionTutor).getByLabelText(/^¿Cuál parentesco/), '  Abuela ')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByRole('heading', { name: /Consentimiento para el tratamiento/ })
    expect(screen.getByText(/Sin el consentimiento del tutor no se puede completar/)).toBeInTheDocument()
    expect(screen.queryByText(/VAL-025/)).not.toBeInTheDocument()
    await otorgarConsentimiento(user)

    await screen.findByText('Documentos de reg-1 desde el alta')
    expect(rutasPost()).toEqual([
      'personas',
      'personas',
      'personas/p-menor/tutelas',
      'personas/p-menor/consentimientos',
      'registros-jugador',
    ])
    expect(post).toHaveBeenCalledWith('personas/p-menor/tutelas', { tutorId: 'p-tutor', parentesco: 'Abuela' })
    expect(post).toHaveBeenCalledWith('personas/p-menor/consentimientos', {
      avisoId: 'aviso-1',
      finalidades: ['REGISTRO_DEPORTIVO'],
      otorgadoPorId: 'p-tutor',
    })
  })

  it('DEC-103: una tutela que ya existía (409 DUPLICADO) no bloquea el alta', async () => {
    const user = usuario()
    responder({
      personas: { [CURP_MENOR]: [MENOR_EXISTENTE], [CURP_TUTOR]: [TUTOR_EXISTENTE] },
      tutela: error(409, 'DUPLICADO', 'Ese tutor ya está registrado.'),
    })
    montar()

    await llegarAlConsentimientoDeMenor(user)

    expect(rutasPost()).toEqual(['personas/p-menor/tutelas'])
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('H-09 criterio 3 · VAL-025: un menor sin tutor no puede continuar', async () => {
    const user = usuario()
    responder({ personas: { [CURP_MENOR]: [MENOR_EXISTENTE] } })
    montar()

    await identificar(user, CURP_MENOR)
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    const seccionTutor = screen.getByRole('region', { name: 'Tutor del menor' })
    const aviso = await within(seccionTutor).findByText(/Sin tutor no se puede registrar a un menor/)
    expect(aviso.closest('[role="alert"]')).not.toBeNull()
    expect(within(seccionTutor).getByText('Elige el parentesco con el menor.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
    expect(screen.getByRole('heading', { name: 'Datos y equipo' })).toBeInTheDocument()
  })

  it('H-09 criterio 3 · VAL-025: un tutor menor de edad se bloquea antes de enviar', async () => {
    const user = usuario()
    responder({
      personas: { [CURP_MENOR]: [MENOR_EXISTENTE], [CURP_TUTOR]: [{ ...TUTOR_EXISTENTE, esMenor: true }] },
    })
    montar()

    await identificar(user, CURP_MENOR)
    await elegirEquipo(user)
    const seccionTutor = screen.getByRole('region', { name: 'Tutor del menor' })
    await user.type(within(seccionTutor).getByLabelText(/^CURP del tutor/), CURP_TUTOR)
    await user.click(within(seccionTutor).getByRole('button', { name: 'Buscar tutor' }))

    expect(await within(seccionTutor).findByRole('alert')).toHaveTextContent('El tutor debe ser mayor de edad')
    await user.selectOptions(within(seccionTutor).getByLabelText(/^Parentesco/), 'Madre')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(post).not.toHaveBeenCalled()
  })

  it('H-09 criterio 3 · VAL-025: un 422 TUTOR del alta lleva a la sección del tutor', async () => {
    const user = usuario()
    responder({
      personas: { [CURP_MENOR]: [MENOR_EXISTENTE], [CURP_TUTOR]: [TUTOR_EXISTENTE] },
      altas: [error(422, 'VAL-025', 'Registra primero al tutor del menor.', { requisito: 'TUTOR' })],
    })
    montar()

    await llegarAlConsentimientoDeMenor(user)
    await otorgarConsentimiento(user)

    const seccionTutor = await screen.findByRole('region', { name: 'Tutor del menor' })
    expect(within(seccionTutor).getByRole('alert')).toHaveTextContent('Registra primero al tutor del menor.')
    expect(screen.getByRole('heading', { name: 'Datos y equipo' })).toHaveFocus()
  })

  it('H-09 criterio 3 · VAL-025: un 422 CONSENTIMIENTO del alta vuelve a pedirlo en el paso 3', async () => {
    const user = usuario()
    responder({
      personas: { [CURP_MENOR]: [MENOR_EXISTENTE], [CURP_TUTOR]: [TUTOR_EXISTENTE] },
      altas: [
        error(422, 'VAL-025', 'Falta el consentimiento del tutor.', { requisito: 'CONSENTIMIENTO' }),
        registro('Mateo Luna'),
      ],
    })
    montar()

    await llegarAlConsentimientoDeMenor(user)
    await otorgarConsentimiento(user)

    expect(await screen.findByRole('alert')).toHaveTextContent('Falta el consentimiento del tutor.')
    expect(screen.getByRole('checkbox', { name: 'Registro deportivo' })).not.toBeChecked()
    await otorgarConsentimiento(user)

    await screen.findByText('Documentos de reg-1 desde el alta')
    expect(rutasPost().filter((ruta) => ruta.endsWith('/consentimientos'))).toHaveLength(2)
  })

  it('H-09 criterio 2 · VAL-017: muestra el club y el equipo sin el código, y «Cambiar equipo» no repite escrituras', async () => {
    const user = usuario()
    responder({
      altasPersona: { [CURP_ADULTO]: ALTA_ADULTO },
      altas: [
        error(409, 'VAL-017', 'Esta persona ya está registrada en esta temporada en el club «Club Águilas», equipo «Águilas».', {
          club: 'Club Águilas',
          equipo: 'Águilas',
        }),
        registro('Ana López', HALCONES_B),
      ],
    })
    montar()

    await identificar(user, CURP_ADULTO)
    await capturarJugador(user, { nombre: 'Ana', apellido: 'López', nacimiento: '1990-05-01' })
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await otorgarConsentimiento(user)

    const alerta = await screen.findByRole('alert')
    expect(within(alerta).getByText('Club Águilas', { selector: 'dd' })).toBeInTheDocument()
    expect(within(alerta).getByText('Águilas', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.queryByText(/VAL-017/)).not.toBeInTheDocument()

    await user.click(within(alerta).getByRole('button', { name: 'Cambiar equipo' }))
    expect(await screen.findByRole('heading', { name: 'Datos y equipo' })).toHaveFocus()
    expect(screen.getByText('Identidad registrada')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Anterior: identidad/ })).not.toBeInTheDocument()
    await elegirEquipo(user, HALCONES_B)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByText(/El consentimiento ya quedó registrado/)
    await user.click(screen.getByRole('button', { name: 'Enviar a validación' }))

    await screen.findByText('Documentos de reg-1 desde el alta')
    expect(rutasPost()).toEqual([
      'personas',
      'personas/p-nuevo/consentimientos',
      'registros-jugador',
      'registros-jugador',
    ])
    expect(post).toHaveBeenLastCalledWith('registros-jugador', { personaId: 'p-nuevo', equipoId: 'e3' })
  })

  it.each([
    ['VAL-026', 'Esta persona estuvo registrada en otro club en esta temporada.'],
    ['RN-039', 'Dentro de una temporada no hay cambio de equipo.'],
  ])('%s: muestra el detail con «Cambiar equipo»', async (codigo, detail) => {
    const user = usuario()
    responder({ personas: { [CURP_ADULTO]: [ADULTO_EXISTENTE] }, altas: [error(409, codigo, detail)] })
    montar()

    await identificar(user, CURP_ADULTO)
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await otorgarConsentimiento(user)

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent(detail)
    expect(alerta).not.toHaveTextContent(codigo)
    expect(within(alerta).getByRole('button', { name: 'Cambiar equipo' })).toBeInTheDocument()
  })

  it('RF-097: sin finalidades o sin la casilla, «Enviar a validación» está deshabilitado; esDemo muestra «No oficial»', async () => {
    const user = usuario()
    responder({ personas: { [CURP_ADULTO]: [ADULTO_EXISTENTE] } })
    montar()

    await identificar(user, CURP_ADULTO)
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    const enviar = await screen.findByRole('button', { name: 'Enviar a validación' })
    expect(await screen.findByText('No oficial')).toBeInTheDocument()
    expect(screen.getByText(/AVISO DE PRIVACIDAD DE DEMOSTRACIÓN/)).toBeInTheDocument()
    const finalidad = screen.getByRole('checkbox', { name: 'Registro deportivo' })
    expect(finalidad).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Difusión' })).not.toBeChecked()
    expect(enviar).toBeDisabled()

    await user.click(finalidad)
    expect(enviar).toBeDisabled()
    await user.click(finalidad)
    await user.click(screen.getByRole('checkbox', { name: /Ana López Ruiz leyó el aviso/ }))
    expect(enviar).toBeDisabled()
    await user.click(finalidad)
    expect(enviar).toBeEnabled()
  })

  it('DEC-106: sin aviso vigente (404) el paso 3 se bloquea y pide contactar a la liga', async () => {
    const user = usuario()
    responder({ personas: { [CURP_ADULTO]: [ADULTO_EXISTENTE] }, aviso: noEncontrada() })
    montar()

    await identificar(user, CURP_ADULTO)
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/Contacta a la liga/)
    expect(screen.getByRole('button', { name: 'Enviar a validación' })).toBeDisabled()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('RF-097: si el aviso cambió, se recarga y se pide marcar de nuevo', async () => {
    const user = usuario()
    responder({
      personas: { [CURP_ADULTO]: [ADULTO_EXISTENTE] },
      consentimiento: error(422, 'RF-097', 'El aviso ya no es el vigente.'),
    })
    montar()

    await identificar(user, CURP_ADULTO)
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await otorgarConsentimiento(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(/marca de nuevo las finalidades/)
    expect(screen.getByRole('checkbox', { name: 'Registro deportivo' })).not.toBeChecked()
    await waitFor(() =>
      expect(get.mock.calls.filter(([ruta]) => ruta === 'avisos-privacidad/vigente')).toHaveLength(2),
    )
    expect(rutasPost()).not.toContain('registros-jugador')
  })

  it('DEC-107: VAL-022 de la tutela dice que no puede registrarse como tutor desde su cuenta', async () => {
    const user = usuario()
    responder({
      personas: { [CURP_MENOR]: [MENOR_EXISTENTE], [CURP_TUTOR]: [TUTOR_EXISTENTE] },
      tutela: error(403, 'VAL-022', 'Texto del servidor.'),
    })
    montar()

    await identificar(user, CURP_MENOR)
    await elegirEquipo(user)
    const seccionTutor = screen.getByRole('region', { name: 'Tutor del menor' })
    await user.type(within(seccionTutor).getByLabelText(/^CURP del tutor/), CURP_TUTOR)
    await user.click(within(seccionTutor).getByRole('button', { name: 'Buscar tutor' }))
    await within(seccionTutor).findByText('Laura Ruiz')
    await user.selectOptions(within(seccionTutor).getByLabelText(/^Parentesco/), 'Padre')
    await user.click(screen.getByRole('button', { name: 'Continuar' }))

    expect(await within(seccionTutor).findByRole('alert')).toHaveTextContent(
      'No puedes registrarte como tutor desde tu propia cuenta',
    )
  })

  it('D-12: «Cancelar» con escrituras pide confirmación y avisa que lo creado se conserva', async () => {
    const user = usuario()
    responder({
      altasPersona: { [CURP_ADULTO]: ALTA_ADULTO },
      aviso: noEncontrada(),
    })
    montar()

    await identificar(user, CURP_ADULTO)
    await capturarJugador(user, { nombre: 'Ana', apellido: 'López', nacimiento: '1990-05-01' })
    await elegirEquipo(user)
    await user.click(screen.getByRole('button', { name: 'Continuar' }))
    await screen.findByRole('heading', { name: /Consentimiento para el tratamiento/ })

    await user.click(screen.getByRole('button', { name: 'Cancelar' }))
    const dialogo = await screen.findByRole('dialog', { name: '¿Salir del alta del jugador?' })
    expect(dialogo).toHaveTextContent(/se conserva/)
    await user.click(within(dialogo).getByRole('button', { name: 'Salir del alta' }))

    expect(await screen.findByText('Plantilla de jugadores')).toBeInTheDocument()
  })

  it('DEC-116: sin escrituras, «Cancelar» vuelve a Jugadores sin preguntar', async () => {
    const user = usuario()
    montar()

    await user.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(await screen.findByText('Plantilla de jugadores')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
