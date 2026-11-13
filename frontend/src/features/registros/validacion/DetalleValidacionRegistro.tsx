import { useId, useState } from 'react'
import { ArrowLeft, CircleCheck, CircleX, FileCheck2, Gavel, Info, Lock } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useRecargarRegistro, useValidarRegistro } from '@/features/registros/api'
import { CabeceraRegistro } from '@/features/registros/documentos/CabeceraRegistro'
import { etiquetaEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import { TarjetaRequisito } from '@/features/registros/documentos/TarjetaRequisito'
import { VisorDocumento } from '@/features/registros/documentos/VisorDocumento'
import type {
  DocumentoRegistro,
  RegistroJugadorDetalle,
  RequisitosRegistro,
} from '@/features/registros/tipos'
import {
  accionErrorValidacion,
  type AccionErrorValidacion,
} from '@/features/registros/validacion/erroresValidacion'
import {
  TEXTO_FALTANTE,
  faltantesDeRequisitos,
  motivoFaltante,
} from '@/features/registros/validacion/faltantes'
import { ModalActivarRegistro } from '@/features/registros/validacion/ModalActivarRegistro'
import { formatearFechaHora } from '@/features/temporadas/fechas'

type Props = {
  registro: RegistroJugadorDetalle
  requisitos: RequisitosRegistro | undefined
  requisitosError: unknown
  onReintentarRequisitos: () => void
  puedeVer: boolean
  puedeValidarDocumentos: boolean
  onVolver: () => void
  onResuelto: (mensaje: string) => void
}

const AVISO_RECHAZO =
  'Un documento con problemas se rechaza como documento y el club lo reemplaza; no hace falta rechazar el registro. Rechaza el registro solo por la identidad: la persona equivocada o una CURP que no le corresponde.'

/**
 * Expediente de un registro en la bandeja (F-19, DEC-114): resumen, requisitos con lo que falta
 * («Falta cargarlo» o «Falta revisarlo»), visor con «Aprobar» y «Rechazar» por documento, y las
 * acciones del registro. «Activar registro» solo se habilita con `listoParaValidar`; un registro ya
 * resuelto muestra quién y cuándo lo resolvió, sin acciones.
 *
 * Refs: RF-109, RF-017, VAL-001, VAL-022, VAL-025, RF-013, RF-003, DEC-97 a DEC-100, DEC-114
 */
export function DetalleValidacionRegistro({
  registro,
  requisitos,
  requisitosError,
  onReintentarRequisitos,
  puedeVer,
  puedeValidarDocumentos,
  onVolver,
  onResuelto,
}: Props) {
  const id = useId()
  const validar = useValidarRegistro(registro.id)
  const recargar = useRecargarRegistro()
  const [visible, setVisible] = useState<DocumentoRegistro | null>(null)
  const [activando, setActivando] = useState(false)
  const [rechazando, setRechazando] = useState(false)
  const [errorActivar, setErrorActivar] = useState<AccionErrorValidacion | null>(null)
  const [aviso, setAviso] = useState<{ tipo: 'error' | 'exito'; texto: string } | null>(null)
  const [soloLectura, setSoloLectura] = useState(false)

  const pendiente = registro.estado === 'PENDIENTE_VALIDACION'
  const cerrada = registro.temporada.estado === 'CERRADA'
  const accionable = pendiente && !cerrada && !soloLectura
  const faltantes = requisitos ? faltantesDeRequisitos(requisitos.requisitos) : []
  const listo = requisitos?.listoParaValidar === true
  const estado = etiquetaEstadoRegistro[registro.estado]

  /** Recargas y solo lectura que pide un error (DEC-114). */
  const aplicar = (accion: AccionErrorValidacion) => {
    if (accion.soloLectura) {
      setSoloLectura(true)
    }
    if (accion.recargar === 'todo') {
      void recargar.todo(registro.id)
    } else if (accion.recargar === 'requisitos') {
      void recargar.requisitos(registro.id)
    }
  }

  const activar = (observaciones?: string) => {
    setErrorActivar(null)
    validar.mutate(
      { decision: 'ACTIVO', ...(observaciones ? { observaciones } : {}) },
      {
        onSuccess: () => {
          setActivando(false)
          onResuelto(`El registro de ${registro.persona.nombreCompleto} quedó activo.`)
        },
        onError: (error) => {
          const accion = accionErrorValidacion(error)
          setErrorActivar(accion)
          aplicar(accion)
        },
      },
    )
  }

  const rechazar = (observaciones: string) => {
    setAviso(null)
    validar.mutate(
      { decision: 'RECHAZADO', observaciones },
      {
        onSuccess: () => {
          setRechazando(false)
          onResuelto(`El registro de ${registro.persona.nombreCompleto} se rechazó con observaciones.`)
        },
        onError: (error) => {
          const accion = accionErrorValidacion(error)
          setRechazando(false)
          setAviso({ tipo: 'error', texto: accion.mensaje })
          aplicar(accion)
        },
      },
    )
  }

  const puedeValidar = (documento: DocumentoRegistro) =>
    puedeValidarDocumentos && accionable && documento.estadoValidacion === 'PENDIENTE'

  return (
    <article
      aria-labelledby={`${id}-nombre`}
      className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-surface"
    >
      <button
        type="button"
        onClick={onVolver}
        className="flex min-h-11 items-center gap-2 border-b border-border px-4 text-label-md text-primary hover:bg-background lg:hidden"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Volver a la bandeja
      </button>

      <header className="border-b border-border p-4 sm:p-6">
        <CabeceraRegistro registro={registro} idTitulo={`${id}-nombre`} />
      </header>

        <section
          aria-labelledby={`${id}-validacion`}
          className="flex min-w-0 flex-col gap-4 border-b border-border bg-background/60 p-4 sm:p-6"
        >
          <h3 id={`${id}-validacion`} className="flex items-center gap-2 text-headline-sm text-text-main">
            <Gavel aria-hidden="true" className="size-5 text-primary" />
            {pendiente ? 'Validación' : 'Resolución'}
          </h3>

          {aviso && (
            <p
              role={aviso.tipo === 'error' ? 'alert' : 'status'}
              className={[
                'rounded-md p-3 text-body-md',
                aviso.tipo === 'error' ? 'bg-error/8 text-error-strong' : 'bg-success/8 text-success-strong',
              ].join(' ')}
            >
              {aviso.texto}
            </p>
          )}

          {!pendiente ? (
            <div className="flex flex-col gap-3">
              <div>
                <Insignia variante={estado.variante} icono={estado.icono}>
                  {estado.texto}
                </Insignia>
              </div>
              <dl className="grid gap-x-3 gap-y-2 text-body-sm sm:grid-cols-[auto_minmax(0,1fr)]">
                <dt className="text-text-muted">Resolvió</dt>
                <dd className="min-w-0 break-words text-text-main">
                  {registro.validadoPor?.nombre ?? 'Usuario de la liga'}
                </dd>
                <dt className="text-text-muted">Fecha</dt>
                <dd className="text-text-main">
                  {registro.validadoEn ? formatearFechaHora(registro.validadoEn) : 'Sin fecha registrada'}
                </dd>
              </dl>
              {registro.observaciones && (
                <div className="rounded-md bg-background p-3">
                  <p className="text-label-md text-text-muted">Observaciones</p>
                  <p className="mt-1 whitespace-pre-line break-words text-body-md text-text-main">
                    {registro.observaciones}
                  </p>
                </div>
              )}
            </div>
          ) : !accionable ? (
            <p className="flex items-start gap-2 rounded-md bg-warning/14 p-3 text-body-md text-warning-strong">
              <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              La temporada está cerrada: el registro ya no admite validación.
            </p>
          ) : (
            <div className="flex flex-col gap-3">
              {requisitos && !listo && (
                <div className="flex flex-col gap-2 rounded-md bg-background p-3">
                  <p className="flex items-start gap-2 text-body-md text-text-main">
                    <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-text-muted" />
                    Para activar el registro, todos los documentos deben estar aprobados.
                  </p>
                  {faltantes.length > 0 && (
                    <ul aria-label="Documentos pendientes" className="flex flex-col gap-1 pl-6">
                      {faltantes.map((faltante) => (
                        <li key={faltante.tipoDocumentoId} className="text-body-md text-text-main">
                          <span className="font-medium">{faltante.nombre}:</span>{' '}
                          <span className="text-text-muted">
                            {TEXTO_FALTANTE[faltante.motivo].toLowerCase()}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
              <div className="flex flex-col gap-2 sm:flex-row-reverse sm:justify-start">
                <Button
                  icono={CircleCheck}
                  disabled={!listo || validar.isPending}
                  onClick={() => {
                    setAviso(null)
                    setErrorActivar(null)
                    setActivando(true)
                  }}
                  className="w-full sm:w-auto"
                >
                  Activar registro
                </Button>
                <Button
                  variante="destructivo"
                  icono={CircleX}
                  disabled={validar.isPending}
                  onClick={() => {
                    setAviso(null)
                    setRechazando(true)
                  }}
                  className="w-full sm:w-auto"
                >
                  Rechazar registro
                </Button>
              </div>
            </div>
          )}
        </section>
        <section
          aria-labelledby={`${id}-documentos`}
          className="flex min-w-0 flex-col gap-3 p-4 sm:p-6"
        >
          <h3 id={`${id}-documentos`} className="flex items-center gap-2 text-headline-sm text-text-main">
            <FileCheck2 aria-hidden="true" className="size-5 text-primary" />
            Documentos
          </h3>

          {requisitosError != null ? (
            <ErrorConsulta error={requisitosError} onReintentar={onReintentarRequisitos} />
          ) : !requisitos ? (
            <EstadoCarga etiqueta="Cargando los documentos…" filas={3} columnas={2} />
          ) : requisitos.requisitos.length === 0 ? (
            <EstadoVacio
              icono={FileCheck2}
              titulo="La categoría no pide documentos"
              descripcion={`No hay documentos configurados para «${requisitos.categoria.nombre}».`}
            />
          ) : (
            requisitos.requisitos.map((requisito) => (
              <TarjetaRequisito
                key={requisito.tipoDocumento.id}
                requisito={requisito}
                editable={false}
                puedeVer={puedeVer}
                faltante={pendiente ? motivoFaltante(requisito) : undefined}
                onVer={(documento) => {
                  setAviso(null)
                  setVisible(documento)
                }}
              />
            ))
          )}
        </section>


      {visible && (
        <VisorDocumento
          key={visible.id}
          documento={visible}
          onCerrar={() => setVisible(null)}
          validacion={
            puedeValidar(visible)
              ? {
                  onValidado: (documento) => {
                    setVisible(null)
                    setAviso({
                      tipo: 'exito',
                      texto:
                        documento.estadoValidacion === 'APROBADO'
                          ? `${documento.tipoDocumento.nombre}: aprobado.`
                          : `${documento.tipoDocumento.nombre}: rechazado. El club podrá reemplazarlo.`,
                    })
                  },
                  onError: aplicar,
                }
              : undefined
          }
        />
      )}

      {activando && (
        <ModalActivarRegistro
          nombre={registro.persona.nombreCompleto}
          equipo={registro.equipo.nombre}
          procesando={validar.isPending}
          error={errorActivar}
          onConfirmar={activar}
          onCerrar={() => {
            setActivando(false)
            setErrorActivar(null)
          }}
        />
      )}

      <ModalMotivo
        abierto={rechazando}
        titulo="Rechazar registro"
        descripcion={`El registro de ${registro.persona.nombreCompleto} quedará RECHAZADO y no se podrá reactivar. El club verá el motivo.`}
        aviso={
          <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-md text-info-strong">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            {AVISO_RECHAZO}
          </p>
        }
        etiquetaConfirmar="Rechazar registro"
        varianteConfirmar="destructivo"
        procesando={validar.isPending}
        onConfirmar={rechazar}
        onCancelar={() => setRechazando(false)}
      />
    </article>
  )
}
