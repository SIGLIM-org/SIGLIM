import { useId } from 'react'
import type { AvanceDesignacion as Avance } from '@/features/arbitraje/designaciones/tipos'

type Props = {
  avance: Avance
}

function partidosConCentral({ conCentral, partidos }: Avance): string {
  return `${conCentral} de ${partidos} ${partidos === 1 ? 'partido' : 'partidos'} con árbitro central`
}

/**
 * Avance de la jornada (UI-025, «Terminado cuando» de H-14.2): barra accesible con los partidos que
 * ya tienen central y, debajo, los que tienen sus dos asistentes. Es un indicador calculado por el
 * backend, no un dato oficial.
 */
export function AvanceDesignacion({ avance }: Props) {
  const id = useId()

  if (avance.partidos === 0) {
    return (
      <p className="text-body-md text-text-muted">
        La jornada no tiene partidos por designar.
      </p>
    )
  }

  const porcentaje = Math.round((avance.conCentral / avance.partidos) * 100)
  const texto = partidosConCentral(avance)

  return (
    <div className="flex flex-col gap-2">
      <p id={id} className="text-label-md text-text-main">
        <span className="font-mono tabular-nums">{avance.conCentral}</span> de{' '}
        <span className="font-mono tabular-nums">{avance.partidos}</span>{' '}
        {avance.partidos === 1 ? 'partido' : 'partidos'} con árbitro central
      </p>
      <div
        role="progressbar"
        aria-labelledby={id}
        aria-valuemin={0}
        aria-valuemax={avance.partidos}
        aria-valuenow={avance.conCentral}
        aria-valuetext={texto}
        className="h-2 w-full overflow-hidden rounded-full bg-border"
      >
        <div
          className="h-full rounded-full bg-primary transition-[width] duration-200 ease-out motion-reduce:transition-none"
          style={{ width: `${porcentaje}%` }}
        />
      </div>
      <p className="text-body-sm text-text-muted">
        <span className="font-mono tabular-nums">{avance.asistentesCompletos}</span> de{' '}
        <span className="font-mono tabular-nums">{avance.partidos}</span> con asistentes completos
      </p>
    </div>
  )
}
