import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { ApiError } from '@/lib/api/ApiError'

function error(status: number, detail: string) {
  return new ApiError({ title: 'Error', status, detail, codigo: 'CODIGO' })
}

describe('ErrorConsulta', () => {
  it('VAL-015: un 403 dice que no hay permiso y muestra la causa, sin reintentar', () => {
    render(<ErrorConsulta error={error(403, 'Tu cuenta no tiene el permiso.')} onReintentar={vi.fn()} />)

    expect(screen.getByRole('alert')).toHaveTextContent('No tienes permiso para consultar esto')
    expect(screen.getByText('Tu cuenta no tiene el permiso.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('VAL-024: un 404 no distingue si existe y ofrece la acción', () => {
    render(
      <ErrorConsulta
        error={error(404, 'Texto del servidor')}
        accion={<a href="/catalogo">Volver</a>}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent(
      'No encontramos esta información. Puede que no exista o que no tengas acceso',
    )
    expect(screen.queryByText('Texto del servidor')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver' })).toBeInTheDocument()
  })

  it('otro error muestra su causa y «Reintentar»', async () => {
    const onReintentar = vi.fn()
    render(<ErrorConsulta error={error(503, 'Servicio caído.')} onReintentar={onReintentar} />)

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Servicio caído.')
    expect(onReintentar).toHaveBeenCalledOnce()
  })

  it('un error que no es de la API usa un texto genérico', () => {
    render(<ErrorConsulta error={new Error('interno')} />)

    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cargar la información')
    expect(screen.queryByText('interno')).not.toBeInTheDocument()
  })
})
