import { TriangleAlert } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { useCuotaArbitros } from '@/features/arbitraje/api'
import type { ClubParaArbitro } from '@/features/arbitraje/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  club: ClubParaArbitro
}

/**
 * Celda «Cumple cuota» de F-20 en la vista de la Comisión (B-08.2, RF-114). Consulta el indicador
 * del club que presenta al árbitro y solo lo muestra: no cambia el estado ni bloquea el dictamen.
 * Las consultas del mismo club comparten la clave de TanStack Query, así que se piden una vez.
 */
export function CeldaCumpleCuota({ club }: Props) {
  const cuota = useCuotaArbitros(club.id)

  if (cuota.isPending) {
    return (
      <span className="inline-flex min-h-6 items-center">
        <span className="sr-only">Consultando la cuota de {club.nombre}…</span>
        <span
          aria-hidden="true"
          className="h-3 w-20 animate-pulse rounded-sm bg-border motion-reduce:animate-none"
        />
      </span>
    )
  }

  if (cuota.isError || !cuota.data) {
    const detalle =
      cuota.error instanceof ApiError
        ? cuota.error.problema.detail
        : 'No se pudo consultar la cuota del club.'

    return (
      <span title={detalle}>
        <Insignia variante="neutra">Sin dato</Insignia>
      </span>
    )
  }

  const { requeridos, validados, cumple } = cuota.data.cuotaArbitros

  if (requeridos === 0) {
    return (
      <span className="inline-flex" title="Sin equipos activos">
        <Insignia variante="neutra">Sin cuota</Insignia>
        <span className="sr-only">El club no tiene equipos activos</span>
      </span>
    )
  }

  return (
    <span
      className="inline-flex"
      title={`${validados} de ${requeridos} árbitros validados`}
    >
      <Insignia
        variante={cumple ? 'exito' : 'advertencia'}
        icono={cumple ? undefined : TriangleAlert}
      >
        {cumple ? 'Cumple' : 'No cumple'}
      </Insignia>
      <span className="sr-only">
        {validados} de {requeridos} árbitros validados
      </span>
    </span>
  )
}
