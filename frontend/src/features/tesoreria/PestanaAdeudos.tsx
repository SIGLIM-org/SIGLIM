import { Building2, Shield, Wallet } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { contarCobros, formatearMonto } from '@/features/tesoreria/fechas'
import type { Adeudos } from '@/features/tesoreria/tipos'

type Props = {
  adeudos?: Adeudos
  cargando: boolean
  error: unknown
  onReintentar?: () => void
}

function cantidadClubes(total: number): string {
  return total === 1 ? '1 club con adeudo' : `${total} clubes con adeudo`
}

/**
 * Pestaña «Adeudos por club» de F-33 (H-29.2, RF-140). Solo muestra los clubes y equipos con
 * adeudo que devuelve `GET /adeudos`; un club sin saldo no aparece. No hay retenciones ni
 * sanciones configurables que el ERS no defina.
 */
export function PestanaAdeudos({ adeudos, cargando, error, onReintentar }: Props) {
  if (cargando) {
    return <EstadoCarga etiqueta="Cargando adeudos…" filas={4} columnas={3} />
  }

  if (error) {
    return <ErrorConsulta error={error} onReintentar={onReintentar} />
  }

  if (!adeudos || adeudos.clubes.length === 0) {
    return (
      <EstadoVacio
        icono={Wallet}
        titulo="Sin adeudos registrados"
        descripcion="Los cobros pendientes o vencidos aparecerán aquí."
      />
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <p aria-live="polite" className="text-label-md text-text-main">
        {cantidadClubes(adeudos.clubes.length)} · {contarCobros(adeudos.cobros)} por cobrar
      </p>
      <div className="grid gap-4 xl:grid-cols-2">
        {adeudos.clubes.map((club) => (
          <article
            key={club.club.id}
            className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4"
          >
            <header className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex min-w-0 items-start gap-3">
                <span
                  aria-hidden="true"
                  className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary"
                >
                  <Building2 className="size-5" />
                </span>
                <div className="min-w-0">
                  <h2 className="break-words text-headline-sm text-text-main">
                    {club.club.nombre}
                  </h2>
                  <p className="text-body-sm text-text-muted">
                    {contarCobros(club.cobros)} con saldo
                  </p>
                </div>
              </div>
              <div className="text-right">
                <p className="text-label-sm text-text-muted uppercase">Adeudo del club</p>
                <p className="font-mono text-mono-score tabular-nums text-text-main">
                  {formatearMonto(club.total)}
                </p>
              </div>
            </header>

            <dl className="grid grid-cols-2 gap-3 rounded-md bg-background p-3">
              <div>
                <dt className="text-label-sm text-text-muted uppercase">Pendiente</dt>
                <dd className="mt-0.5 font-mono text-mono-data tabular-nums text-text-main">
                  {formatearMonto(club.pendiente)}
                </dd>
              </div>
              <div>
                <dt className="text-label-sm text-text-muted uppercase">Vencido</dt>
                <dd className="mt-0.5 font-mono text-mono-data tabular-nums text-error-strong">
                  {formatearMonto(club.vencido)}
                </dd>
              </div>
            </dl>

            {club.equipos.length > 0 ? (
              <section aria-label={`Equipos con adeudo de ${club.club.nombre}`}>
                <h3 className="mb-2 text-label-sm text-text-muted uppercase">
                  Equipos con adeudo
                </h3>
                <ul className="flex flex-col divide-y divide-border">
                  {club.equipos.map((equipo) => (
                    <li
                      key={equipo.equipo.id}
                      className="flex flex-wrap items-start justify-between gap-2 py-2 first:pt-0 last:pb-0"
                    >
                      <div className="flex min-w-0 items-start gap-2">
                        <Shield
                          aria-hidden="true"
                          className="mt-0.5 size-4 shrink-0 text-text-muted"
                        />
                        <div className="min-w-0">
                          <p className="break-words text-body-md text-text-main">
                            {equipo.equipo.nombre}
                          </p>
                          <p className="text-body-sm text-text-muted">
                            {contarCobros(equipo.cobros)}
                            {equipo.vencido > 0
                              ? ` · Vencido ${formatearMonto(equipo.vencido)}`
                              : ''}
                          </p>
                        </div>
                      </div>
                      <p className="font-mono text-mono-data tabular-nums text-text-main">
                        {formatearMonto(equipo.total)}
                      </p>
                    </li>
                  ))}
                </ul>
              </section>
            ) : (
              <p className="text-body-sm text-text-muted">
                El adeudo está cargado al club, sin equipos con saldo.
              </p>
            )}
          </article>
        ))}
      </div>
    </div>
  )
}
