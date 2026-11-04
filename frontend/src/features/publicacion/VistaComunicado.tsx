import { useState } from 'react'
import { CheckCircle2, CornerUpLeft, Eye, Lock } from 'lucide-react'
import { AlertaBloqueo } from '@/components/ui/AlertaBloqueo'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { nombreAprobador, nombreRedactor } from '@/features/publicacion/actores'
import { etiquetaEstadoPublicacion } from '@/features/publicacion/etiquetas'
import { formatearFechaHora } from '@/features/publicacion/fechas'
import { ModalDictamen } from '@/features/publicacion/ModalDictamen'
import type { Comunicado, DecisionComunicado } from '@/features/publicacion/tipos'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'

type Props = {
  comunicado: Comunicado
  onResuelto: (comunicado: Comunicado) => void
}

const REDACTAR = 'publicacion.comunicado:REDACTAR'
const APROBAR = 'publicacion.comunicado:APROBAR'

/**
 * Comunicado en solo lectura: lo que la Presidencia dictamina y lo que la
 * Secretaría revisa después de una devolución (RF-090, UI-031). Las acciones
 * dependen del estado y de los permisos; el backend vuelve a autorizarlas.
 */
export function VistaComunicado({ comunicado, onResuelto }: Props) {
  const { yo } = useSesionActual()
  const puedeRedactar = tienePermiso(yo.permisos, REDACTAR)
  const puedeAprobar = tienePermiso(yo.permisos, APROBAR)
  const esPropio = comunicado.redactadoPor === yo.perfil.id
  const etiqueta = etiquetaEstadoPublicacion[comunicado.estadoPublicacion]
  const [dictamen, setDictamen] = useState<DecisionComunicado | null>(null)

  const enRevision = comunicado.estadoPublicacion === 'REVISION'
  const publicado = comunicado.estadoPublicacion === 'PUBLICADO'
  const esNotaAprobacion = publicado
  const tituloObservaciones = esNotaAprobacion
    ? 'Nota de aprobación'
    : 'Observaciones de la Presidencia'

  return (
    <section
      aria-label="Comunicado"
      className="flex flex-col gap-5 rounded-lg border border-border bg-surface p-5 sm:p-6"
    >
      <header className="flex flex-col gap-3 border-b border-border pb-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="break-words text-headline-lg text-text-main">
            {comunicado.titulo}
          </h2>
        </div>
        <Insignia variante={etiqueta.variante} icono={etiqueta.icono}>
          {etiqueta.texto}
        </Insignia>
      </header>

      <dl className="grid gap-3 text-body-md sm:grid-cols-2">
        <div>
          <dt className="text-label-sm text-text-muted">Redactado por</dt>
          <dd className="text-text-main">{nombreRedactor(comunicado.redactadoPor, yo)}</dd>
        </div>
        <div>
          <dt className="text-label-sm text-text-muted">Última actualización</dt>
          <dd className="text-text-main">
            <time dateTime={comunicado.actualizadoEn}>
              {formatearFechaHora(comunicado.actualizadoEn)}
            </time>
          </dd>
        </div>
        {comunicado.aprobadoPor && (
          <div>
            <dt className="text-label-sm text-text-muted">Aprobado por</dt>
            <dd className="text-text-main">{nombreAprobador(comunicado.aprobadoPor, yo)}</dd>
          </div>
        )}
        {comunicado.aprobadoEn && (
          <div>
            <dt className="text-label-sm text-text-muted">Aprobado el</dt>
            <dd className="text-text-main">
              <time dateTime={comunicado.aprobadoEn}>
                {formatearFechaHora(comunicado.aprobadoEn)}
              </time>
            </dd>
          </div>
        )}
        {comunicado.publicadoEn && (
          <div>
            <dt className="text-label-sm text-text-muted">Publicado el</dt>
            <dd className="text-text-main">
              <time dateTime={comunicado.publicadoEn}>
                {formatearFechaHora(comunicado.publicadoEn)}
              </time>
            </dd>
          </div>
        )}
      </dl>

      <div className="whitespace-pre-wrap border-t border-border pt-4 text-body-lg text-text-main">
        {comunicado.cuerpo}
      </div>

      {comunicado.observaciones && (
        <section
          aria-label={tituloObservaciones}
          className={[
            'rounded-md border p-4',
            esNotaAprobacion
              ? 'border-info/30 bg-info/8 text-info-strong'
              : 'border-warning/40 bg-warning/8 text-warning-strong',
          ].join(' ')}
        >
          <h3 className="text-headline-sm">{tituloObservaciones}</h3>
          <p className="mt-1 whitespace-pre-wrap text-body-md">{comunicado.observaciones}</p>
        </section>
      )}

      {enRevision && (
        <div className="border-t border-border pt-4">
          {puedeAprobar ? (
            esPropio ? (
              <AlertaBloqueo
                causa="No puedes aprobar tu propio comunicado"
                conflicto="Lo redactaste tú; la aprobación corresponde a otra persona con permiso de la Presidencia."
                accion="Pide a la Presidencia que dictamine este comunicado."
                codigo="RF-090"
              />
            ) : (
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-end">
                <Button
                  variante="secundario"
                  icono={CornerUpLeft}
                  onClick={() => setDictamen('BORRADOR')}
                >
                  Devolver con observaciones
                </Button>
                <Button icono={CheckCircle2} onClick={() => setDictamen('PUBLICADO')}>
                  Aprobar y publicar
                </Button>
              </div>
            )
          ) : (
            <p className="flex items-start gap-2 text-body-md text-text-muted">
              <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              En revisión por la Presidencia. Mientras espera el dictamen, el comunicado no se
              puede editar.
            </p>
          )}
        </div>
      )}

      {publicado && (
        <p className="flex items-start gap-2 border-t border-border pt-4 text-body-md text-success-strong">
          <Eye aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          El comunicado está publicado y ya no se puede editar.
        </p>
      )}

      {!enRevision && !publicado && !puedeRedactar && (
        <p className="flex items-start gap-2 border-t border-border pt-4 text-body-md text-text-muted">
          <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          Solo la Secretaría puede editar este borrador. Cuando lo envíe a revisión, la
          Presidencia podrá dictaminarlo.
        </p>
      )}

      {dictamen && (
        <ModalDictamen
          comunicado={comunicado}
          decision={dictamen}
          onCerrar={() => setDictamen(null)}
          onResuelto={(resuelto) => {
            setDictamen(null)
            onResuelto(resuelto)
          }}
        />
      )}
    </section>
  )
}
