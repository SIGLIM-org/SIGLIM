import type { DocumentoFaltante, Requisito } from '@/features/registros/tipos'
import { ApiError } from '@/lib/api/ApiError'

export type MotivoFaltante = 'cargar' | 'revisar'

/** Requisito que impide activar el registro (VAL-001, DEC-114). */
export type Faltante = {
  tipoDocumentoId: number
  nombre: string
  motivo: MotivoFaltante
}

export const TEXTO_FALTANTE: Record<MotivoFaltante, string> = {
  cargar: 'Falta cargarlo',
  revisar: 'Falta revisarlo',
}

type Conteo = Pick<DocumentoFaltante, 'tipoDocumento' | 'cantidad' | 'cargados' | 'aprobados'>

/**
 * Motivo por el que un requisito todavía no cumple VAL-001, o `undefined` si ya cumple.
 * `cargados` cuenta los vigentes no rechazados, así que un rechazado sin reemplazar cuenta como
 * «Falta cargarlo»; un cargado sin aprobar, como «Falta revisarlo».
 */
export function motivoFaltante({ cantidad, cargados, aprobados }: Conteo): MotivoFaltante | undefined {
  if (cargados < cantidad) {
    return 'cargar'
  }
  if (aprobados < cargados) {
    return 'revisar'
  }
  return undefined
}

function aFaltantes(conteos: readonly Conteo[]): Faltante[] {
  return conteos.flatMap((conteo) => {
    const motivo = motivoFaltante(conteo)

    return motivo
      ? [{ tipoDocumentoId: conteo.tipoDocumento.id, nombre: conteo.tipoDocumento.nombre, motivo }]
      : []
  })
}

/** Faltantes calculados con los requisitos que ya se ven en pantalla. */
export function faltantesDeRequisitos(requisitos: readonly Requisito[]): Faltante[] {
  return aFaltantes(requisitos)
}

function esDocumentoFaltante(valor: unknown): valor is DocumentoFaltante {
  if (typeof valor !== 'object' || valor === null) {
    return false
  }

  const { tipoDocumento, cantidad, cargados, aprobados } = valor as Record<string, unknown>

  return (
    typeof cantidad === 'number' &&
    typeof cargados === 'number' &&
    typeof aprobados === 'number' &&
    typeof tipoDocumento === 'object' &&
    tipoDocumento !== null &&
    typeof (tipoDocumento as Record<string, unknown>).id === 'number' &&
    typeof (tipoDocumento as Record<string, unknown>).nombre === 'string'
  )
}

/**
 * Faltantes de la extensión `faltantes` de un `422 VAL-001` (DEC-99), con guarda de tipo: si la
 * extensión no tiene la forma esperada, devuelve una lista vacía y se muestra solo el `detail`.
 */
export function faltantesDeError(error: unknown): Faltante[] {
  if (!(error instanceof ApiError) || error.codigo !== 'VAL-001') {
    return []
  }

  const lista = error.problema.extensiones?.faltantes

  return Array.isArray(lista) ? aFaltantes(lista.filter(esDocumentoFaltante)) : []
}
