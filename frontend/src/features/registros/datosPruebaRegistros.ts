/*
 * Datos compartidos por las pruebas de documentos y validación de registros (H-10.3). Cada archivo
 * de prueba simula `@/lib/api/cliente` y la sesión con `vi.mock`; aquí solo hay datos.
 */
import type {
  DocumentoRegistro,
  EnlaceDocumento,
  RegistroJugadorDetalle,
  RegistroJugadorFila,
  Requisito,
  RequisitosRegistro,
} from '@/features/registros/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'
import type { Pagina } from '@/lib/api/tipos'

export const FOTO = { id: 5, codigo: 'FOTOGRAFIA', nombre: 'Fotografía' }
export const ACTA = { id: 1, codigo: 'ACTA_NACIMIENTO', nombre: 'Acta de nacimiento' }

export function documento(cambios: Partial<DocumentoRegistro> = {}): DocumentoRegistro {
  return {
    id: 'doc-1',
    registroId: 'reg-1',
    tipoDocumento: FOTO,
    tipoMime: 'image/jpeg',
    tamanoBytes: 183204,
    estadoValidacion: 'PENDIENTE',
    observaciones: null,
    validadoPor: null,
    validadoEn: null,
    reemplazaA: null,
    cargadoEn: '2026-11-12T12:00:00-06:00',
    ...cambios,
  }
}

export function requisito(cambios: Partial<Requisito> = {}): Requisito {
  const documentos = cambios.documentos ?? []
  const cargados = documentos.filter(({ estadoValidacion }) => estadoValidacion !== 'RECHAZADO').length
  const aprobados = documentos.filter(({ estadoValidacion }) => estadoValidacion === 'APROBADO').length
  const cantidad = cambios.cantidad ?? 1

  return {
    tipoDocumento: FOTO,
    cantidad,
    soloMenores: false,
    esDemo: true,
    cargados,
    aprobados,
    faltan: Math.max(0, cantidad - cargados),
    documentos,
    ...cambios,
  }
}

export function requisitos(lista: Requisito[], cambios: Partial<RequisitosRegistro> = {}): RequisitosRegistro {
  const completo = lista.every(({ cargados, cantidad }) => cargados >= cantidad)
  const listo = lista.every(({ aprobados, cantidad }) => aprobados >= cantidad)

  return {
    registroId: 'reg-1',
    estadoRegistro: 'PENDIENTE_VALIDACION',
    categoria: { id: 'cat-1', nombre: 'Libre', tipo: 'LIBRE' },
    esMenor: false,
    requisitos: lista,
    completo,
    listoParaValidar: listo,
    ...cambios,
  }
}

export function detalle(cambios: Partial<RegistroJugadorDetalle> = {}): RegistroJugadorDetalle {
  return {
    id: 'reg-1',
    persona: {
      id: 'p-1',
      nombreCompleto: 'Ana López Ruiz',
      curpEnmascarada: 'LORA******MMN***05',
      curp: 'LORA900501MMNPZN05',
    },
    equipo: { id: 'e1', nombre: 'Halcones' },
    club: { id: 'c1', nombre: 'Club Halcones' },
    categoria: { id: 'cat-1', nombre: 'Libre' },
    temporada: { id: 't-2026', nombre: 'Temporada 2026', estado: 'ACTIVA' },
    estado: 'PENDIENTE_VALIDACION',
    esMenor: false,
    altaEn: '2026-11-11',
    creadoEn: '2026-11-11T12:00:00-06:00',
    validadoPor: null,
    validadoEn: null,
    observaciones: null,
    tutores: [],
    ...cambios,
  }
}

export function fila(cambios: Partial<RegistroJugadorFila> = {}): RegistroJugadorFila {
  return {
    id: 'reg-1',
    persona: { id: 'p-1', nombreCompleto: 'Ana López Ruiz', curpEnmascarada: 'LORA******MMN***05' },
    equipo: { id: 'e1', nombre: 'Halcones' },
    club: { id: 'c1', nombre: 'Club Halcones' },
    categoria: { id: 'cat-1', nombre: 'Libre' },
    temporada: { id: 't-2026', nombre: 'Temporada 2026', estado: 'ACTIVA' },
    estado: 'PENDIENTE_VALIDACION',
    esMenor: false,
    altaEn: '2026-11-11',
    creadoEn: '2026-11-11T12:00:00-06:00',
    ...cambios,
  }
}

export function pagina<T>(contenido: T[]): Pagina<T> {
  return { contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: 1 }
}

export const URL_FIRMADA = 'https://s3.example/registros/doc-1?firma=abc'

export function enlace(segundos = 60): EnlaceDocumento {
  return { url: URL_FIRMADA, expiraEn: new Date(Date.now() + segundos * 1000).toISOString() }
}

export function error(
  status: number,
  codigo: string,
  detail = 'Detalle del servidor.',
  extensiones?: Record<string, unknown>,
): ApiError {
  return new ApiError({ title: 'Error', status, detail, codigo, ...(extensiones ? { extensiones } : {}) })
}

export function permisos(...codigos: string[]): Permiso[] {
  return codigos.map((codigo) => ({ codigo, modulo: 'M-04', ambito: 'LIGA' as const, clubId: null }))
}

/** Archivo de prueba con el tamaño indicado sin reservar esa memoria. */
export function archivo(nombre: string, tipo: string, tamano = 1024): File {
  const resultado = new File(['contenido'], nombre, { type: tipo })
  Object.defineProperty(resultado, 'size', { value: tamano })
  return resultado
}
