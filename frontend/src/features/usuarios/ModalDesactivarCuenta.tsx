import { useState } from 'react'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useActualizarUsuario } from '@/features/usuarios/api'
import type { Usuario } from '@/features/usuarios/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  usuario: Usuario
  onCerrar: () => void
  onDesactivada: () => void
}

/**
 * Desactivación de una cuenta desde F-12 (H-03.2, RF-094). Pide motivo y lo deja en la auditoría;
 * la cuenta deja de operar aunque su token siga vigente (ADR-0003).
 */
export function ModalDesactivarCuenta({ usuario, onCerrar, onDesactivada }: Props) {
  const actualizar = useActualizarUsuario(usuario.id)
  const [error, setError] = useState<string | null>(null)

  const enviar = (motivo: string) => {
    setError(null)
    actualizar.mutate(
      { activo: false, motivo },
      {
        onSuccess: onDesactivada,
        onError: (fallo) => {
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo desactivar la cuenta. Intenta de nuevo.',
          )
        },
      },
    )
  }

  return (
    <ModalMotivo
      abierto
      titulo="Desactivar cuenta"
      descripcion={`«${usuario.nombreMostrado}» no podrá operar en SIGLIM. La cuenta y su historial se conservan, y puedes volver a activarla después.`}
      aviso={
        error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )
      }
      etiquetaConfirmar="Desactivar cuenta"
      varianteConfirmar="destructivo"
      procesando={actualizar.isPending}
      onConfirmar={enviar}
      onCancelar={onCerrar}
    />
  )
}
