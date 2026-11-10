import { ShieldCheck } from 'lucide-react'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { Insignia } from '@/components/ui/Insignia'
import type { RolCatalogo } from '@/features/roles/tipos'

type Props = {
  roles: readonly RolCatalogo[]
  seleccionado: string | null
  onSeleccionar: (id: string) => void
}

/**
 * Selector de rol de F-13: lista lateral en escritorio y lista desplegable en móvil, para que la
 * matriz no quede detrás de una columna de 12 roles (RNF-007).
 */
export function ListaRoles({ roles, seleccionado, onSeleccionar }: Props) {
  return (
    <div className="flex min-w-0 flex-col gap-3">
      <div className="lg:hidden">
        <CampoSeleccion
          id="rol-matriz-movil"
          etiqueta="Rol que quieres revisar"
          value={seleccionado ?? ''}
          onChange={(evento) => onSeleccionar(evento.target.value)}
        >
          {roles.map((rol) => (
            <option key={rol.id} value={rol.id}>
              {rol.nombre}
            </option>
          ))}
        </CampoSeleccion>
      </div>

      <section aria-labelledby="titulo-roles" className="hidden lg:flex lg:flex-col lg:gap-3">
        <div className="flex items-baseline justify-between gap-2">
          <h2 id="titulo-roles" className="text-headline-sm text-text-main">
            Roles
          </h2>
          <span className="font-mono text-mono-data text-text-muted">{roles.length}</span>
        </div>

        <ul className="flex flex-col gap-1.5">
          {roles.map((rol) => {
            const activo = rol.id === seleccionado

            return (
              <li key={rol.id}>
                <button
                  type="button"
                  aria-current={activo ? 'true' : undefined}
                  onClick={() => onSeleccionar(rol.id)}
                  className={[
                    'flex w-full flex-col gap-1 rounded-lg border p-3 text-left transition-colors',
                    activo
                      ? 'border-primary bg-primary/5'
                      : 'border-border bg-surface hover:bg-background',
                  ].join(' ')}
                >
                  <span className="flex items-start gap-2">
                    <ShieldCheck
                      aria-hidden="true"
                      className={[
                        'mt-0.5 size-4 shrink-0',
                        activo ? 'text-primary' : 'text-text-muted',
                      ].join(' ')}
                    />
                    <span className="min-w-0 text-label-md text-text-main">{rol.nombre}</span>
                  </span>
                  <span className="break-all pl-6 font-mono text-body-sm text-text-muted">
                    {rol.codigo}
                  </span>
                  {!rol.asignable && (
                    <span className="pl-6">
                      <Insignia variante="neutra">No asignable</Insignia>
                    </span>
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      </section>
    </div>
  )
}
