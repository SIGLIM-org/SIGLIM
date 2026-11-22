import type { ReactNode } from 'react'
import { Ban } from 'lucide-react'

type Props = {
  /** Qué no se pudo hacer, sin códigos internos (DEC-63, DEC-107). */
  titulo: string
  /** `detail` del backend o el texto propio que explica la causa y qué hacer. */
  mensaje: string
  /** Datos que chocan, p. ej. el club y el equipo donde ya está registrada la CURP (VAL-017). */
  children?: ReactNode
  /** Acción para resolverlo, p. ej. «Cambiar equipo». */
  accion?: ReactNode
}

/** Bloqueo del asistente de alta: dice la causa y qué hacer, y se anuncia con `role="alert"`. */
export function AvisoBloqueo({ titulo, mensaje, children, accion }: Props) {
  return (
    <div
      role="alert"
      className="flex gap-3 rounded-lg border border-error/40 bg-error/8 p-4 text-text-main"
    >
      <Ban aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-error-strong" />
      <div className="flex min-w-0 flex-col gap-2">
        <p className="text-headline-sm text-error-strong">{titulo}</p>
        <p className="text-body-md">{mensaje}</p>
        {children}
        {accion && <div className="pt-1">{accion}</div>}
      </div>
    </div>
  )
}
