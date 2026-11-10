/** Nombres de los módulos del ERS §2.2; los de la captura F-14 están desplazados y no se usan. */
const NOMBRES_MODULOS: Readonly<Record<string, string>> = {
  'M-01': 'Administración y seguridad',
  'M-02': 'Temporadas y normativa',
  'M-03': 'Organización deportiva',
  'M-04': 'Registros y elegibilidad',
  'M-05': 'Árbitros y designaciones',
  'M-06': 'Canchas y recursos',
  'M-07': 'Programación manual',
  'M-08': 'Partidos, cédula y resultados',
  'M-09': 'Disciplina',
  'M-10': 'Competencias y fases finales',
  'M-11': 'Portal público y publicación',
  'M-12': 'Reportes y documentos',
  'M-13': 'Tesorería y cobros',
  'M-14': 'Notificaciones',
}

function nombreConocido(codigo: string): string | undefined {
  return Object.hasOwn(NOMBRES_MODULOS, codigo) ? NOMBRES_MODULOS[codigo] : undefined
}

/** Nombre del módulo; un código desconocido se devuelve tal cual. */
export function nombreModulo(codigo: string): string {
  return nombreConocido(codigo) ?? codigo
}

/** «M-06 · Canchas y recursos»; un código desconocido se devuelve tal cual. */
export function etiquetaModulo(codigo: string): string {
  const nombre = nombreConocido(codigo)

  return nombre ? `${codigo} · ${nombre}` : codigo
}
