import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { PortalProgramacion } from '@/features/portal/PortalProgramacion'

function montar() {
  render(
    <MemoryRouter>
      <PortalProgramacion />
    </MemoryRouter>,
  )
}

describe('PortalProgramacion', () => {
  it('presenta la programación y los accesos del portal', () => {
    montar()

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: 'Programación de la semana',
      }),
    ).toBeInTheDocument()
    const resultados = screen.getAllByRole('link', {
      name: /Consultar resultados/,
    })
    expect(resultados.length).toBeGreaterThan(0)
    resultados.forEach((enlace) =>
      expect(enlace).toHaveAttribute('href', '/resultados'),
    )

    const tablas = screen.getAllByRole('link', {
      name: /Ver tabla de posiciones/,
    })
    expect(tablas.length).toBeGreaterThan(0)
    tablas.forEach((enlace) => expect(enlace).toHaveAttribute('href', '/tabla'))

    expect(
      screen.getByRole('link', { name: /Iniciar sesión/ }),
    ).toHaveAttribute('href', '/login')
  })

  it('muestra el estado de la programación sin publicar', () => {
    montar()

    expect(
      screen.getByText('Aún no hay programación publicada'),
    ).toBeInTheDocument()
    expect(screen.getByText('Sin jornada publicada')).toBeInTheDocument()
  })

  it('explica que solo se publica información aprobada', () => {
    montar()

    expect(
      screen.getByText(/Solo se publica información aprobada/),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Información oficial, paso a paso' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Cédula validada')).toBeInTheDocument()
    expect(screen.getByText('Tabla calculada')).toBeInTheDocument()
    expect(
      screen.getByText(/no publica datos personales/),
    ).toBeInTheDocument()
  })
})
