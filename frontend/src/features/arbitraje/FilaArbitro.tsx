import { Building2, CalendarDays, CircleCheck, CircleX, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import {
  etiquetaEstadoArbitro,
  inicialesArbitro,
  nombrePerfilArbitro,
} from '@/features/arbitraje/etiquetas'
import type { Arbitro, DecisionArbitro } from '@/features/arbitraje/tipos'
import { formatearFechaHora } from '@/features/temporadas/fechas'

type Props = {
  arbitro: Arbitro
  puedeValidar: boolean
  onDictamen: (arbitro: Arbitro, decision: DecisionArbitro) => void
}

const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

/**
 * Fila de la tabla de árbitros (F-20, RF-021). Bajo `lg` se apila como tarjeta para no exigir
 * desplazamiento horizontal (RNF-007). Las acciones solo se ofrecen en `PROPUESTO` y con el permiso
 * de validar; el backend vuelve a decidir (VAL-015).
 */
export function FilaArbitro({ arbitro, puedeValidar, onDictamen }: Props) {
  const estado = etiquetaEstadoArbitro[arbitro.estado]
  const mostrarAcciones = puedeValidar && arbitro.estado === 'PROPUESTO'

  return (
    <tr
      role="row"
      className="block border-b border-border p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:border-b lg:p-0"
    >
      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Árbitro</span>
        <div className="flex min-w-0 items-start gap-3">
          <span
            aria-hidden="true"
            className="grid size-10 shrink-0 place-items-center rounded-md border border-border bg-background text-label-md font-semibold text-text-main"
          >
            {inicialesArbitro(arbitro.persona.nombreCompleto)}
          </span>
          <div className="min-w-0">
            <p className="break-words text-label-md font-semibold text-text-main">
              {arbitro.persona.nombreCompleto}
            </p>
            <p className="flex items-center gap-1 text-body-sm text-text-muted">
              <UserRound aria-hidden="true" className="size-3.5 shrink-0" />
              Presentado por {nombrePerfilArbitro(arbitro.propuestoPor)}
            </p>
          </div>
        </div>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Club que lo presenta</span>
        <span className="flex items-center gap-1.5 text-body-md text-text-main">
          <Building2 aria-hidden="true" className="size-4 shrink-0 text-text-muted" />
          <span className="break-words">{arbitro.club.nombre}</span>
        </span>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Propuesta</span>
        <span className="flex items-center gap-1.5 font-mono text-mono-data text-text-main">
          <CalendarDays aria-hidden="true" className="size-3.5 shrink-0 text-text-muted" />
          {formatearFechaHora(arbitro.propuestoEn)}
        </span>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Estado</span>
        <div className="flex flex-col items-start gap-1">
          <Insignia variante={estado.variante} icono={estado.icono}>
            {estado.texto}
          </Insignia>
          {arbitro.validadoEn && (
            <span className="text-body-sm text-text-muted">
              {arbitro.estado === 'VALIDADO' ? 'Validado' : 'Rechazado'} el{' '}
              {formatearFechaHora(arbitro.validadoEn)}
            </span>
          )}
          {arbitro.observaciones && (
            <span className="line-clamp-2 max-w-xs text-body-sm text-text-muted">
              {arbitro.observaciones}
            </span>
          )}
        </div>
      </td>

      <td role="cell" className="block pt-3 lg:table-cell lg:px-4 lg:py-3 lg:text-right">
        <span className={CLASE_ETIQUETA}>Acciones</span>
        {mostrarAcciones ? (
          <div className="flex flex-col gap-2 sm:flex-row lg:justify-end">
            <Button
              icono={CircleCheck}
              aria-label={`Validar a ${arbitro.persona.nombreCompleto}`}
              onClick={() => onDictamen(arbitro, 'VALIDADO')}
            >
              Validar
            </Button>
            <Button
              variante="destructivo"
              icono={CircleX}
              aria-label={`Rechazar a ${arbitro.persona.nombreCompleto}`}
              onClick={() => onDictamen(arbitro, 'RECHAZADO')}
            >
              Rechazar
            </Button>
          </div>
        ) : (
          <span className="text-body-sm text-text-muted">
            {arbitro.estado === 'PROPUESTO'
              ? 'Espera el dictamen de la Comisión'
              : 'Sin acciones pendientes'}
          </span>
        )}
      </td>
    </tr>
  )
}
