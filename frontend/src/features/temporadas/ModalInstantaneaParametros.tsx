import { useId } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useVersionParametros } from '@/features/temporadas/api'
import { formatearFecha } from '@/features/temporadas/fechas'
import { InsigniaOficial } from '@/features/temporadas/InsigniaOficial'
import { ResumenParametros } from '@/features/temporadas/ResumenParametros'
import { reintentarConsulta } from '@/lib/api/errores'

type Props = {
  temporadaId: string
  version: number
  onCerrar: () => void
}

/**
 * Instantánea de una versión de parámetros del historial (F-11, RF-006, D7): consulta
 * `GET /temporadas/{id}/parametros/{version}` y la muestra en solo lectura.
 */
export function ModalInstantaneaParametros({ temporadaId, version, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const consulta = useVersionParametros(temporadaId, version)
  const idTitulo = `${useId()}-titulo`

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        onCerrar()
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex flex-col gap-4 p-6">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 id={idTitulo} className="text-headline-md">
              Versión {version} de los parámetros
            </h2>
            {consulta.data && (
              <div className="mt-1 flex flex-wrap items-center gap-2 text-body-sm text-text-muted">
                <InsigniaOficial esDemo={consulta.data.esDemo} />
                <span>
                  {formatearFecha(consulta.data.creadoEn.slice(0, 10))}
                  {consulta.data.creadoPor && ` · ${consulta.data.creadoPor.nombre}`}
                </span>
              </div>
            )}
          </div>
          <button
            type="button"
            aria-label="Cerrar instantánea"
            onClick={onCerrar}
            className="inline-flex size-11 shrink-0 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-background hover:text-text-main"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        {consulta.isPending && (
          <EstadoCarga etiqueta={`Cargando la versión ${version}…`} filas={4} columnas={3} />
        )}

        {consulta.isError && (
          <ErrorConsulta
            error={consulta.error}
            onReintentar={
              reintentarConsulta(0, consulta.error) ? () => void consulta.refetch() : undefined
            }
          />
        )}

        {consulta.data && (
          <>
            <p className="break-words text-body-md text-text-main">
              <span className="text-text-muted">Motivo: </span>«{consulta.data.motivo}»
            </p>
            <ResumenParametros version={consulta.data} />
          </>
        )}

        <div className="flex justify-end">
          <Button variante="secundario" onClick={onCerrar}>
            Cerrar
          </Button>
        </div>
      </div>
    </dialog>
  )
}
