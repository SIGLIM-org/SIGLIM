import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { PublicLayout } from '@/layouts/PublicLayout'

function montar() {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<p>Contenido del portal</p>} />
          <Route path="/resultados" element={<p>Contenido de resultados</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('PublicLayout', () => {
  it('muestra el encabezado, la navegación y el pie del portal', () => {
    montar()

    const encabezado = screen.getByRole('banner')

    expect(
      within(encabezado).getByRole('link', {
        name: 'SIGLIM, portal público',
      }),
    ).toHaveAttribute('href', '/')
    expect(
      within(encabezado).getByRole('link', { name: 'Programación' }),
    ).toHaveAttribute('href', '/')
    expect(
      within(encabezado).getByRole('link', { name: 'Resultados' }),
    ).toHaveAttribute('href', '/resultados')
    expect(
      within(encabezado).getByRole('link', { name: 'Tablas de posiciones' }),
    ).toHaveAttribute('href', '/tabla')
    expect(
      within(encabezado).getByRole('link', { name: 'Iniciar sesión' }),
    ).toHaveAttribute('href', '/login')

    const pie = screen.getByRole('contentinfo')
    expect(
      within(pie).getByText('Liga Municipal de Futbol Amateur Morelia'),
    ).toBeInTheDocument()

    const enlacesPie = [
      ['Programación', '/'],
      ['Resultados', '/resultados'],
      ['Tablas de posiciones', '/tabla'],
      ['Aviso de privacidad', '/aviso-de-privacidad'],
    ]

    enlacesPie.forEach(([nombre, ruta]) => {
      expect(within(pie).getByRole('link', { name: nombre })).toHaveAttribute(
        'href',
        ruta,
      )
    })
  })

  it('abre y cierra la navegación móvil', async () => {
    const usuario = userEvent.setup()
    montar()

    const boton = screen.getByRole('button', { name: 'Abrir menú' })
    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(
      screen.getAllByRole('navigation', { name: 'Navegación del portal' }),
    ).toHaveLength(1)

    await usuario.click(boton)

    expect(boton).toHaveAttribute('aria-expanded', 'true')
    expect(boton).toHaveAccessibleName('Cerrar menú')
    expect(
      screen.getAllByRole('navigation', { name: 'Navegación del portal' }),
    ).toHaveLength(2)

    await usuario.keyboard('{Escape}')

    expect(boton).toHaveAttribute('aria-expanded', 'false')
    expect(
      screen.getAllByRole('navigation', { name: 'Navegación del portal' }),
    ).toHaveLength(1)
  })

  it('navega a resultados desde el menú móvil', async () => {
    const usuario = userEvent.setup()
    montar()

    await usuario.click(screen.getByRole('button', { name: 'Abrir menú' }))

    const menu = document.getElementById('menu-portal')
    expect(menu).not.toBeNull()

    await usuario.click(
      within(menu as HTMLElement).getByRole('link', { name: 'Resultados' }),
    )

    expect(screen.getByText('Contenido de resultados')).toBeInTheDocument()
    expect(
      screen.getAllByRole('navigation', { name: 'Navegación del portal' }),
    ).toHaveLength(1)
  })
})
