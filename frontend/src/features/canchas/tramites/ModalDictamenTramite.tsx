import { useId, useState, type FormEvent } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useResolverSolicitudCancha } from '@/features/canchas/tramites/api'
import {
  etiquetaEstadoTramite,
  etiquetaTipoTramite,
  solicitanteDe,
  textoDecision,
} from '@/features/canchas/tramites/etiquetas'
import type { DecisionSolicitudCancha, SolicitudCancha } from '@/features/canchas/tramites/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  solicitud: SolicitudCancha
  decision: DecisionSolicitudCancha
  onCerrar: () => void
  onResuelta: (solicitud: SolicitudCancha) => void
}

/** Mínimo de observaciones de la decisión desfavorable (RN-021). */
const MINIMO_OBSERVACIONES = 10
const MAXIMO_OBSERVACIONES = 500

/**
 * Dictamen del Presidente sobre un trámite pendiente (B-07, RF-116, RF-117). La decisión
 * desfavorable exige el motivo; la favorable admite condiciones. El backend vuelve a validar.
 */
export function ModalDictamenTramite({ solicitud, decision, onCerrar, onResuelta }: Props) {
  const dialogoRef = useDialogoModal()
  const resolver = useResolverSolicitudCancha(solicitud.id)
  const [observaciones, setObservaciones] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idCampo = `${id}-observaciones`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const favorable = decision === 'APROBADA' || decision === 'ACEPTADA'
  const texto = observaciones.trim()
  const valido = favorable || texto.length >= MINIMO_OBSERVACIONES
  const tipo = etiquetaTipoTramite[solicitud.tipo].texto.toLowerCase()
  const estadoFinal = etiquetaEstadoTramite[decision].texto.toUpperCase()

  const alConfirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    resolver.mutate(
      { decision, ...(texto ? { observaciones: texto } : {}) },
      {
        onSuccess: onResuelta,
        onError: (fallo) =>
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo registrar el dictamen. Intenta de nuevo.',
          ),
      },
    )
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      aria-describedby={`${id}-descripcion`}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!resolver.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alConfirmar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={`${id}-titulo`} className="text-balance text-headline-md">
            {textoDecision[decision]} {tipo} · {solicitud.numeroOficio}
          </h2>
          <p id={`${id}-descripcion`} className="mt-1 text-body-md text-text-muted">
            El trámite de «{solicitanteDe(solicitud)}» quedará {estadoFinal}. El dictamen queda en el
            historial y no se puede deshacer.
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md text-text-main">
            {favorable ? 'Condiciones (opcional)' : 'Motivo del dictamen'}
            {!favorable && (
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            )}
          </label>
          <textarea
            id={idCampo}
            value={observaciones}
            required={!favorable}
            rows={4}
            maxLength={MAXIMO_OBSERVACIONES}
            aria-invalid={mostrarError && !valido}
            aria-describedby={mostrarError && !valido ? `${idError} ${idAyuda}` : idAyuda}
            onChange={(evento) => {
              setObservaciones(evento.target.value)
              setMostrarError(false)
            }}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
            placeholder={
              favorable
                ? 'Por ejemplo: entregar la cancha limpia al término del partido.'
                : 'Por ejemplo: la cancha ya está asignada a otro club en ese horario.'
            }
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>
              {favorable
                ? 'Aparecerán en la carta y en el historial.'
                : `Mínimo ${MINIMO_OBSERVACIONES} caracteres. Aparecerá en la carta y en el historial.`}
            </span>
            <span className="font-mono">
              {texto.length}/{MAXIMO_OBSERVACIONES}
            </span>
          </p>
          {mostrarError && !valido && (
            <p id={idError} role="alert" className="text-body-sm text-error-strong">
              Escribe un motivo de al menos {MINIMO_OBSERVACIONES} caracteres.
            </p>
          )}
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={resolver.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            variante={favorable ? 'primario' : 'destructivo'}
            icono={favorable ? CheckCircle2 : XCircle}
            cargando={resolver.isPending}
            disabled={resolver.isPending}
          >
            {textoDecision[decision]} trámite
          </Button>
        </div>
      </form>
    </dialog>
  )
}
