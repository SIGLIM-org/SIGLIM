import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FormularioCancha } from '@/features/canchas/FormularioCancha'
import type { Cancha, Sede } from '@/features/canchas/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, patch } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), patch: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, patch } }))

const SEDES: Sede[] = [
  { id: 's1', nombre: 'Unidad Deportiva Morelos', direccion: 'Av. Morelos 1' },
  { id: 's2', nombre: 'Unidad Deportiva Norte' },
]

const CANCHA: Cancha = {
  id: 'c1',
  nombre: 'Cancha 1',
  ubicacion: 'UNIDAD_DEPORTIVA',
  direccion: 'Calle Reforma 1',
  propietario: 'LIGA',
  caracteristicas: 'Pasto natural',
  activa: true,
  creadoEn: '2030-01-01T10:00:00-06:00',
}

function montar(cancha?: Cancha) {
  const onGuardada = vi.fn()
  const onCerrar = vi.fn()

  render(
    <QueryClientProvider client={new QueryClient()}>
      <FormularioCancha cancha={cancha} onCerrar={onCerrar} onGuardada={onGuardada} />
    </QueryClientProvider>,
  )

  return { onGuardada, onCerrar }
}

const guardar = () => userEvent.click(screen.getByRole('button', { name: 'Guardar' }))

