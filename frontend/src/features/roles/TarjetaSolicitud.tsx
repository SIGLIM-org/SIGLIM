import { Check, Info, UserRound, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { formatearFechaHora } from '@/features/roles/fechas'
import { ListaPermisosSolicitud } from '@/features/roles/ListaPermisosSolicitud'
import type { DecisionSolicitud, SolicitudCambioPermisos } from '@/features/roles/tipos'

type Props = {
  solicitud: SolicitudCambioPermisos
  descripciones: ReadonlyMap<string, string>
  /** Quien solicita no puede aprobar ni rechazar su propio trámite (DEC-04). */
  esSolicitante: boolean
  /** El Presidente no puede aprobar un cambio sobre el rol que él mismo tiene (VAL-022). */
  esRolPropio: boolean
  onDictaminar: (decision: DecisionSolicitud) => void
}

/**
 * Tarjeta de una solicitud de control dual (F-13, RF-096): qué rol cambia, qué permisos se
 * agregan o quitan, quién la pidió, cuándo y por qué. Los permisos se muestran por su código y
 * su descripción, sin datos personales más allá del nombre visible de quien solicita.
 */
export function TarjetaSolicitud({
  solicitud,
  descripciones,
  esSolicitante,
  esRolPropio,
  onDictaminar,
}: Props) {
  return (
    <article className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-5">
      <header className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h3 className="text-headline-sm text-text-main">{solicitud.rol.nombre}</h3>
          <p className="break-all font-mono text-body-sm text-text-muted">
            {solicitud.rol.codigo}
          </p>
        </div>
        <Insignia variante="advertencia">Pendiente de dictamen</Insignia>
      </header>

      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-body-sm text-text-muted">
        <UserRound aria-hidden="true" className="size-4 shrink-0" />
        <span>Solicitó {solicitud.solicitadoPor.nombreMostrado}</span>
        <span aria-hidden="true">·</span>
        <time dateTime={solicitud.solicitadoEn}>{formatearFechaHora(solicitud.solicitadoEn)}</time>
      </p>

      <div className="grid gap-4 border-y border-border py-4 sm:grid-cols-2">
        <ListaPermisosSolicitud
          titulo="Permisos que se agregan"
          codigos={solicitud.permisosAgregados}
          descripciones={descripciones}
          sentido="agrega"
        />
        <ListaPermisosSolicitud
          titulo="Permisos que se quitan"
          codigos={solicitud.permisosQuitados}
          descripciones={descripciones}
          sentido="quita"
        />
      </div>

      <blockquote className="rounded-md bg-background p-3 text-body-md text-text-main">
        <p className="text-label-sm uppercase text-text-muted">Motivo de la solicitud</p>
        <p className="mt-1 whitespace-pre-line">{solicitud.motivo}</p>
      </blockquote>

      {esSolicitante ? (
        <p className="flex items-start gap-2 rounded-md border border-border bg-background p-3 text-body-md text-text-muted">
          <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          Quien solicita no puede dictaminar este cambio. Debe resolverlo otra cuenta con el permiso
          de aprobar pares incompatibles.
        </p>
      ) : (
        <>
          {esRolPropio && (
            <p className="flex items-start gap-2 rounded-md border border-warning/40 bg-warning/10 p-3 text-body-md text-warning-strong">
              <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              No puedes aprobar un cambio sobre tu propio rol (VAL-022). Puedes rechazarlo.
            </p>
          )}
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variante="secundario" icono={X} onClick={() => onDictaminar('RECHAZADO')}>
              Rechazar
            </Button>
            <Button icono={Check} disabled={esRolPropio} onClick={() => onDictaminar('APROBADO')}>
              Aprobar
            </Button>
          </div>
        </>
      )}
    </article>
  )
}
