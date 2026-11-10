import { useEffect, useId, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'

type Props = {
  titulo: string
  descripcion?: string
  /** Insignia del elemento, p. ej. «No oficial» si es un valor de demostración. */
  insignia?: ReactNode
  onCerrar: () => void
  children: ReactNode
}

/**
 * Panel lateral de edición de F-32. A partir de `xl` acompaña a la tabla y se
 * mantiene visible al desplazarse; en pantallas menores aparece debajo. Al
 * montarse lleva el foco al título para que lectores de pantalla anuncien el
 * contexto del formulario (RNF-008).
 */
export function PanelEdicion({ titulo, descripcion, insignia, onCerrar, children }: Props) {
  const idTitulo = useId()
  const refTitulo = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    refTitulo.current?.focus()
  }, [])

  return (
    <aside
      aria-labelledby={idTitulo}
      className="flex min-w-0 flex-col rounded-lg border border-border bg-surface xl:sticky xl:top-20 xl:max-h-[calc(100dvh-6rem)] xl:overflow-y-auto"
    >
      <div className="flex items-start justify-between gap-3 rounded-t-lg border-b border-border bg-background p-4 xl:sticky xl:top-0 xl:z-10">
        <div className="flex min-w-0 flex-col gap-1">
          {insignia && <div className="self-start">{insignia}</div>}
          <h2
            id={idTitulo}
            ref={refTitulo}
            tabIndex={-1}
            className="text-headline-md text-text-main focus:outline-none"
          >
            {titulo}
          </h2>
          {descripcion && <p className="text-body-sm text-text-muted">{descripcion}</p>}
        </div>
        <button
          type="button"
          onClick={onCerrar}
          aria-label="Cerrar el formulario"
          className="inline-flex size-11 shrink-0 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-border/50 hover:text-text-main"
        >
          <X aria-hidden="true" className="size-5" />
        </button>
      </div>
      {children}
    </aside>
  )
}
