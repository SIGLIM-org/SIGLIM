import { useId } from 'react'
import { Ban, Lock, Pencil } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoEquipo } from '@/features/organizacion/etiquetas'
import type { Equipo } from '@/features/organizacion/tipos'
import { formatearFecha } from '@/features/temporadas/fechas'

type Props = {
  equipo: Equipo
  mostrarTemporada: boolean
  conAcciones: boolean
  onEditar: (equipo: Equipo) => void
  onDarDeBaja: (equipo: Equipo) => void
}

const CLASE_CELDA = 'block py-1 lg:table-cell lg:px-4 lg:py-3 lg:align-top'
const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

/**
 * Fila de la tabla de equipos (F-15). Un equipo en BAJA muestra su fecha y motivo y no tiene
 * acciones (RN-005). Con la temporada cerrada las acciones se deshabilitan con la causa a la vista;
 * el servidor sigue siendo la barrera (409 RF-003).
 */
export function FilaEquipo({ equipo, mostrarTemporada, conAcciones, onEditar, onDarDeBaja }: Props) {
  const idCausa = useId()
  const estado = etiquetaEstadoEquipo[equipo.estado]
  const deBaja = equipo.estado === 'BAJA'
  const temporadaCerrada = equipo.temporada.estado === 'CERRADA'

  return (
    <tr
      role="row"
      className="block border-b border-border p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:p-0"
    >
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Equipo</span>
        <span className="block break-words text-headline-sm text-text-main">{equipo.nombre}</span>
        {mostrarTemporada && (
          <span className="block text-body-sm text-text-muted">{equipo.temporada.nombre}</span>
        )}
        {deBaja && (
          <span className="mt-1 block max-w-prose text-body-sm text-text-muted">
            Baja el {equipo.bajaEn ? formatearFecha(equipo.bajaEn) : 'sin fecha'}
            {equipo.motivoBaja && (
              <>
                : <span className="whitespace-pre-line break-words text-text-main">{equipo.motivoBaja}</span>
              </>
            )}
          </span>
        )}
      </td>
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Categoría</span>
        <span className="block break-words text-body-md text-text-main">{equipo.categoria.nombre}</span>
      </td>
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Grupo</span>
        <span className="block break-words text-body-md text-text-main">{equipo.grupo.nombre}</span>
      </td>
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Estado</span>
        <Insignia variante={estado.variante} icono={estado.icono}>
          {estado.texto}
        </Insignia>
      </td>
      {conAcciones && (
        <td role="cell" className={deBaja ? 'hidden lg:table-cell' : `${CLASE_CELDA} pt-3 lg:text-right`}>
          {!deBaja && (
            <div className="flex flex-col items-start gap-2 lg:items-end">
              <div className="flex flex-wrap gap-2 lg:justify-end">
                <Button
                  variante="secundario"
                  icono={Pencil}
                  disabled={temporadaCerrada}
                  aria-describedby={temporadaCerrada ? idCausa : undefined}
                  aria-label={`Editar ${equipo.nombre}`}
                  onClick={() => onEditar(equipo)}
                >
                  Editar
                </Button>
                <Button
                  variante="secundario"
                  icono={Ban}
                  disabled={temporadaCerrada}
                  aria-describedby={temporadaCerrada ? idCausa : undefined}
                  aria-label={`Dar de baja ${equipo.nombre}`}
                  onClick={() => onDarDeBaja(equipo)}
                >
                  Dar de baja
                </Button>
              </div>
              {temporadaCerrada && (
                <p id={idCausa} className="flex items-center gap-1.5 text-body-sm text-text-muted">
                  <Lock aria-hidden="true" className="size-4 shrink-0" />
                  Temporada cerrada: no admite cambios.
                </p>
              )}
            </div>
          )}
        </td>
      )}
    </tr>
  )
}
