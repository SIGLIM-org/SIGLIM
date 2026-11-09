import { useId, useState, type FormEvent } from 'react'
import { Check, CircleAlert, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useResolverSolicitud } from '@/features/roles/api'
import type {
  DecisionSolicitud,
  SolicitudCambioPermisos,
} from '@/features/roles/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  solicitud: SolicitudCambioPermisos
  decision: DecisionSolicitud
  onCerrar: () => void
  onResuelto: (solicitud: SolicitudCambioPermisos) => void
}

/**
 * Dictamen del Presidente sobre un cambio incompatible (F-13, RF-096): aprobar aplica la matriz
 * solicitada; rechazar la descarta. Las dos decisiones llevan motivo y quedan auditadas.
 */
export function ModalDictamenPermisos({
  solicitud,
  decision,
  onCerrar,
  onResuelto,
}: Props) {
  const dialogoRef = useDialogoModal()
  const resolver = useResolverSolicitud()
  const [motivo, setMotivo] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idCampo = `${id}-motivo`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const aprobar = decision === 'APROBADO'
  const texto = motivo.trim()
  const valido = texto.length >= MINIMO_MOTIVO

  const confirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    resolver.mutate(
      { id: solicitud.id, decision, motivo: texto },
      {
        onSuccess: onResuelto,
        onError: (fallo) => {
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo registrar el dictamen. Intenta de nuevo.',
          )
        },
      },
    )
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!resolver.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={confirmar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            {aprobar ? 'Aprobar cambio de permisos' : 'Rechazar cambio de permisos'}
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            {aprobar
              ? `Se aplicará a «${solicitud.rol.nombre}» la matriz solicitada. El cambio no se puede deshacer y quedará en la auditoría.`
              : `Se descartará el cambio solicitado para «${solicitud.rol.nombre}». La matriz del rol no se modifica y el trámite no se puede reabrir.`}
          </p>
        </div>

        <div className="rounded-md border border-border bg-background p-3 text-body-md">
          <p className="text-label-md text-text-main">{solicitud.rol.nombre}</p>
          <p className="break-all font-mono text-body-sm text-text-muted">
            {solicitud.rol.codigo}
          </p>
          <p className="mt-2 text-text-muted">
            Solicitó {solicitud.solicitadoPor.nombreMostrado}: {solicitud.motivo}
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md">
            Motivo del dictamen <span aria-hidden="true" className="text-error">*</span>
          </label>
          <textarea
            id={idCampo}
            value={motivo}
            required
            rows={4}
            maxLength={MAXIMO_MOTIVO}
            aria-invalid={mostrarError && !valido}
            aria-describedby={mostrarError && !valido ? `${idError} ${idAyuda}` : idAyuda}
            onChange={(evento) => setMotivo(evento.target.value)}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md aria-invalid:border-error"
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>Mínimo {MINIMO_MOTIVO} caracteres. Quedará en el historial.</span>
            <span className="font-mono">
              {texto.length}/{MAXIMO_MOTIVO}
            </span>
          </p>
          {mostrarError && !valido && (
            <p id={idError} role="alert" className="text-body-sm text-error-strong">
              Escribe un motivo de al menos {MINIMO_MOTIVO} caracteres.
            </p>
          )}
        </div>

        {error && (
          <p
            role="alert"
            className="flex items-start gap-2 rounded-md bg-error/8 p-3 text-body-md text-error-strong"
          >
            <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={resolver.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            icono={aprobar ? Check : X}
            variante={aprobar ? 'primario' : 'destructivo'}
            cargando={resolver.isPending}
            disabled={resolver.isPending}
          >
            {aprobar ? 'Aprobar cambio' : 'Rechazar cambio'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
