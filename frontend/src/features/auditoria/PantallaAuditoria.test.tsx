import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaAuditoria } from '@/features/auditoria/PantallaAuditoria'
import type { OpcionesFiltroAuditoria, RegistroAuditoria } from '@/features/auditoria/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'auditoria.registro:CONSULTAR'
const RUTA = '/app/administracion/auditoria'
const PRIMERA = 'auditoria?pagina=0&tamano=50&orden=ocurridoEn%2Cdesc'

const COMPLETO: RegistroAuditoria = {
  id: 1042,
  ocurridoEn: '2026-10-25T12:30:00-06:00',
  perfil: { id: 'p1', nombreMostrado: 'Ana López' },
  modulo: 'M-06',
  accion: 'BLOQUEAR_CANCHA',
  entidad: 'disponibilidad_canchas',
  entidadId: 'e-1',
  motivo: 'Riego de la cancha <b>programado</b>',
  detalle: { tipo: 'BLOQUEO', partidos: 2, extra: { a: [1, 2] } },
}
const MINIMO: RegistroAuditoria = {
  id: 7,
  ocurridoEn: '2026-10-24T09:00:00-06:00',
  modulo: 'M-99',
  accion: 'INICIAR_TAREA',
  entidad: 'tareas',
}
const OPCIONES: OpcionesFiltroAuditoria = {
  perfiles: [{ id: 'p1', nombreMostrado: 'Ana López' }],
  modulos: ['M-06', 'M-07'],
  acciones: ['BLOQUEAR_CANCHA', 'INICIAR_TAREA'],
}

function pagina(contenido: RegistroAuditoria[], extra: object = {}) {
  return {
    contenido,
    pagina: 0,
    tamano: 50,
    totalElementos: contenido.length,
    totalPaginas: contenido.length ? 1 : 0,
    ...extra,
  }
}

