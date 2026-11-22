import { useId } from 'react'
import { History, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useHistorialRegistro } from '@/features/registros/api'
import { etiquetaEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import type { CambioEstadoRegistro, RegistroJugadorFila } from '@/features/registros/tipos'
import { formatearFechaHora } from '@/features/temporadas/fechas'
import { reintentarConsulta } from '@/lib/api/errores'

type Props = {
  registro: RegistroJugadorFila
  onCerrar: () => void
}

/*
 * [PROPUESTA] El contrato deja `cambiadoPor.nombre` en `null` cuando la sesión no puede ver ese
 * perfil (hoy, una cuenta de club no ve los de la liga) y `cambiadoPor` en `null` si no se guardó
 * usuario. Los textos de esos dos casos son del equipo; ajustarlos si la Liga prefiere otros.
 */
function autor(cambio: CambioEstadoRegistro): string {
  if (!cambio.cambiadoPor) {
    return 'Sin usuario registrado'
  }
  return cambio.cambiadoPor.nombre ? `Por ${cambio.cambiadoPor.nombre}` : 'Por otro usuario'
}

function origen(cambio: CambioEstadoRegistro): string {
  return cambio.estadoAnterior
    ? `Antes: ${etiquetaEstadoRegistro[cambio.estadoAnterior].texto}`
    : 'Estado inicial'
}

/**
 * Historial de estados de un registro desde la plantilla (F-17, B-07.2, RF-018): consulta
 * `GET /registros-jugador/{id}/historial` y lo muestra como línea de tiempo del alta al cambio más
 * reciente, con el estado (ícono y texto), la fecha y hora, quién lo hizo y el motivo. Solo lectura.
 *
 * Refs: B-07, RF-018, UI-006, VAL-024
 */
export function ModalHistorialRegistro({ registro, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const consulta = useHistorialRegistro(registro.id)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const nombre = registro.persona.nombreCompleto

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
      onCancel={(evento) => {
        evento.preventDefault()
        onCerrar()
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex flex-col gap-5 p-6">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 id={idTitulo} className="text-headline-md text-balance">
              Historial de {nombre}
            </h2>
            <p id={idDescripcion} className="mt-1 text-body-sm text-text-muted">
              {registro.equipo.nombre} · {registro.categoria.nombre} · {registro.temporada.nombre}
            </p>
          </div>
          <button
            type="button"
            aria-label="Cerrar historial"
            onClick={onCerrar}
            className="inline-flex size-11 shrink-0 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-background hover:text-text-main"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        {consulta.isPending && (
          <EstadoCarga etiqueta="Cargando el historial…" filas={3} columnas={2} />
        )}

        {consulta.isError && (
          <ErrorConsulta
            error={consulta.error}
            onReintentar={
              reintentarConsulta(0, consulta.error) ? () => void consulta.refetch() : undefined
            }
          />
        )}

        {consulta.data &&
          (consulta.data.length === 0 ? (
            <EstadoVacio
              icono={History}
              titulo="Este registro aún no tiene cambios de estado"
              descripcion="El alta y cada cambio de estado aparecerán aquí con su motivo."
            />
          ) : (
            <ol aria-label={`Cambios de estado de ${nombre}`} className="flex flex-col">
              {consulta.data.map((cambio, indice) => {
                const estado = etiquetaEstadoRegistro[cambio.estadoNuevo]
                const ultimo = indice === consulta.data.length - 1

                return (
                  <li
                    key={`${cambio.cambiadoEn}-${indice}`}
                    className="relative grid grid-cols-[1rem_minmax(0,1fr)] gap-x-3"
                  >
                    <span aria-hidden="true" className="relative flex justify-center">
                      <span className="mt-1.5 size-2.5 rounded-full border-2 border-text-muted bg-surface" />
                      {!ultimo && (
                        <span className="absolute top-5 bottom-0 w-px bg-border" />
                      )}
                    </span>
                    <div className={`flex min-w-0 flex-col gap-1.5 ${ultimo ? '' : 'pb-5'}`}>
                      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <Insignia variante={estado.variante} icono={estado.icono}>
                          {estado.texto}
                        </Insignia>
                        <span className="text-body-sm text-text-muted">{origen(cambio)}</span>
                      </div>
                      <p className="text-body-sm text-text-muted">
                        <time dateTime={cambio.cambiadoEn} className="tabular-nums">
                          {formatearFechaHora(cambio.cambiadoEn)}
                        </time>
                        {' · '}
                        {autor(cambio)}
                      </p>
                      <p className="break-words text-body-md text-text-main">
                        <span className="sr-only">Motivo: </span>«{cambio.motivo}»
                      </p>
                    </div>
                  </li>
                )
              })}
            </ol>
          ))}

        <div className="flex justify-end">
          <Button variante="secundario" onClick={onCerrar}>
            Cerrar
          </Button>
        </div>
      </div>
    </dialog>
  )
}
