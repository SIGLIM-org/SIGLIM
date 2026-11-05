import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CONSULTAR,
  EDITAR,
  EDITAR_PARAMETROS,
  MOTIVO,
  PREDETERMINADOS,
  SIN_VERSION,
  errorApi,
  montarEstructura,
  permisos,
  resolverGet,
  respuestasPorDefecto,
  version,
} from '@/features/temporadas/datosPruebaEstructura'
import type { Permiso } from '@/features/seguridad/tipos'

const { get, post, patch, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, patch } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const { esDemo: _esDemo, ...VALORES } = PREDETERMINADOS

function sinVersion() {
  get.mockImplementation(
    resolverGet(respuestasPorDefecto({ 'temporadas/t-1/parametros': SIN_VERSION })),
  )
}

async function formulario(): Promise<HTMLFormElement> {
  const campo = await screen.findByLabelText('Puntos por victoria')
  return campo.closest('form') as HTMLFormElement
}

async function guardarConMotivo(boton: string | RegExp, tituloModal: string) {
  await userEvent.click(screen.getByRole('button', { name: boton }))
  const dialogo = screen.getByRole('dialog', { name: tituloModal })
  await userEvent.type(within(dialogo).getByLabelText(/Motivo/), MOTIVO)
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar versión' }))
  return dialogo
}

