import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PestanaTarifas } from '@/features/tesoreria/PestanaTarifas'
import type { ContextoTarifas, Tarifa } from '@/features/tesoreria/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, patch } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), patch: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, patch } }))

const LIBRE = { id: 'cat-1', nombre: 'Libre' }
const VETERANOS = { id: 'cat-2', nombre: 'Veteranos' }

const CONTEXTO: ContextoTarifas = {
  temporadas: [
    { id: 'tp-2026', nombre: 'Temporada 2026', estado: 'ACTIVA', categorias: [LIBRE, VETERANOS] },
    { id: 'tp-2025', nombre: 'Temporada 2025', estado: 'CERRADA', categorias: [LIBRE] },
  ],
}

function tarifa(id: string, temporadaId: string, concepto: Tarifa['concepto'], monto: number): Tarifa {
  return {
    id,
    temporada: { id: temporadaId, nombre: temporadaId },
    categoria: LIBRE,
    concepto,
    monto,
    esDemo: true,
  }
}

const ARBITRAJE_2026 = tarifa('t-1', 'tp-2026', 'ARBITRAJE', 350)
const CANCHA_2025 = tarifa('t-9', 'tp-2025', 'CANCHA', 200)

let tarifas2026: Tarifa[]

function responder(contexto: unknown = CONTEXTO) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'tarifas/contexto') {
      if (contexto instanceof Error) throw contexto
      return contexto
    }
    if (ruta === 'tarifas?temporadaId=tp-2026') return tarifas2026
    if (ruta === 'tarifas?temporadaId=tp-2025') return [CANCHA_2025]
    throw new Error(`Ruta inesperada: ${ruta}`)
  })
}

function montar() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PestanaTarifas />
    </QueryClientProvider>,
  )
}

function error(status: number, codigo: string, detail: string, errores?: { campo: string; mensaje: string }[]) {
  return new ApiError({ title: 'Error', status, detail, codigo, errores })
}

async function filaDe(nombre: string): Promise<HTMLElement> {
  return (await screen.findByRole('rowheader', { name: nombre })).closest('tr') as HTMLElement
}

