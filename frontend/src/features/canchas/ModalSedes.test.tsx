import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ModalSedes } from '@/features/canchas/ModalSedes'
import type { Sede } from '@/features/canchas/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, patch } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), patch: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, patch } }))

const MORELOS: Sede = { id: 's1', nombre: 'Unidad Deportiva Morelos', direccion: 'Av. Morelos 1' }
const NORTE: Sede = { id: 's2', nombre: 'Unidad Deportiva Norte' }

let sedes: Sede[] = []

function montar() {
  const onCerrar = vi.fn()

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ModalSedes onCerrar={onCerrar} />
    </QueryClientProvider>,
  )

  return { onCerrar }
}

const lista = () => screen.getByRole('region', { name: 'Sedes registradas' })
const formulario = () => screen.getByRole('form')

describe('ModalSedes', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    sedes = [MORELOS]
    get.mockImplementation(async (ruta: string) => {
      if (ruta === 'sedes') {
        return sedes
      }
      throw new Error(`Ruta no simulada: ${ruta}`)
    })
  })

  it('RF-028 (CA-1): registrar una sede envía el nombre recortado y la nueva queda en la lista', async () => {
    post.mockImplementation(async () => {
      sedes = [MORELOS, NORTE]
      return NORTE
    })
    montar()

    expect(await within(lista()).findByText('Unidad Deportiva Morelos')).toBeInTheDocument()
    await userEvent.type(within(formulario()).getByLabelText(/Nombre/), '  Unidad Deportiva Norte ')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar sede' }))

    expect(post).toHaveBeenCalledWith('sedes', { nombre: 'Unidad Deportiva Norte' })
    expect(await within(lista()).findByText('Unidad Deportiva Norte')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Sede «Unidad Deportiva Norte» registrada.')
    expect(within(formulario()).getByLabelText(/Nombre/)).toHaveValue('')
  })

  it('RF-028 (CA-1): la dirección se envía solo si se escribió', async () => {
    post.mockResolvedValue(NORTE)
    montar()

    await userEvent.type(within(formulario()).getByLabelText(/Nombre/), 'Sede Sur')
    await userEvent.type(within(formulario()).getByLabelText(/Dirección/), ' Calle 5 ')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar sede' }))

    expect(post).toHaveBeenCalledWith('sedes', { nombre: 'Sede Sur', direccion: 'Calle 5' })
  })

  it('RF-028 (CA-1): un 409 DUPLICADO se marca en el nombre y el modal sigue abierto', async () => {
    post.mockRejectedValue(
      new ApiError({
        title: 'Conflicto',
        status: 409,
        detail: 'Ya existe una sede con ese nombre.',
        codigo: 'DUPLICADO',
      }),
    )
    const { onCerrar } = montar()

    await userEvent.type(within(formulario()).getByLabelText(/Nombre/), 'Unidad Deportiva Morelos')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar sede' }))

    expect(await screen.findByText('Ya existe una sede con ese nombre.')).toBeInTheDocument()
    expect(within(formulario()).getByLabelText(/Nombre/)).toHaveAttribute('aria-invalid', 'true')
    expect(onCerrar).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog', { name: 'Sedes' })).toBeInTheDocument()
  })

  it('RF-028: renombrar envía solo lo que cambió', async () => {
    patch.mockResolvedValue({ ...MORELOS, nombre: 'Unidad Morelos' })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Editar Unidad Deportiva Morelos' }))
    expect(screen.getByRole('heading', { name: 'Editar sede — Unidad Deportiva Morelos' })).toBeInTheDocument()
    await userEvent.clear(within(formulario()).getByLabelText(/Nombre/))
    await userEvent.type(within(formulario()).getByLabelText(/Nombre/), 'Unidad Morelos')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(patch).toHaveBeenCalledWith('sedes/s1', { nombre: 'Unidad Morelos' })
    expect(await screen.findByRole('heading', { name: 'Nueva sede' })).toBeInTheDocument()
  })

  it('RF-028: vaciar la dirección envía direccion en blanco', async () => {
    patch.mockResolvedValue({ id: 's1', nombre: MORELOS.nombre })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Editar Unidad Deportiva Morelos' }))
    await userEvent.clear(within(formulario()).getByLabelText(/Dirección/))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(patch).toHaveBeenCalledWith('sedes/s1', { direccion: '' })
  })

  it('RF-028: editar sin cambios no llama a la API y «Cancelar edición» vuelve a «Nueva sede»', async () => {
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Editar Unidad Deportiva Morelos' }))
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(patch).not.toHaveBeenCalled()
    expect(screen.getByRole('heading', { name: 'Nueva sede' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Editar Unidad Deportiva Morelos' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar edición' }))

    expect(screen.getByRole('heading', { name: 'Nueva sede' })).toBeInTheDocument()
    expect(within(formulario()).getByLabelText(/Nombre/)).toHaveValue('')
  })

  it('RF-028 (D9): un 404 al editar se explica y recarga las sedes', async () => {
    patch.mockRejectedValue(
      new ApiError({ title: 'No encontrado', status: 404, detail: 'La sede no existe.', codigo: 'NO_ENCONTRADO' }),
    )
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Editar Unidad Deportiva Morelos' }))
    await userEvent.type(within(formulario()).getByLabelText(/Nombre/), ' 2')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByText('La sede no existe.')).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('RF-028: un nombre vacío no llama a la API', async () => {
    montar()

    await userEvent.type(within(formulario()).getByLabelText(/Nombre/), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar sede' }))

    expect(post).not.toHaveBeenCalled()
    expect(screen.getByText('Escribe el nombre de la sede.')).toBeInTheDocument()
  })

  it('RF-028: sin sedes muestra «Aún no hay sedes»', async () => {
    sedes = []
    montar()

    expect(await screen.findByText('Aún no hay sedes')).toBeInTheDocument()
  })

  it('RF-028: si la lista falla ofrece reintentar', async () => {
    get.mockRejectedValue(new ApiError({ title: 'Error', status: 500, detail: 'Falla del servidor.', codigo: 'INTERNO' }))
    montar()

    expect(await screen.findByText('Falla del servidor.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
