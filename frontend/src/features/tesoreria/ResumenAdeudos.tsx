import { Building2, CircleAlert, Clock3, Wallet, type LucideIcon } from 'lucide-react'
import { contarCobros, formatearMonto } from '@/features/tesoreria/fechas'
import type { Adeudos } from '@/features/tesoreria/tipos'

type Props = {
  adeudos?: Adeudos
  cargando: boolean
}

type Tarjeta = {
  etiqueta: string
  valor: string
  detalle: string
  icono: LucideIcon
  claseIcono: string
  claseValor?: string
}

function tarjetasDe(adeudos: Adeudos): Tarjeta[] {
  const clubes = adeudos.clubes.length
  const hayVencido = adeudos.vencido > 0

  return [
    {
      etiqueta: 'Adeudo total',
      valor: formatearMonto(adeudos.total),
      detalle: `${contarCobros(adeudos.cobros)} por cobrar`,
      icono: Wallet,
      claseIcono: 'bg-primary/10 text-primary',
    },
    {
      etiqueta: 'Pendiente',
      valor: formatearMonto(adeudos.pendiente),
      detalle: adeudos.pendiente > 0 ? 'Dentro del plazo' : 'Sin pendientes',
      icono: Clock3,
      claseIcono: 'bg-info/12 text-info-strong',
    },
    {
      etiqueta: 'Vencido',
      valor: formatearMonto(adeudos.vencido),
      detalle: hayVencido ? 'Fuera del plazo' : 'Sin vencidos',
      icono: CircleAlert,
      claseIcono: hayVencido
        ? 'bg-error/10 text-error-strong'
        : 'bg-background text-text-muted',
      claseValor: hayVencido ? 'text-error-strong' : undefined,
    },
    {
      etiqueta: 'Clubes con adeudo',
      valor: String(clubes),
      detalle:
        clubes === 0
          ? 'Sin clubes con saldo'
          : clubes === 1
            ? 'Un club con saldo'
            : 'Clubes con saldo',
      icono: Building2,
      claseIcono: 'bg-background text-text-muted',
    },
  ]
}

/**
 * Tarjetas de resumen de F-33 (H-29.2, RF-140). Solo usa los totales que devuelve `GET /adeudos`:
 * no hay cifras de «cobrado» ni cortes que la API no exponga.
 */
export function ResumenAdeudos({ adeudos, cargando }: Props) {
  if (cargando && !adeudos) {
    return (
      <section aria-label="Resumen de adeudos" aria-busy="true">
        <span className="sr-only">Cargando adeudos…</span>
        <div
          aria-hidden="true"
          className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"
        >
          {Array.from({ length: 4 }, (_, indice) => (
            <div
              key={indice}
              className="animate-pulse rounded-lg border border-border bg-surface p-4 motion-reduce:animate-none"
            >
              <div className="h-3 w-24 rounded-sm bg-border" />
              <div className="mt-4 h-7 w-32 rounded-sm bg-border/60" />
              <div className="mt-3 h-3 w-20 rounded-sm bg-border/60" />
            </div>
          ))}
        </div>
      </section>
    )
  }

  if (!adeudos) {
    return null
  }

  return (
    <section aria-label="Resumen de adeudos">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {tarjetasDe(adeudos).map(({ etiqueta, valor, detalle, icono: Icono, claseIcono, claseValor }) => (
          <article
            key={etiqueta}
            className="rounded-lg border border-border bg-surface p-4"
          >
            <div className="flex items-start justify-between gap-2">
              <h2 className="text-label-sm text-text-muted uppercase">{etiqueta}</h2>
              <span
                aria-hidden="true"
                className={`grid size-8 shrink-0 place-items-center rounded-md ${claseIcono}`}
              >
                <Icono className="size-4" />
              </span>
            </div>
            <p
              className={[
                'mt-3 font-mono text-mono-score tabular-nums',
                claseValor ?? 'text-text-main',
              ].join(' ')}
            >
              {valor}
            </p>
            <p className="mt-1 text-body-sm text-text-muted">{detalle}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
