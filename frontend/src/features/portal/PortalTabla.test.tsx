import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { PortalTabla } from '@/features/portal/PortalTabla'

function montar() {
  render(
    <MemoryRouter>
      <PortalTabla />
    </MemoryRouter>,
  )
}

describe('PortalTabla', () => {
  it('presenta la tabla y los accesos a las otras secciones', () => {
    montar()

    expect(
      screen.getByRole('heading', { level: 1, name: 'Tabla de posiciones' }),
    ).toBeInTheDocument()

    const resultados = screen.getAllByRole('link', {
      name: /Consultar resultados|Ver resultados/,
    })
    expect(resultados.length).toBeGreaterThan(0)
    resultados.forEach((enlace) =>
      expect(enlace).toHaveAttribute('href', '/resultados'),
    )

    expect(
      screen.getByRole('link', { name: /Ver programación/ }),
    ).toHaveAttribute('href', '/')
  })

  it('muestra el estado de la tabla sin publicar', () => {
    montar()

    expect(screen.getByText('Aún no hay tabla de posiciones')).toBeInTheDocument()
    expect(screen.getByText('Sin posiciones publicadas')).toBeInTheDocument()
  })

  it('distingue la tabla calculada de los resultados oficiales', () => {
    montar()

    expect(
      screen.getByText(/La tabla es un indicador calculado/),
    ).toBeInTheDocument()
    expect(screen.getByText('Columnas de juego')).toBeInTheDocument()
    expect(screen.getByText('Criterios de desempate')).toBeInTheDocument()
    expect(screen.getByText('Parámetros no oficiales')).toBeInTheDocument()
    expect(screen.getByText('No oficial')).toBeInTheDocument()
  })
})
