import { useId, useState, type FormEvent } from 'react'
import { Send } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useEnviarFecha } from '@/features/programacion/api'
import { diasDeFecha } from '@/features/programacion/fechas'
import type { FechaProgramacion } from '@/features/programacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  fecha: FechaProgramacion
  /** Advertencias que no bloquean (sin árbitro, sin asignar, localía), para que la persona decida. */
  advertencias: number
  onCerrar: () => void
  onEnviada: (fecha: FechaProgramacion) => void
}

/**
 * Confirmación del envío de la fecha completa (F-22, H-35, RF-121, DEC-175): todas sus jornadas
 * pasan juntas a Revisión. Si el backend encuentra conflictos nuevos (`409 RF-039`), el modal
 * explica la causa y la pantalla refresca la verificación para marcarlos en cada fila.
 */
export function ModalEnvioFecha({ fecha, advertencias, onCerrar, onEnviada }: Props) {
  const dialogoRef = useDialogoModal()
  const enviar = useEnviarFecha()
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    enviar.mutate(fecha.id, {
      onSuccess: onEnviada,
      onError: (fallo) => {
        if (!(fallo instanceof ApiError)) {
          setError('No se pudo enviar la fecha a aprobación. Intenta de nuevo.')
          return
        }

        setError(
          fallo.codigo === 'RF-039'
            ? `${fallo.problema.detail} Cierra este aviso para ver los conflictos marcados en cada partido.`
            : fallo.problema.detail,
        )
      },
    })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!enviar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            Enviar la fecha {fecha.numero} a aprobación
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            Las {fecha.jornadas} {fecha.jornadas === 1 ? 'jornada' : 'jornadas'} del{' '}
            {diasDeFecha(fecha.dias)}, con {fecha.partidos}{' '}
            {fecha.partidos === 1 ? 'partido' : 'partidos'}, pasarán juntas a revisión de la
            Presidencia. Mientras estén en revisión no podrás asignar ni ajustar partidos.
          </p>
        </div>

        {(fecha.sinAsignar > 0 || advertencias > 0) && (
          <p className="rounded-md border border-warning/30 bg-warning/8 p-3 text-body-md text-warning-strong">
            {fecha.sinAsignar > 0
              ? `${fecha.sinAsignar} ${fecha.sinAsignar === 1 ? 'partido queda' : 'partidos quedan'} sin asignar y no se publicarán. `
              : ''}
            Las advertencias no impiden el envío, pero la Presidencia las verá al revisar.
          </p>
        )}

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={enviar.isPending}>
            {error ? 'Cerrar' : 'Cancelar'}
          </Button>
          <Button type="submit" icono={Send} cargando={enviar.isPending} disabled={enviar.isPending}>
            Enviar fecha
          </Button>
        </div>
      </form>
    </dialog>
  )
}
