import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { TarjetaAdeudos } from '@/features/tesoreria/TarjetaAdeudos'

/**
 * Inicio de la aplicación interna (F-08, H-29.2). Por ahora reúne la tarjeta de adeudos de
 * tesorería para quien puede consultarlos; el resto de indicadores de F-08 llega con sus módulos.
 */
export function PanelInicio() {
  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Inicio</h1>
        <p className="mt-1 max-w-2xl text-body-md text-text-muted">
          Resumen de tu operación en la Liga. Usa el Menú Ejecutivo para abrir los módulos de tu
          cuenta.
        </p>
      </header>

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
