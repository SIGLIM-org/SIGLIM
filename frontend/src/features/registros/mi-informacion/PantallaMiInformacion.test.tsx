import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { error } from '@/features/registros/datosPruebaRegistros'
import { PantallaMiInformacion } from '@/features/registros/mi-informacion/PantallaMiInformacion'
import type {
  MiInformacion,
  RegistroPropio,
  RequisitoPropio,
  Titular,
} from '@/features/registros/mi-informacion/tipos'
import { RUTA_MI_INFORMACION } from '@/features/registros/rutas'
import type { ApiError } from '@/lib/api/ApiError'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get } }))

/** Busca el elemento cuyo texto completo coincide aunque esté repartido en varios `<span>`. */
function textoCompleto(texto: string) {
  return (_: string, elemento: Element | null) =>
    elemento?.textContent === texto &&
    [...elemento.children].every((hijo) => hijo.textContent !== texto)
}

const FOTOGRAFIA = { id: 5, codigo: 'FOTOGRAFIA', nombre: 'Fotografía' }

const PROPIA: Titular = { personaId: 'p-1', nombre: 'María López Ruiz', esPropia: true, esMenor: false }
const ANA: Titular = { personaId: 'p-2', nombre: 'Ana López Ruiz', esPropia: false, esMenor: true }
const LUIS: Titular = { personaId: 'p-3', nombre: 'Luis López Ruiz', esPropia: false, esMenor: true }

function requisito(cambios: Partial<RequisitoPropio> = {}): RequisitoPropio {
  return {
    tipoDocumento: FOTOGRAFIA,
    cantidad: 1,
    soloMenores: false,
    esDemo: false,
    cargados: 1,
    aprobados: 1,
    faltan: 0,
    documentos: [
      {
        id: 'doc-1',
        tipo: FOTOGRAFIA,
        estadoValidacion: 'APROBADO',
        observaciones: null,
        cargadoEn: '2026-11-03T10:15:00-06:00',
      },
    ],
    ...cambios,
  }
}

function registro(cambios: Partial<RegistroPropio> = {}): RegistroPropio {
  return {
    id: 'reg-1',
    temporada: { id: 't-1', nombre: 'Temporada 2026', estado: 'ACTIVA' },
    club: { id: 'c-1', nombre: 'Club Halcones' },
    equipo: { id: 'e-1', nombre: 'Halcones' },
    categoria: { id: 'cat-1', nombre: 'Infantil' },
    estado: 'ACTIVO',
    altaEn: '2026-11-03',
    bajaEn: null,
    motivoBaja: null,
    observaciones: null,
    elegibilidad: { elegible: true, causas: [] },
    documentos: { requisitos: [requisito()], completo: true, listoParaValidar: true },
    proximosPartidos: [
      {
        id: 'par-1',
        jornada: 4,
        inicio: '2026-11-22T10:00:00-06:00',
        cancha: 'Unidad Deportiva 1',
        local: { id: 'e-1', nombre: 'Halcones' },
        visitante: { id: 'e-2', nombre: 'Lobos' },
        esLocal: true,
        estado: 'PROGRAMADO',
        resultado: null,
      },
    ],
    partidosJugados: [
      {
        id: 'par-0',
        jornada: 3,
        inicio: '2026-11-15T10:00:00-06:00',
        cancha: 'Unidad Deportiva 2',
        local: { id: 'e-3', nombre: 'Pumas' },
        visitante: { id: 'e-1', nombre: 'Halcones' },
        esLocal: false,
        estado: 'FINALIZADO',
        resultado: { golesLocal: 1, golesVisitante: 2, penalesLocal: null, penalesVisitante: null },
      },
    ],
    ...cambios,
  }
}

function informacion(titular: Titular, cambios: Partial<MiInformacion> = {}): MiInformacion {
  return {
    persona: {
      id: titular.personaId,
      nombreCompleto: titular.nombre,
      curpEnmascarada: 'LORA******MMN***05',
      esMenor: titular.esMenor,
    },
    registros: [registro()],
    sanciones: [],
    ...cambios,
  }
}

let servidor: {
  titulares: Titular[] | ApiError
  detalles: Record<string, MiInformacion | ApiError>
}

function responder(cambios: Partial<typeof servidor> = {}) {
  servidor = {
    titulares: [PROPIA],
    detalles: { 'p-1': informacion(PROPIA) },
    ...cambios,
  }

  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'mi-informacion') {
      if (servidor.titulares instanceof Error) {
        throw servidor.titulares
      }
      return { titulares: servidor.titulares }
    }
    const personaId = ruta.replace('mi-informacion/', '')
    const detalle = servidor.detalles[personaId]
    if (detalle instanceof Error) {
      throw detalle
    }
    if (detalle) {
      return detalle
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })
}

function montar(busqueda = '') {
  const enrutador = createMemoryRouter(
    [
      { path: RUTA_MI_INFORMACION, element: <PantallaMiInformacion /> },
      { path: '/aviso-de-privacidad', element: <h1>Aviso de privacidad</h1> },
    ],
    { initialEntries: [`${RUTA_MI_INFORMACION}${busqueda}`] },
  )

  const resultado = render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return { enrutador, ...resultado }
}

