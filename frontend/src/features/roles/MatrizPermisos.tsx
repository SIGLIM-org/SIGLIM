import { etiquetaModulo } from '@/lib/modulos'
import type { GrupoPermisos } from '@/features/roles/tipos'

type Props = {
  grupos: readonly GrupoPermisos[]
  seleccion: ReadonlySet<string>
  /** Sin permiso de edición o con el propio rol, la matriz se consulta pero no se cambia. */
  deshabilitada: boolean
  onAlternar: (codigo: string, marcado: boolean) => void
}

/**
 * Matriz de permisos de F-13 agrupada por módulo del ERS. Cada casilla muestra la descripción
 * legible y el código `modulo.recurso:ACCION` que usa la API; el conjunto final se envía completo
 * al guardar (convenciones §6.1, RF-095).
 */
export function MatrizPermisos({ grupos, seleccion, deshabilitada, onAlternar }: Props) {
  return (
    <div className="grid gap-4 xl:grid-cols-2">
      {grupos.map(({ modulo, permisos }) => {
        const marcados = permisos.filter(({ codigo }) => seleccion.has(codigo)).length

        return (
          <section
            key={modulo}
            aria-labelledby={`modulo-${modulo}`}
            className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-surface"
          >
            <header className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 border-b border-border bg-background px-4 py-3">
              <h3 id={`modulo-${modulo}`} className="text-label-md text-text-main">
                {etiquetaModulo(modulo)}
              </h3>
              <span className="font-mono text-mono-data text-text-muted">
                {marcados}/{permisos.length}
              </span>
            </header>

            <ul className="flex flex-col divide-y divide-border">
              {permisos.map((permiso) => (
                <li key={permiso.codigo}>
                  <label
                    className={[
                      'flex min-h-11 cursor-pointer items-start gap-3 px-4 py-3 transition-colors',
                      deshabilitada
                        ? 'cursor-not-allowed'
                        : 'hover:bg-background',
                    ].join(' ')}
                  >
                    <input
                      type="checkbox"
                      checked={seleccion.has(permiso.codigo)}
                      disabled={deshabilitada}
                      onChange={(evento) => onAlternar(permiso.codigo, evento.target.checked)}
                      className="mt-0.5 size-[1.125rem] shrink-0 accent-primary disabled:cursor-not-allowed"
                    />
                    <span className="flex min-w-0 flex-col gap-0.5">
                      <span className="text-body-md text-text-main">{permiso.descripcion}</span>
                      <span className="break-all font-mono text-body-sm text-text-muted">
                        {permiso.codigo}
                      </span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}
