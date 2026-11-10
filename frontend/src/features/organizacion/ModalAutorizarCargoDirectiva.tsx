import { useId, useState } from 'react'
import { ShieldCheck, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useAutorizarCargoDirectiva } from '@/features/organizacion/api'
import { diaAnterior } from '@/features/organizacion/directiva'
import { detalleDeFallo } from '@/features/organizacion/erroresFormulario'
import { etiquetaCargoDirectiva } from '@/features/organizacion/etiquetas'
import type { CargoDirectiva } from '@/features/organizacion/tipos'
import { formatearFecha } from '@/features/temporadas/fechas'

type Props = {
  cargo: CargoDirectiva
  /** Titular autorizado vigente del mismo cargo; la autorización termina su vigencia (RF-102). */
  vigente?: CargoDirectiva
  onCerrar: () => void
  onAutorizado: (cargo: CargoDirectiva) => void
}

/**
 * Autorización de un cambio de directiva pendiente (F-15, RF-102). Explica a quién reemplaza y desde
 * cuándo antes de confirmar; el backend cierra la vigencia anterior el día previo al inicio nuevo y
 * audita la operación (RF-093). Un traslape o una autorización repetida responde `409 VAL-003`.
 */
export function ModalAutorizarCargoDirectiva({ cargo, vigente, onCerrar, onAutorizado }: Props) {
  const dialogoRef = useDialogoModal()
  const autorizar = useAutorizarCargoDirectiva(cargo.clubId)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDetalle = `${id}-detalle`
  const etiquetaCargo = etiquetaCargoDirectiva[cargo.cargo]

  const alConfirmar = () => {
    setError(null)
    autorizar.mutate(cargo.id, {
      onSuccess: onAutorizado,
      onError: (fallo) =>
        setError(detalleDeFallo(fallo, 'No se pudo autorizar el cambio. Intenta de nuevo.')),
    })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDetalle}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!autorizar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h2 id={idTitulo} className="text-balance text-headline-md">
            Autorizar cambio de {etiquetaCargo.texto.toLocaleLowerCase('es-MX')}
          </h2>
          <p className="text-body-md text-text-muted">La autorización es definitiva y queda auditada.</p>
        </div>

        <div
          id={idDetalle}
          className="flex flex-col gap-3 rounded-lg border border-warning/50 bg-warning/14 p-4"
        >
          <div className="flex items-start gap-2">
            <TriangleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-strong" />
            <div className="flex min-w-0 flex-col gap-2 text-body-md">
              <p className="break-words">
                <span className="text-text-muted">Titular propuesto: </span>
                <strong className="font-semibold text-text-main">{cargo.nombrePersona}</strong>
              </p>
              <p>
                <span className="text-text-muted">Vigencia desde: </span>
                <strong className="font-semibold text-text-main">{formatearFecha(cargo.desde)}</strong>
              </p>
              {vigente ? (
                <p className="break-words">
                  Al autorizar, {vigente.nombrePersona} terminará su cargo el{' '}
                  <strong className="font-semibold">
                    {formatearFecha(diaAnterior(cargo.desde))}
                  </strong>
                  .
                </p>
              ) : (
                <p>No hay un titular vigente de este cargo; este será el primero.</p>
              )}
            </div>
          </div>
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={autorizar.isPending}>
            Cancelar
          </Button>
          <Button icono={ShieldCheck} cargando={autorizar.isPending} onClick={alConfirmar}>
            Autorizar cambio
          </Button>
        </div>
      </div>
    </dialog>
  )
}
