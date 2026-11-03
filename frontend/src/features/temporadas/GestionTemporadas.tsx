import { useState } from 'react'
import { CalendarDays, CircleAlert, CircleCheck, Info, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useActivarTemporada, useTemporadas } from '@/features/temporadas/api'
import { FormularioTemporada } from '@/features/temporadas/FormularioTemporada'
import { ListaVerificacion } from '@/features/temporadas/ListaVerificacion'
import { ModalCerrarTemporada } from '@/features/temporadas/ModalCerrarTemporada'
import { TablaTemporadas } from '@/features/temporadas/TablaTemporadas'
import type { Temporada } from '@/features/temporadas/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { ApiError } from '@/lib/api/ApiError'
import { esSinPermiso, reintentarConsulta } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

const TAMANO = 20
const formatoEntero = new Intl.NumberFormat('es-MX')

function detalleDe(error: unknown): string {
  return error instanceof ApiError
    ? error.problema.detail
    : 'No se pudo completar la acción. Intenta de nuevo.'
}

/**
 * Gestión de temporadas (F-10, H-04.2): tabla con su estado, lista de verificación de activación,
 * alta y edición, activación y cierre con motivo. La pantalla explica por qué una temporada no
 * puede activarse en lugar de dejar el botón sin causa (RF-100, RN-058).
 */
