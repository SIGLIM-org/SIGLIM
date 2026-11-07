import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ModalPagoSimulado } from '@/features/tesoreria/ModalPagoSimulado'
import type { Cobro, IntentoPago } from '@/features/tesoreria/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { post } = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { post } }))

const COBRO: Cobro = {
  id: 'cb-1',
  tipo: 'ARBITRAJE',
  concepto: 'Arbitraje de jornada',
  monto: 850.5,
  fechaLimite: '2026-11-15',
  estado: 'PENDIENTE',
  club: { id: 'c-1', nombre: 'Club Halcones' },
  equipo: { id: 'e-1', nombre: 'Halcones Sub-17' },
  creadoEn: '2026-11-01T10:00:00-06:00',
}

const INTENTO_PENDIENTE: IntentoPago = {
  id: 'int-1',
  referencia: 'SIM-20261106-4K7Q2M',
  estado: 'PENDIENTE',
  iniciadoEn: '2026-11-06T12:00:00-06:00',
}

const INTENTO_APROBADO: IntentoPago = {
  ...INTENTO_PENDIENTE,
  estado: 'APROBADO',
  resueltoEn: '2026-11-06T12:02:00-06:00',
}

const INTENTO_RECHAZADO: IntentoPago = {
  ...INTENTO_PENDIENTE,
  estado: 'RECHAZADO',
  resueltoEn: '2026-11-06T12:03:00-06:00',
}

const INTENTO_REINTENTO: IntentoPago = {
  ...INTENTO_PENDIENTE,
  id: 'int-2',
  referencia: 'SIM-20261106-9X2P1A',
}

function error(status: number, codigo: string, detail: string) {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

function montar(cobro: Cobro = COBRO) {
  const onCerrar = vi.fn()

  render(
    <QueryClientProvider client={new QueryClient()}>
      <ModalPagoSimulado cobro={cobro} onCerrar={onCerrar} />
    </QueryClientProvider>,
  )

  return { onCerrar }
}

describe('ModalPagoSimulado', () => {
  beforeEach(() => {
    post.mockReset()
  })

  it('H-30.2: recorre los tres pasos y muestra el comprobante al aprobar', async () => {
    post
      .mockResolvedValueOnce(INTENTO_PENDIENTE)
      .mockResolvedValueOnce(INTENTO_APROBADO)
    montar()

    expect(
      screen.getByText('Pago simulado: no se realiza ningún cargo'),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Cobro a pagar' })).toBeInTheDocument()
    expect(screen.getByText('$850.50 MXN')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Iniciar pago simulado' }))

    expect(await screen.findByText('SIM-20261106-4K7Q2M')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Simulación de pago' })).toBeInTheDocument()
    expect(post).toHaveBeenNthCalledWith(1, 'cobros/cb-1/intentos-pago', {})

    await userEvent.click(screen.getByRole('button', { name: 'Aprobar pago simulado' }))

    expect(
      await screen.findByRole('heading', { name: 'Pago registrado' }),
    ).toBeInTheDocument()
    expect(post).toHaveBeenNthCalledWith(2, 'intentos-pago/int-1/confirmacion', {
      resultado: 'APROBADO',
    })
    expect(screen.getByText('Comprobante de pago simulado')).toBeInTheDocument()
    expect(screen.getByText('SIM-20261106-4K7Q2M')).toBeInTheDocument()
    expect(screen.getByText('6 nov 2026, 12:02')).toBeInTheDocument()
    expect(screen.getByText('$850.50 MXN')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /descargar/i })).not.toBeInTheDocument()
  })

  it('H-30.2: al rechazar muestra el comprobante y permite reintentar', async () => {
    post
      .mockResolvedValueOnce(INTENTO_PENDIENTE)
      .mockResolvedValueOnce(INTENTO_RECHAZADO)
      .mockResolvedValueOnce(INTENTO_REINTENTO)
    montar()

    await userEvent.click(screen.getByRole('button', { name: 'Iniciar pago simulado' }))
    await screen.findByText('SIM-20261106-4K7Q2M')
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar pago simulado' }))

    expect(
      await screen.findByRole('heading', { name: 'El pago fue rechazado' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Comprobante de pago simulado')).toBeInTheDocument()
    expect(screen.getByText('6 nov 2026, 12:03')).toBeInTheDocument()
    expect(post).toHaveBeenNthCalledWith(2, 'intentos-pago/int-1/confirmacion', {
      resultado: 'RECHAZADO',
    })

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar pago' }))

    expect(await screen.findByText('SIM-20261106-9X2P1A')).toBeInTheDocument()
    expect(post).toHaveBeenNthCalledWith(3, 'cobros/cb-1/intentos-pago', {})
    expect(screen.getByRole('heading', { name: 'Simulación de pago' })).toBeInTheDocument()
  })

  it('H-30.2: volver al cobro reutiliza el intento pendiente sin otra llamada', async () => {
    post.mockResolvedValueOnce(INTENTO_PENDIENTE)
    montar()

    await userEvent.click(screen.getByRole('button', { name: 'Iniciar pago simulado' }))
    await screen.findByText('SIM-20261106-4K7Q2M')
    await userEvent.click(screen.getByRole('button', { name: 'Volver al cobro' }))

    expect(screen.getByRole('heading', { name: 'Cobro a pagar' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Iniciar pago simulado' }))

    expect(await screen.findByText('SIM-20261106-4K7Q2M')).toBeInTheDocument()
    expect(post).toHaveBeenCalledTimes(1)
  })

  it('H-30.2: muestra el detalle de la API si no puede iniciar la simulación', async () => {
    post.mockRejectedValueOnce(error(409, 'VAL-028', 'El cobro ya está pagado.'))
    montar()

    await userEvent.click(screen.getByRole('button', { name: 'Iniciar pago simulado' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El cobro ya está pagado.')
    expect(screen.getByRole('heading', { name: 'Cobro a pagar' })).toBeInTheDocument()
  })

  it('H-30.2: muestra el detalle de la API si otra confirmación ganó', async () => {
    post
      .mockResolvedValueOnce(INTENTO_PENDIENTE)
      .mockRejectedValueOnce(
        error(409, 'CONCURRENCIA', 'Otra persona confirmó el pago antes que tú.'),
      )
    montar()

    await userEvent.click(screen.getByRole('button', { name: 'Iniciar pago simulado' }))
    await screen.findByText('SIM-20261106-4K7Q2M')
    await userEvent.click(screen.getByRole('button', { name: 'Aprobar pago simulado' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Otra persona confirmó el pago antes que tú.',
    )
    expect(screen.getByRole('heading', { name: 'Simulación de pago' })).toBeInTheDocument()
  })
})
