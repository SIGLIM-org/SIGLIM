import { Link } from 'react-router'
import { ArrowRight, CircleAlert, RotateCw, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { useAdeudos } from '@/features/tesoreria/api'
import { contarCobros, formatearMonto } from '@/features/tesoreria/fechas'
import { ApiError } from '@/lib/api/ApiError'
import { reintentarConsulta } from '@/lib/api/errores'

function cantidadClubes(total: number): string {
  return total === 1 ? '1 club' : `${total} clubes`
}

/**
 * Tarjeta de adeudos de F-08 (H-29.2, RF-140) reutilizada en Inicio. Muestra solo el total
 * pendiente y vencido que devuelve `GET /adeudos`; no ofrece pagar porque eso es H-30.
 */
export function TarjetaAdeudos() {
  const consulta = useAdeudos()
  const adeudos = consulta.data
  const error = consulta.error

  return (
    <section
      aria-label="Tesorería y cuotas"
      className="flex h-full flex-col justify-between rounded-lg border border-border bg-surface p-4"
    >
      <div>
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-label-md text-text-main">Tesorería y cuotas</h2>
            <p className="mt-0.5 text-body-sm text-text-muted">
              Adeudos pendientes y vencidos de tu ámbito.
            </p>
          </div>
          <span
            aria-hidden="true"
            className="grid size-10 shrink-0 place-items-center rounded-md bg-warning/14 text-warning-strong"
          >
            <Wallet className="size-5" />
          </span>
        </div>

        {consulta.isPending && (
          <p role="status" className="mt-4 text-body-md text-text-muted">
            Cargando adeudos…
          </p>
        )}

        {consulta.isError && (
          <div role="alert" className="mt-4 rounded-md bg-error/8 p-3">
            <p className="text-body-md text-error-strong">
              {error instanceof ApiError
                ? error.problema.detail
                : 'No se pudieron cargar los adeudos.'}
            </p>
            {reintentarConsulta(0, error) && (
              <Button
                variante="secundario"
                icono={RotateCw}
                onClick={() => void consulta.refetch()}
                className="mt-2"
              >
                Reintentar
              </Button>
            )}
          </div>
        )}

        {adeudos && (
          <div className="mt-4">
            <p className="font-mono text-mono-score tabular-nums text-text-main">
              {formatearMonto(adeudos.total)}
            </p>
            <p className="mt-1 text-body-sm text-text-muted">
              {adeudos.total === 0
                ? 'Sin adeudos registrados.'
                : `${contarCobros(adeudos.cobros)} en ${cantidadClubes(
                    adeudos.clubes.length,
                  )}.`}
            </p>
            {adeudos.vencido > 0 && (
              <p className="mt-2 flex items-center gap-1.5 text-body-sm text-error-strong">
                <CircleAlert aria-hidden="true" className="size-4 shrink-0" />
                Vencido: {formatearMonto(adeudos.vencido)}
              </p>
            )}
          </div>
        )}
      </div>

      <Link
        to="/app/tesoreria"
        className={`${clasesBoton('secundario')} mt-4 w-full`}
      >
        Ver tesorería
        <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
      </Link>
    </section>
  )
}
