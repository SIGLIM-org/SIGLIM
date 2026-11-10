import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CargandoPagina } from '@/app/CargandoPagina'

describe('CargandoPagina', () => {
  it('DT-01: anuncia la carga del módulo con el esqueleto de T-03.3', () => {
    render(<CargandoPagina />)

    expect(screen.getByRole('status')).toHaveTextContent('Cargando módulo…')
  })
})
