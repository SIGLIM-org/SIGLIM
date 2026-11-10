import { Link } from 'react-router'
import { House, Lock } from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'

type Props = {
  /** A dónde lleva «Volver al inicio»: `/app` dentro de la aplicación interna. */
  rutaInicio?: string
}

/**
 * Página 403 (F-36) para acciones denegadas por falta de permiso (VAL-015).
 * La usa `RutaConPermiso` cuando la URL de un módulo no está permitida (H-02.4).
 */
export function PaginaSinPermiso({ rutaInicio = '/' }: Props) {
  return (
    <section className="mx-auto flex max-w-xl flex-col items-center gap-4 px-4 py-16 text-center">
      <Lock aria-hidden="true" className="size-12 text-text-muted" />
      <p className="font-mono text-mono-data text-text-muted">Error 403</p>
      <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
        No tienes permiso para esta acción
      </h1>
      <p className="text-body-lg text-text-muted">
        Si crees que deberías tenerlo, pide a la administración de la Liga que revise tus
        permisos.
      </p>
      <Link to={rutaInicio} className={`${clasesBoton('primario')} mt-2`}>
        <House aria-hidden="true" className="size-4 shrink-0" />
        Volver al inicio
      </Link>
    </section>
  )
}