function error(status: number, codigo: string, detail: string) {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

function responder(lista: unknown = pagina([COMPLETO, MINIMO])) {
  get.mockImplementation(async (ruta: string) => {
    const respuesta = ruta === 'auditoria/opciones-filtro' ? OPCIONES : lista

    if (respuesta instanceof Error) {
      throw respuesta
    }

    return respuesta
  })
}

function montar(busqueda = '') {
  const enrutador = createMemoryRouter([{ path: RUTA, element: <PantallaAuditoria /> }], {
    initialEntries: [`${RUTA}${busqueda}`],
  })

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

function ultimaConsulta(): string {
  const rutas = get.mock.calls.map(([ruta]) => String(ruta)).filter((ruta) => ruta.startsWith('auditoria?'))

  return rutas.at(-1) ?? ''
}

async function aplicar() {
  await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
}

describe('PantallaAuditoria', () => {
  beforeEach(() => {
    get.mockReset()
    sesion.yo.permisos = [{ codigo: CONSULTAR, modulo: 'M-01', ambito: 'LIGA', clubId: null }]
    responder()
  })

  it('RF-134: muestra la fila completa, con el módulo por su nombre y sin rol', async () => {
    montar()

    const fila = (await screen.findByText('Ana López', { selector: 'span' })).closest('tr') as HTMLElement

    expect(within(fila).getByText('M-06 · Canchas y recursos')).toBeInTheDocument()
    expect(within(fila).getByText('BLOQUEAR_CANCHA')).toBeInTheDocument()
    expect(within(fila).getByText('disponibilidad_canchas')).toBeInTheDocument()
    expect(within(fila).getByText('e-1')).toBeInTheDocument()
    expect(within(fila).getByRole('button', { name: 'Ver detalle del registro 1042' })).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: /Rol/ })).not.toBeInTheDocument()
    expect(screen.getByText('2 eventos')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith(PRIMERA)
  })

  it('convenciones §1: una fila sin campos opcionales se muestra sin fallar', async () => {
    montar()

    const boton = await screen.findByRole('button', { name: 'Ver detalle del registro 7' })
    const fila = boton.closest('tr') as HTMLElement

    expect(within(fila).getByText('INICIAR_TAREA')).toBeInTheDocument()
    expect(within(fila).getByText('Sin usuario')).toBeInTheDocument()
    expect(within(fila).getByText('M-99')).toBeInTheDocument()
    expect(fila).not.toHaveTextContent('undefined')
  })

  it('RNF-008: la tarjeta etiqueta sus campos y omite «Motivo» si no viene', async () => {
    montar()

    const completa = (await screen.findByRole('button', { name: 'Ver detalle del registro 1042' })).closest('tr') as HTMLElement
    const minima = screen.getByRole('button', { name: 'Ver detalle del registro 7' }).closest('tr') as HTMLElement

    expect(within(completa).getByText('Entidad')).toBeInTheDocument()
    expect(within(completa).getByText('Motivo')).toBeInTheDocument()
    expect(within(minima).getByText('Entidad')).toBeInTheDocument()
    expect(within(minima).queryByText('Motivo')).not.toBeInTheDocument()
  })

  it('formatea el total con separador de miles de es-MX', async () => {
    responder(pagina([COMPLETO], { totalElementos: 4829, totalPaginas: 97 }))
    montar()

    expect(await screen.findByText('4,829 eventos')).toBeInTheDocument()
  })

  it('RF-134: la fila se expande con motivo, IDs y detalle, solo como texto', async () => {
    montar()

    const boton = await screen.findByRole('button', { name: 'Ver detalle del registro 1042' })

    expect(boton).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(boton)

    const abierto = screen.getByRole('button', { name: 'Ocultar detalle del registro 1042' })
    expect(abierto).toHaveAttribute('aria-expanded', 'true')
    const panel = document.getElementById(abierto.getAttribute('aria-controls') ?? '') as HTMLElement

    expect(within(panel).getByText('Riego de la cancha <b>programado</b>')).toBeInTheDocument()
    expect(document.body.querySelector('b')).toBeNull()
    expect(within(panel).getByText('1042')).toBeInTheDocument()
    expect(within(panel).getByText('BLOQUEO')).toBeInTheDocument()
    expect(within(panel).getByText('2')).toBeInTheDocument()
    expect(within(panel).getByText('{"a":[1,2]}')).toBeInTheDocument()

    await userEvent.click(abierto)
    expect(screen.queryByText('{"a":[1,2]}')).not.toBeInTheDocument()
  })

  it('sin detalle no aparece esa sección', async () => {
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Ver detalle del registro 7' }))

    expect(screen.getByText('ID del registro')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Detalle' })).not.toBeInTheDocument()
  })

  it('RF-134: aplica filtros multivalor separados por coma y fechas convertidas', async () => {
    const enrutador = montar()

    await screen.findByRole('option', { name: 'Ana López' })
    await userEvent.selectOptions(screen.getByLabelText('Usuario'), 'p1')

    await userEvent.click(screen.getByRole('button', { name: /^Módulo/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'M-06 · Canchas y recursos' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'M-07 · Programación manual' }))
    await userEvent.click(screen.getByRole('button', { name: /^Acción/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'BLOQUEAR_CANCHA' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'INICIAR_TAREA' }))
    await userEvent.type(screen.getByLabelText('Desde'), '2026-10-01')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-12-31')

    expect(ultimaConsulta()).toBe(PRIMERA)
    await aplicar()

    await waitFor(() =>
      expect(ultimaConsulta()).toBe(
        'auditoria?perfilId=p1&modulo=M-06%2CM-07&accion=BLOQUEAR_CANCHA%2CINICIAR_TAREA' +
          '&desde=2026-10-01T00%3A00%3A00-06%3A00&hasta=2027-01-01T00%3A00%3A00-06%3A00' +
          '&pagina=0&tamano=50&orden=ocurridoEn%2Cdesc',
      ),
    )
    const enUrl = new URLSearchParams(enrutador.state.location.search)
    expect(enUrl.get('desde')).toBe('2026-10-01')
    expect(enUrl.get('hasta')).toBe('2026-12-31')
  })

  it('los filtros de la URL se restauran en el formulario y en la petición', async () => {
    montar('?modulo=M-06&desde=2026-10-01')

    expect(await screen.findByRole('button', { name: 'Módulo M-06 · Canchas y recursos' })).toBeInTheDocument()
    expect(screen.getByLabelText('Desde')).toHaveValue('2026-10-01')
    expect(ultimaConsulta()).toBe(
      'auditoria?modulo=M-06&desde=2026-10-01T00%3A00%3A00-06%3A00&pagina=0&tamano=50&orden=ocurridoEn%2Cdesc',
    )
  })

  it('desde posterior a hasta muestra el error y no hace la petición', async () => {
    montar()

    await screen.findByText('2 eventos')
    const peticiones = get.mock.calls.length
    await userEvent.type(screen.getByLabelText('Desde'), '2026-10-10')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-10-01')
    await aplicar()

    expect(await screen.findByRole('alert')).toHaveTextContent('«Hasta» no puede ser anterior a «Desde»')
    expect(screen.getByLabelText('Hasta')).toBeInvalid()
    expect(get.mock.calls.length).toBe(peticiones)
  })

  it('el mismo día en desde y hasta es válido', async () => {
    montar()

    await screen.findByText('2 eventos')
    await userEvent.type(screen.getByLabelText('Desde'), '2026-10-10')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-10-10')
    await aplicar()

    await waitFor(() => expect(ultimaConsulta()).toContain('hasta=2026-10-11T'))
  })

  it('filtrar reinicia la página a 0', async () => {
    const enrutador = montar('?pagina=3')

    await screen.findByRole('option', { name: 'Ana López' })
    await userEvent.selectOptions(screen.getByLabelText('Usuario'), 'p1')
    await aplicar()

    await waitFor(() => expect(ultimaConsulta()).toContain('perfilId=p1&pagina=0'))
    expect(enrutador.state.location.search).toBe('?perfilId=p1')
  })

  it('ordenar alterna desc y asc, con aria-sort, y reinicia la página', async () => {
    const enrutador = montar('?pagina=2')

    const encabezado = await screen.findByRole('columnheader', { name: 'Fecha y hora' })

    expect(encabezado).toHaveAttribute('aria-sort', 'descending')
    await userEvent.click(within(encabezado).getByRole('button'))

    await waitFor(() => expect(ultimaConsulta()).toBe('auditoria?pagina=0&tamano=50&orden=ocurridoEn%2Casc'))
    expect(enrutador.state.location.search).toBe('?orden=asc')
    expect(screen.getByRole('columnheader', { name: 'Fecha y hora' })).toHaveAttribute('aria-sort', 'ascending')
  })

  it('cambiar el tamaño reinicia la página; solo ofrece 50 y 100', async () => {
    responder(pagina([COMPLETO], { pagina: 2, totalElementos: 200, totalPaginas: 4 }))
    const enrutador = montar('?pagina=2')

    const tamano = await screen.findByLabelText('Registros por página')

    expect(within(tamano).getAllByRole('option').map((opcion) => opcion.textContent)).toEqual(['50', '100'])
    await userEvent.selectOptions(tamano, '100')

    await waitFor(() => expect(ultimaConsulta()).toBe('auditoria?pagina=0&tamano=100&orden=ocurridoEn%2Cdesc'))
    expect(enrutador.state.location.search).toBe('?tamano=100')
  })

  it('paginar conserva los filtros y deshabilita los extremos', async () => {
    responder(pagina([COMPLETO], { totalElementos: 120, totalPaginas: 3 }))
    montar('?modulo=M-06')

    await screen.findByText('Página 1 de 3')
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    await waitFor(() => expect(ultimaConsulta()).toBe('auditoria?modulo=M-06&pagina=1&tamano=50&orden=ocurridoEn%2Cdesc'))
    expect(await screen.findByText('Página 2 de 3')).toBeInTheDocument()
  })

  it('vacío sin filtros lo dice, sin ofrecer limpiar', async () => {
    responder(pagina([]))
    montar()

    expect(await screen.findByText('No hay registros de auditoría para mostrar')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Limpiar filtros' })).not.toBeInTheDocument()
  })

  it('vacío con filtros ofrece limpiarlos y vuelve a consultar sin ellos', async () => {
    responder(pagina([]))
    const enrutador = montar('?modulo=M-06')

    expect(await screen.findByText('Ningún registro coincide con los filtros')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }))

    await waitFor(() => expect(ultimaConsulta()).toBe(PRIMERA))
    expect(enrutador.state.location.search).toBe('')
    expect(screen.getByRole('button', { name: 'Módulo Todos' })).toBeInTheDocument()
  })

  it('una página fuera de rango no se confunde con la auditoría vacía', async () => {
    responder(pagina([], { pagina: 9, totalElementos: 12, totalPaginas: 1 }))
    const enrutador = montar('?pagina=9')

    await userEvent.click(await screen.findByRole('button', { name: 'Ir a la primera página' }))

    expect(enrutador.state.location.search).toBe('')
  })

  it('400 VALIDACION muestra el detail y conserva el formulario, sin reintentar', async () => {
    responder(error(400, 'VALIDACION', 'El parámetro perfilId no es un UUID.'))
    montar('?perfilId=otro')

    expect(await screen.findByText('El parámetro perfilId no es un UUID.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aplicar filtros' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('VAL-015: un 403 de la API muestra ErrorConsulta sin formulario', async () => {
    responder(error(403, 'VAL-015', 'No tienes el permiso auditoria.registro:CONSULTAR.'))
    montar()

    expect(await screen.findByText('No tienes permiso para consultar esto')).toBeInTheDocument()
    expect(screen.getByText('No tienes el permiso auditoria.registro:CONSULTAR.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aplicar filtros' })).not.toBeInTheDocument()
  })

  it('VAL-015: sin el permiso muestra la 403 y no pide datos', () => {
    sesion.yo.permisos = []
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('solo llama a los dos endpoints de auditoría', async () => {
    montar()

    await screen.findByText('2 eventos')

    for (const [ruta] of get.mock.calls) {
      expect(String(ruta)).toMatch(/^auditoria(\?|\/opciones-filtro$)/)
    }
  })

  it('si fallan las opciones, los resultados siguen y se puede reintentar', async () => {
    get.mockImplementation(async (ruta: string) => {
      if (ruta === 'auditoria/opciones-filtro') {
        throw error(500, 'ERROR_INTERNO', 'Fallo.')
      }

      return pagina([COMPLETO])
    })
    montar()

    expect(await screen.findByText('No se pudieron cargar las opciones de los filtros; los resultados siguen disponibles.')).toBeInTheDocument()
    expect(screen.getByText('1 evento')).toBeInTheDocument()
  })
})
