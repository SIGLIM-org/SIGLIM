import { useEffect, useId, useRef, useState } from 'react'
import {
  CircleCheck,
  CircleX,
  ExternalLink,
  RotateCw,
  Timer,
  TriangleAlert,
  X,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { Insignia } from '@/components/ui/Insignia'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useEnlaceDocumento, useValidarDocumento } from '@/features/registros/api'
import { esMimeImagen, tamanoLegible } from '@/features/registros/documentos/archivoDocumento'
import { etiquetaEstadoDocumento } from '@/features/registros/documentos/etiquetas'
import type { DocumentoRegistro } from '@/features/registros/tipos'
import {
  accionErrorValidacion,
  type AccionErrorValidacion,
} from '@/features/registros/validacion/erroresValidacion'
import { formatearFechaHora } from '@/features/temporadas/fechas'
import { ApiError } from '@/lib/api/ApiError'

type Validacion = {
  /** Se resolvió el documento: la bandeja cierra el visor y avisa. */
  onValidado: (documento: DocumentoRegistro) => void
  /** Error de la validación, para recargar o pasar a solo lectura (DEC-114). */
  onError: (accion: AccionErrorValidacion) => void
}

type Props = {
  documento: DocumentoRegistro
  onCerrar: () => void
  /**
   * Solo en la bandeja, con `registros.documento:VALIDAR`, documento PENDIENTE, registro
   * PENDIENTE_VALIDACION y temporada no CERRADA. Sin esta prop, el visor no ofrece acciones.
   */
  validacion?: Validacion
}

const ERROR_ENLACE = 'No se pudo abrir el documento. Revisa tu conexión e intenta de nuevo.'

/** «0:45». */
function cuentaRegresiva(milisegundos: number): string {
  const segundos = Math.ceil(milisegundos / 1000)

  return `${Math.floor(segundos / 60)}:${String(segundos % 60).padStart(2, '0')}`
}

/**
 * Visor seguro de un documento (DEC-113). Al abrirse pide `GET /documentos/{id}/enlace`, una URL
 * prefirmada de 60 s que vive solo en el estado de este modal y se descarta al cerrarlo: nunca va a
 * la caché de consultas, al almacenamiento del navegador ni a la consola (RNF-018). No se renueva
 * sola, porque cada enlace se audita como `CONSULTAR_DOCUMENTO`: al vencer queda a la vista lo ya
 * cargado y se ofrece «Ver de nuevo».
 *
 * Refs: RF-108, RF-109, RNF-018, VAL-022, DEC-95, DEC-113, DEC-114
 */
