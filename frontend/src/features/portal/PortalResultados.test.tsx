import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { PortalResultados } from '@/features/portal/PortalResultados'

function montar() {
  render(
    <MemoryRouter>
      <PortalResultados />
    </MemoryRouter>,
  )
}

describe('PortalResultados', () => {
  it('presenta los resultados y los accesos a las otras secciones', () => {
    montar()

    expect(
      screen.getByRole('heading', { level: 1, name: 'Resultados oficiales' }),
    ).toBeInTheDocument()

    const tablas = screen.getAllByRole('link', {
      name: /tabla de posiciones|Consultar tabla/,
    })
    expect(tablas.length).toBeGreaterThan(0)
    tablas.forEach((enlace) => expect(enlace).toHaveAttribute('href', '/tabla'))

    expect(
      screen.getByRole('link', { name: /Ver programación/ }),
    ).toHaveAttribute('href', '/')
  })

  it('muestra el estado de los resultados sin publicar', () => {
    montar()

    expect(screen.getByText('Aún no hay resultados publicados')).toBeInTheDocument()
    expect(screen.getByText('Sin resultados publicados')).toBeInTheDocument()
  })

  it('explica que solo se publican resultados oficiales', () => {
    montar()

    expect(screen.getByText(/Solo se publican resultados oficiales/)).toBeInTheDocument()
    expect(screen.getByText('Marcador oficial')).toBeInTheDocument()
    expect(screen.getByText('Resultado por default')).toBeInTheDocument()
    expect(screen.getByText('Sin datos personales')).toBeInTheDocument()
  })
})
