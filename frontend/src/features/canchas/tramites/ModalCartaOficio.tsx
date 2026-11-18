import { useId, useRef, useState } from 'react'
import { Download } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { CartaOficioDocumento } from '@/features/canchas/tramites/CartaOficioDocumento'
import { useCartaOficio } from '@/features/canchas/tramites/api'
import { imprimirCarta } from '@/features/canchas/tramites/imprimirCarta'
import type { SolicitudCancha } from '@/features/canchas/tramites/tipos'

type Props = {
  solicitud: SolicitudCancha
  onCerrar: () => void
}

/**
 * Vista previa de la carta con número de oficio y su descarga en PDF (B-07, RF-118). La carta se
 * arma con los datos del trámite; el PDF lo genera el diálogo de impresión del navegador.
 */
export function ModalCartaOficio({ solicitud, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const carta = useCartaOficio(solicitud.id)
  const documentoRef = useRef<HTMLElement>(null)
  const [bloqueada, setBloqueada] = useState(false)
  const id = useId()

  const descargar = () => {
    if (documentoRef.current) {
      setBloqueada(!imprimirCarta(documentoRef.current, `Oficio ${solicitud.numeroOficio}`))
    }
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      onCancel={(evento) => {
        evento.preventDefault()
        onCerrar()
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-3xl overflow-y-auto rounded-lg border border-border bg-background p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="sticky top-0 z-10 flex flex-col gap-3 border-b border-border bg-surface p-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 id={`${id}-titulo`} className="text-headline-md">
            Oficio <span className="font-mono tabular-nums">{solicitud.numeroOficio}</span>
          </h2>
          <p className="text-body-sm text-text-muted">
            Vista previa de la carta. En el diálogo de impresión elige «Guardar como PDF».
          </p>
        </div>
        <div className="flex flex-col-reverse gap-2 sm:flex-row">
          <Button variante="secundario" onClick={onCerrar}>
            Cerrar
          </Button>
          <Button icono={Download} onClick={descargar} disabled={!carta.data}>
            Descargar PDF
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-3 p-4">
        {bloqueada && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            El navegador bloqueó la ventana de impresión. Permite las ventanas emergentes de SIGLIM e
            inténtalo de nuevo.
          </p>
        )}
        {carta.isPending && <EstadoCarga etiqueta="Preparando la carta…" filas={6} columnas={1} />}
        {carta.error != null && <ErrorConsulta error={carta.error} onReintentar={() => void carta.refetch()} />}
        {carta.data && (
          <div className="rounded-md border border-border shadow-sm">
            <CartaOficioDocumento ref={documentoRef} carta={carta.data} />
          </div>
        )}
      </div>
    </dialog>
  )
}
