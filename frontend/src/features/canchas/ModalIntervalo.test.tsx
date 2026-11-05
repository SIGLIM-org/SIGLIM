import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ModalIntervalo } from '@/features/canchas/ModalIntervalo'
import type { PartidoEnCancha } from '@/features/canchas/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { post, patch } = vi.hoisted(() => ({ post: vi.fn(), patch: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { post, patch } }))

const MOTIVO = 'Riego de la cancha programado'
const PARTIDO: PartidoEnCancha = {
  id: 'partido-1',
  estado: 'PROGRAMADO',
  periodo: { desde: '2030-03-10T10:00:00-06:00', hasta: '2030-03-10T12:00:00-06:00' },
  equipoLocal: { id: 'e1', nombre: 'Halcones' },
  equipoVisitante: { id: 'e2', nombre: 'Toros' },
}
const REGISTRO = {
  disponibilidad: {
    id: 'd1',
    tipo: 'BLOQUEO',
    periodo: PARTIDO.periodo,
    motivo: MOTIVO,
    creadoEn: '2030-03-01T10:00:00-06:00',
  },
  partidosAfectados: [PARTIDO],
}
const CUERPO = {
  tipo: 'BLOQUEO',
  periodo: { desde: '2030-03-10T10:00:00-06:00', hasta: '2030-03-10T12:00:00-06:00' },
  motivo: MOTIVO,
}

const AYUDA_MANTENIMIENTO = 'Bloquea la cancha igual que un bloqueo y se distingue en el calendario.'

function conflictoAceptable(detail = 'El bloqueo afecta a 1 partido(s), que no se reprogramarán solos.') {
  return new ApiError({
    title: 'Conflicto',
    status: 409,
    detail,
    codigo: 'RF-030',
    aceptable: true,
    extensiones: { partidosAfectados: [PARTIDO] },
  })
}

function montar(tipo: 'DISPONIBLE' | 'BLOQUEO' = 'BLOQUEO') {
  const onRegistrado = vi.fn()
  const onCerrar = vi.fn()

  render(
    <QueryClientProvider client={new QueryClient()}>
      <ModalIntervalo
        canchaId="c1"
        canchaNombre="Cancha 2"
        tipo={tipo}
        onCerrar={onCerrar}
        onRegistrado={onRegistrado}
      />
    </QueryClientProvider>,
  )

  return { onRegistrado, onCerrar }
}

function llenar(motivo = MOTIVO) {
  fireEvent.change(screen.getByLabelText(/Inicio/), { target: { value: '2030-03-10T10:00' } })
  fireEvent.change(screen.getByLabelText(/^Fin/), { target: { value: '2030-03-10T12:00' } })
  if (motivo) {
    fireEvent.change(screen.getByRole('textbox'), { target: { value: motivo } })
  }
}

const guardar = () => userEvent.click(screen.getByRole('button', { name: 'Guardar' }))
const selectorTipo = () => screen.getByRole('combobox', { name: /Tipo de restricción/ })
const elegirMantenimiento = () => userEvent.selectOptions(selectorTipo(), 'MANTENIMIENTO')

