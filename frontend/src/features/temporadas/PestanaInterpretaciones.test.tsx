import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  APROBAR_INTERPRETACION,
  CONSULTAR,
  INTERPRETACIONES,
  REGISTRAR_INTERPRETACION,
  TEMPORADA,
  errorApi,
  montarEstructura,
  paginaDe,
  permisos,
  resolverGet,
  respuestasPorDefecto,
} from '@/features/temporadas/datosPruebaEstructura'
import type { InterpretacionRegla } from '@/features/temporadas/tipos'
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

const LISTA = 'interpretaciones-regla?temporadaId=t-1&pagina=0&tamano=10'

let usuario: ReturnType<typeof userEvent.setup>

function pendientes(): string {
  return `${LISTA}&estado=PENDIENTE`
}

function montarInterpretaciones() {
  return montarEstructura('?pestana=interpretaciones')
}

function dialogo(nombre: string): HTMLElement {
  return screen.getByRole('dialog', { name: nombre })
}

describe('PestanaInterpretaciones', () => {
  beforeEach(() => {
    usuario = userEvent.setup({ delay: null })
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    sesion.yo.permisos = permisos(CONSULTAR, APROBAR_INTERPRETACION)
    get.mockImplementation(resolverGet(respuestasPorDefecto()))
  })

  it('RF-007: quien solo resuelve ve primero las pendientes y no puede registrar', async () => {
    montarInterpretaciones()

    expect(
      await screen.findByRole('heading', { name: 'Interpretaciones de regla' }),
    ).toBeInTheDocument()
    expect(await screen.findByText(/2 interpretaciones · Pendientes/)).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith(pendientes())
    expect(
      screen.getByText(/Si un equipo no se presenta y avisa con 48 horas/),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Registrar interpretación' }),
    ).not.toBeInTheDocument()
    expect(screen.getAllByText('Pendiente')).toHaveLength(2)
  })

  it('DEC-57: la propia interpretación no ofrece resolución y lo explica', async () => {
    montarInterpretaciones()

    await screen.findByText(/Cuando la cancha se inunda/)
    expect(
      screen.getByText('Registrada por ti: la debe resolver otra persona (DEC-57).'),
    ).toBeInTheDocument()
    // Solo la interpretación de otro perfil ofrece Autorizar y Rechazar.
    expect(
      screen.getAllByRole('button', { name: /^Autorizar la interpretación/ }),
    ).toHaveLength(1)
    expect(
      screen.getAllByRole('button', { name: /^Rechazar la interpretación/ }),
    ).toHaveLength(1)
  })

  it('RF-007: los filtros consultan el estado y muestran lo resuelto', async () => {
    montarInterpretaciones()

    await screen.findByText(/Si un equipo no se presenta/)
    await usuario.click(screen.getByRole('tab', { name: 'Autorizadas' }))

    expect(
      await screen.findByText(/Un jugador expulsado por doble amarilla/),
    ).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith(`${LISTA}&estado=AUTORIZADA`)
    expect(screen.getByText('Nota de autorización')).toBeInTheDocument()
    expect(screen.getByText(/Se autoriza: coincide con el criterio/)).toBeInTheDocument()

    await usuario.click(screen.getByRole('tab', { name: 'Todas' }))

    expect(await screen.findByText(/4 interpretaciones/)).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith(LISTA)
  })

  it('RF-007: registra una interpretación y la deja pendiente', async () => {
    sesion.yo.permisos = permisos(CONSULTAR, REGISTRAR_INTERPRETACION)
    const nueva: InterpretacionRegla = {
      id: 'int-5',
      temporadaId: 't-1',
      descripcion: 'La copa se juega con jugadores de la misma categoría.',
      estado: 'PENDIENTE',
      registradaPor: 'perfil-1',
      registradaEn: '2026-11-07T12:00:00-06:00',
    }
    post.mockResolvedValue(nueva)
    montarInterpretaciones()

    await screen.findByText(/Si un equipo no se presenta/)
    await usuario.click(screen.getByRole('button', { name: 'Registrar interpretación' }))

    const modal = dialogo('Registrar interpretación')
    await usuario.click(
      within(modal).getByRole('button', { name: 'Registrar interpretación' }),
    )
    expect(within(modal).getByRole('alert')).toHaveTextContent(
      'Escribe la interpretación.',
    )

    await usuario.type(
      within(modal).getByLabelText(/^Interpretación/),
      nueva.descripcion,
    )
    await usuario.click(
      within(modal).getByRole('button', { name: 'Registrar interpretación' }),
    )

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('interpretaciones-regla', {
        temporadaId: 't-1',
        descripcion: nueva.descripcion,
      }),
    )
    expect(
      await screen.findByText(
        'La interpretación quedó registrada y pendiente de autorización.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('dialog', { name: 'Registrar interpretación' }),
    ).not.toBeInTheDocument()
  })

  it('RF-007: muestra el error de campo que devuelve el backend al registrar', async () => {
    sesion.yo.permisos = permisos(CONSULTAR, REGISTRAR_INTERPRETACION)
    post.mockRejectedValue(
      errorApi(400, 'VAL-004', 'Revisa los datos de la interpretación.', {
        errores: [
          {
            campo: 'descripcion',
            mensaje: 'La descripción de la interpretación admite hasta 2000 caracteres',
          },
        ],
      }),
    )
    montarInterpretaciones()

    await screen.findByText(/Si un equipo no se presenta/)
    await usuario.click(screen.getByRole('button', { name: 'Registrar interpretación' }))

    const modal = dialogo('Registrar interpretación')
    await usuario.type(
      within(modal).getByLabelText(/^Interpretación/),
      'Una interpretación demasiado larga',
    )
    await usuario.click(
      within(modal).getByRole('button', { name: 'Registrar interpretación' }),
    )

    expect(await within(modal).findByRole('alert')).toHaveTextContent(
      'La descripción de la interpretación admite hasta 2000 caracteres',
    )
    expect(within(modal).getByLabelText(/^Interpretación/)).toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })

  it('RF-007: el Presidente autoriza con una nota opcional', async () => {
    post.mockResolvedValue({ ...INTERPRETACIONES[0], estado: 'AUTORIZADA' })
    montarInterpretaciones()

    await screen.findByText(/Cuando la cancha se inunda/)
    await usuario.click(
      screen.getByRole('button', { name: /^Autorizar la interpretación/ }),
    )

    const modal = dialogo('Autorizar interpretación')
    await usuario.type(
      within(modal).getByLabelText(/^Nota de autorización/),
      'Se autoriza por unanimidad.',
    )
    await usuario.click(
      within(modal).getByRole('button', { name: 'Autorizar interpretación' }),
    )

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('interpretaciones-regla/int-1/resolucion', {
        estado: 'AUTORIZADA',
        observaciones: 'Se autoriza por unanimidad.',
      }),
    )
    expect(await screen.findByText('La interpretación quedó autorizada.')).toBeInTheDocument()
  })

  it('RF-007: rechazar exige observaciones de al menos 10 caracteres', async () => {
    post.mockResolvedValue({ ...INTERPRETACIONES[0], estado: 'RECHAZADA' })
    montarInterpretaciones()

    await screen.findByText(/Cuando la cancha se inunda/)
    await usuario.click(
      screen.getByRole('button', { name: /^Rechazar la interpretación/ }),
    )

    const modal = dialogo('Rechazar interpretación')
    const campo = within(modal).getByLabelText(/^Observaciones del rechazo/)
    const confirmar = within(modal).getByRole('button', {
      name: 'Rechazar interpretación',
    })

    await usuario.click(confirmar)
    expect(within(modal).getByRole('alert')).toHaveTextContent(
      'Escribe observaciones de al menos 10 caracteres',
    )

    await usuario.type(campo, 'Corto')
    await usuario.click(confirmar)
    expect(post).not.toHaveBeenCalled()

    await usuario.clear(campo)
    await usuario.type(campo, 'No procede: contradice el artículo 12 del reglamento.')
    await usuario.click(confirmar)

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('interpretaciones-regla/int-1/resolucion', {
        estado: 'RECHAZADA',
        observaciones: 'No procede: contradice el artículo 12 del reglamento.',
      }),
    )
    expect(
      await screen.findByText(
        'La interpretación quedó rechazada y las observaciones se guardaron.',
      ),
    ).toBeInTheDocument()
  })

  it('DEC-59: una temporada cerrada no admite altas pero sí resuelve pendientes', async () => {
    sesion.yo.permisos = permisos(
      CONSULTAR,
      REGISTRAR_INTERPRETACION,
      APROBAR_INTERPRETACION,
    )
    get.mockImplementation(
      resolverGet(
        respuestasPorDefecto({ 'temporadas/t-1': { ...TEMPORADA, estado: 'CERRADA' } }),
      ),
    )
    montarInterpretaciones()

    expect(
      await screen.findByText(/La temporada está cerrada: no admite interpretaciones nuevas/),
    ).toBeInTheDocument()
    await screen.findByText(/Si un equipo no se presenta/)
    expect(
      screen.queryByRole('button', { name: 'Registrar interpretación' }),
    ).not.toBeInTheDocument()
    expect(
      screen.getAllByRole('button', { name: /^Autorizar la interpretación/ }),
    ).toHaveLength(1)
  })

  it('VAL-015: un 403 del listado se muestra con ErrorConsulta', async () => {
    get.mockImplementation(
      resolverGet(
        respuestasPorDefecto({
          [pendientes()]: errorApi(403, 'VAL-015', 'No tienes acceso a esta temporada.'),
        }),
      ),
    )
    montarInterpretaciones()

    expect(
      await screen.findByText('No tienes permiso para consultar esto'),
    ).toBeInTheDocument()
    expect(screen.getByText('No tienes acceso a esta temporada.')).toBeInTheDocument()
  })

  it('RF-007: pagina el listado de interpretaciones', async () => {
    sesion.yo.permisos = permisos(CONSULTAR, REGISTRAR_INTERPRETACION)
    const muchas = Array.from({ length: 12 }, (_, indice) => ({
      ...INTERPRETACIONES[0],
      id: `int-${indice + 1}`,
      descripcion: `Interpretación número ${indice + 1}`,
    }))
    get.mockImplementation(
      resolverGet(
        respuestasPorDefecto({
          [LISTA]: paginaDe(muchas.slice(0, 10), {
            totalElementos: 12,
            totalPaginas: 2,
          }),
          'interpretaciones-regla?temporadaId=t-1&pagina=1&tamano=10': paginaDe(
            muchas.slice(10),
            {
              pagina: 1,
              totalElementos: 12,
              totalPaginas: 2,
            },
          ),
        }),
      ),
    )
    montarInterpretaciones()

    expect(await screen.findByText('Página 1 de 2')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument()
    expect(screen.getByText('Interpretación número 11')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith(
      'interpretaciones-regla?temporadaId=t-1&pagina=1&tamano=10',
    )

    await usuario.click(screen.getByRole('button', { name: 'Anterior' }))
    expect(await screen.findByText('Página 1 de 2')).toBeInTheDocument()
  })
})
