import { useEffect, useId, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { CircleCheck, FileCheck2, Lock, UserRoundPlus } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { PASOS } from '@/features/registros/asistenteAlta'
import { IndicadorPasos } from '@/features/registros/IndicadorPasos'
import {
  useCargarDocumento,
  useRecargarRegistro,
  useReemplazarDocumento,
  useRegistro,
  useRequisitosRegistro,
} from '@/features/registros/api'
import { CabeceraRegistro } from '@/features/registros/documentos/CabeceraRegistro'
import { CargaDocumento } from '@/features/registros/documentos/CargaDocumento'
import { accionErrorCarga, type AccionErrorCarga } from '@/features/registros/documentos/erroresCarga'
import { TarjetaRequisito } from '@/features/registros/documentos/TarjetaRequisito'
import { VisorDocumento } from '@/features/registros/documentos/VisorDocumento'
import { CARGAR_DOCUMENTO, usePermisosRegistros } from '@/features/registros/permisos'
import {
  RUTA_ALTA_JUGADOR,
  RUTA_JUGADORES,
  rutaPlantilla,
  vieneDelAlta,
} from '@/features/registros/rutas'
import type { DocumentoRegistro, RegistroJugadorDetalle } from '@/features/registros/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de documentos de un registro (paso 4 de F-18, H-10.3). No va en el menú: solo con
 * `registros.documento:CREAR`; el backend es la barrera (DEC-111).
 */
export function PantallaDocumentosRegistro() {
  return (
    <ConPermiso permiso={CARGAR_DOCUMENTO} alternativa={<PaginaSinPermiso rutaInicio="/app" />}>
      <PaginaDocumentosRegistro />
    </ConPermiso>
  )
}

/** Lugar con el panel de carga abierto: un lugar libre del tipo o el reemplazo de un documento. */
type Objetivo = { tipoDocumentoId: number; documento: DocumentoRegistro | null }

/** Por qué el registro no admite cargas, o `null` si las admite (DEC-112). */
function motivoSoloLectura(registro: RegistroJugadorDetalle): string | null {
  if (registro.temporada.estado === 'CERRADA') {
    return 'La temporada está cerrada: los documentos ya no se pueden cargar ni reemplazar.'
  }
  if (registro.estado === 'ACTIVO') {
    return 'La liga ya validó este registro. Sus documentos quedan como consulta.'
  }
  if (registro.estado === 'RECHAZADO') {
    return 'La liga rechazó este registro. Para registrar de nuevo a la persona, da de alta otro registro.'
  }
  if (registro.estado !== 'PENDIENTE_VALIDACION') {
    return 'El registro ya no está pendiente: sus documentos quedan como consulta.'
  }
  return null
}

/**
 * La Directiva carga y reemplaza los documentos que exige la categoría (DEC-112) y ve el estado y
 * las observaciones de cada uno. Tras el `201` del alta, el asistente llega aquí con
 * `state.desdeAlta` y la página muestra el paso 4 (DEC-111); al recargar, ya sin indicador.
 *
 * Refs: RF-108, RF-109, RF-017, RF-003, RNF-018, DEC-111, DEC-112, DEC-113, DEC-114
 */
function PaginaDocumentosRegistro() {
  const { registroId = '' } = useParams()
  const { pathname, state } = useLocation()
  const navegar = useNavigate()
  // Se lee una vez: el estado del historial sobrevive a una recarga, así que se limpia en cuanto se
  // usa para que al recargar la página ya no muestre el paso 4 (DEC-111).
  const [desdeAlta] = useState(() => vieneDelAlta(state))
  const id = useId()
  const { ver } = usePermisosRegistros()
  const detalle = useRegistro(registroId)
  const requisitos = useRequisitosRegistro(registroId)
  const recargar = useRecargarRegistro()
  const cargar = useCargarDocumento(registroId)
  const reemplazar = useReemplazarDocumento()
  const [objetivo, setObjetivo] = useState<Objetivo | null>(null)
  const [error, setError] = useState<AccionErrorCarga | null>(null)
  const [bloqueada, setBloqueada] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [visible, setVisible] = useState<DocumentoRegistro | null>(null)

  useEffect(() => {
    if (vieneDelAlta(state)) {
      void navegar(pathname, { replace: true, state: null })
    }
  }, [navegar, pathname, state])

  const errorConsulta = detalle.error ?? requisitos.error
  if (esSinPermiso(errorConsulta)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const registro = detalle.data
  const datosRequisitos = requisitos.data
  const motivo = registro ? motivoSoloLectura(registro) : null
  const editable = Boolean(registro) && motivo === null && !bloqueada
  const enviando = cargar.isPending || reemplazar.isPending

  const abrir = (nuevo: Objetivo) => {
    setError(null)
    setMensaje(null)
    setObjetivo(nuevo)
  }

  const alFallar = (fallo: unknown) => {
    const accion = accionErrorCarga(fallo)
    setError(accion)
    if (accion.soloLectura) {
      setBloqueada(true)
      setObjetivo(null)
    }
    if (accion.recargar === 'todo') {
      void recargar.todo(registroId)
    } else if (accion.recargar === 'requisitos') {
      void recargar.requisitos(registroId)
      setObjetivo(null)
    }
  }

  const subir = (archivo: File) => {
    if (!objetivo) {
      return
    }
    const nombre =
      datosRequisitos?.requisitos.find(
        ({ tipoDocumento }) => tipoDocumento.id === objetivo.tipoDocumentoId,
      )?.tipoDocumento.nombre ?? 'El documento'
    const alSubir = () => {
      setObjetivo(null)
      setError(null)
      setMensaje(`${nombre}: el archivo se subió y queda por revisar por la liga.`)
    }

    if (objetivo.documento) {
      reemplazar.mutate(
        { documentoId: objetivo.documento.id, archivo },
        { onSuccess: alSubir, onError: alFallar },
      )
    } else {
      cargar.mutate(
        { archivo, tipoDocumentoId: objetivo.tipoDocumentoId },
        { onSuccess: alSubir, onError: alFallar },
      )
    }
  }

  const totales = datosRequisitos?.requisitos.reduce(
    (suma, { cantidad, cargados, aprobados }) => ({
      cantidad: suma.cantidad + cantidad,
      cargados: suma.cargados + Math.min(cargados, cantidad),
      aprobados: suma.aprobados + Math.min(aprobados, cantidad),
    }),
    { cantidad: 0, cargados: 0, aprobados: 0 },
  )

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
          Documentos del registro
        </h1>
        <p className="max-w-prose text-body-md text-text-muted">
          Carga los documentos que pide la categoría. La liga los revisa y, con todos aprobados,
          valida el registro.
        </p>
      </header>

      {desdeAlta && (
        <>
          <IndicadorPasos pasos={PASOS} actual={4} />
          <p
            role="status"
            className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
          >
            <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            El registro se envió y quedó pendiente de validación. Carga ahora sus documentos o
            vuelve más tarde desde Jugadores.
          </p>
        </>
      )}

      {errorConsulta != null ? (
        <ErrorConsulta
          error={errorConsulta}
          onReintentar={() => {
            void detalle.refetch()
            void requisitos.refetch()
          }}
          accion={
            <Link to={RUTA_JUGADORES} className={clasesBoton('secundario')}>
              Volver a Jugadores
            </Link>
          }
        />
      ) : !registro || !datosRequisitos ? (
        <EstadoCarga etiqueta="Cargando el registro y sus documentos…" filas={4} columnas={2} />
      ) : (
        <>
          <section
            aria-labelledby={`${id}-registro`}
            className="rounded-lg border border-border bg-surface p-4 sm:p-6"
          >
            <CabeceraRegistro registro={registro} idTitulo={`${id}-registro`} />
            {(motivo || bloqueada) && (
              <div className="mt-4 flex flex-col gap-2 rounded-md bg-background p-3">
                <p className="flex items-start gap-2 text-body-md text-text-main">
                  <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-text-muted" />
                  {motivo ?? 'Este registro ya no admite cargas. Revisa su estado actual.'}
                </p>
                {registro.observaciones && (
                  <p className="whitespace-pre-line break-words text-body-md text-text-main">
                    <span className="font-medium">Observaciones de la liga: </span>
                    {registro.observaciones}
                  </p>
                )}
              </div>
            )}
          </section>

          {error?.lugar === 'general' && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {error.mensaje}
            </p>
          )}
          {mensaje && (
            <p
              role="status"
              className="flex items-start gap-2 rounded-md border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
            >
              <FileCheck2 aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              {mensaje}
            </p>
          )}

          <section aria-labelledby={`${id}-requisitos`} className="flex flex-col gap-3">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 id={`${id}-requisitos`} className="text-headline-md text-text-main">
                Documentos que pide la categoría
              </h2>
              {totales && totales.cantidad > 0 && (
                <p className="text-body-sm text-text-muted">
                  <span className="font-mono tabular-nums text-text-main">
                    {totales.cargados}/{totales.cantidad}
                  </span>{' '}
                  cargados ·{' '}
                  <span className="font-mono tabular-nums text-text-main">
                    {totales.aprobados}/{totales.cantidad}
                  </span>{' '}
                  aprobados
                </p>
              )}
            </div>

            {datosRequisitos.requisitos.length === 0 ? (
              <EstadoVacio
                icono={FileCheck2}
                titulo="Esta categoría no pide documentos"
                descripcion={`La liga no configuró documentos para «${datosRequisitos.categoria.nombre}». El registro queda pendiente hasta que la liga lo valide.`}
              />
            ) : (
              datosRequisitos.requisitos.map((requisito) => {
                const propio = objetivo?.tipoDocumentoId === requisito.tipoDocumento.id

                return (
                  <TarjetaRequisito
                    key={requisito.tipoDocumento.id}
                    requisito={requisito}
                    editable={editable}
                    puedeVer={ver}
                    cargaAbierta={propio && objetivo ? { documentoId: objetivo.documento?.id ?? null } : null}
                    panelCarga={
                      propio && objetivo ? (
                        <CargaDocumento
                          nombre={requisito.tipoDocumento.nombre}
                          modo={objetivo.documento ? 'reemplazar' : 'cargar'}
                          enviando={enviando}
                          errorServidor={error?.lugar === 'archivo' ? error.mensaje : undefined}
                          onElegir={() => setError(null)}
                          onSubir={subir}
                          onCancelar={() => {
                            setObjetivo(null)
                            setError(null)
                          }}
                        />
                      ) : undefined
                    }
                    onCargar={() => abrir({ tipoDocumentoId: requisito.tipoDocumento.id, documento: null })}
                    onReemplazar={(documento) =>
                      abrir({ tipoDocumentoId: requisito.tipoDocumento.id, documento })
                    }
                    onVer={setVisible}
                  />
                )
              })
            )}
          </section>
        </>
      )}

      <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:justify-end">
        {/* Vuelve a la plantilla del equipo del registro (DEC-116). */}
        <Link
          to={registro ? rutaPlantilla(registro.club.id, registro.equipo.id) : RUTA_JUGADORES}
          className={clasesBoton('secundario')}
        >
          Volver
        </Link>
        {desdeAlta && (
          <Link to={RUTA_ALTA_JUGADOR} className={clasesBoton('primario')}>
            <UserRoundPlus aria-hidden="true" className="size-4 shrink-0" />
            Dar de alta a otro jugador
          </Link>
        )}
      </div>

      {visible && <VisorDocumento documento={visible} onCerrar={() => setVisible(null)} />}
    </div>
  )
}
