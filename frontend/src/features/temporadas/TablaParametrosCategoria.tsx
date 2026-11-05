import { Link } from 'react-router'
import { Layers } from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import {
  campoFila,
  type FilaCategoriaFormulario,
} from '@/features/temporadas/parametros'
import type { CategoriaTemporada } from '@/features/temporadas/tipos'

type Props = {
  categorias: CategoriaTemporada[]
  filas: Record<string, FilaCategoriaFormulario>
  /** Valores de la temporada, que se muestran como `placeholder`. */
  duracionTemporada: string
  bloqueTemporada: string
  errores: Record<string, string>
  /** Categorías que el servidor rechazó (`422 categoriasInvalidas`). */
  invalidas: readonly string[]
  onCambiar: (categoriaId: string, campo: keyof FilaCategoriaFormulario, valor: string) => void
}

const CLASE_ENCABEZADO = 'px-3 py-2 text-left text-label-sm text-text-muted'
const CLASE_ETIQUETA_MOVIL = 'block text-label-sm text-text-muted lg:hidden'

/**
 * Duración del juego y bloque de cancha propios de cada categoría asignada (F-11, RF-100,
 * DEC-25). Una fila vacía usa los valores de la temporada; una fila lleva ambos o ninguno. Bajo
 * `lg` cada fila se apila como tarjeta (RNF-007).
 */
export function TablaParametrosCategoria({
  categorias,
  filas,
  duracionTemporada,
  bloqueTemporada,
  errores,
  invalidas,
  onCambiar,
}: Props) {
  if (categorias.length === 0) {
    return (
      <EstadoVacio
        icono={Layers}
        titulo="La temporada aún no tiene categorías"
        descripcion="Asigna categorías para darles una duración y un bloque propios."
        accion={
          <Link to={{ search: '?pestana=categorias' }} replace className={clasesBoton('secundario')}>
            Ir a Categorías
          </Link>
        }
      />
    )
  }

  return (
    <div className="flex flex-col gap-2">
      <p className="text-body-sm text-text-muted">Vacío = usa los valores de la temporada.</p>
      <div className="overflow-hidden rounded-md border border-border">
        <table role="table" className="block w-full lg:table">
          <caption className="sr-only">Duración del juego y bloque de cancha por categoría</caption>
          <thead role="rowgroup" className="hidden border-b-2 border-border bg-background lg:table-header-group">
            <tr role="row">
              <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                Categoría
              </th>
              <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                Duración del juego (min)
              </th>
              <th scope="col" role="columnheader" className={CLASE_ENCABEZADO}>
                Bloque de cancha (min)
              </th>
            </tr>
          </thead>
          <tbody role="rowgroup" className="block lg:table-row-group">
            {categorias.map(({ categoria }) => {
              const fila = filas[categoria.id] ?? { duracionJuegoMin: '', bloqueCanchaMin: '' }
              const invalida = invalidas.includes(categoria.id)
              const idDuracion = `fila-${categoria.id}-duracion`
              const idBloque = `fila-${categoria.id}-bloque`
              const errorDuracion = errores[campoFila(categoria.id, 'duracionJuegoMin')]
              const errorBloque = errores[campoFila(categoria.id, 'bloqueCanchaMin')]

              return (
                <tr
                  key={categoria.id}
                  role="row"
                  data-invalida={invalida || undefined}
                  className={[
                    'grid grid-cols-2 gap-x-3 gap-y-2 border-b border-border p-3 last:border-b-0 lg:table-row lg:p-0',
                    invalida ? 'bg-error/8' : '',
                  ].join(' ')}
                >
                  <th
                    scope="row"
                    role="rowheader"
                    className="col-span-2 text-left font-normal lg:px-3 lg:py-2 lg:align-top"
                  >
                    <span className="block text-label-md text-text-main">{categoria.nombre}</span>
                    {invalida && (
                      <span role="alert" className="block text-body-sm text-error-strong">
                        El servidor rechazó esta categoría para la temporada.
                      </span>
                    )}
                  </th>
                  {(
                    [
                      ['duracionJuegoMin', idDuracion, 'Duración', duracionTemporada, errorDuracion],
                      ['bloqueCanchaMin', idBloque, 'Bloque', bloqueTemporada, errorBloque],
                    ] as const
                  ).map(([campo, idCampo, nombre, marcador, error]) => (
                    <td key={campo} role="cell" className="min-w-0 lg:px-3 lg:py-2 lg:align-top">
                      <label htmlFor={idCampo} className={CLASE_ETIQUETA_MOVIL}>
                        {nombre} (min)
                      </label>
                      <input
                        id={idCampo}
                        type="number"
                        inputMode="numeric"
                        step={1}
                        min={1}
                        max={600}
                        value={fila[campo]}
                        placeholder={marcador}
                        aria-label={`${nombre} de ${categoria.nombre} (min)`}
                        aria-invalid={error || invalida ? true : undefined}
                        aria-describedby={error ? `${idCampo}-error` : undefined}
                        onChange={(evento) => onCambiar(categoria.id, campo, evento.target.value)}
                        className={[
                          'min-h-11 w-full rounded-md border bg-surface px-3 font-mono text-mono-data text-text-main tabular-nums placeholder:text-text-muted/70 lg:max-w-40',
                          error || invalida ? 'border-error' : 'border-border',
                        ].join(' ')}
                      />
                      {error && (
                        <p
                          id={`${idCampo}-error`}
                          role="alert"
                          className="mt-1 text-body-sm text-error-strong"
                        >
                          {error}
                        </p>
                      )}
                    </td>
                  ))}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
