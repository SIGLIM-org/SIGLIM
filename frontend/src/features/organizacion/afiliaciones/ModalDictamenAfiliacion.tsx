import { useId, useState, type FormEvent } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useResolverAfiliacion } from '@/features/organizacion/afiliaciones/api'
import type {
  Afiliacion,
  DecisionAfiliacion,
} from '@/features/organizacion/afiliaciones/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  afiliacion: Afiliacion
  decision: DecisionAfiliacion
  onCerrar: () => void
  onResuelto: (afiliacion: Afiliacion) => void
}

/** Mínimo de observaciones al rechazar (H-08.1, RF-101). */
const MINIMO_OBSERVACIONES = 10

/** Tope de las observaciones, igual que las demás transiciones del sistema. */
const MAXIMO_OBSERVACIONES = 500

/**
 * Dictamen del Presidente sobre un tarjetón SOLICITADA (F-16, RF-101, CU-018): aprobar la
 * afiliación o rechazarla explicando qué debe corregir la Directiva. El rechazo exige al menos
 * 10 caracteres; el backend lo vuelve a validar.
 */
export function ModalDictamenAfiliacion({
  afiliacion,
  decision,
  onCerrar,
  onResuelto,
}: Props) {
  const dialogoRef = useDialogoModal()
  const resolver = useResolverAfiliacion(afiliacion.id)
  const [observaciones, setObservaciones] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idCampo = `${id}-observaciones`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const esRechazo = decision === 'RECHAZADA'
  const texto = observaciones.trim()
  const valido = !esRechazo || texto.length >= MINIMO_OBSERVACIONES

  const alConfirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    resolver.mutate(
      {
        decision,
        ...(texto ? { observaciones: texto } : {}),
      },
      {
        onSuccess: onResuelto,
        onError: (fallo) => {
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo aplicar el dictamen. Intenta de nuevo.',
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
      <form onSubmit={alConfirmar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-balance text-headline-md">
            {esRechazo ? 'Rechazar afiliación' : 'Aprobar afiliación'}
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            {esRechazo
              ? `El tarjetón de «${afiliacion.club.nombre}» quedará RECHAZADO. Explica qué debe corregir la Directiva; el motivo quedará en el expediente.`
              : `«${afiliacion.club.nombre}» quedará afiliado a ${afiliacion.temporada.nombre}. Esta acción no se puede deshacer.`}
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md text-text-main">
            {esRechazo ? 'Observaciones para la Directiva' : 'Nota de aprobación (opcional)'}
            {esRechazo && (
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            )}
          </label>
          <textarea
            id={idCampo}
            value={observaciones}
            required={esRechazo}
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
              esRechazo
                ? 'Por ejemplo: faltan las firmas del secretario o la categoría no corresponde.'
                : 'Puedes dejar una nota sobre la aprobación.'
            }
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>
              {esRechazo
                ? `Mínimo ${MINIMO_OBSERVACIONES} caracteres. Quedará en el expediente.`
                : 'La nota es opcional y también queda en el expediente.'}
            </span>
            <span className="font-mono">
              {texto.length}/{MAXIMO_OBSERVACIONES}
            </span>
          </p>
          {mostrarError && !valido && (
            <p id={idError} role="alert" className="text-body-sm text-error-strong">
              Escribe observaciones de al menos {MINIMO_OBSERVACIONES} caracteres para rechazar la
              afiliación.
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
            variante={esRechazo ? 'destructivo' : 'primario'}
            icono={esRechazo ? XCircle : CheckCircle2}
            cargando={resolver.isPending}
            disabled={resolver.isPending}
          >
            {esRechazo ? 'Rechazar afiliación' : 'Aprobar afiliación'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
