import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CalendarioSemanal } from '@/features/canchas/CalendarioSemanal'
import type { BloqueOcupacion } from '@/features/canchas/tipos'
import { ApiError } from '@/lib/api/ApiError'

const SEMANA = '2026-10-26'

function periodo(desde: string, hasta: string) {
  return { desde: `2026-10-26T${desde}:00-06:00`, hasta: `2026-10-26T${hasta}:00-06:00` }
}

const BLOQUES: BloqueOcupacion[] = [
  { tipo: 'DISPONIBLE', periodo: periodo('08:00', '20:00') },
  { tipo: 'BLOQUEO', periodo: periodo('10:00', '12:00'), motivo: 'Riego de la cancha' },
  { tipo: 'MANTENIMIENTO', periodo: periodo('14:00', '15:00'), motivo: 'Marcaje de líneas' },
  {
    tipo: 'PARTIDO',
    periodo: periodo('11:00', '13:00'),
    partido: {
      id: 'p1',
      estado: 'PROGRAMADO',
      periodo: periodo('11:00', '13:00'),
      equipoLocal: { id: 'e1', nombre: 'Halcones' },
      equipoVisitante: { id: 'e2', nombre: 'Toros' },
    },
  },
]

describe('CalendarioSemanal', () => {
  it('H-07 criterio 2: muestra disponible, bloqueo con motivo, mantenimiento y partido', () => {
    render(<CalendarioSemanal semana={SEMANA} bloques={BLOQUES} onCambiarSemana={vi.fn()} />)

    expect(screen.getAllByText('Disponible').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Bloqueo').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Riego de la cancha').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Mantenimiento').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Marcaje de líneas').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Halcones vs Toros').length).toBeGreaterThan(0)
    expect(screen.getAllByText('11:00 – 13:00').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Programado').length).toBeGreaterThan(0)
  })

  it('D4: el partido dentro de un bloqueo lo dice con texto y el bloqueo tapa la disponibilidad', () => {
    render(<CalendarioSemanal semana={SEMANA} bloques={BLOQUES} onCambiarSemana={vi.fn()} />)

    expect(screen.getAllByText('Dentro de un bloqueo').length).toBeGreaterThan(0)

    const rejilla = screen.getByRole('region', { name: 'Calendario de la semana' })
    const capa = (titulo: RegExp) =>
      Number(
        [...rejilla.querySelectorAll<HTMLElement>('[title]')].find((elemento) =>
          titulo.test(elemento.title),
        )?.style.zIndex,
      )

    expect(capa(/^Bloqueo/)).toBeGreaterThan(capa(/^Disponible/))
    expect(capa(/Halcones/)).toBeGreaterThan(capa(/^Bloqueo/))
  })

  it('D3: no presenta las horas vacías como no disponibles', () => {
    render(<CalendarioSemanal semana={SEMANA} bloques={BLOQUES} onCambiarSemana={vi.fn()} />)

    expect(screen.getByText('Disponible (registrada)')).toBeInTheDocument()
    expect(screen.getByText(/no están prohibidas/)).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/fuera de disponibilidad|no disponible/i)
  })

  it('una semana sin bloques muestra el estado vacío', () => {
    render(<CalendarioSemanal semana={SEMANA} bloques={[]} onCambiarSemana={vi.fn()} />)

    expect(screen.getByText('Sin registros esta semana')).toBeInTheDocument()
  })

  it('el navegador muestra el rango y cambia de semana', async () => {
    const onCambiarSemana = vi.fn()
    render(<CalendarioSemanal semana={SEMANA} bloques={[]} onCambiarSemana={onCambiarSemana} />)

    expect(
      screen.getByRole('heading', { name: 'Lunes 26 de octubre – Domingo 1 de noviembre de 2026' }),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Semana siguiente' }))
    expect(onCambiarSemana).toHaveBeenLastCalledWith('2026-11-02')

    await userEvent.click(screen.getByRole('button', { name: 'Semana anterior' }))
    expect(onCambiarSemana).toHaveBeenLastCalledWith('2026-10-19')
  })

  it('muestra los estados de carga y de error', () => {
    const { rerender } = render(<CalendarioSemanal semana={SEMANA} cargando onCambiarSemana={vi.fn()} />)
    expect(screen.getByRole('status')).toHaveTextContent('Cargando la ocupación')

    rerender(
      <CalendarioSemanal
        semana={SEMANA}
        error={new ApiError({ title: 'Error', status: 503, detail: 'Servicio caído.', codigo: 'X' })}
        onReintentar={vi.fn()}
        onCambiarSemana={vi.fn()}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Servicio caído.')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })
})
