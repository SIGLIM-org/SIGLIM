import { CalendarRange, ListOrdered, type LucideIcon } from 'lucide-react'

export type VistaProgramacion = 'fecha' | 'jornada'

type Props = {
  vista: VistaProgramacion
  onCambiar: (vista: VistaProgramacion) => void
}

const OPCIONES: { valor: VistaProgramacion; texto: string; icono: LucideIcon }[] = [
  { valor: 'fecha', texto: 'Por fecha', icono: CalendarRange },
  { valor: 'jornada', texto: 'Por jornada', icono: ListOrdered },
]

/**
 * Alterna F-22 entre la vista por fecha (H-35, DEC-175) y la programación manual por jornada, que
 * se conserva para las fases finales y las jornadas fuera del calendario.
 */
export function SelectorVistaProgramacion({ vista, onCambiar }: Props) {
  return (
    <div
      role="group"
      aria-label="Vista de la programación"
      className="inline-flex rounded-md border border-border bg-surface p-0.5"
    >
      {OPCIONES.map(({ valor, texto, icono: Icono }) => {
        const activa = vista === valor

        return (
          <button
            key={valor}
            type="button"
            aria-pressed={activa}
            onClick={() => onCambiar(valor)}
            className={[
              'inline-flex min-h-10 items-center gap-1.5 rounded-[5px] px-3 text-label-md transition-colors',
              activa
                ? 'bg-primary text-on-primary'
                : 'text-text-muted hover:bg-background hover:text-text-main',
            ].join(' ')}
          >
            <Icono aria-hidden="true" className="size-4 shrink-0" />
            {texto}
          </button>
        )
      })}
    </div>
  )
}
