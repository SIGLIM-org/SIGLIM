import { useId, useRef, useState, type DragEvent } from 'react'
import { ArrowDown, ArrowUp, GripVertical, Plus, X } from 'lucide-react'
import {
  agregarCriterio,
  criteriosDisponibles,
  moverCriterio,
  puedeBajar,
  puedeSubir,
  quitarCriterio,
} from '@/features/temporadas/desempates'
import { etiquetaCriterio } from '@/features/temporadas/etiquetas'
import type { CriterioDesempate } from '@/features/temporadas/tipos'

type Props = {
  criterios: CriterioDesempate[]
  onCambiar: (criterios: CriterioDesempate[]) => void
  /** Id del encabezado de la sección, para nombrar la lista. */
  idTitulo: string
}

const CLASE_ICONO =
  'inline-flex size-11 shrink-0 items-center justify-center rounded-md border border-border bg-surface text-text-main transition-colors hover:bg-background disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-surface'

/**
 * Orden de los criterios de desempate (F-11, RF-050, D3). El mecanismo accesible son los botones
 * Subir y Bajar; el arrastre nativo es un atajo solo para mouse. PUNTOS queda fijo arriba y
 * SORTEO, si está, fijo al final. Cada movimiento se anuncia en una región `aria-live`.
 */
