import { useState } from 'react'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useDarDeBajaEquipo } from '@/features/organizacion/api'
import { detalleDeFallo } from '@/features/organizacion/erroresFormulario'
import type { Equipo } from '@/features/organizacion/tipos'

type Props = {
  equipo: Equipo
  onCerrar: () => void
  onDadoDeBaja: (equipo: Equipo) => void
}

/**
 * Baja de un equipo con motivo obligatorio (F-15, RF-009, RN-021). El equipo queda en BAJA y se
 * conserva con su historial (RN-005); el servidor explica si no procede (RF-009, RF-003).
 */
export function ModalBajaEquipo({ equipo, onCerrar, onDadoDeBaja }: Props) {
  const baja = useDarDeBajaEquipo(equipo.id)
  const [error, setError] = useState<string | null>(null)

  const enviar = (motivo: string) => {
    setError(null)
    baja.mutate(
      { motivo },
      {
        onSuccess: onDadoDeBaja,
        onError: (fallo) => setError(detalleDeFallo(fallo, 'No se pudo dar de baja el equipo. Intenta de nuevo.')),
      },
    )
  }

  return (
    <ModalMotivo
      abierto
      titulo={`Dar de baja — ${equipo.nombre}`}
      descripcion="El equipo quedará en BAJA y ya no podrá editarse. No se borra: conserva su historial de partidos y registros."
      aviso={
        error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )
      }
      etiquetaConfirmar="Dar de baja"
      varianteConfirmar="destructivo"
      procesando={baja.isPending}
      onConfirmar={enviar}
      onCancelar={onCerrar}
    />
  )
}
