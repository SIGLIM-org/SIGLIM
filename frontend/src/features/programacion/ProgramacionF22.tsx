import { useState } from 'react'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { ProgramacionFechas } from '@/features/programacion/ProgramacionFechas'
import { ProgramacionJornadas } from '@/features/programacion/ProgramacionJornadas'
import {
  SelectorVistaProgramacion,
  type VistaProgramacion,
} from '@/features/programacion/SelectorVistaProgramacion'
import { useFechas, useTemporadaActiva } from '@/features/programacion/api'
import { usePermisosProgramacion } from '@/features/programacion/permisos'
import { useCategoriasTemporada } from '@/features/temporadas/api'

/**
 * F-22 para quien programa (H-35.2). Si la temporada activa ya tiene fechas del calendario
 * (DEC-175), abre la vista por fecha y ofrece cambiar a la programación por jornada; sin fechas,
 * o si no se pudieron consultar, queda la programación manual por jornada (H-12.4), que sigue
 * sirviendo para fases finales y jornadas fuera del calendario.
 */
export function ProgramacionF22() {
  const permisos = usePermisosProgramacion()
  const temporadas = useTemporadaActiva({ habilitada: permisos.editar })
  const temporada = temporadas.data ?? null
  const fechas = useFechas(temporada?.id, { habilitada: permisos.editar })
  // Las dos vistas necesitan las categorías: se piden en paralelo con las fechas para no esperar una
  // vuelta más al montar la vista elegida (la consulta queda en caché con la misma clave).
  useCategoriasTemporada(temporada?.id ?? '', { habilitada: Boolean(temporada) && permisos.editar })
  const [vista, setVista] = useState<VistaProgramacion>('fecha')

  if (permisos.editar && temporada && fechas.isPending) {
    return <EstadoCarga etiqueta="Cargando las fechas de la temporada…" filas={4} columnas={3} />
  }

  const lista = fechas.data?.contenido ?? []
  const selector =
    lista.length > 0 ? <SelectorVistaProgramacion vista={vista} onCambiar={setVista} /> : undefined

  if (temporada && lista.length > 0 && vista === 'fecha') {
    return <ProgramacionFechas temporada={temporada} fechas={lista} selectorVista={selector} />
  }

  return <ProgramacionJornadas selectorVista={selector} />
}
