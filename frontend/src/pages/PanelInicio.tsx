import { Link } from 'react-router'
import { ArrowRight, IdCard } from 'lucide-react'
import { RUTA_MI_INFORMACION } from '@/features/registros/rutas'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { TarjetaAdeudos } from '@/features/tesoreria/TarjetaAdeudos'
import { tienePermisoPropio } from '@/layouts/navegacion'

/** Enlace a la consulta propia del jugador o tutor (DEC-147); sin redirección automática. */
function TarjetaMiInformacion() {
  return (
    <Link
      to={RUTA_MI_INFORMACION}
      className="flex min-h-11 items-center gap-3 rounded-lg border border-border bg-surface p-4 transition-colors hover:bg-background"
    >
      <span
        aria-hidden="true"
        className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary"
      >
        <IdCard className="size-5" />
      </span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="text-label-md text-text-main">Mi información</span>
        <span className="text-body-sm text-text-muted">
          Tu registro, documentos, partidos y sanciones
        </span>
      </span>
      <ArrowRight aria-hidden="true" className="size-5 shrink-0 text-text-muted" />
    </Link>
  )
}

/**
 * Inicio de la aplicación interna (F-08, H-29.2). Reúne la tarjeta de adeudos de tesorería para
 * quien puede consultarlos y, para el jugador o tutor, el enlace a «Mi información» (DEC-147); el
 * resto de indicadores de F-08 llega con sus módulos.
 */
export function PanelInicio() {
  const { yo } = useSesionActual()
  const conConsultaPropia = tienePermisoPropio(yo.permisos, 'registros.registro:CONSULTAR')

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Inicio</h1>
        <p className="mt-1 max-w-2xl text-body-md text-text-muted">
          Resumen de tu operación en la Liga. Usa el Menú Ejecutivo para abrir los módulos de tu
          cuenta.
        </p>
      </header>

      {conConsultaPropia && (
        <section aria-label="Tu información" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <TarjetaMiInformacion />
        </section>
      )}

      <ConPermiso permiso="tesoreria.cobro:CONSULTAR">
        <section
          aria-label="Resumen de tesorería"
          className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"
        >
          <TarjetaAdeudos />
        </section>
      </ConPermiso>
    </div>
  )
}