export function GestionTemporadas() {
  const [pagina, setPagina] = useState(0)
  const [creando, setCreando] = useState(false)
  const [editando, setEditando] = useState<Temporada | null>(null)
  const [cerrando, setCerrando] = useState<Temporada | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const consulta = useTemporadas(pagina, TAMANO)
  const activar = useActivarTemporada()
  const activandoId = activar.isPending ? (activar.variables ?? null) : null

  if (esSinPermiso(consulta.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const datos = consulta.data
  const configuracion =
    datos && !consulta.isError
      ? datos.contenido.filter((temporada) => temporada.estado === 'CONFIGURACION')
      : []

  const abrirNueva = () => {
    setMensaje(null)
    setErrorAccion(null)
    setCreando(true)
  }

  const abrirEdicion = (temporada: Temporada) => {
    setMensaje(null)
    setErrorAccion(null)
    setEditando(temporada)
  }

  const abrirCierre = (temporada: Temporada) => {
    setMensaje(null)
    setErrorAccion(null)
    setCerrando(temporada)
  }

  const alActivar = (temporada: Temporada) => {
    setMensaje(null)
    setErrorAccion(null)
    activar.mutate(temporada.id, {
      onSuccess: () => setMensaje(`Temporada «${temporada.nombre}» activada.`),
      onError: (error) => {
        setErrorAccion(detalleDe(error))
        // La lista pudo quedar desactualizada: se vuelve a pedir para refrescar la verificación.
        void consulta.refetch()
      },
    })
  }

  const alCrear = (temporada: Temporada) => {
    setCreando(false)
    setMensaje(`Temporada «${temporada.nombre}» creada.`)
  }

  const alEditar = (temporada: Temporada) => {
    setEditando(null)
    setMensaje(`Temporada «${temporada.nombre}» actualizada.`)
  }

  const alCerrar = (temporada: Temporada) => {
    setCerrando(null)
    setMensaje(`Temporada «${temporada.nombre}» cerrada.`)
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Temporadas</h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Ciclos deportivos de la Liga. Solo una temporada puede estar activa y las cerradas
            conservan su histórico.
          </p>
        </div>
        <ConPermiso permiso="temporadas.temporada:EDITAR">
          <Button icono={Plus} onClick={abrirNueva} className="shrink-0">
            Nueva temporada
          </Button>
        </ConPermiso>
      </header>

      <section
        aria-label="Regla de una sola temporada activa"
        className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4"
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-info/12 text-info-strong">
          <Info aria-hidden="true" className="size-5" />
        </span>
        <div>
          <h2 className="text-headline-sm text-text-main">Una sola temporada activa</h2>
          <p className="mt-0.5 text-body-md text-text-muted">
            Para activar una temporada, cierra primero la que está activa. Una temporada cerrada
            queda en solo lectura.
          </p>
        </div>
      </section>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      {errorAccion && (
        <p
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
        >
          <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {errorAccion}
        </p>
      )}

      {consulta.isPending && (
        <EstadoCarga etiqueta="Cargando temporadas…" filas={4} columnas={5} />
      )}

      {consulta.isError && (
        <ErrorConsulta
          error={consulta.error}
          onReintentar={
            reintentarConsulta(0, consulta.error) ? () => void consulta.refetch() : undefined
          }
        />
      )}

      {!consulta.isError && datos?.totalElementos === 0 && (
        <EstadoVacio
          icono={CalendarDays}
          titulo="Aún no hay temporadas registradas"
          descripcion="Crea la primera temporada para empezar a configurar el ciclo deportivo."
          accion={
            <ConPermiso permiso="temporadas.temporada:EDITAR">
              <Button icono={Plus} onClick={abrirNueva}>
                Crear temporada
              </Button>
            </ConPermiso>
          }
        />
      )}

      {!consulta.isError && datos && datos.contenido.length === 0 && datos.totalElementos > 0 && (
        <EstadoVacio
          titulo="Esta página ya no tiene temporadas"
          accion={
            <Button variante="secundario" onClick={() => setPagina(0)}>
              Ir a la primera página
            </Button>
          }
        />
      )}

      {!consulta.isError && datos && datos.contenido.length > 0 && (
        <section aria-label="Temporadas registradas" className="flex flex-col gap-3">
          <p aria-live="polite" className="text-label-md text-text-main">
            {datos.totalElementos === 1
              ? '1 temporada'
              : `${formatoEntero.format(datos.totalElementos)} temporadas`}
          </p>
          <TablaTemporadas
            temporadas={datos.contenido}
            activandoId={activandoId}
            actualizando={consulta.isFetching}
            onEditar={abrirEdicion}
            onActivar={alActivar}
            onCerrar={abrirCierre}
          />
          {datos.totalPaginas > 1 && (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="order-first w-full text-center text-body-sm text-text-muted sm:order-none sm:w-auto">
                Página {datos.pagina + 1} de {datos.totalPaginas}
              </span>
              <Button
                variante="secundario"
                disabled={datos.pagina === 0}
                onClick={() => setPagina(datos.pagina - 1)}
              >
                Anterior
              </Button>
              <Button
                variante="secundario"
                disabled={datos.pagina + 1 >= datos.totalPaginas}
                onClick={() => setPagina(datos.pagina + 1)}
              >
                Siguiente
              </Button>
            </div>
          )}
        </section>
      )}

      {configuracion.length > 0 && (
        <section aria-labelledby="titulo-requisitos" className="flex flex-col gap-3">
          <div>
            <h2 id="titulo-requisitos" className="text-headline-md text-text-main">
              Lista de verificación para activar
            </h2>
            <p className="mt-1 text-body-md text-text-muted">
              Una temporada en configuración solo se activa cuando cumple todos los requisitos.
            </p>
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            {configuracion.map((temporada) => (
              <ListaVerificacion key={temporada.id} temporada={temporada} />
            ))}
          </div>
        </section>
      )}

      {creando && (
        <FormularioTemporada onCerrar={() => setCreando(false)} onGuardada={alCrear} />
      )}

      {editando && (
        <FormularioTemporada
          key={editando.id}
          temporada={editando}
          onCerrar={() => setEditando(null)}
          onGuardada={alEditar}
        />
      )}

      {cerrando && (
        <ModalCerrarTemporada
          key={cerrando.id}
          temporada={cerrando}
          onCerrar={() => setCerrando(null)}
          onCerrada={alCerrar}
        />
      )}
    </div>
  )
}
