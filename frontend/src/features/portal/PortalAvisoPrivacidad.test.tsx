import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { PortalAvisoPrivacidad } from '@/features/portal/PortalAvisoPrivacidad'

function montar() {
  render(
    <MemoryRouter>
      <PortalAvisoPrivacidad />
    </MemoryRouter>,
  )
}

describe('PortalAvisoPrivacidad', () => {
  it('presenta el aviso y los accesos al portal', () => {
    montar()

    expect(
      screen.getByRole('heading', { level: 1, name: 'Aviso de privacidad' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: /Ir a la programación/ }),
    ).toHaveAttribute('href', '/')
    expect(
      screen.getByRole('link', { name: /Ver resultados/ }),
    ).toHaveAttribute('href', '/resultados')
    expect(
      screen.getByRole('link', { name: /Volver al portal/ }),
    ).toHaveAttribute('href', '/')
  })

  it('marca el texto legal como pendiente sin inventarlo', () => {
    montar()

    expect(screen.getByText('El aviso aún no está disponible')).toBeInTheDocument()
    expect(screen.getByText('Pendiente de publicación')).toBeInTheDocument()
    expect(
      screen.getByText(/El contenido legal del aviso de privacidad lo define la Liga/),
    ).toBeInTheDocument()
  })

  it('explica las reglas de protección de datos vigentes', () => {
    montar()

    expect(
      screen.getByText(/El portal no publica datos personales/),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Datos personales fuera del portal'),
    ).toBeInTheDocument()
    expect(screen.getByText('Difusión con consentimiento')).toBeInTheDocument()
    expect(screen.getByText('Acceso por ámbito')).toBeInTheDocument()
    expect(
      screen.getByText(/Jugador \(dato reservado\)/),
    ).toBeInTheDocument()
  })
})
