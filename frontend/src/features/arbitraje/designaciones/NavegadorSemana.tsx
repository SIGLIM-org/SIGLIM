import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { formatearSemanaCorta } from '@/features/arbitraje/designaciones/fechas'
import { semanaAnterior, semanaSiguiente } from '@/features/canchas/fechas'

type Props = {
  semana: string
  esSemanaActual: boolean
  onCambiar: (lunes: string | null) => void
}

/**
 * Semana de F-24: anterior, esta semana y siguiente; `null` vuelve a la actual (DEC-209). En móvil
 * las flechas quedan solo con ícono (el texto sigue para los lectores de pantalla) para que los
 * tres botones quepan desde 360 px.
 */
export function NavegadorSemana({ semana, esSemanaActual, onCambiar }: Props) {
  return (
    <nav
      aria-label="Semana"
      className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
    >
      <p aria-live="polite" className="text-headline-md text-text-main">
        <span className="sr-only">Semana del </span>
        {formatearSemanaCorta(semana)}
      </p>
      <div className="flex gap-2">
        <Button
          variante="secundario"
          icono={ChevronLeft}
          onClick={() => onCambiar(semanaAnterior(semana))}
          className="max-sm:px-3"
        >
          <span className="max-sm:sr-only">Anterior</span>
        </Button>
        <Button
          variante="secundario"
          disabled={esSemanaActual}
          onClick={() => onCambiar(null)}
          className="flex-1 sm:flex-none"
        >
          Esta semana
        </Button>
        <Button
          variante="secundario"
          onClick={() => onCambiar(semanaSiguiente(semana))}
          className="flex-row-reverse max-sm:px-3"
          icono={ChevronRight}
        >
          <span className="max-sm:sr-only">Siguiente</span>
        </Button>
      </div>
    </nav>
  )
}
