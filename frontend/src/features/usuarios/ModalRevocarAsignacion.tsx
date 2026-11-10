import { useState } from 'react'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useRevocarAsignacion } from '@/features/usuarios/api'
import { describirAsignacion } from '@/features/usuarios/etiquetas'
import type { AsignacionUsuario } from '@/features/usuarios/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  asignacion: AsignacionUsuario
  onCerrar: () => void
  onRevocada: () => void
}

/**
 * Revocación de una asignación desde F-12 (H-03.2, RF-094): pide motivo y no borra el historial;
 * la asignación queda inactiva con su fecha (RN-005, RF-093).
 */
export function ModalRevocarAsignacion({ asignacion, onCerrar, onRevocada }: Props) {
  const revocar = useRevocarAsignacion()
  const [error, setError] = useState<string | null>(null)

  const enviar = (motivo: string) => {
    setError(null)
    revocar.mutate(
      { id: asignacion.id, motivo },
      {
        onSuccess: onRevocada,
        onError: (fallo) => {
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo revocar la asignación. Intenta de nuevo.',
          )
        },
      },
    )
  }

  return (
    <ModalMotivo
      abierto
      titulo="Revocar asignación"
      descripcion={`«${describirAsignacion(asignacion)}» dejará de estar activa. La cuenta conserva el historial y puedes volver a asignarla después.`}
      aviso={
        error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )
      }
      etiquetaConfirmar="Revocar asignación"
      varianteConfirmar="destructivo"
      procesando={revocar.isPending}
      onConfirmar={enviar}
      onCancelar={onCerrar}
    />
  )
}
