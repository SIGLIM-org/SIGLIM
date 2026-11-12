import { useId } from 'react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'

type Props = {
  onQuedarse: () => void
  onSalir: () => void
}

/**
 * Confirmación de «Cancelar» cuando ya hubo escrituras (D-12, DEC-103): lo creado se conserva
 * porque la identidad es global y reutilizable por CURP (RF-010).
 */
export function ModalSalirAlta({ onQuedarse, onSalir }: Props) {
  const dialogoRef = useDialogoModal()
  const id = useId()

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      aria-describedby={`${id}-descripcion`}
      onCancel={(evento) => {
        evento.preventDefault()
        onQuedarse()
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex flex-col gap-4 p-6">
        <h2 id={`${id}-titulo`} className="text-headline-md">
          ¿Salir del alta del jugador?
        </h2>
        <p id={`${id}-descripcion`} className="text-body-md text-text-muted">
          El registro no se enviará a validación. Lo que ya se guardó se conserva: la persona, el
          tutor y la tutela quedan registrados y podrás reutilizarlos buscando su CURP.
        </p>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onQuedarse}>
            Seguir con el alta
          </Button>
          <Button variante="destructivo" onClick={onSalir}>
            Salir del alta
          </Button>
        </div>
      </div>
    </dialog>
  )
}
