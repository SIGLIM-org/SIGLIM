/*
 * Datos y montaje compartidos por las pruebas de F-11 (H-05.3). Cada archivo de prueba simula
 * `@/lib/api/cliente` y la sesión con `vi.mock`; aquí solo hay datos y el enrutador de memoria.
 */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { PantallaEstructuraTemporada } from '@/features/temporadas/PantallaEstructuraTemporada'
import type {
  Categoria,
  CategoriaTemporada,
  ParametrosPredeterminados,
  ParametrosTemporada,
  ResumenVersion,
  Temporada,
  VersionParametros,
} from '@/features/temporadas/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

export const CONSULTAR = 'temporadas.temporada:CONSULTAR'
export const EDITAR = 'temporadas.temporada:EDITAR'
export const EDITAR_PARAMETROS = 'temporadas.parametros:EDITAR'
export const MOTIVO = 'Ajuste acordado en la asamblea de la Liga'

export const TEMPORADA: Temporada = {
  id: 't-1',
  nombre: 'Temporada 2026–2027',
  fechaInicio: '2026-10-01',
  fechaFin: '2027-06-30',
  estado: 'CONFIGURACION',
  creadoEn: '2026-09-01T10:00:00-06:00',
  categoriasAsignadas: 2,
  gruposCreados: 3,
  versionParametros: 2,
}

export const CATALOGO: Categoria[] = [
  { id: 'cat-libre', nombre: 'Libre', tipo: 'LIBRE', orden: 1, activa: true, creadoEn: '2026-09-01T10:00:00-06:00' },
  {
    id: 'cat-juv',
    nombre: 'Juvenil',
    tipo: 'LIBRE',
    orden: 2,
    categoriaSuperior: { id: 'cat-libre', nombre: 'Libre' },
    activa: true,
    creadoEn: '2026-09-01T10:00:00-06:00',
  },
  { id: 'cat-vet', nombre: 'Veteranos', tipo: 'VETERANOS', orden: 3, activa: true, creadoEn: '2026-09-01T10:00:00-06:00' },
  { id: 'cat-inf', nombre: 'Infantil antigua', tipo: 'INFANTIL', orden: 4, activa: false, creadoEn: '2026-09-01T10:00:00-06:00' },
]

export const CATEGORIAS_TEMPORADA: CategoriaTemporada[] = [
  {
    id: 'ct-libre',
    categoria: { id: 'cat-libre', nombre: 'Libre', tipo: 'LIBRE', orden: 1 },
    grupos: [
      { id: 'g-a', nombre: 'A', equipos: 8 },
      { id: 'g-b', nombre: 'B', equipos: 6 },
    ],
  },
  {
    id: 'ct-vet',
    categoria: { id: 'cat-vet', nombre: 'Veteranos', tipo: 'VETERANOS', orden: 3 },
    grupos: [{ id: 'g-u', nombre: 'Único', equipos: 5 }],
  },
]

export const PREDETERMINADOS: ParametrosPredeterminados = {
  puntosVictoria: 3,
  puntosEmpate: 1,
  puntosDerrota: 0,
  criteriosDesempate: ['PUNTOS', 'DIFERENCIA_GOLES', 'GOLES_FAVOR', 'ENFRENTAMIENTO_DIRECTO', 'MENOS_EXPULSIONES', 'SORTEO'],
  golesDefaultGanador: 3,
  golesDefaultPerdedor: 0,
  plazoValidacionCedulaHoras: 48,
  duracionJuegoMin: 90,
  bloqueCanchaMin: 120,
  clasificadosLiguilla: 8,
  formatoLiguilla: 'PARTIDO_UNICO',
  empateLiguillaPorPenales: true,
  equiposAscienden: 2,
  equiposDescienden: 2,
  permiteCambioClub: true,
  esDemo: true,
}

