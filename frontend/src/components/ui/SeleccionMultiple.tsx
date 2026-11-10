import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { ChevronDown } from 'lucide-react'

export type OpcionMultiple = {
  valor: string
  etiqueta: string
}

type Props = {
  id: string
  etiqueta: string
  opciones: readonly OpcionMultiple[]
  seleccion: readonly string[]
  onChange: (seleccion: string[]) => void
  /** Clases del texto de cada opción, p. ej. `font-mono` para valores crudos. */
  claseOpcion?: string
  textoVacio?: string
}

/**
 * Desplegable de selección múltiple del sistema de diseño (DESIGN.md, Filtros): un botón con
 * `aria-expanded` abre un grupo de casillas nativas, que dan la semántica y el teclado sin roles
 * a medida (RNF-008). Escape o salir del componente lo cierran.
 */
export function SeleccionMultiple({
  id,
  etiqueta,
  opciones,
  seleccion,
  onChange,
  claseOpcion = '',
  textoVacio = 'Sin opciones',
}: Props) {
  const [abierto, setAbierto] = useState(false)
  const raiz = useRef<HTMLDivElement>(null)
  const boton = useRef<HTMLButtonElement>(null)
  const idEtiqueta = `${id}-etiqueta`
  const idValor = `${id}-valor`
  const idPanel = `${id}-panel`

  useEffect(() => {
    if (!abierto) {
      return
    }

    const alPulsarFuera = (evento: PointerEvent) => {
      if (!raiz.current?.contains(evento.target as Node)) {
        setAbierto(false)
      }
    }

    document.addEventListener('pointerdown', alPulsarFuera)

    return () => document.removeEventListener('pointerdown', alPulsarFuera)
  }, [abierto])

  const alPresionar = (evento: KeyboardEvent) => {
    if (abierto && evento.key === 'Escape') {
      evento.stopPropagation()
      setAbierto(false)
      boton.current?.focus()
    }
  }

  const alternar = (valor: string, marcada: boolean) => {
    onChange(marcada ? [...seleccion, valor] : seleccion.filter((actual) => actual !== valor))
  }

  const resumen =
    seleccion.length === 0
      ? 'Todos'
      : seleccion.length === 1
        ? (opciones.find(({ valor }) => valor === seleccion[0])?.etiqueta ?? seleccion[0])
        : `${seleccion.length} seleccionados`

  return (
    <div
      ref={raiz}
      className="relative flex min-w-0 flex-col gap-1.5"
      onKeyDown={alPresionar}
      onBlur={(evento) => {
        if (evento.relatedTarget && !raiz.current?.contains(evento.relatedTarget)) {
          setAbierto(false)
        }
      }}
    >
      <span id={idEtiqueta} className="text-label-md text-text-main">
        {etiqueta}
      </span>
      <button
        ref={boton}
        id={id}
        type="button"
        aria-labelledby={`${idEtiqueta} ${idValor}`}
        aria-expanded={abierto}
        aria-controls={idPanel}
        onClick={() => setAbierto((actual) => !actual)}
        className="flex min-h-11 w-full items-center justify-between gap-2 rounded-md border border-border bg-surface px-3 text-left text-body-md text-text-main transition-colors hover:bg-background"
      >
        <span id={idValor} className={['min-w-0 truncate', seleccion.length === 1 ? claseOpcion : ''].join(' ')}>
          {resumen}
        </span>
        <ChevronDown
          aria-hidden="true"
          className={['size-4 shrink-0 text-text-muted transition-transform', abierto ? 'rotate-180' : ''].join(' ')}
        />
      </button>
      <div
        id={idPanel}
        role="group"
        aria-labelledby={idEtiqueta}
        hidden={!abierto}
        className="absolute left-0 top-full z-20 mt-1 max-h-64 w-full min-w-56 overflow-y-auto rounded-md border border-border bg-surface p-1 shadow-overlay"
      >
        {opciones.length === 0 ? (
          <p className="px-2 py-3 text-body-md text-text-muted">{textoVacio}</p>
        ) : (
          opciones.map(({ valor, etiqueta: texto }) => (
            <label
              key={valor}
              className="flex min-h-11 cursor-pointer items-center gap-2.5 rounded-sm px-2 py-1 text-body-md text-text-main hover:bg-background"
            >
              <input
                type="checkbox"
                checked={seleccion.includes(valor)}
                onChange={(evento) => alternar(valor, evento.target.checked)}
                className="size-[1.125rem] shrink-0 accent-primary"
              />
              <span className={['min-w-0 break-words', claseOpcion].join(' ')}>{texto}</span>
            </label>
          ))
        )}
      </div>
    </div>
  )
}
