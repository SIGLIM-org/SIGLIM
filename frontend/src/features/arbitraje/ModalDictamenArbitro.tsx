import { useId, useState, type FormEvent } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useValidarArbitro } from '@/features/arbitraje/api'
import type { Arbitro, DecisionArbitro } from '@/features/arbitraje/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  arbitro: Arbitro
  decision: DecisionArbitro
  onCerrar: () => void
  onResuelto: (arbitro: Arbitro) => void
}

/** Mínimo de observaciones al rechazar (RF-113), igual que las demás transiciones. */
const MINIMO_OBSERVACIONES = 10

/** Tope de las observaciones, igual que las demás transiciones del sistema. */
const MAXIMO_OBSERVACIONES = 500

/**
 * Dictamen de la Comisión sobre un árbitro PROPUESTO (F-20, RF-113): validarlo para que pueda
 * designarse o rechazarlo explicando qué debe corregir el club. El rechazo exige al menos 10
 * caracteres; el backend lo vuelve a validar.
 */
export function ModalDictamenArbitro({ arbitro, decision, onCerrar, onResuelto }: Props) {
  const dialogoRef = useDialogoModal()
  const validar = useValidarArbitro(arbitro.id)
  const [observaciones, setObservaciones] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idCampo = `${id}-observaciones`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const esRechazo = decision === 'RECHAZADO'
  const texto = observaciones.trim()
  const valido = !esRechazo || texto.length >= MINIMO_OBSERVACIONES

  const alConfirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    validar.mutate(
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
        if (!validar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alConfirmar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-balance text-headline-md">
            {esRechazo ? 'Rechazar árbitro' : 'Validar árbitro'}
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            {esRechazo
              ? `«${arbitro.persona.nombreCompleto}», presentado por «${arbitro.club.nombre}», quedará RECHAZADO. Explica qué debe corregir el club; el motivo quedará en la auditoría.`
              : `«${arbitro.persona.nombreCompleto}» quedará VALIDADO y podrá designarse a partidos. La Comisión de Arbitraje quedará registrada como quien lo validó.`}
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md text-text-main">
            {esRechazo ? 'Observaciones para el club' : 'Nota de validación (opcional)'}
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
                ? 'Por ejemplo: la constancia no acredita la categoría o los datos no coinciden con la CURP.'
                : 'Puedes dejar una nota sobre la validación.'
            }
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>
              {esRechazo
                ? `Mínimo ${MINIMO_OBSERVACIONES} caracteres. Quedará en la auditoría.`
                : 'La nota es opcional y también queda en la auditoría.'}
            </span>
            <span className="font-mono">
              {texto.length}/{MAXIMO_OBSERVACIONES}
            </span>
          </p>
          {mostrarError && !valido && (
            <p id={idError} role="alert" className="text-body-sm text-error-strong">
              Escribe observaciones de al menos {MINIMO_OBSERVACIONES} caracteres para rechazar al
              árbitro.
            </p>
          )}
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={validar.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            variante={esRechazo ? 'destructivo' : 'primario'}
            icono={esRechazo ? XCircle : CheckCircle2}
            cargando={validar.isPending}
            disabled={validar.isPending}
          >
            {esRechazo ? 'Rechazar árbitro' : 'Validar árbitro'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