describe('PestanaParametros', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    sesion.yo.permisos = permisos(CONSULTAR, EDITAR, EDITAR_PARAMETROS)
    get.mockImplementation(resolverGet(respuestasPorDefecto()))
  })

  it('RF-100: sin vigente prellena los predeterminados, avisa y rotula «No oficial»', async () => {
    sinVersion()
    montarEstructura()

    const formularioParametros = await formulario()

    expect(screen.getByLabelText('Puntos por victoria')).toHaveValue(3)
    expect(screen.getByLabelText('Bloque de cancha (min)')).toHaveValue(120)
    expect(
      screen.getByText(
        'Aún no hay parámetros. La temporada no podrá activarse hasta guardar la versión 1 (RF-100).',
      ),
    ).toBeInTheDocument()
    // Los 13 campos [DEMO] de §6.2, con una sola insignia para los criterios de desempate.
    expect(within(formularioParametros).getAllByText('No oficial')).toHaveLength(13)
    expect(screen.getByRole('button', { name: 'Guardar la versión 1' })).toBeEnabled()
  })

  it('RF-006: guardar la versión 1 envía el juego completo con motivo y esDemo', async () => {
    sinVersion()
    post.mockResolvedValue(version(1))
    montarEstructura()
    await formulario()

    await userEvent.click(screen.getByRole('button', { name: 'Guardar la versión 1' }))
    const dialogo = screen.getByRole('dialog', { name: 'Guardar la versión 1' })
    expect(
      within(dialogo).getByText(
        'Con estos parámetros la temporada cumple el requisito RF-100 para activarse.',
      ),
    ).toBeInTheDocument()

    await userEvent.type(within(dialogo).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar versión' }))

    expect(post).toHaveBeenCalledWith('temporadas/t-1/parametros', {
      ...VALORES,
      esDemo: true,
      categorias: [],
      motivo: MOTIVO,
    })
    expect(await screen.findByRole('status')).toHaveTextContent('Se guardó la versión 1.')
  })

  it('PEN-021: el interruptor de oficiales oculta las insignias y envía esDemo:false', async () => {
    sinVersion()
    post.mockResolvedValue(version(1, { esDemo: false, camposDemo: [] }))
    montarEstructura()
    const formularioParametros = await formulario()

    await userEvent.click(
      screen.getByRole('switch', { name: 'Valores confirmados por la Liga (oficiales)' }),
    )
    expect(within(formularioParametros).queryAllByText('No oficial')).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: 'Guardar la versión 1' }))
    const dialogo = screen.getByRole('dialog', { name: 'Guardar la versión 1' })
    expect(
      within(dialogo).getByText('La Liga confirmó estos valores (PEN-021).'),
    ).toBeInTheDocument()
    await userEvent.type(within(dialogo).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar versión' }))

    expect(post).toHaveBeenCalledWith(
      'temporadas/t-1/parametros',
      expect.objectContaining({ esDemo: false }),
    )
  })

  it('RF-050: subir un desempate cambia el orden enviado y PUNTOS sigue primero', async () => {
    post.mockResolvedValue(version(3))
    montarEstructura()
    await formulario()

    const guardar = screen.getByRole('button', { name: 'Guardar como nueva versión' })
    expect(guardar).toBeDisabled()
    expect(screen.getByText('Sin cambios respecto a la versión 2.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Subir Puntos' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Subir Goles a favor' }))
    expect(screen.getByText('Goles a favor, posición 2 de 6')).toBeInTheDocument()
    expect(guardar).toBeEnabled()

    await userEvent.click(guardar)
    const dialogo = screen.getByRole('dialog', { name: 'Guardar como versión 3' })
    expect(
      within(dialogo).getByText(
        'La versión 2 se conserva en el historial. La tabla de posiciones de toda la temporada se recalculará con los puntos y criterios de la versión nueva.',
      ),
    ).toBeInTheDocument()
    expect(
      within(dialogo).getByText(
        'No cambian los bloques de los partidos ya programados, los plazos de las cédulas ya enviadas ni las fases y propuestas ya registradas.',
      ),
    ).toBeInTheDocument()
    expect(within(dialogo).queryByText(/retroactivo/)).not.toBeInTheDocument()

    await userEvent.type(within(dialogo).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar versión' }))

    expect(post).toHaveBeenCalledWith(
      'temporadas/t-1/parametros',
      expect.objectContaining({
        criteriosDesempate: [
          'PUNTOS',
          'GOLES_FAVOR',
          'DIFERENCIA_GOLES',
          'ENFRENTAMIENTO_DIRECTO',
          'MENOS_EXPULSIONES',
          'SORTEO',
        ],
        motivo: MOTIVO,
      }),
    )
  })

  it('DEC-24: la validación local marca el campo y no abre el modal', async () => {
    montarEstructura()
    await formulario()

    const perdedor = screen.getByLabelText('Goles del perdedor por default')
    await userEvent.clear(perdedor)
    await userEvent.type(perdedor, '5')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar como nueva versión' }))

    expect(screen.getByText('Debe ser menor que los goles del ganador.')).toBeInTheDocument()
    expect(perdedor).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('VALIDACION: un 400 marca el campo y la fila de la categoría enviada', async () => {
    post.mockRejectedValue(
      errorApi(400, 'VALIDACION', 'Revisa los campos marcados.', {
        errores: [
          { campo: 'puntosVictoria', mensaje: 'El servidor no acepta este valor.' },
          { campo: 'categorias[0].bloqueCanchaMin', mensaje: 'Bloque fuera de rango.' },
        ],
      }),
    )
    montarEstructura()
    await formulario()

    await userEvent.type(screen.getByLabelText('Duración de Veteranos (min)'), '70')
    await userEvent.type(screen.getByLabelText('Bloque de Veteranos (min)'), '90')
    await guardarConMotivo('Guardar como nueva versión', 'Guardar como versión 3')

    expect(await screen.findByText('Bloque fuera de rango.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Bloque de Veteranos (min)')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Puntos por victoria')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('El servidor no acepta este valor.')).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith(
      'temporadas/t-1/parametros',
      expect.objectContaining({
        categorias: [{ categoriaId: 'cat-vet', duracionJuegoMin: 70, bloqueCanchaMin: 90 }],
      }),
    )
  })

  it('RN-021: un 400 del motivo se muestra dentro del modal', async () => {
    post.mockRejectedValue(errorApi(400, 'RN-021', 'El motivo debe tener al menos 10 caracteres.'))
    montarEstructura()
    await formulario()

    await userEvent.click(screen.getByRole('button', { name: 'Subir Goles a favor' }))
    const dialogo = await guardarConMotivo('Guardar como nueva versión', 'Guardar como versión 3')

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(
      'El motivo debe tener al menos 10 caracteres.',
    )
    expect(screen.getByRole('dialog', { name: 'Guardar como versión 3' })).toBeInTheDocument()
  })

  it('RF-100: un 422 marca la categoría de categoriasInvalidas', async () => {
    post.mockRejectedValue(
      errorApi(422, 'REFERENCIA_INVALIDA', 'Una categoría no está asignada a la temporada.', {
        extensiones: { categoriasInvalidas: ['cat-vet'] },
      }),
    )
    montarEstructura()
    await formulario()

    await userEvent.type(screen.getByLabelText('Duración de Veteranos (min)'), '70')
    await userEvent.type(screen.getByLabelText('Bloque de Veteranos (min)'), '90')
    await guardarConMotivo('Guardar como nueva versión', 'Guardar como versión 3')

    expect(
      await screen.findByText('El servidor rechazó esta categoría para la temporada.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Duración de Veteranos (min)')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Duración de Libre (min)')).not.toHaveAttribute('aria-invalid')
    expect(screen.getByText('Una categoría no está asignada a la temporada.')).toBeInTheDocument()
  })

  it('RF-003: un 409 se muestra en el modal donde ocurrió', async () => {
    post.mockRejectedValue(errorApi(409, 'RF-003', 'La temporada está cerrada: solo lectura.'))
    montarEstructura()
    await formulario()

    await userEvent.click(screen.getByRole('button', { name: 'Subir Goles a favor' }))
    const dialogo = await guardarConMotivo('Guardar como nueva versión', 'Guardar como versión 3')

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(
      'La temporada está cerrada: solo lectura.',
    )
  })

  it('RF-006: el historial lista camposCambiados con etiquetas y abre la instantánea', async () => {
    montarEstructura()

    const historial = (await screen.findByRole('heading', { name: 'Historial de versiones' }))
      .closest('section') as HTMLElement
    const version2 = within(historial).getByRole('heading', { name: 'Versión 2' })
      .closest('li') as HTMLElement

    expect(within(version2).getByText('Vigente')).toBeInTheDocument()
    expect(within(version2).getByText('Puntos por victoria')).toBeInTheDocument()
    expect(within(version2).getByText('Duración y bloque por categoría')).toBeInTheDocument()
    expect(within(version2).getByText('Oficial / No oficial')).toBeInTheDocument()
    expect(within(version2).getByText(/Ana López/)).toBeInTheDocument()
    expect(within(version2).getByText('«Ajuste de la asamblea de la Liga»')).toBeInTheDocument()

    await userEvent.click(
      within(historial).getByRole('button', { name: 'Ver instantánea de la versión 1' }),
    )

    const dialogo = await screen.findByRole('dialog', { name: 'Versión 1 de los parámetros' })
    expect(get).toHaveBeenCalledWith('temporadas/t-1/parametros/1')
    expect(await within(dialogo).findByText('«Motivo de la versión 1»')).toBeInTheDocument()
    expect(within(dialogo).getByRole('heading', { name: 'Puntuación' })).toBeInTheDocument()
  })

  it('D8: sin temporadas.parametros:EDITAR muestra el resumen sin formulario', async () => {
    sesion.yo.permisos = permisos(CONSULTAR, EDITAR)
    get.mockImplementation(
      resolverGet(
        respuestasPorDefecto({
          'temporadas/t-1/parametros': {
            vigente: version(2, { camposDemo: ['puntosVictoria'] }),
            historial: [],
          },
        }),
      ),
    )
    montarEstructura()

    expect(await screen.findByRole('heading', { name: 'Puntuación' })).toBeInTheDocument()
    expect(screen.getByText('Solo consulta')).toBeInTheDocument()
    expect(screen.queryByLabelText('Puntos por victoria')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Guardar/ })).not.toBeInTheDocument()
    // Las insignias del resumen salen de los camposDemo de la versión.
    const resumen = screen.getByRole('heading', { name: 'Puntuación' }).closest('section') as HTMLElement
    expect(within(resumen).getAllByText('No oficial')).toHaveLength(1)
  })

  it('D8: sin permiso y sin versiones muestra «Aún no hay parámetros»', async () => {
    sesion.yo.permisos = permisos(CONSULTAR)
    sinVersion()
    montarEstructura()

    expect(await screen.findByText('Aún no hay parámetros')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Guardar la versión 1' })).not.toBeInTheDocument()
  })
})