describe('PestanaTarifas', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    tarifas2026 = [ARBITRAJE_2026]
    responder()
  })

  it('B-29.2: abre en la temporada activa con la tabla categoría × concepto y la insignia «No oficial»', async () => {
    montar()

    const libre = await filaDe('Libre')
    expect(screen.getByLabelText('Temporada')).toHaveValue('tp-2026')
    expect(screen.getByText('No oficial')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Arbitraje' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Cancha' })).toBeInTheDocument()
    expect(within(libre).getByLabelText('Arbitraje de Libre')).toHaveValue('350.00')
    expect(within(libre).getByLabelText('Cancha de Libre')).toHaveValue('')
    expect(within(libre).getByRole('button', { name: 'Guardar tarifas de Libre' })).toBeDisabled()
    expect(await filaDe('Veteranos')).toBeInTheDocument()
  })

  it('RF-135: crea la tarifa que falta y cambia la existente, solo con lo que cambió', async () => {
    post.mockResolvedValue(tarifa('t-2', 'tp-2026', 'CANCHA', 120))
    patch.mockResolvedValue(tarifa('t-1', 'tp-2026', 'ARBITRAJE', 400))
    montar()
    const libre = await filaDe('Libre')

    await userEvent.clear(within(libre).getByLabelText('Arbitraje de Libre'))
    await userEvent.type(within(libre).getByLabelText('Arbitraje de Libre'), '400')
    await userEvent.type(within(libre).getByLabelText('Cancha de Libre'), '120.5')
    await userEvent.click(within(libre).getByRole('button', { name: 'Guardar tarifas de Libre' }))

    expect(patch).toHaveBeenCalledWith('tarifas/t-1', { monto: 400 })
    expect(post).toHaveBeenCalledWith('tarifas', {
      temporadaId: 'tp-2026',
      categoriaId: 'cat-1',
      concepto: 'CANCHA',
      monto: 120.5,
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Tarifas de «Libre» guardadas: arbitraje $400.00 y cancha $120.50.',
    )
  })

  it('B-29 criterio 2 · RF-135: un monto negativo se explica en la celda y no se envía', async () => {
    montar()
    const libre = await filaDe('Libre')

    await userEvent.type(within(libre).getByLabelText('Cancha de Libre'), '-5')
    await userEvent.click(within(libre).getByRole('button', { name: 'Guardar tarifas de Libre' }))

    expect(within(libre).getByRole('alert')).toHaveTextContent('El monto no puede ser negativo.')
    expect(within(libre).getByLabelText('Cancha de Libre')).toHaveAttribute('aria-invalid', 'true')
    expect(within(libre).getByLabelText('Cancha de Libre')).toHaveFocus()
    expect(post).not.toHaveBeenCalled()
  })

  it('RF-135: una tarifa existente no se puede dejar vacía', async () => {
    montar()
    const libre = await filaDe('Libre')

    await userEvent.clear(within(libre).getByLabelText('Arbitraje de Libre'))
    await userEvent.click(within(libre).getByRole('button', { name: 'Guardar tarifas de Libre' }))

    expect(within(libre).getByRole('alert')).toHaveTextContent('La tarifa de arbitraje ya existe')
    expect(patch).not.toHaveBeenCalled()
  })

  it('B-29 criterio 1 · RF-135: muestra el 409 DUPLICADO del backend en la fila', async () => {
    post.mockRejectedValue(
      error(409, 'DUPLICADO', 'Ya existe la tarifa de cancha para esa categoría en la temporada: edita su monto.'),
    )
    montar()
    const libre = await filaDe('Libre')

    await userEvent.type(within(libre).getByLabelText('Cancha de Libre'), '100')
    await userEvent.click(within(libre).getByRole('button', { name: 'Guardar tarifas de Libre' }))

    expect(await within(libre).findByRole('alert')).toHaveTextContent('Ya existe la tarifa de cancha')
  })

  it('B-29 criterio 3 · RF-003: una temporada cerrada se muestra de solo lectura', async () => {
    montar()
    await filaDe('Libre')

    await userEvent.selectOptions(screen.getByLabelText('Temporada'), 'tp-2025')

    expect(
      await screen.findByText('La temporada «Temporada 2025» está cerrada: sus tarifas son de solo lectura.'),
    ).toBeInTheDocument()
    const libre = await filaDe('Libre')
    expect(within(libre).getByText('$200.00')).toBeInTheDocument()
    expect(within(libre).getByText('Sin tarifa')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Guardar/ })).not.toBeInTheDocument()
  })

  it('RF-003: si la temporada se cierra al guardar, explica el bloqueo y relee el contexto', async () => {
    patch.mockRejectedValue(
      error(409, 'RF-003', 'La temporada «Temporada 2026» está cerrada: sus tarifas son de solo lectura.'),
    )
    montar()
    const libre = await filaDe('Libre')

    await userEvent.clear(within(libre).getByLabelText('Arbitraje de Libre'))
    await userEvent.type(within(libre).getByLabelText('Arbitraje de Libre'), '1')
    await userEvent.click(within(libre).getByRole('button', { name: 'Guardar tarifas de Libre' }))

    expect(await within(libre).findByRole('alert')).toHaveTextContent('está cerrada')
    await waitFor(() =>
      expect(get.mock.calls.filter(([ruta]) => ruta === 'tarifas/contexto')).toHaveLength(2),
    )
  })

  it('B-29.2: sin temporadas o sin categorías explica qué falta', async () => {
    responder({ temporadas: [] })
    montar()
    expect(await screen.findByText('Aún no hay temporadas')).toBeInTheDocument()
  })

  it('B-29.2: una temporada sin categorías asignadas lo indica', async () => {
    responder({ temporadas: [{ id: 'tp-2026', nombre: 'Temporada 2026', estado: 'CONFIGURACION', categorias: [] }] })
    montar()
    expect(await screen.findByText('Esta temporada no tiene categorías')).toBeInTheDocument()
  })

  it('VAL-015: sin permiso muestra el error de consulta', async () => {
    responder(error(403, 'VAL-015', 'No tienes permiso para realizar esta acción.'))
    montar()
    expect(await screen.findByText('No tienes permiso para consultar esto')).toBeInTheDocument()
  })
})
