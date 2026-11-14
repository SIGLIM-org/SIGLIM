import { useState } from 'react'
import { TriangleAlert } from 'lucide-react'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useDarDeBajaRegistro } from '@/features/registros/api'
import { accionErrorBaja } from '@/features/registros/plantilla/erroresBaja'
import type { RegistroJugador, RegistroJugadorFila } from '@/features/registros/tipos'

type Props = {
  registro: RegistroJugadorFila
  onCerrar: () => void
  onDadoDeBaja: (registro: RegistroJugador) => void
  /** Error que cierra el modal y se muestra en la plantilla (DEC-129). */
  onErrorPagina: (mensaje: string, recargar: boolean) => void
}

const AVISOS = [
  'Dejará de ser elegible desde hoy. La baja no se puede deshacer.',
  'El registro y su historial se conservan; el motivo queda en el historial.',
  'Para que vuelva a jugar en este equipo, el club tendrá que darlo de alta de nuevo y la liga validar sus documentos otra vez.',
  'En esta temporada no podrá registrarse en otro equipo ni en otro club.',
]

/**
 * Baja de un registro `ACTIVO` con motivo obligatorio (F-17, RF-014, RN-021), sobre `ModalMotivo`
 * como la baja de un equipo. El aviso explica qué implica antes de confirmar (DEC-126). Los errores
 * que se corrigen en el motivo se quedan aquí; los que cambian la fila cierran el modal (DEC-129).
 *
 * Refs: RF-014, RF-017, RN-021, DEC-118 a DEC-124, DEC-126, DEC-129
 */
export function ModalBajaRegistro({ registro, onCerrar, onDadoDeBaja, onErrorPagina }: Props) {
  const baja = useDarDeBajaRegistro(registro.id)
  const [error, setError] = useState<string | null>(null)
  const nombre = registro.persona.nombreCompleto

  const enviar = (motivo: string) => {
    setError(null)
    baja.mutate(
      { motivo },
      {
        onSuccess: onDadoDeBaja,
        onError: (fallo) => {
          const accion = accionErrorBaja(fallo)
          if (accion.lugar === 'pagina') {
            onErrorPagina(accion.mensaje, accion.recargar)
          } else {
            setError(accion.mensaje)
          }
        },
      },
    )
  }

  return (
    <ModalMotivo
      abierto
      titulo={`Dar de baja a ${nombre}`}
      descripcion={`El registro en ${registro.equipo.nombre} (${registro.categoria.nombre}, ${registro.temporada.nombre}) pasará a Baja.`}
      aviso={
        <>
          <div className="flex gap-2 rounded-md bg-warning/14 p-3 text-warning-strong">
            <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <ul className="flex list-disc flex-col gap-1 pl-4 text-body-md">
              {AVISOS.map((aviso) => (
                <li key={aviso}>{aviso}</li>
              ))}
            </ul>
          </div>
          {error && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {error}
            </p>
          )}
        </>
      }
      etiquetaConfirmar="Dar de baja"
      varianteConfirmar="destructivo"
      procesando={baja.isPending}
      onConfirmar={enviar}
      onCancelar={onCerrar}
    />
  )
}