export const CAMPOS_DEMO_VERSION = [
  'puntosVictoria', 'puntosEmpate', 'puntosDerrota', 'criteriosDesempate',
  'golesDefaultGanador', 'golesDefaultPerdedor', 'plazoValidacionCedulaHoras', 'bloqueCanchaMin',
  'clasificadosLiguilla', 'formatoLiguilla', 'empateLiguillaPorPenales',
  'equiposAscienden', 'equiposDescienden',
]

export function version(numero: number, cambios: Partial<VersionParametros> = {}): VersionParametros {
  const { esDemo: _esDemo, ...valores } = PREDETERMINADOS

  return {
    ...valores,
    id: `p-${numero}`,
    temporadaId: 't-1',
    version: numero,
    categorias: [],
    esDemo: true,
    camposDemo: CAMPOS_DEMO_VERSION,
    motivo: `Motivo de la versión ${numero}`,
    creadoPor: { id: 'perfil-1', nombre: 'Ana López' },
    creadoEn: '2026-11-05T10:00:00-06:00',
    ...cambios,
  }
}

export const HISTORIAL: ResumenVersion[] = [
  {
    id: 'p-2',
    version: 2,
    esDemo: true,
    motivo: 'Ajuste de la asamblea de la Liga',
    creadoPor: { id: 'perfil-1', nombre: 'Ana López' },
    creadoEn: '2026-11-05T10:00:00-06:00',
    camposCambiados: ['puntosVictoria', 'categorias', 'esDemo'],
  },
  {
    id: 'p-1',
    version: 1,
    esDemo: true,
    motivo: 'Valores de demostración iniciales',
    creadoEn: '2026-11-01T10:00:00-06:00',
    camposCambiados: ['puntosVictoria', 'criteriosDesempate'],
  },
]

export const CON_VERSION: ParametrosTemporada = {
  vigente: version(2),
  historial: HISTORIAL,
}

export const SIN_VERSION: ParametrosTemporada = {
  vigente: null,
  historial: [],
  predeterminados: PREDETERMINADOS,
}

export function permisos(...codigos: string[]): Permiso[] {
  return codigos.map((codigo) => ({ codigo, modulo: 'M-02', ambito: 'LIGA', clubId: null }))
}

export function errorApi(
  status: number,
  codigo: string,
  detail: string,
  extra: Partial<ConstructorParameters<typeof ApiError>[0]> = {},
) {
  return new ApiError({ title: 'Error', status, detail, codigo, ...extra })
}

/** Respuestas del `GET` simulado por ruta; un valor `Error` se lanza. */
export type RespuestasGet = Record<string, unknown>

export function respuestasPorDefecto(cambios: RespuestasGet = {}): RespuestasGet {
  return {
    'temporadas/t-1': TEMPORADA,
    categorias: CATALOGO,
    'temporadas/t-1/categorias': CATEGORIAS_TEMPORADA,
    'temporadas/t-1/parametros': CON_VERSION,
    'temporadas/t-1/parametros/1': version(1),
    ...cambios,
  }
}

/** Implementación del `api.get` simulado a partir de las respuestas por ruta. */
export function resolverGet(respuestas: RespuestasGet) {
  return async (ruta: string) => {
    if (!(ruta in respuestas)) {
      throw new Error(`Ruta inesperada: ${ruta}`)
    }

    const respuesta = respuestas[ruta]

    if (respuesta instanceof Error) {
      throw respuesta
    }

    return respuesta
  }
}

export const RUTA_ESTRUCTURA = '/app/administracion/temporadas/:temporadaId/estructura'

/** Monta F-11 en `?pestana=` (o sin él) y devuelve el enrutador para leer la URL. */
export function montarEstructura(busqueda = '') {
  const enrutador = createMemoryRouter(
    [
      { path: RUTA_ESTRUCTURA, element: <PantallaEstructuraTemporada /> },
      { path: '/app/administracion/temporadas', element: <p>Lista de temporadas</p> },
    ],
    { initialEntries: [`/app/administracion/temporadas/t-1/estructura${busqueda}`] },
  )

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}
