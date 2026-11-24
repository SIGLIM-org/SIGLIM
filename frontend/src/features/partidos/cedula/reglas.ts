import type { Cedula, Faltante, TipoEvento } from '@/features/partidos/cedula/tipos'

export const etiquetaTipoEvento: Record<TipoEvento, string> = {
  GOL: 'Gol',
  AUTOGOL: 'Autogol',
  TARJETA_AMARILLA: 'Tarjeta amarilla',
  TARJETA_ROJA: 'Tarjeta roja',
  SUSTITUCION: 'Sustitución',
}

/**
 * Lo que le falta a la cédula para enviarse (VAL-020, DEC-170): la misma regla del backend, para
 * avisar antes de enviar. El marcador y la alineación de cada equipo que se presentó.
 */
export function faltantesDeCedula(cedula: Cedula): Faltante[] {
  const { marcador, partido, alineaciones } = cedula
  const noPresentado = marcador.noPresentado?.id
  const faltantes: Faltante[] = []

  if (marcador.golesLocal === null || marcador.golesVisitante === null) {
    faltantes.push({ campo: 'marcador', mensaje: 'Captura el marcador o marca al equipo que no se presentó.' })
  }
  for (const [campo, equipo] of [
    ['alineacionLocal', partido.equipoLocal],
    ['alineacionVisitante', partido.equipoVisitante],
  ] as const) {
    if (equipo.id !== noPresentado && !alineaciones.some(({ equipoId }) => equipoId === equipo.id)) {
      faltantes.push({ campo, mensaje: `Captura la alineación de «${equipo.nombre}».` })
    }
  }

  return faltantes
}
