import { useId, useState, type FormEvent } from 'react'
import { Send } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useEnviarJornada } from '@/features/programacion/api'
import type { Jornada } from '@/features/programacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  jornada: Jornada
  /** «Libre Primera · Grupo A», para que la persona confirme qué jornada envía. */
  contexto: string
  partidos: number
  onCerrar: () => void
  onEnviada: (jornada: Jornada) => void
}

/**
 * Confirmación del envío a aprobación desde F-22 (H-13.2, RF-121). Al enviarla, la jornada pasa a
 * Revisión y deja de admitir altas y ajustes hasta que la Presidencia la publique o la devuelva.
 *
 * Si el backend encuentra conflictos que la última verificación no mostraba (`409 RF-039`), el
 * modal explica la causa y la pantalla refresca la verificación para marcarlos en cada partido.
 */
export function ModalEnvioJornada({ jornada, contexto, partidos, onCerrar, onEnviada }: Props) {
  const dialogoRef = useDialogoModal()
  const enviar = useEnviarJornada()
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    enviar.mutate(jornada.id, {
      onSuccess: onEnviada,
      onError: (fallo) => {
        if (!(fallo instanceof ApiError)) {
          setError('No se pudo enviar la jornada a aprobación. Intenta de nuevo.')
          return
        }

        setError(
          fallo.codigo === 'RF-039'
            ? `${fallo.problema.detail} Cierra este aviso para ver los conflictos marcados en cada partido.`
            : fallo.problema.detail,
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
            Enviar a aprobación
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            La jornada {jornada.numero} de {contexto}, con {partidos}{' '}
            {partidos === 1 ? 'partido' : 'partidos'}, pasará a revisión de la Presidencia. Mientras
            esté en revisión no podrás programar ni ajustar partidos; si la devuelven, verás sus
            observaciones aquí.
          </p>
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={enviar.isPending}>
            {error ? 'Cerrar' : 'Cancelar'}
          </Button>
          <Button
            type="submit"
            icono={Send}
            cargando={enviar.isPending}
            disabled={enviar.isPending}
          >
            Enviar a aprobación
          </Button>
        </div>
      </form>
    </dialog>
  )
}