export function EditorDesempates({ criterios, onCambiar, idTitulo }: Props) {
  const id = useId()
  const [anuncio, setAnuncio] = useState('')
  const [arrastrando, setArrastrando] = useState<number | null>(null)
  const [sobre, setSobre] = useState<number | null>(null)
  const listaRef = useRef<HTMLOListElement>(null)
  const disponibles = criteriosDisponibles(criterios)

  const anunciarPosicion = (lista: CriterioDesempate[], criterio: CriterioDesempate) => {
    const posicion = lista.indexOf(criterio) + 1
    setAnuncio(`${etiquetaCriterio[criterio].texto}, posición ${posicion} de ${lista.length}`)
  }

  /** Tras mover, el foco sigue al criterio; si su botón quedó deshabilitado, pasa al otro. */
  const enfocar = (criterio: CriterioDesempate, preferido: 'subir' | 'bajar') => {
    window.requestAnimationFrame(() => {
      const otro = preferido === 'subir' ? 'bajar' : 'subir'
      const boton =
        listaRef.current?.querySelector<HTMLButtonElement>(
          `[data-criterio="${criterio}"][data-accion="${preferido}"]:not(:disabled)`,
        ) ??
        listaRef.current?.querySelector<HTMLButtonElement>(
          `[data-criterio="${criterio}"][data-accion="${otro}"]:not(:disabled)`,
        )
      boton?.focus()
    })
  }

  const mover = (indice: number, delta: number) => {
    const criterio = criterios[indice]
    const nueva = moverCriterio(criterios, indice, delta)
    onCambiar(nueva)
    anunciarPosicion(nueva, criterio)
    enfocar(criterio, delta < 0 ? 'subir' : 'bajar')
  }

  const quitar = (criterio: CriterioDesempate) => {
    onCambiar(quitarCriterio(criterios, criterio))
    setAnuncio(`${etiquetaCriterio[criterio].texto} quitado de los criterios`)
  }

  const agregar = (criterio: CriterioDesempate) => {
    const nueva = agregarCriterio(criterios, criterio)
    onCambiar(nueva)
    anunciarPosicion(nueva, criterio)
  }

  const soltar = (evento: DragEvent<HTMLLIElement>, destino: number) => {
    evento.preventDefault()

    if (arrastrando !== null && arrastrando !== destino) {
      const criterio = criterios[arrastrando]
      const nueva = moverCriterio(criterios, arrastrando, destino - arrastrando)
      onCambiar(nueva)
      anunciarPosicion(nueva, criterio)
    }

    setArrastrando(null)
    setSobre(null)
  }

  return (
    <div className="flex flex-col gap-4">
      <ol ref={listaRef} aria-labelledby={idTitulo} className="flex flex-col gap-2">
        {criterios.map((criterio, indice) => {
          const { texto, descripcion } = etiquetaCriterio[criterio]
          const fijo = criterio === 'PUNTOS'
          const movible = !fijo && criterio !== 'SORTEO'

          return (
            <li
              key={criterio}
              draggable={movible}
              onDragStart={(evento) => {
                evento.dataTransfer.effectAllowed = 'move'
                setArrastrando(indice)
              }}
              onDragOver={(evento) => {
                if (arrastrando !== null) {
                  evento.preventDefault()
                  setSobre(indice)
                }
              }}
              onDragLeave={() => setSobre((actual) => (actual === indice ? null : actual))}
              onDrop={(evento) => soltar(evento, indice)}
              onDragEnd={() => {
                setArrastrando(null)
                setSobre(null)
              }}
              className={[
                'flex flex-wrap items-center gap-x-3 gap-y-2 rounded-md border bg-surface p-2 transition-colors sm:p-3',
                sobre === indice && arrastrando !== indice ? 'border-primary' : 'border-border',
                arrastrando === indice ? 'opacity-50' : '',
              ].join(' ')}
            >
              {movible ? (
                <GripVertical
                  aria-hidden="true"
                  className="hidden size-5 shrink-0 cursor-grab text-text-muted sm:block"
                />
              ) : (
                <span aria-hidden="true" className="hidden size-5 shrink-0 sm:block" />
              )}
              <span
                aria-hidden="true"
                className={[
                  'flex size-7 shrink-0 items-center justify-center rounded-sm font-mono text-mono-data',
                  fijo ? 'bg-primary text-on-primary' : 'bg-background text-text-main',
                ].join(' ')}
              >
                {indice + 1}
              </span>
              <div className="min-w-40 flex-1">
                <p className="text-label-md text-text-main">
                  <span className="sr-only">Posición {indice + 1}: </span>
                  {texto}
                </p>
                <p className="text-body-sm text-text-muted">{descripcion}</p>
              </div>
              {fijo ? (
                <span className="shrink-0 text-body-sm text-text-muted">Siempre primero</span>
              ) : (
                <div className="ml-auto flex shrink-0 gap-1">
                  <button
                    type="button"
                    data-criterio={criterio}
                    data-accion="subir"
                    aria-label={`Subir ${texto}`}
                    disabled={!puedeSubir(criterios, indice)}
                    onClick={() => mover(indice, -1)}
                    className={CLASE_ICONO}
                  >
                    <ArrowUp aria-hidden="true" className="size-4" />
                  </button>
                  <button
                    type="button"
                    data-criterio={criterio}
                    data-accion="bajar"
                    aria-label={`Bajar ${texto}`}
                    disabled={!puedeBajar(criterios, indice)}
                    onClick={() => mover(indice, 1)}
                    className={CLASE_ICONO}
                  >
                    <ArrowDown aria-hidden="true" className="size-4" />
                  </button>
                  <button
                    type="button"
                    aria-label={`Quitar ${texto}`}
                    onClick={() => quitar(criterio)}
                    className={CLASE_ICONO}
                  >
                    <X aria-hidden="true" className="size-4" />
                  </button>
                </div>
              )}
            </li>
          )
        })}
      </ol>

      <p aria-live="polite" className="sr-only">
        {anuncio}
      </p>

      {disponibles.length > 0 && (
        <div className="flex flex-col gap-2">
          <h4 id={`${id}-disponibles`} className="text-label-md text-text-main">
            Criterios disponibles
          </h4>
          <ul aria-labelledby={`${id}-disponibles`} className="flex flex-col gap-2">
            {disponibles.map((criterio) => {
              const { texto, descripcion } = etiquetaCriterio[criterio]

              return (
                <li
                  key={criterio}
                  className="flex items-center gap-3 rounded-md border border-dashed border-border p-2 sm:p-3"
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-label-md text-text-main">{texto}</p>
                    <p className="text-body-sm text-text-muted">{descripcion}</p>
                  </div>
                  <button
                    type="button"
                    aria-label={`Agregar ${texto}`}
                    onClick={() => agregar(criterio)}
                    className="inline-flex min-h-11 shrink-0 items-center gap-1.5 rounded-md border border-border bg-surface px-3 text-label-md text-text-main transition-colors hover:bg-background"
                  >
                    <Plus aria-hidden="true" className="size-4" />
                    Agregar
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </div>
  )
}
