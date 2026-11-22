import { useId, useState, type FormEvent } from 'react'
import { CircleCheck } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import type { AccionErrorValidacion } from '@/features/registros/validacion/erroresValidacion'
import { TEXTO_FALTANTE } from '@/features/registros/validacion/faltantes'

/** Tope de las observaciones, como las demás transiciones (DEC-98). */
const MAXIMO_OBSERVACIONES = 500

type Props = {
  nombre: string
  equipo: string
  procesando: boolean
  error: AccionErrorValidacion | null
  /** Recibe las observaciones recortadas, o `undefined` si quedaron vacías. */
  onConfirmar: (observaciones?: string) => void
  onCerrar: () => void
}

/**
 * Confirmación de «Activar registro» (F-19, DEC-114), con observaciones opcionales de hasta 500
 * caracteres. Un `422 VAL-001` muestra el `detail` y, con la extensión `faltantes`, qué falta
 * cargar o revisar.
 *
 * Refs: RF-109, VAL-001, VAL-025, RF-013, DEC-98, DEC-99, DEC-114
 */
export function ModalActivarRegistro({ nombre, equipo, procesando, error, onConfirmar, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const [observaciones, setObservaciones] = useState('')
  const id = useId()
  const texto = observaciones.trim()

  const confirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    onConfirmar(texto || undefined)
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      aria-describedby={`${id}-descripcion`}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!procesando) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={confirmar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={`${id}-titulo`} className="text-balance text-headline-md">
            Activar registro
          </h2>
          <p id={`${id}-descripcion`} className="mt-1 text-body-md text-text-muted">
            El registro de {nombre} en {equipo} quedará ACTIVO. Esta acción no se puede deshacer.
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={`${id}-observaciones`} className="text-label-md text-text-main">
            Observaciones (opcional)
          </label>
          <textarea
            id={`${id}-observaciones`}
            value={observaciones}
            rows={3}
            maxLength={MAXIMO_OBSERVACIONES}
            aria-describedby={`${id}-ayuda`}
            onChange={(evento) => setObservaciones(evento.target.value)}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main"
          />
          <p id={`${id}-ayuda`} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>Quedan en el historial del registro.</span>
            <span className="font-mono">
              {texto.length}/{MAXIMO_OBSERVACIONES}
            </span>
          </p>
        </div>

        {error && (
          <div role="alert" className="flex flex-col gap-2 rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            <p>{error.mensaje}</p>
            {error.faltantes.length > 0 && (
              <ul className="flex flex-col gap-1">
                {error.faltantes.map((faltante) => (
                  <li key={faltante.tipoDocumentoId}>
                    <span className="font-medium">{faltante.nombre}:</span>{' '}
                    {TEXTO_FALTANTE[faltante.motivo].toLowerCase()}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={procesando}>
            Cancelar
          </Button>
          <Button type="submit" icono={CircleCheck} cargando={procesando}>
            Activar registro
          </Button>
        </div>
      </form>
    </dialog>
  )
}
