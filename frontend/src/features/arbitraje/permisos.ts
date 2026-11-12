import { useMemo } from 'react'
import type { ClubParaArbitro } from '@/features/arbitraje/tipos'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

export const CONSULTAR_ARBITRO = 'arbitraje.arbitro:CONSULTAR'
export const PROPONER_ARBITRO = 'arbitraje.arbitro:PROPONER'
export const VALIDAR_ARBITRO = 'arbitraje.arbitro:VALIDAR'
export const EDITAR_PERSONA = 'organizacion.persona:EDITAR'

/**
 * Acciones visibles de F-20 según los permisos de `/yo` (H-02.4). El backend vuelve a decidir el
 * permiso y el ámbito de cada árbitro; aquí solo se evita ofrecer lo que no aplica.
 */
export function usePermisosArbitros() {
  const { yo } = useSesionActual()
  const proponer = tienePermiso(yo.permisos, PROPONER_ARBITRO)

  const clubesProponer = useMemo<readonly ClubParaArbitro[]>(() => {
    if (!proponer) {
      return []
    }

    const porId = new Map<string, ClubParaArbitro>()

    for (const permiso of yo.permisos) {
      if (
        permiso.codigo !== PROPONER_ARBITRO ||
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
  }, [proponer, yo])

  return {
    consultar: tienePermiso(yo.permisos, CONSULTAR_ARBITRO),
    proponer,
    validar: tienePermiso(yo.permisos, VALIDAR_ARBITRO),
    puedeRegistrarPersona: tienePermiso(yo.permisos, EDITAR_PERSONA),
    clubesProponer,
  }
}
