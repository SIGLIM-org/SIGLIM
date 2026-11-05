import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'

function montar(error?: string, ayuda?: string) {
  render(
    <CampoSeleccion id="ubicacion" etiqueta="Ubicación" required error={error} ayuda={ayuda}>
      <option value="">Selecciona…</option>
      <option value="EXTERNA">Externa</option>
    </CampoSeleccion>,
  )
}

describe('CampoSeleccion', () => {
  it('RNF-008: la etiqueta queda asociada a la lista', () => {
    montar()

    const campo = screen.getByRole('combobox', { name: /Ubicación/ })

    expect(campo).toBeRequired()
    expect(campo).not.toHaveAttribute('aria-invalid')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('RNF-008: el error se anuncia con role="alert" y queda enlazado al campo', () => {
    montar('Elige la ubicación.')

    const campo = screen.getByRole('combobox', { name: /Ubicación/ })

    expect(screen.getByRole('alert')).toHaveTextContent('Elige la ubicación.')
    expect(campo).toHaveAttribute('aria-invalid', 'true')
    expect(campo).toHaveAccessibleDescription('Elige la ubicación.')
  })

  it('RNF-008: la ayuda se muestra y es la descripción accesible', () => {
    montar(undefined, 'Se usa en la programación.')

    const campo = screen.getByRole('combobox', { name: /Ubicación/ })

    expect(screen.getByText('Se usa en la programación.')).toBeInTheDocument()
    expect(campo).toHaveAccessibleDescription('Se usa en la programación.')
  })

  it('RNF-008: con error, la ayuda se oculta y la descripción es el error', () => {
    montar('Elige la ubicación.', 'Se usa en la programación.')

    const campo = screen.getByRole('combobox', { name: /Ubicación/ })

    expect(screen.queryByText('Se usa en la programación.')).not.toBeInTheDocument()
    expect(campo).toHaveAccessibleDescription('Elige la ubicación.')
  })
})
