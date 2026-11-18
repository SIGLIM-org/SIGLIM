import { useId } from 'react'
import { Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useRetirarDisponibilidadArbitro } from '@/features/arbitraje/api'
import { describirPeriodo } from '@/features/arbitraje/fechasDisponibilidad'
import type { DisponibilidadArbitral } from '@/features/arbitraje/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  arbitroId: string
  nombreArbitro: string
  disponibilidad: DisponibilidadArbitral
  onCerrar: () => void
  onRetirada: (disponibilidad: DisponibilidadArbitral) => void
}

/**
 * Confirmación del retiro de un periodo de disponibilidad (B-09.2, RF-022). Usa el modal del
 * sistema de diseño en lugar de `confirm()` del navegador (D-12). El retiro borra la fila porque la
 * disponibilidad no es historial deportivo, así que el texto avisa que no se puede deshacer.
 */
export function ModalRetirarDisponibilidad({
  arbitroId,
  nombreArbitro,
  disponibilidad,
  onCerrar,
  onRetirada,
}: Props) {
  const dialogoRef = useDialogoModal()
  const id = useId()
  const retirar = useRetirarDisponibilidadArbitro(arbitroId)
  const { fecha, horario } = describirPeriodo(disponibilidad.periodo)
  const error = retirar.error instanceof ApiError ? retirar.error : null

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      aria-describedby={`${id}-descripcion`}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!retirar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex flex-col gap-4 p-6">
        <h2 id={`${id}-titulo`} className="text-balance text-headline-md">
          ¿Retirar este periodo?
        </h2>
        <div id={`${id}-descripcion`} className="flex flex-col gap-3">
          <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1 rounded-md bg-background p-3 text-body-md">
            <dt className="text-text-muted">Árbitro</dt>
            <dd className="break-words text-text-main">{nombreArbitro}</dd>
            <dt className="text-text-muted">Fecha</dt>
            <dd className="text-text-main">{fecha}</dd>
            <dt className="text-text-muted">Horario</dt>
            <dd className="font-mono text-mono-data text-text-main">{horario}</dd>
            <dt className="text-text-muted">Registrado como</dt>
            <dd className="text-text-main">
              {disponibilidad.disponible ? 'Disponible' : 'No disponible'}
            </dd>
          </dl>
          <p className="text-body-md text-text-muted">
            El periodo se elimina del calendario del árbitro y no se puede deshacer. Si te
            equivocaste, podrás registrarlo de nuevo.
          </p>
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error.problema.detail}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={retirar.isPending}>
            Conservar
          </Button>
          <Button
            variante="destructivo"
            icono={Trash2}
            cargando={retirar.isPending}
            onClick={() =>
              retirar.mutate(disponibilidad.id, {
                onSuccess: () => onRetirada(disponibilidad),
              })
            }
          >
            Retirar periodo
          </Button>
        </div>
      </div>
    </dialog>
  )
}
