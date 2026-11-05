import type { ReactNode } from 'react'

type Props = {
  titulo: string
  /** Id de la leyenda, para nombrar listas internas (p. ej. los criterios de desempate). */
  idTitulo?: string
  /** Insignia junto al título, p. ej. «No oficial». */
  insignia?: ReactNode
  descripcion?: string
  children: ReactNode
}

/**
 * Sección del formulario de parámetros de F-11 con `fieldset` y `legend` (RNF-008). La leyenda
 * flota para comportarse como un elemento más del contenedor y admitir la insignia.
 */
export function SeccionParametros({ titulo, idTitulo, insignia, descripcion, children }: Props) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-5">
      <legend
        id={idTitulo}
        className="float-left flex w-full flex-wrap items-center gap-2 text-headline-sm text-text-main"
      >
        {titulo}
        {insignia}
      </legend>
      {descripcion && <p className="-mt-2 text-body-sm text-text-muted">{descripcion}</p>}
      {children}
    </fieldset>
  )
}
