import type { ReactNode } from 'react'
import { ShieldCheck } from 'lucide-react'

type Props = {
  /** Nombre de la sección que se muestra junto a la liga. */
  seccion: string
  titulo: string
  descripcion: string
  /** Acciones principales de la página (enlaces que se ven como botón). */
  acciones?: ReactNode
}

/** Encabezado institucional compartido por programación, resultados y tabla (F-01, F-03, F-04). */
export function EncabezadoPortal({
  seccion,
  titulo,
  descripcion,
  acciones,
}: Props) {
  return (
    <section className="overflow-hidden rounded-lg bg-surface-dark text-on-surface-dark">
      <div className="flex flex-col gap-6 p-6 sm:p-8">
        <div className="flex flex-wrap items-center gap-3">
          <span className="inline-flex items-center gap-1.5 rounded-md bg-primary px-2.5 py-1 text-label-sm uppercase text-on-primary">
            <ShieldCheck aria-hidden="true" className="size-4" />
            Portal público
          </span>
          <span className="text-label-sm uppercase text-on-surface-dark/80">
            {seccion} · Liga Municipal de Futbol Amateur Morelia
          </span>
        </div>

        <div className="max-w-3xl">
          <h1 className="text-display-lg-mobile sm:text-display-lg">{titulo}</h1>
          <p className="mt-3 text-body-lg text-on-surface-dark/85">{descripcion}</p>
        </div>

        {acciones && <div className="flex flex-col gap-3 sm:flex-row">{acciones}</div>}
      </div>
    </section>
  )
}
