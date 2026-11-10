import { useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ModalMotivo } from '@/components/ui/ModalMotivo'

function Escenario({ onConfirmar = vi.fn() }: { onConfirmar?: (motivo: string) => void }) {
  const [abierto, setAbierto] = useState(false)

  return (
    <>
      <button type="button" onClick={() => setAbierto(true)}>
        Suspender partido
      </button>
      <ModalMotivo
        abierto={abierto}
        titulo="Suspender partido"
        descripcion="El partido pasará a Suspendido y no contará en la tabla."
        onConfirmar={(motivo) => {
          onConfirmar(motivo)
          setAbierto(false)
        }}
        onCancelar={() => setAbierto(false)}
      />
    </>
  )
}

async function abrir() {
  await userEvent.click(screen.getByRole('button', { name: 'Suspender partido' }))
  return screen.getByRole('dialog', { name: 'Suspender partido' })
}

describe('ModalMotivo', () => {
  it('muestra título, efecto y el campo «Motivo» obligatorio', async () => {
    render(<Escenario />)

    const dialogo = await abrir()

    expect(dialogo).toHaveAccessibleDescription(
      'El partido pasará a Suspendido y no contará en la tabla.',
    )
    expect(screen.getByLabelText(/Motivo/)).toBeRequired()
  })

  it('RN-021: no confirma con menos de 10 caracteres', async () => {
    const onConfirmar = vi.fn()
    render(<Escenario onConfirmar={onConfirmar} />)
    await abrir()

    await userEvent.type(screen.getByLabelText(/Motivo/), 'Lluvia')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(onConfirmar).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Escribe un motivo de al menos 10 caracteres.',
    )
    expect(screen.getByLabelText(/Motivo/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('RN-021: no confirma si el motivo son solo espacios', async () => {
    const onConfirmar = vi.fn()
    render(<Escenario onConfirmar={onConfirmar} />)
    await abrir()

    await userEvent.type(screen.getByLabelText(/Motivo/), '               ')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(onConfirmar).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('RN-021: confirma con el motivo recortado y devuelve el foco', async () => {
    const onConfirmar = vi.fn()
    render(<Escenario onConfirmar={onConfirmar} />)
    await abrir()

    await userEvent.type(screen.getByLabelText(/Motivo/), '  Cancha inundada por lluvia  ')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(onConfirmar).toHaveBeenCalledWith('Cancha inundada por lluvia')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Suspender partido' })).toHaveFocus()
  })

  it('RNF-008: Cancelar y Esc cierran sin confirmar', async () => {
    const onConfirmar = vi.fn()
    render(<Escenario onConfirmar={onConfirmar} />)

    await abrir()
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    const dialogo = await abrir()
    fireEvent(dialogo, new Event('cancel', { cancelable: true }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    expect(onConfirmar).not.toHaveBeenCalled()
  })

  it('cuenta los caracteres del motivo sin los espacios de los extremos', async () => {
    render(<Escenario />)
    await abrir()

    await userEvent.type(screen.getByLabelText(/Motivo/), '   corto   ')

    expect(screen.getByText('5/500')).toBeInTheDocument()
  })

  it('H-07: pinta el aviso entre la descripción y el motivo, y conserva lo escrito', () => {
    const { rerender } = render(
      <ModalMotivo
        abierto
        titulo="Desactivar cancha"
        descripcion="Efecto"
        onConfirmar={vi.fn()}
        onCancelar={vi.fn()}
      />,
    )
    fireEvent.change(screen.getByLabelText(/Motivo/), { target: { value: 'Motivo escrito antes' } })

    rerender(
      <ModalMotivo
        abierto
        titulo="Desactivar cancha"
        descripcion="Efecto"
        aviso={<p>Dos partidos afectados</p>}
        onConfirmar={vi.fn()}
        onCancelar={vi.fn()}
      />,
    )

    expect(screen.getByText('Dos partidos afectados')).toBeInTheDocument()
    expect(screen.getByLabelText(/Motivo/)).toHaveValue('Motivo escrito antes')
  })

  it('limita el motivo a 500 caracteres', async () => {
    render(<Escenario />)
    await abrir()

    expect(screen.getByLabelText(/Motivo/)).toHaveAttribute('maxlength', '500')
  })
})
