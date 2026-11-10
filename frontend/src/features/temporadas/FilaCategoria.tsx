import type { DragEvent } from 'react'
import { ArrowDown, ArrowUp, CircleCheck, GripVertical, Pencil, Plus, Power, PowerOff } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaTipoCategoria } from '@/features/temporadas/etiquetas'
import type { Categoria } from '@/features/temporadas/tipos'

type Props = {
  categoria: Categoria
  /** Posición en el orden local (1…n). */
  posicion: number
  total: number
  asignada: boolean
  /** Muestra «Asignar»: EDITAR, temporada en CONFIGURACION y categoría activa (D8). */
  puedeAsignar: boolean
  /** EDITAR y temporada no cerrada: reordenar, editar, desactivar y reactivar (D8). */
  puedeEditar: boolean
  asignando: boolean
  accionesDeshabilitadas: boolean
  arrastrada: boolean
  destinoArrastre: boolean
  onMover: (delta: number) => void
  onAsignar: () => void
  onEditar: () => void
  onCambiarActiva: () => void
  onArrastreInicio: () => void
  onArrastreSobre: (evento: DragEvent<HTMLTableRowElement>) => void
  onSoltar: (evento: DragEvent<HTMLTableRowElement>) => void
  onArrastreFin: () => void
}

const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'
const CLASE_CELDA = 'block py-1 lg:table-cell lg:px-4 lg:py-2 lg:align-middle'
const CLASE_ICONO =
  'inline-flex size-11 shrink-0 items-center justify-center rounded-md border border-border bg-surface text-text-main transition-colors hover:bg-background disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-surface'

/**
 * Fila del catálogo de categorías en F-11 (H-05.3, RF-099, RF-004). Bajo `lg` se apila como
 * tarjeta con sus etiquetas visibles (RNF-007). Subir y Bajar son el mecanismo accesible para
 * reordenar; el arrastre nativo es un atajo solo para mouse (D3).
 */
export function FilaCategoria({
  categoria,
  posicion,
  total,
  asignada,
  puedeAsignar,
  puedeEditar,
  asignando,
  accionesDeshabilitadas,
  arrastrada,
  destinoArrastre,
  onMover,
  onAsignar,
  onEditar,
  onCambiarActiva,
  onArrastreInicio,
  onArrastreSobre,
  onSoltar,
  onArrastreFin,
}: Props) {
  return (
    <tr
      role="row"
      data-categoria={categoria.id}
      draggable={puedeEditar}
      onDragStart={(evento) => {
        evento.dataTransfer.effectAllowed = 'move'
        onArrastreInicio()
      }}
      onDragOver={onArrastreSobre}
      onDrop={onSoltar}
      onDragEnd={onArrastreFin}
      className={[
        'block border-b p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:p-0',
        destinoArrastre ? 'border-primary' : 'border-border',
        arrastrada ? 'opacity-50' : '',
      ].join(' ')}
    >
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Orden</span>
        <div className="flex items-center gap-1">
          {puedeEditar && (
            <GripVertical
              aria-hidden="true"
              className="hidden size-5 shrink-0 cursor-grab text-text-muted lg:block"
            />
          )}
          <span className="w-6 font-mono text-mono-data text-text-main">{posicion}</span>
          {puedeEditar && (
            <>
              <button
                type="button"
                data-accion="subir"
                aria-label={`Subir ${categoria.nombre}`}
                disabled={posicion === 1}
                onClick={() => onMover(-1)}
                className={CLASE_ICONO}
              >
                <ArrowUp aria-hidden="true" className="size-4" />
              </button>
              <button
                type="button"
                data-accion="bajar"
                aria-label={`Bajar ${categoria.nombre}`}
                disabled={posicion === total}
                onClick={() => onMover(1)}
                className={CLASE_ICONO}
              >
                <ArrowDown aria-hidden="true" className="size-4" />
              </button>
            </>
          )}
        </div>
      </td>

      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Nombre</span>
        <span className="block break-words text-headline-sm text-text-main">{categoria.nombre}</span>
      </td>

      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Tipo</span>
        <span className="text-body-md text-text-main">{etiquetaTipoCategoria[categoria.tipo]}</span>
      </td>

      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Categoría superior</span>
        {categoria.categoriaSuperior ? (
          <span className="text-body-md text-text-main">{categoria.categoriaSuperior.nombre}</span>
        ) : (
          <span className="text-body-md text-text-muted">Tope de su rama</span>
        )}
      </td>

      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Estado</span>
        {categoria.activa ? (
          <Insignia variante="exito">Activa</Insignia>
        ) : (
          <Insignia variante="neutra" icono={PowerOff}>
            Inactiva
          </Insignia>
        )}
      </td>

      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>En esta temporada</span>
        {asignada ? (
          <Insignia variante="informacion" icono={CircleCheck}>
            Asignada
          </Insignia>
        ) : puedeAsignar ? (
          <Button
            variante="secundario"
            icono={Plus}
            cargando={asignando}
            disabled={accionesDeshabilitadas}
            aria-label={`Asignar ${categoria.nombre} a la temporada`}
            onClick={onAsignar}
          >
            Asignar
          </Button>
        ) : (
          <span className="text-body-md text-text-muted">
            <span aria-hidden="true">—</span>
            <span className="sr-only">No asignada</span>
          </span>
        )}
      </td>

      {puedeEditar && (
        <td role="cell" className={`${CLASE_CELDA} pt-3 lg:text-right`}>
          <span className={CLASE_ETIQUETA}>Acciones</span>
          <div className="flex flex-wrap gap-2 lg:justify-end">
            <Button
              variante="secundario"
              icono={Pencil}
              disabled={accionesDeshabilitadas}
              aria-label={`Editar ${categoria.nombre}`}
              onClick={onEditar}
            >
              Editar
            </Button>
            <Button
              variante="secundario"
              icono={categoria.activa ? PowerOff : Power}
              disabled={accionesDeshabilitadas}
              aria-label={`${categoria.activa ? 'Desactivar' : 'Reactivar'} ${categoria.nombre}`}
              onClick={onCambiarActiva}
            >
              {categoria.activa ? 'Desactivar' : 'Reactivar'}
            </Button>
          </div>
        </td>
      )}
    </tr>
  )
}
