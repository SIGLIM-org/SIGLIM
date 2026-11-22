import { useMemo } from 'react'
import type { ClubParaArbitro } from '@/features/arbitraje/tipos'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'
import type { Yo } from '@/features/seguridad/tipos'

export const CONSULTAR_ARBITRO = 'arbitraje.arbitro:CONSULTAR'
export const PROPONER_ARBITRO = 'arbitraje.arbitro:PROPONER'
export const VALIDAR_ARBITRO = 'arbitraje.arbitro:VALIDAR'
export const EDITAR_PERSONA = 'organizacion.persona:EDITAR'
export const EDITAR_DISPONIBILIDAD = 'arbitraje.disponibilidad:EDITAR'

/** Clubes del ámbito propio que concede un permiso; el nombre sale de la asignación de `/yo`. */
function clubesDelAmbito(yo: Yo, codigo: string): readonly ClubParaArbitro[] {
  const porId = new Map<string, ClubParaArbitro>()

  for (const permiso of yo.permisos) {
    if (
      permiso.codigo !== codigo ||
      permiso.ambito !== 'CLUB' ||
      !permiso.clubId
    ) {
      continue
    }

    const asignacion = yo.asignaciones.find(
      ({ club }) => club?.id === permiso.clubId,
    )
    porId.set(permiso.clubId, {
      id: permiso.clubId,
      nombre: asignacion?.club?.nombre ?? 'Club asignado',
    })
  }

  return [...porId.values()]
}

/**
 * Acciones visibles de F-20 según los permisos de `/yo` (H-02.4). El backend vuelve a decidir el
 * permiso y el ámbito de cada árbitro; aquí solo se evita ofrecer lo que no aplica.
 *
 * `consultaAmbitoLiga` distingue la vista de la Comisión (ve todos los clubes, su filtro y la
 * insignia «Cumple cuota») de la del club (solo el suyo y su indicador de cuota, B-08.2).
 */
export function usePermisosArbitros() {
  const { yo } = useSesionActual()
  const proponer = tienePermiso(yo.permisos, PROPONER_ARBITRO)

  const clubesProponer = useMemo(
    () => (proponer ? clubesDelAmbito(yo, PROPONER_ARBITRO) : []),
    [proponer, yo],
  )
  const clubesConsulta = useMemo(
    () => clubesDelAmbito(yo, CONSULTAR_ARBITRO),
    [yo],
  )

  return {
    consultar: tienePermiso(yo.permisos, CONSULTAR_ARBITRO),
    consultaAmbitoLiga: yo.permisos.some(
      (permiso) =>
        permiso.codigo === CONSULTAR_ARBITRO && permiso.ambito === 'LIGA',
    ),
    proponer,
    validar: tienePermiso(yo.permisos, VALIDAR_ARBITRO),
    /** Ver y editar la disponibilidad (B-09.2, RF-022); el backend decide el ámbito. */
    disponibilidad: tienePermiso(yo.permisos, EDITAR_DISPONIBILIDAD),
    puedeRegistrarPersona: tienePermiso(yo.permisos, EDITAR_PERSONA),
    clubesProponer,
    clubesConsulta,
  }
}
