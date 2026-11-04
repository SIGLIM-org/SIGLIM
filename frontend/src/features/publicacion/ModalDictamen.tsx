import { useId, useState, type FormEvent } from 'react'
import { CheckCircle2, CornerUpLeft } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useResolverComunicado } from '@/features/publicacion/api'
import type { Comunicado, DecisionComunicado } from '@/features/publicacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  comunicado: Comunicado
  decision: DecisionComunicado
  onCerrar: () => void
  onResuelto: (comunicado: Comunicado) => void
}

/** Mínimo de las observaciones al devolver (convenciones de API §6.11, RF-090). */
const MINIMO_OBSERVACIONES = 10

/*
 * [PROPUESTA] Ni el ERS ni la BD fijan un máximo para las observaciones; 500
 * caracteres evita textos desmedidos en el historial, igual que en los demás
 * motivos del sistema. Ajustar cuando la Liga lo defina.
 */
const MAXIMO_OBSERVACIONES = 500

/**
 * Dictamen del Presidente sobre un comunicado en revisión (RF-090, CU-027):
 * aprobarlo y publicarlo, o devolverlo a la Secretaría con observaciones.
 */
export function ModalDictamen({ comunicado, decision, onCerrar, onResuelto }: Props) {
  const dialogoRef = useDialogoModal()
  const resolver = useResolverComunicado()
  const [observaciones, setObservaciones] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idCampo = `${id}-observaciones`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const esDevolucion = decision === 'BORRADOR'
  const texto = observaciones.trim()
  const valido = !esDevolucion || texto.length >= MINIMO_OBSERVACIONES

  const alConfirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    resolver.mutate(
      {
        id: comunicado.id,
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
          <h2 id={idTitulo} className="text-headline-md">
            {esDevolucion ? 'Devolver a borrador' : 'Aprobar y publicar'}
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            {esDevolucion
              ? 'El comunicado volverá a Borrador para que la Secretaría lo corrija. Explica qué debe cambiar; las observaciones quedarán en el historial.'
              : 'El comunicado quedará publicado y ya no podrá editarse. Esta acción no se puede deshacer.'}
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md text-text-main">
            {esDevolucion ? 'Observaciones para la Secretaría' : 'Nota de aprobación (opcional)'}
            {esDevolucion && (
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            )}
          </label>
          <textarea
            id={idCampo}
            value={observaciones}
            required={esDevolucion}
            rows={4}
            maxLength={MAXIMO_OBSERVACIONES}
            aria-invalid={mostrarError && !valido}
            aria-describedby={mostrarError && !valido ? `${idError} ${idAyuda}` : idAyuda}
            onChange={(evento) => setObservaciones(evento.target.value)}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>
              {esDevolucion
                ? `Mínimo ${MINIMO_OBSERVACIONES} caracteres. Quedará en el historial.`
                : 'Puedes dejar una nota sobre la aprobación.'}
            </span>
            <span className="font-mono">
              {texto.length}/{MAXIMO_OBSERVACIONES}
            </span>
          </p>
          {mostrarError && !valido && (
            <p id={idError} role="alert" className="text-body-sm text-error-strong">
              Escribe observaciones de al menos {MINIMO_OBSERVACIONES} caracteres para devolver el
              comunicado.
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
            variante={esDevolucion ? 'secundario' : 'primario'}
            icono={esDevolucion ? CornerUpLeft : CheckCircle2}
            cargando={resolver.isPending}
            disabled={resolver.isPending}
          >
            {esDevolucion ? 'Devolver a borrador' : 'Aprobar y publicar'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
