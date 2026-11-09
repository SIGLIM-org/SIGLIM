import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'
import type { Permiso } from '@/features/seguridad/tipos'

export const CONSULTAR_CLUB = 'organizacion.club:CONSULTAR'
export const EDITAR_CLUB = 'organizacion.club:EDITAR'
export const EDITAR_EQUIPO = 'organizacion.equipo:EDITAR'
export const EDITAR_DIRECTIVA = 'organizacion.directiva:EDITAR'
export const APROBAR_DIRECTIVA = 'organizacion.directiva:APROBAR'
export const CONSULTAR_TEMPORADA = 'temporadas.temporada:CONSULTAR'

/**
 * ¿Todos los permisos de consultar clubes son de ámbito CLUB? Entonces la sesión solo alcanza
 * su club y la pantalla abre su detalle directo (F-15). Mira el ámbito, nunca el nombre del rol.
 */
export function soloAmbitoClub(permisos: readonly Permiso[]): boolean {
  const deConsulta = permisos.filter(({ codigo }) => codigo === CONSULTAR_CLUB)

  return deConsulta.length > 0 && deConsulta.every(({ ambito }) => ambito === 'CLUB')
}

/**
 * ¿Puede cambiar el nombre de un club? El contrato exige `organizacion.club:EDITAR` con ámbito de
 * liga (RF-105, DEC-15); se decide por el ámbito, nunca por el nombre del rol, igual que
 * `soloAmbitoClub`. No usa `tienePermiso`, que ignora el ámbito.
 */
export function puedeCambiarNombreClub(permisos: readonly Permiso[]): boolean {
  return permisos.some(({ codigo, ambito }) => codigo === EDITAR_CLUB && ambito === 'LIGA')
}

/**
 * Acciones visibles de F-15 según los permisos de `/yo` (H-02.4). Elegir un grupo pide además
 * `temporadas.temporada:CONSULTAR`, porque el selector lee la estructura de M-02 (DEC-54). La
 * directiva separa registrar (`EDITAR`) de autorizar (`APROBAR`); el backend aplica el ámbito.
 */
export function usePermisosClubes() {
  const { yo } = useSesionActual()
  const editarEquipo = tienePermiso(yo.permisos, EDITAR_EQUIPO)

  return {
    registrarClub: tienePermiso(yo.permisos, EDITAR_CLUB),
    cambiarNombreClub: puedeCambiarNombreClub(yo.permisos),
    editarEquipo,
    elegirGrupo: editarEquipo && tienePermiso(yo.permisos, CONSULTAR_TEMPORADA),
    registrarDirectiva: tienePermiso(yo.permisos, EDITAR_DIRECTIVA),
    autorizarDirectiva: tienePermiso(yo.permisos, APROBAR_DIRECTIVA),
    soloClub: soloAmbitoClub(yo.permisos),
  }
}