export function VisorDocumento({ documento, onCerrar, validacion }: Props) {
  const dialogoRef = useDialogoModal()
  const id = useId()
  const enlace = useEnlaceDocumento()
  const validar = useValidarDocumento()
  const [ahora, setAhora] = useState(() => Date.now())
  const [falloCarga, setFalloCarga] = useState(false)
  const [confirmarAprobar, setConfirmarAprobar] = useState(false)
  const [rechazando, setRechazando] = useState(false)
  const [errorValidacion, setErrorValidacion] = useState<string | null>(null)
  const pedido = useRef(false)

  const nombre = documento.tipoDocumento.nombre
  const estado = etiquetaEstadoDocumento[documento.estadoValidacion]
  const esImagen = esMimeImagen(documento.tipoMime)
  const { mutate: pedirEnlace } = enlace
  const datos = enlace.data
  const restante = datos ? Math.max(0, new Date(datos.expiraEn).getTime() - ahora) : 0
  const vencido = Boolean(datos) && restante === 0
  const ocupado = validar.isPending

  // Un solo enlace por apertura, aunque React monte dos veces el efecto en desarrollo.
  useEffect(() => {
    if (!pedido.current) {
      pedido.current = true
      pedirEnlace(documento.id)
    }
  }, [pedirEnlace, documento.id])

  // Cuenta regresiva desde `expiraEn`; se detiene al vencer.
  useEffect(() => {
    if (!datos || vencido) {
      return
    }
    const intervalo = window.setInterval(() => setAhora(Date.now()), 1000)

    return () => window.clearInterval(intervalo)
  }, [datos, vencido])

  const verDeNuevo = () => {
    setFalloCarga(false)
    setAhora(Date.now())
    pedirEnlace(documento.id)
  }

  const cerrar = () => {
    if (ocupado) {
      return
    }
    // La URL se descarta con el estado del modal (DEC-113).
    enlace.reset()
    onCerrar()
  }

  const resolver = (decision: 'APROBADO' | 'RECHAZADO', observaciones?: string) => {
    if (!validacion) {
      return
    }
    setErrorValidacion(null)
    validar.mutate(
      { documentoId: documento.id, decision, ...(observaciones ? { observaciones } : {}) },
      {
        onSuccess: (resuelto) => {
          setRechazando(false)
          enlace.reset()
          validacion.onValidado(resuelto)
        },
        onError: (error) => {
          const accion = accionErrorValidacion(error)
          setRechazando(false)
          setConfirmarAprobar(false)
          setErrorValidacion(accion.mensaje)
          validacion.onError(accion)
        },
      },
    )
  }

  const errorEnlace =
    enlace.error instanceof ApiError ? enlace.error.problema.detail : enlace.error ? ERROR_ENLACE : null
  const reintentable =
    !(enlace.error instanceof ApiError) || enlace.error.problema.codigo === 'SERVICIO_NO_DISPONIBLE'

  const avisoVencido = (
    <div
      role="alert"
      className="flex flex-col items-start gap-3 rounded-md bg-warning/14 p-3 text-body-md text-warning-strong sm:flex-row sm:items-center sm:justify-between"
    >
      <span className="flex items-start gap-2">
        <Timer aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        El enlace venció. Por seguridad dura un minuto; pide otro para seguir viendo el documento.
      </span>
      <Button variante="secundario" icono={RotateCw} onClick={verDeNuevo} className="shrink-0">
        Ver de nuevo
      </Button>
    </div>
  )

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!rechazando) {
          cerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-3xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex items-start justify-between gap-3 border-b border-border p-4 sm:p-5">
        <div className="flex min-w-0 flex-col gap-1.5">
          <h2 id={`${id}-titulo`} className="break-words text-headline-md">
            {nombre}
          </h2>
          <p className="flex flex-wrap items-center gap-2 text-body-sm text-text-muted">
            <Insignia variante={estado.variante} icono={estado.icono}>
              {estado.texto}
            </Insignia>
            <span className="font-mono tabular-nums">{tamanoLegible(documento.tamanoBytes)}</span>
            <span>Cargado el {formatearFechaHora(documento.cargadoEn)}</span>
          </p>
        </div>
        <button
          type="button"
          aria-label="Cerrar"
          disabled={ocupado}
          onClick={cerrar}
          className="grid size-11 shrink-0 place-items-center rounded-md text-text-muted transition-colors hover:bg-background hover:text-text-main disabled:opacity-50"
        >
          <X aria-hidden="true" className="size-5" />
        </button>
      </div>

      <div className="flex flex-col gap-4 p-4 sm:p-5">
        {enlace.isPending && (
          <p role="status" className="grid min-h-48 place-items-center rounded-md bg-background text-body-md text-text-muted">
            Abriendo el documento…
          </p>
        )}

        {errorEnlace && (
          <div
            role="alert"
            className="flex flex-col items-start gap-3 rounded-md bg-error/8 p-3 text-body-md text-error-strong"
          >
            <span className="flex items-start gap-2">
              <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              {errorEnlace}
            </span>
            {reintentable && (
              <Button variante="secundario" icono={RotateCw} onClick={verDeNuevo}>
                Reintentar
              </Button>
            )}
          </div>
        )}

        {datos && falloCarga && avisoVencido}

        {datos && !falloCarga && (
          <>
            <div className="overflow-hidden rounded-md border border-border bg-background">
              {esImagen ? (
                <img
                  src={datos.url}
                  alt={`Documento: ${nombre}`}
                  referrerPolicy="no-referrer"
                  onError={() => setFalloCarga(true)}
                  className="mx-auto max-h-[60dvh] w-auto object-contain"
                />
              ) : (
                // El iframe no dispara onError ante un 403 de S3 (enlace vencido): la cuenta
                // regresiva es la protección. Chrome en Android no muestra PDF aquí; para eso está
                // «Abrir en otra pestaña».
                <iframe
                  src={datos.url}
                  title={`Documento: ${nombre}`}
                  referrerPolicy="no-referrer"
                  className="h-[60dvh] w-full bg-surface"
                />
              )}
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              {vencido ? (
                <span className="flex items-center gap-2 text-body-sm text-text-muted">
                  <Timer aria-hidden="true" className="size-4" />
                  Enlace vencido
                </span>
              ) : (
                <span className="flex items-center gap-2 text-body-sm text-text-muted">
                  <Timer aria-hidden="true" className="size-4" />
                  Enlace temporal: vence en{' '}
                  <span className="font-mono tabular-nums text-text-main">
                    {cuentaRegresiva(restante)}
                  </span>
                </span>
              )}
              {vencido ? (
                <span
                  aria-disabled="true"
                  className={`${clasesBoton('secundario')} cursor-not-allowed opacity-50`}
                >
                  <ExternalLink aria-hidden="true" className="size-4 shrink-0" />
                  Abrir en otra pestaña
                </span>
              ) : (
                <a
                  href={datos.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  referrerPolicy="no-referrer"
                  className={clasesBoton('secundario')}
                >
                  <ExternalLink aria-hidden="true" className="size-4 shrink-0" />
                  Abrir en otra pestaña
                </a>
              )}
            </div>

            {vencido && avisoVencido}
          </>
        )}

        {documento.observaciones && (
          <p className="whitespace-pre-line break-words rounded-md bg-background p-3 text-body-md text-text-main">
            <span className="font-medium">
              {documento.estadoValidacion === 'RECHAZADO' ? 'Motivo del rechazo: ' : 'Observaciones: '}
            </span>
            {documento.observaciones}
          </p>
        )}

        {validacion && (
          <section
            aria-labelledby={`${id}-validacion`}
            className="flex flex-col gap-3 border-t border-border pt-4"
          >
            <h3 id={`${id}-validacion`} className="text-headline-sm text-text-main">
              Revisión del documento
            </h3>

            {errorValidacion && (
              <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
                {errorValidacion}
              </p>
            )}

            {confirmarAprobar ? (
              <div className="flex flex-col gap-3 rounded-md bg-warning/14 p-3">
                <p className="flex items-start gap-2 text-body-md text-warning-strong">
                  <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                  Aprobar es definitivo: ya no se podrá reemplazar.
                </p>
                <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <Button
                    variante="secundario"
                    disabled={ocupado}
                    onClick={() => setConfirmarAprobar(false)}
                  >
                    Cancelar
                  </Button>
                  <Button icono={CircleCheck} cargando={ocupado} onClick={() => resolver('APROBADO')}>
                    Confirmar
                  </Button>
                </div>
              </div>
            ) : (
              <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <Button
                  variante="destructivo"
                  icono={CircleX}
                  disabled={ocupado}
                  onClick={() => {
                    setErrorValidacion(null)
                    setRechazando(true)
                  }}
                >
                  Rechazar
                </Button>
                <Button
                  icono={CircleCheck}
                  disabled={ocupado}
                  onClick={() => {
                    setErrorValidacion(null)
                    setConfirmarAprobar(true)
                  }}
                >
                  Aprobar
                </Button>
              </div>
            )}
          </section>
        )}
      </div>

      <ModalMotivo
        abierto={rechazando}
        titulo={`Rechazar ${nombre}`}
        descripcion="El club verá el motivo y podrá reemplazar el archivo. El registro sigue pendiente."
        etiquetaConfirmar="Rechazar documento"
        varianteConfirmar="destructivo"
        procesando={ocupado}
        onConfirmar={(motivo) => resolver('RECHAZADO', motivo)}
        onCancelar={() => setRechazando(false)}
      />
    </dialog>
  )
}
