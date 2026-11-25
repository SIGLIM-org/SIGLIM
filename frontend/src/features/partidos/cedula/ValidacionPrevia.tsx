import { CircleAlert, CircleCheck } from 'lucide-react'
import type { Faltante } from '@/features/partidos/cedula/tipos'

type Props = {
  faltantes: readonly Faltante[]
}

/**
 * Validación previa al envío (F-26, VAL-020): lo mismo que revisa el backend al enviar, para que el
 * Capturista vea qué le falta antes de intentarlo.
 */
export function ValidacionPrevia({ faltantes }: Props) {
  if (faltantes.length === 0) {
    return (
      <p className="flex items-center gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong">
        <CircleCheck aria-hidden="true" className="size-4 shrink-0" />
        La cédula tiene lo obligatorio para enviarse a validación.
      </p>
    )
  }

  return (
    <section aria-labelledby="titulo-faltantes" className="flex gap-3 rounded-lg border border-warning/30 bg-warning/8 p-4">
      <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-strong" />
      <div className="min-w-0">
        <h2 id="titulo-faltantes" className="text-headline-sm text-warning-strong">
          {faltantes.length === 1 ? 'Falta 1 dato obligatorio para enviar' : `Faltan ${faltantes.length} datos obligatorios para enviar`}
        </h2>
        <ul className="mt-1.5 flex list-disc flex-col gap-1 pl-5 text-body-md text-warning-strong">
          {faltantes.map((falta) => (
            <li key={falta.campo}>{falta.mensaje}</li>
          ))}
        </ul>
      </div>
    </section>
  )
}
