import { useId, useState, type FormEvent } from 'react'
import { Send } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useEnviarARevision } from '@/features/publicacion/api'
import type { Comunicado } from '@/features/publicacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  comunicado: Comunicado
  onCerrar: () => void
  onEnviado: (comunicado: Comunicado) => void
}

/**
 * Confirmación del envío a revisión (RF-090, CU-027). Al enviarlo, la
 * Secretaría ya no puede editarlo: lo que dictamine la Presidencia es lo que
 * se publicará.
 */
export function ModalEnvioRevision({ comunicado, onCerrar, onEnviado }: Props) {
  const dialogoRef = useDialogoModal()
  const enviar = useEnviarARevision()
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    enviar.mutate(comunicado.id, {
      onSuccess: onEnviado,
      onError: (fallo) => {
        setError(
          fallo instanceof ApiError
            ? fallo.problema.detail
            : 'No se pudo enviar el comunicado a revisión. Intenta de nuevo.',
        )
      },
    })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!enviar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            Enviar a revisión
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            «{comunicado.titulo}» pasará a revisión de la Presidencia y dejará de ser editable
            hasta que se publique o se devuelva con observaciones.
          </p>
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={enviar.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            icono={Send}
            cargando={enviar.isPending}
            disabled={enviar.isPending}
          >
            Enviar a revisión
          </Button>
        </div>
      </form>
    </dialog>
  )
}
