import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { error, fila } from '@/features/registros/datosPruebaRegistros'
import { ModalHistorialRegistro } from '@/features/registros/plantilla/ModalHistorialRegistro'
import type { CambioEstadoRegistro } from '@/features/registros/tipos'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get } }))

const REGISTRO = fila({ estado: 'BAJA' })

const ALTA_VALIDACION_BAJA: CambioEstadoRegistro[] = [
  {
    estadoAnterior: null,
    estadoNuevo: 'PENDIENTE_VALIDACION',
    motivo: 'Alta del registro',
    cambiadoPor: { id: 'perfil-directiva', nombre: 'Directiva Halcones' },
    cambiadoEn: '2026-11-11T12:00:00-06:00',
  },
  {
    estadoAnterior: 'PENDIENTE_VALIDACION',
    estadoNuevo: 'ACTIVO',
    motivo: 'Documentos revisados en ventanilla',
    cambiadoPor: { id: 'perfil-secretario', nombre: null },
    cambiadoEn: '2026-11-12T10:30:00-06:00',
  },
  {
    estadoAnterior: 'ACTIVO',
    estadoNuevo: 'BAJA',
    motivo: 'El jugador se mudó de ciudad',
    cambiadoPor: { id: 'perfil-directiva', nombre: 'Directiva Halcones' },
    cambiadoEn: '2026-11-14T18:05:00-06:00',
  },
]

function montar(onCerrar = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ModalHistorialRegistro registro={REGISTRO} onCerrar={onCerrar} />
    </QueryClientProvider>,
  )
  return onCerrar
}

describe('ModalHistorialRegistro (F-17, B-07.2)', () => {
  beforeEach(() => {
    get.mockReset()
  })

  it('RF-018: muestra alta → validación → baja en orden, con estado, fecha, quién y motivo', async () => {
    get.mockResolvedValue(ALTA_VALIDACION_BAJA)
    montar()

    const dialogo = screen.getByRole('dialog', { name: `Historial de ${REGISTRO.persona.nombreCompleto}` })
    expect(dialogo).toHaveAccessibleDescription('Halcones · Libre · Temporada 2026')
    const lista = await within(dialogo).findByRole('list', {
      name: `Cambios de estado de ${REGISTRO.persona.nombreCompleto}`,
    })
    expect(get).toHaveBeenCalledWith(`registros-jugador/${REGISTRO.id}/historial`)

    const [alta, validacion, baja] = within(lista).getAllByRole('listitem')
    expect(within(alta).getByText('Pendiente de validación')).toBeInTheDocument()
    expect(within(alta).getByText('Estado inicial')).toBeInTheDocument()
    expect(alta).toHaveTextContent('Por Directiva Halcones')
    expect(alta).toHaveTextContent('«Alta del registro»')
    expect(within(alta).getByText(/11 nov 2026/)).toHaveAttribute('datetime', '2026-11-11T12:00:00-06:00')

    expect(within(validacion).getByText('Activo')).toBeInTheDocument()
    expect(within(validacion).getByText('Antes: Pendiente de validación')).toBeInTheDocument()
    expect(validacion).toHaveTextContent('Por otro usuario')
    expect(validacion).toHaveTextContent('Motivo: «Documentos revisados en ventanilla»')

    expect(within(baja).getByText('Baja')).toBeInTheDocument()
    expect(within(baja).getByText('Antes: Activo')).toBeInTheDocument()
    expect(baja).toHaveTextContent('«El jugador se mudó de ciudad»')
    expect(within(baja).getByText(/14 nov 2026/)).toBeInTheDocument()
  })

  it('muestra el estado vacío si el historial no tiene cambios', async () => {
    get.mockResolvedValue([])
    montar()

    expect(await screen.findByText('Este registro aún no tiene cambios de estado')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('VAL-024: un 404 muestra el error de consulta sin reintentar', async () => {
    get.mockRejectedValue(error(404, 'NO_ENCONTRADO'))
    montar()

    expect(await screen.findByText(/No encontramos esta información/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /reintentar/i })).not.toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(1)
  })

  it('se cierra con «Cerrar» y con el botón de la esquina', async () => {
    const user = userEvent.setup()
    get.mockResolvedValue(ALTA_VALIDACION_BAJA)
    const onCerrar = montar()

    await user.click(screen.getByRole('button', { name: 'Cerrar historial' }))
    await user.click(screen.getByRole('button', { name: 'Cerrar' }))

    expect(onCerrar).toHaveBeenCalledTimes(2)
  })
})
