import type { CargoDirectiva, Persona, TipoCargoDirectiva } from '@/features/organizacion/tipos'
import { ZONA_NEGOCIO } from '@/lib/zonaHoraria'

/** Cargos de la directiva en el orden en que los muestra F-15 (DEC-03). */
export const CARGOS_DIRECTIVA: readonly TipoCargoDirectiva[] = [
  'PRESIDENTE',
  'SECRETARIO',
  'TESORERO',
  'DELEGADO',
]

/**
 * Fila de la tabla de directiva. `vacia` cubre un cargo sin titular vigente; `pendiente` conserva
 * el vigente que reemplazará, si existe (RF-102).
 */
export type FilaDirectiva =
  | { tipo: 'vigente'; cargo: CargoDirectiva }
  | { tipo: 'pendiente'; cargo: CargoDirectiva; vigente?: CargoDirectiva }
  | { tipo: 'historial'; cargo: CargoDirectiva }
  | { tipo: 'vacia'; cargo: TipoCargoDirectiva }

/** El cargo autorizado sin `hasta`; los pendientes y los concluidos no ocupan el cargo. */
function vigenteDe(
  cargos: readonly CargoDirectiva[],
  tipo: TipoCargoDirectiva,
): CargoDirectiva | undefined {
  return cargos.find(
    (cargo) => cargo.cargo === tipo && cargo.estado === 'AUTORIZADO' && !cargo.hasta,
  )
}

function porDesdeDescendente(a: CargoDirectiva, b: CargoDirectiva): number {
  return b.desde.localeCompare(a.desde)
}

/**
 * Filas por cargo para la tabla: el vigente, sus pendientes y, si se piden, los cargos concluidos.
 * Los cargos sin titular se muestran como fila vacía para que siempre se vean los cuatro (RF-102).
 */
export function filasDeDirectiva(
  cargos: readonly CargoDirectiva[],
  mostrarHistorial: boolean,
): FilaDirectiva[] {
  const filas: FilaDirectiva[] = []

  for (const tipo of CARGOS_DIRECTIVA) {
    const delCargo = cargos.filter((cargo) => cargo.cargo === tipo)
    const vigente = vigenteDe(delCargo, tipo)
    const pendientes = delCargo
      .filter((cargo) => cargo.estado === 'PENDIENTE')
      .sort(porDesdeDescendente)
    const historial = delCargo
      .filter((cargo) => cargo.estado === 'AUTORIZADO' && cargo.hasta)
      .sort(porDesdeDescendente)

    if (vigente) {
      filas.push({ tipo: 'vigente', cargo: vigente })
    } else {
      filas.push({ tipo: 'vacia', cargo: tipo })
    }
    for (const pendiente of pendientes) {
      filas.push({ tipo: 'pendiente', cargo: pendiente, vigente })
    }
    if (mostrarHistorial) {
      for (const anterior of historial) {
        filas.push({ tipo: 'historial', cargo: anterior })
      }
    }
  }

  return filas
}

/** Cuántos cargos concluidos hay para ofrecer «Ver historial» (RF-102). */
export function contarHistorial(cargos: readonly CargoDirectiva[]): number {
  return cargos.filter((cargo) => cargo.estado === 'AUTORIZADO' && cargo.hasta).length
}

/** Cuántos cambios esperan autorización; se avisa cuando la sesión no puede resolverlos. */
export function contarPendientes(cargos: readonly CargoDirectiva[]): number {
  return cargos.filter((cargo) => cargo.estado === 'PENDIENTE').length
}

/** Día anterior a un día `AAAA-MM-DD`, en UTC para no moverlo por la zona del equipo. */
export function diaAnterior(fecha: string): string {
  const dia = new Date(`${fecha}T00:00:00Z`)
  dia.setUTCDate(dia.getUTCDate() - 1)
  return dia.toISOString().slice(0, 10)
}

/** Día de hoy en la zona de negocio, para proponer el inicio de una vigencia (RF-102). */
export function hoyEnZonaNegocio(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONA_NEGOCIO }).format(new Date())
}

/** Mismo formato que `ck_personas_curp`; se valida antes de llamar al servidor (DEC-10). */
export function curpValida(curp: string): boolean {
  return /^[A-Z][AEIOUX][A-Z]{2}[0-9]{6}[HM][A-Z]{5}[0-9A-Z][0-9]$/.test(curp)
}

/** Nombre completo para confirmar a quién se le registra el cargo (RF-010). */
export function nombreCompletoPersona(persona: Persona): string {
  return [persona.nombre, persona.apellidoPaterno, persona.apellidoMaterno]
    .filter(Boolean)
    .join(' ')
}
