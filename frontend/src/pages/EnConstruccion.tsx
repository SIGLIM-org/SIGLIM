import { Link } from 'react-router'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import type { SubseccionApp } from '@/layouts/navegacion'

type Props = {
  titulo: string
  descripcion: string
  /** Pantallas ya disponibles de la sección, cada una según su permiso. */
  subsecciones?: readonly SubseccionApp[]
}

/** Marcador provisional de las rutas; se sustituye en T-03.2 y las historias de cada módulo. */
export function EnConstruccion({ titulo, descripcion, subsecciones = [] }: Props) {
  return (
    <main className="min-h-dvh bg-background px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">{titulo}</h1>
      <p className="mt-2 text-body-md text-text-muted">{descripcion}</p>
      {subsecciones.length > 0 && (
        <ul className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {subsecciones.map((subseccion) => (
            <ConPermiso key={subseccion.ruta} permiso={subseccion.permiso}>
              <li>
                <Link
                  to={subseccion.ruta}
                  className="flex min-h-11 flex-col gap-1 rounded-lg border border-border bg-surface p-4 transition-colors hover:bg-background"
                >
                  <span className="text-headline-sm text-text-main">{subseccion.nombre}</span>
                  <span className="text-body-md text-text-muted">{subseccion.descripcion}</span>
                </Link>
              </li>
            </ConPermiso>
          ))}
        </ul>
      )}
    </main>
  )
}
