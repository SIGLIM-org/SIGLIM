import { Link } from 'react-router'
import { CircleAlert, Clock, Eye, MapPin, Pencil, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { Insignia } from '@/components/ui/Insignia'
import {
  etiquetaConflicto,
  etiquetaEstadoPartido,
  varianteEstadoPartido,
} from '@/features/programacion/etiquetas'
import { formatearFechaCorta, horarioPartido } from '@/features/programacion/fechas'
import type { AvisoVerificacion, Partido } from '@/features/programacion/tipos'

type Props = {
  partido: Partido
  /** Posición en la jornada, solo para orientar a la persona. */
  numero: number
  /** Conflictos duros que impiden enviar la jornada (H-12.4). */
  avisos: readonly AvisoVerificacion[]
  /** Advertencias de localía: no bloquean el envío y se aceptan con motivo (B-15.2, VAL-027). */
  advertencias: readonly AvisoVerificacion[]
  puedeEditar: boolean
  onEditar: (partido: Partido) => void
  onAceptarLocalia: (partido: Partido) => void
}

/**
 * Un partido de la jornada (F-22). Si la verificación encontró conflictos, la fila se marca y
 * explica cada uno debajo del encuentro (H-12.4). Las advertencias de localía van en una franja
 * aparte, en tono ámbar: se aceptan con motivo y no bloquean el envío (B-15.2, RF-120).
 */
export function FilaPartido({
  partido,
  numero,
  avisos,
  advertencias,
  puedeEditar,
  onEditar,
  onAceptarLocalia,
}: Props) {
  const tieneConflictos = avisos.length > 0
  const localiaAceptada = Boolean(partido.motivoLocalia)
  const advertenciaPendiente = advertencias.length > 0 && !localiaAceptada

  return (
    <li
      className={[
        'overflow-hidden rounded-lg border bg-surface',
        tieneConflictos
          ? 'border-error/40'
          : advertenciaPendiente
            ? 'border-warning/40'
            : 'border-border',
      ].join(' ')}
    >
      <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <p className="text-label-sm uppercase tracking-wide text-text-muted">Partido {numero}</p>
          <p className="mt-0.5 break-words text-headline-sm text-text-main">
            {partido.equipoLocal.nombre}{' '}
            <span className="font-normal text-text-muted">vs.</span>{' '}
            {partido.equipoVisitante.nombre}
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-body-md text-text-muted">
            <span className="inline-flex items-center gap-1.5">
              <Clock aria-hidden="true" className="size-4 shrink-0" />
              <span>
                {formatearFechaCorta(partido.inicio)} · {horarioPartido(partido)}
              </span>
            </span>
            <span className="inline-flex min-w-0 items-center gap-1.5">
              <MapPin aria-hidden="true" className="size-4 shrink-0" />
              <span className="break-words">{partido.cancha.nombre}</span>
            </span>
            <Insignia variante={varianteEstadoPartido[partido.estado]}>
              {etiquetaEstadoPartido[partido.estado]}
            </Insignia>
          </div>
        </div>

        <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
          {/* F-25 (H-15.3): estado, historial y acciones del partido. */}
          <ConPermiso permiso="partidos.partido:CONSULTAR">
            <Link
              to={`/app/partidos/${partido.id}`}
              className={`${clasesBoton('secundario')} w-full sm:w-auto`}
              aria-label={`Ver el detalle del partido ${partido.equipoLocal.nombre} vs. ${partido.equipoVisitante.nombre}`}
            >
              <Eye aria-hidden="true" className="size-4 shrink-0" />
              Ver detalle
            </Link>
          </ConPermiso>
          {puedeEditar && (
            <Button
              variante="secundario"
              icono={Pencil}
              onClick={() => onEditar(partido)}
              className="w-full sm:w-auto"
              aria-label={`Editar el partido ${partido.equipoLocal.nombre} vs. ${partido.equipoVisitante.nombre}`}
            >
              Editar
            </Button>
          )}
        </div>
      </div>

      {tieneConflictos && (
        <div
          role="group"
          aria-label={`Conflictos del partido ${partido.equipoLocal.nombre} vs. ${partido.equipoVisitante.nombre}`}
          className="border-t border-error/20 bg-error/5 px-4 py-3"
        >
          <p className="flex items-center gap-2 text-label-md font-medium text-error-strong">
            <CircleAlert aria-hidden="true" className="size-4 shrink-0" />
            {avisos.length === 1 ? '1 conflicto por resolver' : `${avisos.length} conflictos por resolver`}
          </p>
          <ul className="mt-2 flex flex-col gap-2">
            {avisos.map((aviso, indice) => (
              <li key={`${aviso.codigo}-${indice}`} className="text-body-md text-error-strong">
                <span className="font-semibold">{etiquetaConflicto(aviso.codigo)}:</span>{' '}
                {aviso.mensaje}
              </li>
            ))}
          </ul>
        </div>
      )}

      {advertencias.length > 0 && (
        <div
          role="group"
          aria-label={`Advertencia de localía del partido ${partido.equipoLocal.nombre} vs. ${partido.equipoVisitante.nombre}`}
          className="border-t border-warning/20 bg-warning/8 px-4 py-3"
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0">
              <p className="flex items-center gap-2 text-label-md font-semibold text-warning-strong">
                <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
                {localiaAceptada ? 'Advertencia de localía aceptada' : 'Advertencia de localía'}
              </p>
              <ul className="mt-1.5 flex flex-col gap-1.5">
                {advertencias.map((advertencia, indice) => (
                  <li
                    key={`${advertencia.codigo}-${indice}`}
                    className="break-words text-body-md text-warning-strong"
                  >
                    {advertencia.mensaje}
                  </li>
                ))}
              </ul>
              {localiaAceptada ? (
                <div className="mt-2.5 rounded-md bg-surface p-2.5">
                  <p className="text-label-sm uppercase tracking-wide text-text-muted">
                    Motivo registrado
                  </p>
                  <p className="mt-0.5 break-words text-body-md text-text-main">
                    {partido.motivoLocalia}
                  </p>
                </div>
              ) : (
                <p className="mt-1.5 text-body-sm text-warning-strong">
                  No impide enviar la jornada: se acepta con un motivo que queda en el detalle.
                </p>
              )}
            </div>

            {puedeEditar && !localiaAceptada && (
              <Button
                variante="secundario"
                onClick={() => onAceptarLocalia(partido)}
                className="w-full shrink-0 sm:w-auto"
              >
                Aceptar con motivo
              </Button>
            )}
          </div>
        </div>
      )}
    </li>
  )
}
