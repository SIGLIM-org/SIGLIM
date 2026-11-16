import { useId } from 'react'
import { Info } from 'lucide-react'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'

/**
 * Tarjeta de la regla de cuota en la vista de la Comisión (B-08.2, RF-114). El filtro y la insignia
 * «Cumple cuota» muestran el indicador por club; esta tarjeta explica la regla sin inventar valores:
 * el divisor es configuración `[DEMO]` de la temporada (DEC-17, DEC-72).
 */
export function TarjetaReglaCuota() {
  const id = useId()

  return (
    <section
      aria-labelledby={`${id}-titulo`}
      className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h2 id={`${id}-titulo`} className="text-headline-sm text-text-main">
          Cuota de árbitros por club
        </h2>
        <InsigniaNoOficial />
      </div>
      <p className="text-body-sm text-text-muted">
        La cuota de cada club se calcula con sus equipos activos y el parámetro vigente de la
        temporada, con redondeo hacia arriba. Solo indica: no bloquea la validación ni la
        designación de árbitros.
      </p>
      <p className="flex items-start gap-2 rounded-md bg-background p-3 text-body-sm text-text-muted">
        <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        La insignia «Cumple cuota» junto al club muestra sus árbitros validados frente a los
        requeridos; usa el filtro para ver solo los clubes que no cumplen.
      </p>
    </section>
  )
}