describe('FormularioCancha', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    get.mockImplementation(async (ruta: string) => {
      if (ruta === 'sedes') {
        return SEDES
      }
      throw new Error(`Ruta no simulada: ${ruta}`)
    })
  })

  it('RF-027: crear con propietario externo envía solo lo que corresponde', async () => {
    post.mockResolvedValue(CANCHA)
    const { onGuardada } = montar()

    await userEvent.type(screen.getByLabelText(/Nombre/), '  Cancha Norte ')
    await userEvent.selectOptions(screen.getByLabelText(/Ubicación/), 'EXTERNA')
    await userEvent.selectOptions(screen.getByLabelText(/^Propietario/), 'EXTERNO')
    await userEvent.type(screen.getByLabelText(/Propietario externo/), 'Sr. Pérez')
    await guardar()

    expect(post).toHaveBeenCalledWith('canchas', {
      nombre: 'Cancha Norte',
      ubicacion: 'EXTERNA',
      propietario: 'EXTERNO',
      propietarioExterno: 'Sr. Pérez',
    })
    expect(onGuardada.mock.calls[0][0]).toEqual(CANCHA)
  })

  it('RF-027: al crear no ofrece Club y explica por qué', () => {
    montar()

    expect(screen.queryByRole('option', { name: /Club/ })).not.toBeInTheDocument()
    expect(screen.getByText(/Las canchas de club se registran/)).toBeInTheDocument()
  })

  it('RF-027: exige nombre, ubicación, propietario y propietario externo', async () => {
    montar()

    await userEvent.selectOptions(screen.getByLabelText(/^Propietario/), 'EXTERNO')
    await guardar()

    expect(post).not.toHaveBeenCalled()
    expect(screen.getByText('Escribe el nombre de la cancha.')).toBeInTheDocument()
    expect(screen.getByText('Elige la ubicación.')).toBeInTheDocument()
    expect(screen.getByText('Escribe quién es el propietario externo.')).toBeInTheDocument()
  })

  it('RF-027: PATCH envía solo los campos cambiados', async () => {
    patch.mockResolvedValue(CANCHA)
    montar(CANCHA)

    await userEvent.clear(screen.getByLabelText(/Nombre/))
    await userEvent.type(screen.getByLabelText(/Nombre/), 'Cancha Sur')
    await guardar()

    expect(patch).toHaveBeenCalledWith('canchas/c1', { nombre: 'Cancha Sur' })
  })

  it('RF-027: vaciar la dirección la borra y nunca envía activa', async () => {
    patch.mockResolvedValue(CANCHA)
    montar(CANCHA)

    expect(screen.getAllByText('Déjalo vacío para borrarlo.').length).toBeGreaterThan(0)
    await userEvent.clear(screen.getByLabelText(/Dirección/))
    await guardar()

    expect(patch).toHaveBeenCalledWith('canchas/c1', { direccion: '' })
  })

  it('RF-027: sin cambios no envía y cierra', async () => {
    const { onCerrar } = montar(CANCHA)

    await guardar()

    expect(patch).not.toHaveBeenCalled()
    expect(onCerrar).toHaveBeenCalledOnce()
  })

  it('RF-027: al cambiar de propietario envía también el propietario externo, sin clubId', async () => {
    patch.mockResolvedValue(CANCHA)
    montar(CANCHA)

    await userEvent.selectOptions(screen.getByLabelText(/^Propietario/), 'EXTERNO')
    await userEvent.type(screen.getByLabelText(/Propietario externo/), 'Sr. Pérez')
    await guardar()

    expect(patch).toHaveBeenCalledWith('canchas/c1', {
      propietario: 'EXTERNO',
      propietarioExterno: 'Sr. Pérez',
    })
  })

  it('RF-027: una cancha de club muestra su club y no ofrece otro', () => {
    montar({ ...CANCHA, propietario: 'CLUB', club: { id: 'club-1', nombre: 'Halcones' } })

    expect(screen.getByLabelText(/^Propietario/)).toHaveValue('CLUB')
    expect(screen.getAllByRole('option', { name: /Club/ })).toHaveLength(1)
    expect(screen.getByRole('option', { name: 'Club: Halcones' })).toBeInTheDocument()
  })

  it('RF-027: un 409 DUPLICADO se marca en el nombre', async () => {
    patch.mockRejectedValue(
      new ApiError({ title: 'Conflicto', status: 409, detail: 'Ya existe esa cancha.', codigo: 'DUPLICADO' }),
    )
    montar(CANCHA)

    await userEvent.type(screen.getByLabelText(/Nombre/), ' B')
    await guardar()

    expect(await screen.findByText('Ya existe esa cancha.')).toBeInTheDocument()
    expect(screen.getByLabelText(/Nombre/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('RF-027: un 400 marca los campos de errores y muestra su detalle', async () => {
    post.mockRejectedValue(
      new ApiError({
        title: 'Inválida',
        status: 400,
        detail: 'Revisa los campos marcados.',
        codigo: 'VALIDACION',
        errores: [{ campo: 'nombre', mensaje: 'El nombre admite hasta 120 caracteres' }],
      }),
    )
    montar()

    await userEvent.type(screen.getByLabelText(/Nombre/), 'Cancha')
    await userEvent.selectOptions(screen.getByLabelText(/Ubicación/), 'EXTERNA')
    await userEvent.selectOptions(screen.getByLabelText(/^Propietario/), 'LIGA')
    await guardar()

    expect(await screen.findByText('El nombre admite hasta 120 caracteres')).toBeInTheDocument()
    expect(screen.getByText('Revisa los campos marcados.')).toBeInTheDocument()
  })

  it('RF-028 (CA-2): crear con sede envía sedeId', async () => {
    post.mockResolvedValue(CANCHA)
    montar()

    await userEvent.type(screen.getByLabelText(/Nombre/), 'Cancha Norte')
    await userEvent.selectOptions(screen.getByLabelText(/Ubicación/), 'UNIDAD_DEPORTIVA')
    await userEvent.selectOptions(screen.getByLabelText(/^Propietario/), 'LIGA')
    await userEvent.selectOptions(
      await screen.findByRole('combobox', { name: 'Sede' }),
      'Unidad Deportiva Norte',
    )
    await guardar()

    expect(post).toHaveBeenCalledWith('canchas', {
      nombre: 'Cancha Norte',
      ubicacion: 'UNIDAD_DEPORTIVA',
      propietario: 'LIGA',
      sedeId: 's2',
    })
  })

  it('RF-028 (CA-2): crear con «Sin sede» no envía sedeId', async () => {
    post.mockResolvedValue(CANCHA)
    montar()

    expect(await screen.findByRole('option', { name: 'Unidad Deportiva Norte' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Sede' })).toHaveValue('')
    await userEvent.type(screen.getByLabelText(/Nombre/), 'Cancha Norte')
    await userEvent.selectOptions(screen.getByLabelText(/Ubicación/), 'EXTERNA')
    await userEvent.selectOptions(screen.getByLabelText(/^Propietario/), 'LIGA')
    await guardar()

    expect(post.mock.calls[0][1]).not.toHaveProperty('sedeId')
  })

  it('RF-028 (CA-2): editar cambiando de sede envía sedeId sin quitarSede', async () => {
    patch.mockResolvedValue(CANCHA)
    montar({ ...CANCHA, sede: { id: 's1', nombre: 'Unidad Deportiva Morelos' } })

    await screen.findByRole('option', { name: 'Unidad Deportiva Norte' })
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Sede' }), 's2')
    await guardar()

    expect(patch).toHaveBeenCalledWith('canchas/c1', { sedeId: 's2' })
  })

  it('RF-028 (CA-3): editar a «Sin sede» envía quitarSede sin sedeId', async () => {
    patch.mockResolvedValue(CANCHA)
    montar({ ...CANCHA, sede: { id: 's1', nombre: 'Unidad Deportiva Morelos' } })

    await screen.findByRole('option', { name: 'Unidad Deportiva Norte' })
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Sede' }), '')
    await guardar()

    expect(patch).toHaveBeenCalledWith('canchas/c1', { quitarSede: true })
  })

  it('RF-028: editar sin tocar la sede no envía sedeId ni quitarSede', async () => {
    patch.mockResolvedValue(CANCHA)
    montar({ ...CANCHA, sede: { id: 's1', nombre: 'Unidad Deportiva Morelos' } })

    await screen.findByRole('option', { name: 'Unidad Deportiva Norte' })
    await userEvent.type(screen.getByLabelText(/Nombre/), ' B')
    await guardar()

    expect(patch).toHaveBeenCalledWith('canchas/c1', { nombre: 'Cancha 1 B' })
  })

  it('RF-028: mientras cargan las sedes conserva la sede actual y deshabilita el campo', async () => {
    get.mockReturnValue(new Promise(() => {}))
    montar({ ...CANCHA, sede: { id: 's9', nombre: 'Sede Antigua' } })

    const campo = screen.getByRole('combobox', { name: 'Sede' })

    expect(campo).toBeDisabled()
    expect(campo).toHaveValue('s9')
    expect(screen.getByRole('option', { name: 'Sede Antigua' })).toBeInTheDocument()
    await guardar()
    expect(patch).not.toHaveBeenCalled()
  })

  it('RF-028: conserva la sede actual aunque no venga en la lista de sedes', async () => {
    montar({ ...CANCHA, sede: { id: 's9', nombre: 'Sede Antigua' } })

    await screen.findByRole('option', { name: 'Unidad Deportiva Norte' })

    expect(screen.getByRole('combobox', { name: 'Sede' })).toHaveValue('s9')
    expect(screen.getByRole('option', { name: 'Sede Antigua' })).toBeInTheDocument()
  })

  it('RF-028: sin sede, mientras cargan las sedes muestra «Cargando sedes…»', () => {
    get.mockReturnValue(new Promise(() => {}))
    montar()

    expect(screen.getByRole('combobox', { name: 'Sede' })).toBeDisabled()
    expect(screen.getByRole('option', { name: 'Cargando sedes…' })).toBeInTheDocument()
  })

  it('RF-028: sin sedes registradas explica cómo agregarlas', async () => {
    get.mockResolvedValue([])
    montar()

    expect(await screen.findByText('Registra sedes con el botón Sedes.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Sede' })).toHaveAttribute(
      'aria-describedby',
      expect.stringContaining('sede-ayuda'),
    )
  })

  it('RF-028 (D9): un 422 REFERENCIA_INVALIDA marca el campo Sede', async () => {
    patch.mockRejectedValue(
      new ApiError({
        title: 'Referencia inválida',
        status: 422,
        detail: 'La sede no existe.',
        codigo: 'REFERENCIA_INVALIDA',
      }),
    )
    montar(CANCHA)

    await screen.findByRole('option', { name: 'Unidad Deportiva Norte' })
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Sede' }), 's2')
    await guardar()

    expect(await screen.findByText('La sede elegida ya no existe. Elige otra.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Sede' })).toHaveAttribute('aria-invalid', 'true')
    expect(get).toHaveBeenCalledTimes(2)
  })
})
