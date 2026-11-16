import { ArrowRight, CheckCircle2, Lock, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import {
  etiquetaEstadoOperacion,
  etiquetaTipoOperacion,
  fechaDeOperacion,
  siguientePaso,
} from '@/features/organizacion/operaciones/etiquetas'
import type { OperacionEquipo } from '@/features/organizacion/operaciones/tipos'

type Props = {
  operacion: OperacionEquipo
  conAcciones: boolean
  onAutorizar: (operacion: OperacionEquipo) => void
  onRechazar: (operacion: OperacionEquipo) => void
}

const CLASE_CELDA = 'block py-1.5 lg:table-cell lg:px-3 lg:py-3 lg:align-top'
const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

/**
 * Fila de la bandeja de operaciones de equipo (B-09, UI-034). En móvil se lee como una tarjeta con
 * etiquetas; en escritorio, como una tabla densa. Solo una operación SOLICITADA y de una temporada
 * abierta ofrece los botones del dictamen (RN-040, RF-003).
 */
export function FilaOperacion({ operacion, conAcciones, onAutorizar, onRechazar }: Props) {
  const estado = etiquetaEstadoOperacion[operacion.estado]
  const tipo = etiquetaTipoOperacion[operacion.tipo]
  const IconoTipo = tipo.icono
  const temporadaCerrada =
    operacion.temporada.estado === 'CERRADA' && operacion.estado === 'SOLICITADA'
  const resoluble = conAcciones && operacion.estado === 'SOLICITADA' && !temporadaCerrada
  const fechaEstado = operacion.aplicadaEn ?? operacion.autorizadaEn

  return (
    <tr className="block border-b border-border p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:p-0">
      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Operación</span>
        <span className="flex items-start gap-2">
          <IconoTipo aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-primary" />
          <span className="min-w-0">
            <span className="block break-words text-headline-sm text-text-main">{tipo.texto}</span>
            <span className="block break-words text-body-sm text-text-muted">
              {operacion.equipo.nombre}
            </span>
          </span>
        </span>
      </td>

      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Clubes</span>
        <span className="block break-words text-body-md text-text-main">
          {operacion.clubOrigen.nombre}
        </span>
        {operacion.tipo === 'VENTA' && operacion.clubDestino && (
          <span className="mt-0.5 flex items-start gap-1 text-body-sm text-text-muted">
            <ArrowRight aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            <span className="break-words">{operacion.clubDestino.nombre}</span>
          </span>
        )}
      </td>

      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Temporada</span>
        <span className="block break-words text-body-md text-text-main">
          {operacion.temporada.nombre}
        </span>
        <span className="block text-body-sm text-text-muted">
          {operacion.temporada.estado === 'CERRADA' ? 'Cerrada' : 'Abierta'}
        </span>
      </td>

      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Solicitud</span>
        <span className="block text-body-md text-text-main">
          {fechaDeOperacion(operacion.solicitadaEn)}
        </span>
        <span className="block break-words text-body-sm text-text-muted">
          {operacion.solicitadaPor.nombreMostrado ?? 'Perfil sin nombre visible'}
        </span>
      </td>

      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Estado</span>
        <Insignia variante={estado.variante} icono={estado.icono}>
          {estado.texto}
        </Insignia>
        <span className="mt-1 block max-w-prose break-words text-body-sm text-text-muted">
          {siguientePaso(operacion)}
        </span>
        {fechaEstado && (
          <span className="mt-0.5 block text-body-sm text-text-muted">
            {operacion.estado === 'APLICADA' ? 'Aplicada el ' : 'Resuelta el '}
            {fechaDeOperacion(fechaEstado)}
          </span>
        )}
      </td>

      {conAcciones && (
        <td className={`${CLASE_CELDA} pt-3 lg:text-right`}>
          <span className={CLASE_ETIQUETA}>Acciones</span>
          {resoluble ? (
            <div className="flex flex-col items-start gap-2 lg:items-end xl:flex-row xl:justify-end">
              <Button
                variante="secundario"
                icono={CheckCircle2}
                aria-label={`Autorizar la operación de ${operacion.equipo.nombre}`}
                onClick={() => onAutorizar(operacion)}
              >
                Autorizar
              </Button>
              <Button
                variante="secundario"
                icono={XCircle}
                aria-label={`Rechazar la operación de ${operacion.equipo.nombre}`}
                onClick={() => onRechazar(operacion)}
              >
                Rechazar
              </Button>
            </div>
          ) : temporadaCerrada ? (
            <p className="flex items-start gap-1.5 text-body-sm text-text-muted lg:justify-end">
              <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              Temporada cerrada: ya no se puede dictaminar.
            </p>
          ) : (
            <span className="block text-body-sm text-text-muted lg:text-right">
              {operacion.estado === 'SOLICITADA' ? 'Pendiente de dictamen' : 'Sin acciones'}
            </span>
          )}
        </td>
      )}
    </tr>
  )
}
