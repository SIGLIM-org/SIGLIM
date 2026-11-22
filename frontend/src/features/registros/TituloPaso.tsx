import { useEffect, useRef, type ReactNode } from 'react'

type Props = {
  id: string
  /** Al llegar a este paso desde otro, el título recibe el foco (lectores de pantalla y teclado). */
  enfocar: boolean
  children: ReactNode
}

/** Título de un paso del asistente; enfocable por programa sin entrar al orden de tabulación. */
export function TituloPaso({ id, enfocar, children }: Props) {
  const referencia = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    if (enfocar) {
      referencia.current?.focus()
    }
  }, [enfocar])

  return (
    <h2
      id={id}
      ref={referencia}
      tabIndex={-1}
      className="text-headline-lg text-text-main focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      {children}
    </h2>
  )
}
