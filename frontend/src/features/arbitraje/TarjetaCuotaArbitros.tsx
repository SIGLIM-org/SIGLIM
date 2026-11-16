import { useId } from 'react'
import { CircleCheck, TriangleAlert } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { Insignia } from '@/components/ui/Insignia'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { useCuotaArbitros } from '@/features/arbitraje/api'
import type { ClubParaArbitro } from '@/features/arbitraje/tipos'

type Props = {
  club: ClubParaArbitro
}

/**
 * Indicador de cuota de la vista del club en F-20 (B-08.2, RF-114, DEC-17). Muestra los árbitros
 * validados frente a los requeridos y advierte cuando no alcanza; nunca bloquea. El divisor es el
 * parámetro `[DEMO]` de la temporada, por eso el valor se rotula «No oficial» (DEC-72).
 */
export function TarjetaCuotaArbitros({ club }: Props) {
  const id = useId()
  const cuota = useCuotaArbitros(club.id)
  const resumen = cuota.data?.cuotaArbitros
  const requeridos = resumen?.requeridos ?? 0
  const validados = resumen?.validados ?? 0
  const cumple = resumen?.cumple ?? false
  const faltan = Math.max(0, requeridos - validados)
  const porcentaje =
    requeridos === 0 ? 0 : Math.min(100, Math.round((validados / requeridos) * 100))

  return (
    <section
      aria-labelledby={`${id}-titulo`}
      className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4"
    >
      <div className="flex flex-col gap-1">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <h2 id={`${id}-titulo`} className="text-headline-sm text-text-main">
            Cuota de árbitros
          </h2>
          <InsigniaNoOficial />
        </div>
        <p className="text-body-sm text-text-muted">
          {club.nombre}
          {cuota.data ? ` · ${cuota.data.temporada.nombre}` : ''}
        </p>
      </div>

      {cuota.isPending && (
        <div className="flex flex-col gap-2" role="status">
          <span className="sr-only">Cargando la cuota de árbitros…</span>
          <span
            aria-hidden="true"
            className="h-3 w-4/5 animate-pulse rounded-sm bg-border motion-reduce:animate-none"
          />
          <span
            aria-hidden="true"
            className="h-10 w-full animate-pulse rounded-md bg-border/60 motion-reduce:animate-none"
          />
          <span
            aria-hidden="true"
            className="h-3 w-3/5 animate-pulse rounded-sm bg-border/60 motion-reduce:animate-none"
          />
        </div>
      )}

      {cuota.isError && (
        <ErrorConsulta error={cuota.error} onReintentar={() => void cuota.refetch()} />
      )}

      {cuota.data && (
        <>
          <p className="text-body-sm text-text-muted">
            La Liga pide 1 árbitro validado por cada {cuota.data.equiposPorArbitro}{' '}
            {cuota.data.equiposPorArbitro === 1 ? 'equipo activo' : 'equipos activos'} del club,
            con redondeo hacia arriba.
          </p>

          {requeridos === 0 ? (
            <p className="rounded-md bg-background p-3 text-body-sm text-text-muted">
              El club no tiene equipos activos en esta temporada, así que todavía no hay una cuota
              que cumplir.
            </p>
          ) : (
            <>
              <div className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-background p-3">
                <p className="text-body-md text-text-main">
                  Árbitros validados:{' '}
                  <strong>
                    {validados} de {requeridos} requeridos
                  </strong>
                </p>
                <Insignia
                  variante={cumple ? 'exito' : 'advertencia'}
                  icono={cumple ? CircleCheck : TriangleAlert}
                >
                  {cumple ? 'Cumple' : 'No cumple'}
                </Insignia>
              </div>

              <div
                role="progressbar"
                aria-label={`${validados} de ${requeridos} árbitros validados`}
                aria-valuemin={0}
                aria-valuemax={requeridos}
                aria-valuenow={Math.min(validados, requeridos)}
                className="h-1.5 w-full overflow-hidden rounded-full bg-border"
              >
                <div
                  className={`h-full rounded-full ${cumple ? 'bg-success' : 'bg-warning'}`}
                  style={{ width: `${porcentaje}%` }}
                />
              </div>

              {cumple ? (
                <p className="flex items-start gap-2 text-body-sm text-text-main">
                  <CircleCheck
                    aria-hidden="true"
                    className="mt-0.5 size-4 shrink-0 text-success"
                  />
                  El club cumple la cuota de árbitros validados.
                </p>
              ) : (
                <p className="flex items-start gap-2 rounded-md bg-warning/14 p-3 text-body-sm text-warning-strong">
                  <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                  No cumple la cuota:{' '}
                  {faltan === 1
                    ? 'falta 1 árbitro validado.'
                    : `faltan ${faltan} árbitros validados.`}
                </p>
              )}
            </>
          )}

          <p className="text-body-sm text-text-muted">
            Indicador calculado de la temporada: solo informa, no bloquea la validación ni la
            designación.
          </p>
        </>
      )}
    </section>
  )
}