describe('PantallaMiInformacion (F-35, H-24.2)', () => {
  beforeEach(() => {
    get.mockReset()
    responder()
  })

  it('DEC-148 · DEC-151: con un titular no hay selector y muestra el registro, «Puede jugar» y los documentos', async () => {
    montar()

    const bloque = await screen.findByRole('article', { name: 'Temporada 2026, Halcones' })
    expect(screen.queryByLabelText('Ver la información de')).not.toBeInTheDocument()
    expect(screen.getByText('María López Ruiz')).toBeInTheDocument()
    expect(screen.getByText('LORA******MMN***05')).toBeInTheDocument()
    expect(within(bloque).getByText('Puede jugar')).toBeInTheDocument()
    expect(within(bloque).getByText('Club Halcones')).toBeInTheDocument()
    const documentos = within(bloque).getByRole('region', { name: 'Documentos' })
    expect(within(documentos).getByText('Fotografía')).toBeInTheDocument()
    expect(within(documentos).getByText('Aprobado')).toBeInTheDocument()
    expect(within(bloque).getByText('vs Lobos')).toBeInTheDocument()
    expect(within(bloque).getByText('en casa de Pumas')).toBeInTheDocument()
    expect(within(bloque).getByText('Marcador: Pumas 1, Halcones 2')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('mi-informacion/p-1')
  })

  it('DEC-148: con la persona propia y dos menores, el selector cambia ?persona= y pide su detalle', async () => {
    const user = userEvent.setup()
    responder({
      titulares: [PROPIA, ANA, LUIS],
      detalles: {
        'p-1': informacion(PROPIA),
        'p-2': informacion(ANA, { registros: [registro({ id: 'reg-2', equipo: { id: 'e-9', nombre: 'Pumitas' } })] }),
        'p-3': informacion(LUIS),
      },
    })
    const { enrutador } = montar()

    const selector = await screen.findByLabelText('Ver la información de')
    expect(within(selector).getAllByRole('option').map((opcion) => opcion.textContent)).toEqual([
      'María López Ruiz (tú)',
      'Ana López Ruiz (menor)',
      'Luis López Ruiz (menor)',
    ])
    await user.selectOptions(selector, 'p-2')

    expect(enrutador.state.location.search).toBe('?persona=p-2')
    expect(await screen.findByRole('article', { name: 'Temporada 2026, Pumitas' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('mi-informacion/p-2')
    expect(get).not.toHaveBeenCalledWith('mi-informacion/p-3')
  })

  it('RF-019: una causa de estado muestra «No puede jugar» con su mensaje', async () => {
    responder({
      detalles: {
        'p-1': informacion(PROPIA, {
          registros: [
            registro({
              estado: 'PENDIENTE_VALIDACION',
              elegibilidad: {
                elegible: false,
                causas: [{ codigo: 'RF-019', mensaje: 'El registro aún no está validado por la liga.' }],
              },
            }),
          ],
        }),
      },
    })
    montar()

    expect(await screen.findByText('No puede jugar')).toBeInTheDocument()
    expect(screen.getByText('El registro aún no está validado por la liga.')).toBeInTheDocument()
    expect(screen.queryByText('Suspensión vigente')).not.toBeInTheDocument()
  })

  it('RF-020: una causa de suspensión muestra la tarjeta roja con su mensaje', async () => {
    const mensaje = 'Suspensión de 3 partidos por el artículo 12: faltan 2 partidos.'
    responder({
      detalles: {
        'p-1': informacion(PROPIA, {
          registros: [
            registro({ elegibilidad: { elegible: false, causas: [{ codigo: 'RF-020', mensaje }] } }),
          ],
        }),
      },
    })
    montar()

    expect(await screen.findByText('Suspensión vigente')).toBeInTheDocument()
    expect(screen.getByText(mensaje)).toBeInTheDocument()
    expect(screen.getByText('No puede jugar')).toBeInTheDocument()
  })

  it('DEF-10 · DEC-149: sin titulares avisa que la cuenta no está vinculada', async () => {
    responder({ titulares: [] })
    montar()

    expect(await screen.findByText('Tu cuenta aún no está vinculada a un jugador')).toBeInTheDocument()
    expect(
      screen.getByText('Pide a la Secretaría de la Liga que vincule tu cuenta con tu registro.'),
    ).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('DEC-149: un titular sin registros lo dice y aún muestra Sanciones y Privacidad', async () => {
    responder({ detalles: { 'p-1': informacion(PROPIA, { registros: [] }) } })
    montar()

    expect(await screen.findByText('Sin registros en la temporada vigente')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sanciones' })).toBeInTheDocument()
    expect(screen.getByText('Sin sanciones')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Privacidad' })).toBeInTheDocument()
  })

  it('DEC-148: un ?persona= desconocido no se pide y «Ver mi información» quita el parámetro', async () => {
    const user = userEvent.setup()
    const { enrutador } = montar('?persona=desconocido')

    expect(await screen.findByText('No encontramos esa información')).toBeInTheDocument()
    expect(screen.getByText('Puede que ya no tengas acceso a este registro.')).toBeInTheDocument()
    expect(get).not.toHaveBeenCalledWith('mi-informacion/desconocido')

    await user.click(screen.getByRole('button', { name: 'Ver mi información' }))

    expect(enrutador.state.location.search).toBe('')
    expect(await screen.findByText('Puede jugar')).toBeInTheDocument()
  })

  it('VAL-024 · DEC-148: un detalle con 404 muestra «No encontramos esa información»', async () => {
    responder({
      titulares: [PROPIA, ANA],
      detalles: { 'p-1': informacion(PROPIA), 'p-2': error(404, 'NO_ENCONTRADO') },
    })
    montar('?persona=p-2')

    expect(await screen.findByText('No encontramos esa información')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ver mi información' })).toBeInTheDocument()
  })

  it('VAL-015: un 403 muestra la página sin permiso', async () => {
    responder({ titulares: error(403, 'VAL-015') })
    montar()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
  })

  it('RNF-018: no ofrece ningún enlace ni botón para ver o descargar documentos', async () => {
    montar()

    await screen.findByText('Fotografía')
    const controles = [...screen.queryAllByRole('link'), ...screen.queryAllByRole('button')]
    expect(controles.filter((control) => /descargar|ver documento/i.test(control.textContent ?? ''))).toEqual([])
    expect(
      controles.filter((control) => /descargar|ver documento|^ver /i.test(control.getAttribute('aria-label') ?? '')),
    ).toEqual([])
    expect(
      screen.queryAllByRole('link').filter((enlace) => enlace.getAttribute('href')?.includes('documentos/')),
    ).toEqual([])
  })

  it('DEC-152: con dos registros hay dos <details> y solo el primero va abierto', async () => {
    responder({
      detalles: {
        'p-1': informacion(PROPIA, {
          registros: [
            registro(),
            registro({
              id: 'reg-viejo',
              temporada: { id: 't-0', nombre: 'Temporada 2025', estado: 'CONFIGURACION' },
              estado: 'BAJA',
              bajaEn: '2026-06-30',
              motivoBaja: 'Cambio de domicilio a otra ciudad',
            }),
          ],
        }),
      },
    })
    const { container } = montar()

    await screen.findAllByText('Halcones')
    const bloques = container.querySelectorAll('details')
    expect(bloques).toHaveLength(2)
    expect(bloques[0].open).toBe(true)
    expect(bloques[1].open).toBe(false)
    expect(within(bloques[1]).getByText('Cambio de domicilio a otra ciudad')).toBeInTheDocument()
  })

  it('DEC-150 · DEC-151: «No oficial» en un requisito demo; Privacidad enlaza al aviso y no ofrece revocar', async () => {
    responder({
      detalles: {
        'p-1': informacion(PROPIA, {
          registros: [
            registro({
              documentos: {
                requisitos: [requisito({ esDemo: true, cantidad: 2, faltan: 1 })],
                completo: false,
                listoParaValidar: false,
              },
            }),
          ],
        }),
      },
    })
    montar()

    const documentos = await screen.findByRole('region', { name: 'Documentos' })
    expect(within(documentos).getByText('No oficial')).toBeInTheDocument()
    expect(within(documentos).getByText('Falta 1')).toBeInTheDocument()
    expect(within(documentos).getByText(textoCompleto('0 de 1 completos'))).toBeInTheDocument()
    const privacidad = screen.getByRole('region', { name: 'Privacidad' })
    expect(
      within(privacidad).getByRole('link', { name: 'Consultar el aviso de privacidad' }),
    ).toHaveAttribute('href', '/aviso-de-privacidad')
    expect(within(privacidad).queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByText(/revocación/i)).not.toBeInTheDocument()
  })

  it('DEC-151: cada sanción muestra partidos cumplidos, vigencia, artículo y folio', async () => {
    responder({
      detalles: {
        'p-1': informacion(PROPIA, {
          sanciones: [
            {
              id: 's-1',
              tipo: 'SUSPENSION_PARTIDOS',
              estado: 'VIGENTE',
              partidos: 3,
              partidosCumplidos: 1,
              vigenteDesde: '2026-11-16',
              vigenteHasta: null,
              articulo: { numero: '12', descripcion: 'Conducta antideportiva' },
              folio: 'EXP-2026-0007',
            },
          ],
        }),
      },
    })
    montar()

    const sanciones = await screen.findByRole('region', { name: 'Sanciones' })
    expect(within(sanciones).getByText('Suspensión por partidos')).toBeInTheDocument()
    expect(within(sanciones).getByText('Vigente')).toBeInTheDocument()
    expect(within(sanciones).getByText(textoCompleto('1 de 3'))).toBeInTheDocument()
    expect(within(sanciones).getByText('Desde 16 nov 2026')).toBeInTheDocument()
    expect(within(sanciones).getByText('12 · Conducta antideportiva')).toBeInTheDocument()
    expect(within(sanciones).getByText('EXP-2026-0007')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByText('Sin sanciones')).not.toBeInTheDocument())
  })
})
