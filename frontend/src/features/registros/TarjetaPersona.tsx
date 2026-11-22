import type { ReactNode } from 'react'
import { BadgeCheck, Baby } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { nombreCompleto } from '@/features/registros/asistenteAlta'
import type { PersonaConMinoria } from '@/features/registros/tipos'

type Props = {
  /** «Jugador» o «Tutor». */
  rol: string
  persona: PersonaConMinoria
  /** Dato adicional bajo la CURP, p. ej. el parentesco. */
  detalle?: ReactNode
  /** Acción opcional, p. ej. «Cambiar tutor». */
  accion?: ReactNode
}

/**
 * Identidad de solo lectura (DEC-105): nombre, CURP e insignia «Menor de edad». En el asistente no
 * se editan los datos de una persona existente.
 */
export function TarjetaPersona({ rol, persona, detalle, accion }: Props) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-border bg-background p-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-md bg-success/12 text-success-strong">
          <BadgeCheck aria-hidden="true" className="size-5" />
        </span>
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-body-sm text-text-muted">{rol}</p>
          <p className="break-words text-headline-sm text-text-main">{nombreCompleto(persona)}</p>
          <p className="font-mono text-mono-data text-text-muted">{persona.curp}</p>
          {(persona.esMenor || detalle) && (
            <div className="flex flex-wrap items-center gap-2 pt-1">
              {persona.esMenor && (
                <Insignia variante="informacion" icono={Baby}>
                  Menor de edad
                </Insignia>
              )}
              {detalle}
            </div>
          )}
        </div>
      </div>
      {accion && <div className="shrink-0">{accion}</div>}
    </div>
  )
}
