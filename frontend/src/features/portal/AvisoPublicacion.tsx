import type { ReactNode } from 'react'
import { ShieldCheck } from 'lucide-react'

type Props = {
  children: ReactNode
}

/** Aviso institucional sobre lo que se publica en el portal (RF-056, VAL-014). */
export function AvisoPublicacion({ children }: Props) {
  return (
    <div className="mt-8 flex items-start gap-3 rounded-lg border border-primary/20 bg-surface p-4">
      <ShieldCheck
        aria-hidden="true"
        className="mt-0.5 size-5 shrink-0 text-primary"
      />
      <p className="text-body-md text-text-main">{children}</p>
    </div>
  )
}
