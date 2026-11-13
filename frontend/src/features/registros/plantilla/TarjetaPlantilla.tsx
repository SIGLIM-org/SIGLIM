import { Baby, CalendarDays } from 'lucide-react'
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

/**
 * Registro de la plantilla en móvil y tableta (F-17, DEC-117): los mismos datos que la fila de la
 * tabla, apilados para no desplazar en horizontal desde 360 px (RNF-007).
 */
export function TarjetaPlantilla({ registro, permisos }: Props) {
  const estado = etiquetaEstadoRegistro[registro.estado]

  return (
    <li className="flex flex-col gap-2 p-4">
      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
        <p className="min-w-0 break-words text-label-md font-semibold text-text-main">
          {registro.persona.nombreCompleto}
        </p>
        <IndicadorElegibilidad estado={registro.estado} />
      </div>
      <p className="font-mono text-mono-data text-text-main">
        <span className="sr-only">CURP </span>
        {registro.persona.curpEnmascarada}
      </p>
      <div className="flex flex-wrap items-center gap-1.5">
        <Insignia variante={estado.variante} icono={estado.icono}>
          {estado.texto}
        </Insignia>
        {registro.esMenor && (
          <Insignia variante="informacion" icono={Baby}>
            Menor de edad
          </Insignia>
        )}
      </div>
      <p className="flex items-center gap-1 text-body-sm text-text-muted">
        <CalendarDays aria-hidden="true" className="size-3.5 shrink-0" />
        Alta el {formatearFecha(registro.altaEn)}
      </p>
      <AccionesRegistroPlantilla registro={registro} permisos={permisos} />
    </li>
  )
}
