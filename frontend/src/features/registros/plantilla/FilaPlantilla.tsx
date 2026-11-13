import { Baby } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import {
  AccionesRegistroPlantilla,
  type PermisosAccionesPlantilla,
} from '@/features/registros/plantilla/AccionesRegistroPlantilla'
import { IndicadorElegibilidad } from '@/features/registros/plantilla/IndicadorElegibilidad'
import type { RegistroJugadorFila } from '@/features/registros/tipos'
import { formatearFecha } from '@/features/temporadas/fechas'

type Props = {
  registro: RegistroJugadorFila
  permisos: PermisosAccionesPlantilla
}

const CELDA = 'px-4 py-3 align-middle'

/** Fila de la tabla de la plantilla en escritorio (F-17, DEC-117). */
export function FilaPlantilla({ registro, permisos }: Props) {
  const estado = etiquetaEstadoRegistro[registro.estado]

  return (
    <tr className="border-b border-border transition-colors last:border-b-0 hover:bg-background">
      <th scope="row" className={`${CELDA} text-left font-normal`}>
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-label-md font-semibold text-text-main">
            {registro.persona.nombreCompleto}
          </span>
          {registro.esMenor && (
            <Insignia variante="informacion" icono={Baby}>
              Menor de edad
            </Insignia>
          )}
        </span>
      </th>
      <td className={`${CELDA} whitespace-nowrap font-mono text-mono-data text-text-main`}>
        <span className="sr-only">CURP </span>
        {registro.persona.curpEnmascarada}
      </td>
      <td className={CELDA}>
        <Insignia variante={estado.variante} icono={estado.icono}>
          {estado.texto}
        </Insignia>
      </td>
      <td className={`${CELDA} whitespace-nowrap`}>
        <IndicadorElegibilidad estado={registro.estado} />
      </td>
      <td className={`${CELDA} whitespace-nowrap text-body-sm tabular-nums text-text-muted`}>
        {formatearFecha(registro.altaEn)}
      </td>
      <td className={`${CELDA} text-right`}>
        <AccionesRegistroPlantilla registro={registro} permisos={permisos} />
      </td>
    </tr>
  )
}
