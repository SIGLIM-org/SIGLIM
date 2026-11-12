import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  URL_FIRMADA,
  documento,
  enlace,
  error,
} from '@/features/registros/datosPruebaRegistros'
import { VisorDocumento } from '@/features/registros/documentos/VisorDocumento'
import type { DocumentoRegistro } from '@/features/registros/tipos'

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))

let clienteConsultas: QueryClient

function montar(doc: DocumentoRegistro = documento()) {
  clienteConsultas = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onCerrar = vi.fn()

  render(
    <QueryClientProvider client={clienteConsultas}>
      <VisorDocumento documento={doc} onCerrar={onCerrar} />
    </QueryClientProvider>,
  )

  return { onCerrar }
}

describe('VisorDocumento (DEC-113, RNF-018)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('pide el enlace al abrirse y muestra la imagen sin referrer y el enlace noopener noreferrer', async () => {
    get.mockResolvedValue(enlace())
    montar()

    const imagen = await screen.findByRole('img', { name: 'Documento: Fotografía' })
    expect(get).toHaveBeenCalledTimes(1)
    expect(get).toHaveBeenCalledWith('documentos/doc-1/enlace')
    expect(imagen).toHaveAttribute('src', URL_FIRMADA)
    expect(imagen).toHaveAttribute('referrerpolicy', 'no-referrer')
    const pestana = screen.getByRole('link', { name: 'Abrir en otra pestaña' })
    expect(pestana).toHaveAttribute('target', '_blank')
    expect(pestana).toHaveAttribute('rel', 'noopener noreferrer')
    expect(screen.queryByRole('button', { name: 'Aprobar' })).not.toBeInTheDocument()
  })

  it('un PDF se muestra en un iframe sin referrer', async () => {
    get.mockResolvedValue(enlace())
    montar(documento({ tipoMime: 'application/pdf' }))

    expect(await screen.findByTitle('Documento: Fotografía')).toHaveAttribute('referrerpolicy', 'no-referrer')
  })

  it('al vencer, deja lo cargado, desactiva la pestaña y «Ver de nuevo» pide otro enlace', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    get.mockResolvedValue(enlace(60))
    montar()

    await screen.findByRole('img', { name: 'Documento: Fotografía' })
    await act(async () => {
      vi.advanceTimersByTime(61_000)
    })

    expect(screen.getByRole('alert')).toHaveTextContent('El enlace venció')
    expect(screen.getByRole('img', { name: 'Documento: Fotografía' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Abrir en otra pestaña' })).not.toBeInTheDocument()
    expect(screen.getByText('Abrir en otra pestaña')).toHaveAttribute('aria-disabled', 'true')

    get.mockResolvedValue(enlace(60))
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Ver de nuevo' }))
    })

    expect(get).toHaveBeenCalledTimes(2)
    expect(await screen.findByRole('link', { name: 'Abrir en otra pestaña' })).toBeInTheDocument()
    expect(screen.queryByText(/El enlace venció/)).not.toBeInTheDocument()
  })

  it('si la imagen no carga, muestra el aviso del enlace vencido en su lugar', async () => {
    get.mockResolvedValue(enlace())
    montar()

    fireEvent.error(await screen.findByRole('img', { name: 'Documento: Fotografía' }))

    expect(screen.getByRole('alert')).toHaveTextContent('El enlace venció')
    expect(screen.queryByRole('img', { name: 'Documento: Fotografía' })).not.toBeInTheDocument()
  })

  it('503 SERVICIO_NO_DISPONIBLE muestra el detail con «Reintentar»', async () => {
    get.mockRejectedValue(error(503, 'SERVICIO_NO_DISPONIBLE', 'El almacenamiento no responde.'))
    montar()

    expect(await screen.findByRole('alert')).toHaveTextContent('El almacenamiento no responde.')
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  it('RNF-018: la URL no queda en la caché de consultas y se descarta al cerrar', async () => {
    get.mockResolvedValue(enlace())
    const { onCerrar } = montar()

    await screen.findByRole('img', { name: 'Documento: Fotografía' })
    const enConsultas = JSON.stringify(clienteConsultas.getQueryCache().getAll().map(({ state }) => state.data))
    expect(enConsultas).not.toContain(URL_FIRMADA)

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar' }))
    expect(onCerrar).toHaveBeenCalled()
    // `reset()` suelta la mutación y `gcTime: 0` la saca de la caché en el siguiente ciclo.
    await waitFor(() =>
      expect(
        JSON.stringify(clienteConsultas.getMutationCache().getAll().map(({ state }) => state.data)),
      ).not.toContain(URL_FIRMADA),
    )
  })
})