describe('ModalIntervalo', () => {
  beforeEach(() => {
    post.mockReset()
    patch.mockReset()
  })

  it('RN-021: no envía un bloqueo con motivo de menos de 10 caracteres', async () => {
    montar()
    llenar('   corto   ')

    await guardar()

    expect(post).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('al menos 10 caracteres')
    expect(screen.getByText('5/500')).toBeInTheDocument()
  })

  it('RF-029: exige ambos extremos y que el inicio sea anterior al fin', async () => {
    montar('DISPONIBLE')

    await guardar()
    expect(post).not.toHaveBeenCalled()
    expect(screen.getByText('Indica el inicio.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Inicio/), { target: { value: '2030-03-10T12:00' } })
    fireEvent.change(screen.getByLabelText(/^Fin/), { target: { value: '2030-03-10T10:00' } })
    await guardar()

    expect(post).not.toHaveBeenCalled()
    expect(screen.getByText('El fin debe ser posterior al inicio.')).toBeInTheDocument()
  })

  it('RF-029: la disponibilidad sin nota no envía motivo', async () => {
    post.mockResolvedValue({ ...REGISTRO, partidosAfectados: [] })
    const { onRegistrado } = montar('DISPONIBLE')
    expect(screen.getByLabelText('Nota (opcional)')).toBeInTheDocument()
    llenar('')

    await guardar()

    expect(post).toHaveBeenCalledWith('canchas/c1/disponibilidad', {
      tipo: 'DISPONIBLE',
      periodo: CUERPO.periodo,
    })
    expect(onRegistrado).toHaveBeenCalledOnce()
  })

  it('H-07 criterio 3 / RF-030: lista los partidos afectados y confirma con confirmarPartidosAfectados', async () => {
    post.mockRejectedValueOnce(conflictoAceptable()).mockResolvedValueOnce(REGISTRO)
    const { onRegistrado } = montar()
    llenar()

    await guardar()

    expect(await screen.findByText('Halcones vs Toros')).toBeInTheDocument()
    expect(screen.getByText('Los partidos no se reprogramarán ni se cancelarán.')).toBeInTheDocument()
    expect(onRegistrado).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Bloquear de todos modos' }))

    expect(post).toHaveBeenCalledTimes(2)
    expect(post).toHaveBeenNthCalledWith(1, 'canchas/c1/disponibilidad', CUERPO)
    expect(post).toHaveBeenNthCalledWith(2, 'canchas/c1/disponibilidad', {
      ...CUERPO,
      confirmarPartidosAfectados: true,
    })
    expect(patch).not.toHaveBeenCalled()
    expect(onRegistrado).toHaveBeenCalledOnce()
  })

  it('PEN-007: cambiar un dato en el segundo paso vuelve al primero y no confirma', async () => {
    post.mockRejectedValueOnce(conflictoAceptable())
    montar()
    llenar()
    await guardar()
    await screen.findByText('Halcones vs Toros')

    fireEvent.change(screen.getByLabelText(/^Fin/), { target: { value: '2030-03-10T11:00' } })

    expect(screen.queryByText('Halcones vs Toros')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bloquear de todos modos' })).not.toBeInTheDocument()

    post.mockResolvedValueOnce(REGISTRO)
    await guardar()

    expect(post).toHaveBeenLastCalledWith('canchas/c1/disponibilidad', {
      ...CUERPO,
      periodo: { ...CUERPO.periodo, hasta: '2030-03-10T11:00:00-06:00' },
    })
  })

  it('PEN-007: «Cambiar horario» vuelve al formulario con los datos', async () => {
    post.mockRejectedValueOnce(conflictoAceptable())
    montar()
    llenar()
    await guardar()

    await userEvent.click(await screen.findByRole('button', { name: 'Cambiar horario' }))

    expect(screen.getByRole('button', { name: 'Guardar' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Inicio/)).toHaveValue('2030-03-10T10:00')
    expect(screen.getByRole('textbox')).toHaveValue(MOTIVO)
  })

  it('RF-029: dos disponibilidades traslapadas muestran AlertaBloqueo', async () => {
    post.mockRejectedValue(
      new ApiError({
        title: 'Conflicto',
        status: 409,
        detail: 'La cancha ya tiene disponibilidad registrada.',
        codigo: 'RF-029',
      }),
    )
    montar('DISPONIBLE')
    llenar('')

    await guardar()

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('La cancha ya tiene disponibilidad registrada.')
    expect(alerta).toHaveTextContent('Elige un horario que no se traslape')
    expect(alerta).toHaveTextContent('RF-029')
  })

  it('VAL-015: un 403 muestra su causa dentro del modal y no lo cierra', async () => {
    post.mockRejectedValue(
      new ApiError({ title: 'Prohibido', status: 403, detail: 'Tu cuenta no tiene el permiso.', codigo: 'VAL-015' }),
    )
    const { onCerrar } = montar()
    llenar()

    await guardar()

    expect(await screen.findByRole('alert')).toHaveTextContent('Tu cuenta no tiene el permiso.')
    expect(onCerrar).not.toHaveBeenCalled()
  })

  it('RN-021: un 400 del servidor marca el motivo', async () => {
    post.mockRejectedValue(
      new ApiError({ title: 'Inválida', status: 400, detail: 'Escribe un motivo válido.', codigo: 'RN-021' }),
    )
    montar()
    llenar()

    await guardar()

    expect(await screen.findByRole('alert')).toHaveTextContent('Escribe un motivo válido.')
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true')
  })

  it('RNF-008: Esc cancela, salvo mientras se envía', async () => {
    post.mockReturnValue(new Promise(() => {}))
    const { onCerrar } = montar()
    llenar()
    const dialogo = screen.getByRole('dialog', { name: /Bloquear intervalo/ })

    fireEvent(dialogo, new Event('cancel', { cancelable: true }))
    expect(onCerrar).toHaveBeenCalledOnce()

    await guardar()
    fireEvent(dialogo, new Event('cancel', { cancelable: true }))
    expect(onCerrar).toHaveBeenCalledOnce()
  })

  it('RF-031 (E1/E3): el modo bloqueo ofrece «Tipo de restricción» con «Bloqueo» por defecto; el de disponibilidad no', () => {
    montar()

    expect(selectorTipo()).toHaveValue('BLOQUEO')
    expect(selectorTipo()).toHaveDisplayValue('Bloqueo')
    expect(screen.getByRole('option', { name: 'Mantenimiento' })).toBeInTheDocument()

    cleanup()
    montar('DISPONIBLE')

    expect(screen.queryByRole('combobox', { name: /Tipo de restricción/ })).not.toBeInTheDocument()
  })

  it('RF-031 (criterio 1): registra un mantenimiento con su motivo', async () => {
    post.mockResolvedValue({ ...REGISTRO, partidosAfectados: [] })
    const { onRegistrado } = montar()
    expect(screen.queryByText(AYUDA_MANTENIMIENTO)).not.toBeInTheDocument()

    await elegirMantenimiento()
    llenar()

    expect(screen.getByText(AYUDA_MANTENIMIENTO)).toBeInTheDocument()
    expect(selectorTipo()).toHaveAccessibleDescription(AYUDA_MANTENIMIENTO)

    await guardar()

    expect(post).toHaveBeenCalledWith('canchas/c1/disponibilidad', { ...CUERPO, tipo: 'MANTENIMIENTO' })
    expect(onRegistrado).toHaveBeenCalledOnce()
  })

  it('RF-031 / RN-021 (criterio 3): no envía un mantenimiento con motivo de menos de 10 caracteres', async () => {
    montar()
    await elegirMantenimiento()
    llenar('corto')

    await guardar()

    expect(post).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('al menos 10 caracteres')
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true')
  })

  it('RF-031 / RF-030 (criterio 2): un mantenimiento sobre partidos se confirma con «Registrar de todos modos»', async () => {
    const detalle = 'El mantenimiento afecta a 1 partido(s), que no se reprogramarán solos.'
    post.mockRejectedValueOnce(conflictoAceptable(detalle)).mockResolvedValueOnce(REGISTRO)
    const { onRegistrado } = montar()
    await elegirMantenimiento()
    llenar()

    await guardar()

    expect(await screen.findByText(detalle)).toBeInTheDocument()
    expect(screen.getByText('Halcones vs Toros')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bloquear de todos modos' })).not.toBeInTheDocument()
    expect(onRegistrado).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Registrar de todos modos' }))

    expect(post).toHaveBeenNthCalledWith(2, 'canchas/c1/disponibilidad', {
      ...CUERPO,
      tipo: 'MANTENIMIENTO',
      confirmarPartidosAfectados: true,
    })
    expect(onRegistrado).toHaveBeenCalledOnce()
  })

  it('PEN-007 (E4): cambiar el tipo en el segundo paso vuelve al primero y no confirma', async () => {
    post.mockRejectedValueOnce(conflictoAceptable())
    montar()
    llenar()
    await guardar()
    await screen.findByText('Halcones vs Toros')

    await elegirMantenimiento()

    expect(screen.queryByText('Halcones vs Toros')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bloquear de todos modos' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registrar de todos modos' })).not.toBeInTheDocument()

    post.mockResolvedValueOnce(REGISTRO)
    await guardar()

    expect(post).toHaveBeenLastCalledWith('canchas/c1/disponibilidad', { ...CUERPO, tipo: 'MANTENIMIENTO' })
  })
})
