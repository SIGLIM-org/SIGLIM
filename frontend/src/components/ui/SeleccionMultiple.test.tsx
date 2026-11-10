import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { SeleccionMultiple } from '@/components/ui/SeleccionMultiple'

const OPCIONES = [
  { valor: 'A', etiqueta: 'Opción A' },
  { valor: 'B', etiqueta: 'Opción B' },
]

function Ejemplo({ inicial = [] as string[] }) {
  const [seleccion, setSeleccion] = useState(inicial)

  return (
    <>
      <SeleccionMultiple
        id="prueba"
        etiqueta="Módulo"
        opciones={OPCIONES}
        seleccion={seleccion}
        onChange={setSeleccion}
      />
      <button type="button">Fuera</button>
    </>
  )
}

function boton() {
  return screen.getByRole('button', { name: /^Módulo/ })
}

describe('SeleccionMultiple', () => {
  it('RNF-008: el botón anuncia su etiqueta, su valor y si está abierto', async () => {
    render(<Ejemplo />)

    expect(boton()).toHaveAccessibleName('Módulo Todos')
    expect(boton()).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()

    await userEvent.click(boton())

    expect(boton()).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('group', { name: 'Módulo' })).toBeVisible()
    expect(screen.getAllByRole('checkbox')).toHaveLength(2)
  })

  it('marcar y desmarcar actualiza el resumen', async () => {
    render(<Ejemplo />)

    await userEvent.click(boton())
    await userEvent.click(screen.getByRole('checkbox', { name: 'Opción A' }))
    expect(boton()).toHaveAccessibleName('Módulo Opción A')

    await userEvent.click(screen.getByRole('checkbox', { name: 'Opción B' }))
    expect(boton()).toHaveAccessibleName('Módulo 2 seleccionados')

    await userEvent.click(screen.getByRole('checkbox', { name: 'Opción A' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Opción B' }))
    expect(boton()).toHaveAccessibleName('Módulo Todos')
  })

  it('un valor sin opción, como el de una URL antigua, se muestra crudo', () => {
    render(<Ejemplo inicial={['Z']} />)

    expect(boton()).toHaveAccessibleName('Módulo Z')
  })

  it('RNF-008: Escape cierra y devuelve el foco al botón', async () => {
    render(<Ejemplo />)

    await userEvent.click(boton())
    await userEvent.keyboard('{Escape}')

    expect(boton()).toHaveAttribute('aria-expanded', 'false')
    expect(boton()).toHaveFocus()
  })

  it('pulsar fuera lo cierra', async () => {
    render(<Ejemplo />)

    await userEvent.click(boton())
    await userEvent.click(screen.getByRole('button', { name: 'Fuera' }))

    expect(boton()).toHaveAttribute('aria-expanded', 'false')
  })
})
