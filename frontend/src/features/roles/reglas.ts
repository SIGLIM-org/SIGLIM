import type {
  DiferenciaPermisos,
  GrupoPermisos,
  ParIncompatible,
  PermisoCatalogo,
} from '@/features/roles/tipos'

/** Compara códigos sin depender del orden en que el catálogo los devuelva. */
function ordenarCodigos(codigos: Iterable<string>): string[] {
  return [...codigos].sort((a, b) => a.localeCompare(b, 'es-MX'))
}

/** Agrupa el catálogo por módulo y ordena M-01, M-02… M-14 (no M-1, M-10, M-2). */
export function agruparPorModulo(permisos: readonly PermisoCatalogo[]): GrupoPermisos[] {
  const grupos = new Map<string, PermisoCatalogo[]>()

  for (const permiso of permisos) {
    const actuales = grupos.get(permiso.modulo)

    if (actuales) {
      actuales.push(permiso)
    } else {
      grupos.set(permiso.modulo, [permiso])
    }
  }

  return [...grupos.entries()]
    .sort(([a], [b]) => a.localeCompare(b, 'es-MX', { numeric: true }))
    .map(([modulo, delModulo]) => ({ modulo, permisos: delModulo }))
}

/**
 * Pares incompatibles que el cambio **crea**: los dos permisos quedan en el conjunto final y
 * antes no estaban juntos. Un par que el rol ya tenía no vuelve a bloquear la edición (VAL-023).
 */
export function paresIncompatiblesNuevos(
  seleccion: ReadonlySet<string>,
  original: ReadonlySet<string>,
  pares: readonly ParIncompatible[],
): ParIncompatible[] {
  return pares.filter(
    ({ permisoA, permisoB }) =>
      seleccion.has(permisoA) &&
      seleccion.has(permisoB) &&
      !(original.has(permisoA) && original.has(permisoB)),
  )
}

/** Altas y bajas entre la matriz vigente y el conjunto final que se va a guardar. */
export function diferenciaPermisos(
  original: ReadonlySet<string>,
  seleccion: ReadonlySet<string>,
): DiferenciaPermisos {
  const agregados = [...seleccion].filter((codigo) => !original.has(codigo))
  const quitados = [...original].filter((codigo) => !seleccion.has(codigo))

  return { agregados: ordenarCodigos(agregados), quitados: ordenarCodigos(quitados) }
}

/** Verdadero cuando la selección cambió respecto de la matriz vigente. */
export function hayCambios(
  original: ReadonlySet<string>,
  seleccion: ReadonlySet<string>,
): boolean {
  if (original.size !== seleccion.size) {
    return true
  }

  return [...seleccion].some((codigo) => !original.has(codigo))
}

/** Códigos de la matriz de un rol, listos para comparar. */
export function codigosDeRol(permisos: readonly PermisoCatalogo[]): Set<string> {
  return new Set(permisos.map(({ codigo }) => codigo))
}
