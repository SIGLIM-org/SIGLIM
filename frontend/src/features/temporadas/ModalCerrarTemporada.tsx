import { useState } from 'react'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useCerrarTemporada } from '@/features/temporadas/api'
import type { Temporada } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  temporada: Temporada
  onCerrar: () => void
  onCerrada: (temporada: Temporada) => void
}

/**
 * Cierre de una temporada activa con motivo obligatorio (F-10, H-04.2, RN-021). El cierre deja la
 * temporada en solo lectura y el motivo queda en la auditoría (RF-003, RF-093).
 */
export function ModalCerrarTemporada({ temporada, onCerrar, onCerrada }: Props) {
  const cerrar = useCerrarTemporada()
  const [error, setError] = useState<string | null>(null)

  const enviar = (motivo: string) => {
    setError(null)
    cerrar.mutate(
      { id: temporada.id, motivo },
      {
        onSuccess: onCerrada,
        onError: (fallo) => {
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo cerrar la temporada. Intenta de nuevo.',
          )
        },
      },
    )
  }

  return (
    <ModalMotivo
      abierto
      titulo="Cerrar temporada"
      descripcion={`«${temporada.nombre}» pasará a Cerrada y quedará de solo lectura. El cierre no se puede deshacer y el motivo quedará en la auditoría.`}
      aviso={
        error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )
      }
      etiquetaConfirmar="Cerrar temporada"
      varianteConfirmar="destructivo"
      procesando={cerrar.isPending}
      onConfirmar={enviar}
      onCancelar={onCerrar}
    />
  )
}
