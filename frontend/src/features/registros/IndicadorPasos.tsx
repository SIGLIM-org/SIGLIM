import { Check } from 'lucide-react'
import type { DescripcionPaso, NumeroPaso } from '@/features/registros/asistenteAlta'

type Props = {
  pasos: readonly DescripcionPaso[]
  actual: NumeroPaso
}

/**
 * Avance del asistente (F-18): el paso activo lleva `aria-current="step"` y los anteriores se marcan
 * como completados con ícono y texto, no solo con color (RNF-008).
 */
export function IndicadorPasos({ pasos, actual }: Props) {
  return (
    <nav aria-label="Pasos del alta">
      <ol className="grid grid-cols-4 gap-1.5 sm:gap-3">
        {pasos.map(({ numero, titulo, descripcion }) => {
          const activo = numero === actual
          const completado = numero < actual

          return (
            <li
              key={numero}
              aria-current={activo ? 'step' : undefined}
              className={[
                'flex min-w-0 items-center justify-center gap-3 rounded-lg border p-2 sm:justify-start sm:p-3',
                activo ? 'border-primary bg-primary/6' : 'border-border bg-surface',
              ].join(' ')}
            >
              <span
                aria-hidden="true"
                className={[
                  'grid size-8 shrink-0 place-items-center rounded-md font-mono text-mono-data',
                  activo || completado
                    ? 'bg-primary text-on-primary'
                    : 'bg-background text-text-muted ring-1 ring-border',
                ].join(' ')}
              >
                {completado ? <Check className="size-4" /> : numero}
              </span>
              <span className="flex min-w-0 flex-col">
                <span className="sr-only">
                  Paso {numero} de {pasos.length}
                  {completado ? ', completado' : ''}:{' '}
                </span>
                <span
                  className={[
                    'sr-only sm:not-sr-only sm:text-label-md',
                    activo ? 'text-primary' : 'text-text-main',
                  ].join(' ')}
                >
                  {titulo}
                </span>
                <span className="hidden text-body-sm text-text-muted lg:block">{descripcion}</span>
              </span>
            </li>
          )
        })}
      </ol>
      {/* En móvil solo caben los números: el nombre del paso activo va debajo (la lista ya lo anuncia). */}
      <p aria-hidden="true" className="mt-2 text-label-md text-primary sm:hidden">
        {pasos.find(({ numero }) => numero === actual)?.titulo}
      </p>
    </nav>
  )
}
