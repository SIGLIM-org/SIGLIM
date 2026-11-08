import { useId, useState, type FormEvent } from 'react'
import { CircleCheck, CircleX } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useResolverInterpretacion } from '@/features/temporadas/api'
import type { EstadoInterpretacion, InterpretacionRegla } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Decision = Exclude<EstadoInterpretacion, 'PENDIENTE'>

type Props = {
  interpretacion: InterpretacionRegla
  decision: Decision
  onCerrar: () => void
  onResuelta: (interpretacion: InterpretacionRegla) => void
}

/** Mínimo de las observaciones de un rechazo (DEC-58, convenciones de API §6.2). */
export const MINIMO_OBSERVACIONES = 10

/*
 * [PROPUESTA] Ni el ERS ni la BD fijan un máximo para las observaciones; 500 caracteres evitan
 * textos desmedidos, igual que en los demás motivos del sistema. Ajustar cuando la Liga lo defina.
 */
export const MAXIMO_OBSERVACIONES = 500

/**
 * Resolución definitiva de una interpretación (F-11, B-03.2, RF-007): el Presidente la autoriza o
 * la rechaza con observaciones. No se reabre una resolución y no cambia los parámetros de la
 * temporada. Quien registró no puede resolverla (DEC-57); esa comprobación también la hace el
 * backend.
 */
export function ModalResolverInterpretacion({
  interpretacion,
  decision,
  onCerrar,
  onResuelta,
}: Props) {
  const dialogoRef = useDialogoModal()
  const resolver = useResolverInterpretacion()
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

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    resolver.mutate(
      {
        id: interpretacion.id,
        estado: decision,
        ...(texto ? { observaciones: texto } : {}),
      },
      {
        onSuccess: onResuelta,
        onError: (fallo) => {
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo resolver la interpretación. Intenta de nuevo.',
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
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            {esRechazo ? 'Rechazar interpretación' : 'Autorizar interpretación'}
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            {esRechazo
              ? 'La interpretación quedará rechazada de forma definitiva. Explica el motivo; las observaciones quedarán en el historial.'
              : 'La interpretación quedará autorizada de forma definitiva. Puedes dejar una nota sobre la autorización; no cambia los parámetros ni las reglas versionadas de la temporada.'}
          </p>
        </div>

        <div className="rounded-md border border-border bg-background p-3">
          <p className="text-label-sm text-text-muted">Interpretación propuesta</p>
          <p className="mt-1 whitespace-pre-line break-words text-body-md text-text-main">
            {interpretacion.descripcion}
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md text-text-main">
            {esRechazo ? 'Observaciones del rechazo' : 'Nota de autorización (opcional)'}
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
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>
              {esRechazo
                ? `Mínimo ${MINIMO_OBSERVACIONES} caracteres. Quedará en el historial.`
                : 'Puedes dejar una nota sobre la autorización.'}
            </span>
            <span className="font-mono">
              {observaciones.length}/{MAXIMO_OBSERVACIONES}
            </span>
          </p>
          {mostrarError && !valido && (
            <p id={idError} role="alert" className="text-body-sm text-error-strong">
              Escribe observaciones de al menos {MINIMO_OBSERVACIONES} caracteres para rechazar la
              interpretación.
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
            icono={esRechazo ? CircleX : CircleCheck}
            cargando={resolver.isPending}
          >
            {esRechazo ? 'Rechazar interpretación' : 'Autorizar interpretación'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
