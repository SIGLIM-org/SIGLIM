import { useState } from 'react'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { esBloqueoAceptable, partidosAfectadosDe, useActualizarCancha } from '@/features/canchas/api'
import { AvisoPartidosAfectados } from '@/features/canchas/AvisoPartidosAfectados'
import type { Cancha } from '@/features/canchas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  cancha: Cancha
  onCerrar: () => void
  onActualizada: (cancha: Cancha) => void
}

/**
 * Activa o desactiva una cancha con motivo (RF-027, RN-021). Desactivarla con partidos en
 * curso o futuros pide confirmación con el mismo flujo del bloqueo (RF-030, D8).
 */
export function ModalEstadoCancha({ cancha, onCerrar, onActualizada }: Props) {
  const actualizar = useActualizarCancha(cancha.id)
  const [aviso, setAviso] = useState<ApiError | null>(null)
  const [errorApi, setErrorApi] = useState<ApiError | null>(null)
  const desactivar = cancha.activa

  const enviar = (motivo: string) => {
    setErrorApi(null)
    actualizar.mutate(
      {
        activa: !desactivar,
        motivo,
        ...(aviso ? { confirmarPartidosAfectados: true } : {}),
      },
      {
        onSuccess: onActualizada,
        onError: (error) => {
          if (esBloqueoAceptable(error) && error instanceof ApiError) {
            setAviso(error)
          } else if (error instanceof ApiError) {
            setErrorApi(error)
          }
        },
      },
    )
  }

  return (
    <ModalMotivo
      abierto
      titulo={desactivar ? 'Desactivar cancha' : 'Activar cancha'}
      descripcion={
        desactivar
          ? 'La cancha no podrá usarse en la programación.'
          : 'La cancha volverá a poder usarse en la programación.'
      }
      aviso={
        <>
          {aviso && (
            <div role="status">
              <AvisoPartidosAfectados
                mensaje={aviso.problema.detail}
                nota="Los partidos no se reprogramarán ni se cancelarán."
                partidos={partidosAfectadosDe(aviso)}
              />
            </div>
          )}
          {errorApi && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorApi.problema.detail}
            </p>
          )}
        </>
      }
      etiquetaConfirmar={aviso ? 'Desactivar de todos modos' : desactivar ? 'Desactivar' : 'Activar'}
      varianteConfirmar={desactivar ? 'destructivo' : 'primario'}
      procesando={actualizar.isPending}
      onConfirmar={enviar}
      onCancelar={onCerrar}
    />
  )
}
