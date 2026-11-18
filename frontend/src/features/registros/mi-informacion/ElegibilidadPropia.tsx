import { CircleCheck, CircleX, OctagonAlert } from 'lucide-react'
import type { CausaElegibilidad, Elegibilidad } from '@/features/registros/mi-informacion/tipos'

/** Causa de suspensión por sanción vigente (DEC-142); se decide por código, nunca por el texto. */
export const CODIGO_SUSPENSION = 'RF-020'

function esSuspension({ codigo }: CausaElegibilidad): boolean {
  return codigo === CODIGO_SUSPENSION
}

/**
 * Tarjeta roja con los mensajes de las causas `RF-020`. Va arriba de los detalles del registro y
 * solo repite el `mensaje` del API: no arma textos propios con la sanción (DEC-107, DEC-151).
 *
 * Refs: RF-020, DEC-107, DEC-151
 */
export function TarjetaSuspension({ elegibilidad }: { elegibilidad: Elegibilidad }) {
  const suspensiones = elegibilidad.causas.filter(esSuspension)

  if (suspensiones.length === 0) {
    return null
  }

  return (
    <div className="flex gap-3 rounded-lg border border-error/40 bg-error/8 p-4">
      <OctagonAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-error-strong" />
      <div className="flex min-w-0 flex-col gap-1">
        <p className="text-headline-sm text-error-strong">Suspensión vigente</p>
        <ul className="flex flex-col gap-1">
          {suspensiones.map(({ mensaje }, indice) => (
            <li key={indice} className="break-words text-body-md text-error-strong">
              {mensaje}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

/**
 * Elegibilidad del registro tal como la calcula el API (DEC-117, DEC-142): ícono más texto, nunca
 * solo color (RNF-008). Las causas `RF-020` ya se leen en la tarjeta de suspensión, así que la lista
 * muestra el resto para no repetirlas.
 *
 * Refs: RF-019, RF-020, RNF-008, DEC-151
 */
export function ElegibilidadPropia({ elegibilidad }: { elegibilidad: Elegibilidad }) {
  if (elegibilidad.elegible) {
    return (
      <p className="flex min-h-11 items-center gap-2 rounded-md bg-success/12 px-3 py-2 text-label-md text-success-strong">
        <CircleCheck aria-hidden="true" className="size-5 shrink-0" />
        Puede jugar
      </p>
    )
  }

  const otrasCausas = elegibilidad.causas.filter((causa) => !esSuspension(causa))

  return (
    <div className="flex flex-col gap-1.5 rounded-md bg-error/8 px-3 py-2 text-error-strong">
      <p className="flex min-h-7 items-center gap-2 text-label-md">
        <CircleX aria-hidden="true" className="size-5 shrink-0" />
        No puede jugar
      </p>
      {otrasCausas.length > 0 && (
        <ul className="flex list-disc flex-col gap-1 pl-12 text-body-md">
          {otrasCausas.map(({ mensaje }, indice) => (
            <li key={indice} className="break-words">
              {mensaje}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
