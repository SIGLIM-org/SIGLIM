import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { FileCheck2, Info, Plus } from 'lucide-react'
import { AlertaBloqueo } from '@/components/ui/AlertaBloqueo'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import {
  TAMANO_AFILIACIONES,
  useAfiliacion,
  useAfiliaciones,
  useContextoAfiliaciones,
} from '@/features/organizacion/afiliaciones/api'
import { BandejaAfiliaciones } from '@/features/organizacion/afiliaciones/BandejaAfiliaciones'
import { DetalleAfiliacion } from '@/features/organizacion/afiliaciones/DetalleAfiliacion'
import { FormularioAfiliacion } from '@/features/organizacion/afiliaciones/FormularioAfiliacion'
import { ModalDictamenAfiliacion } from '@/features/organizacion/afiliaciones/ModalDictamenAfiliacion'
import type {
  Afiliacion,
  DecisionAfiliacion,
  FiltroEstadoAfiliacion,
} from '@/features/organizacion/afiliaciones/tipos'
import {
  APROBAR_AFILIACION,
  CAPTURAR_AFILIACION,
  usePermisosAfiliaciones,
} from '@/features/organizacion/afiliaciones/permisos'
import { useClubesActivosTodos } from '@/features/organizacion/api'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

export const RUTA_AFILIACIONES = '/app/organizacion/afiliaciones'

/**
 * Página de la ruta de afiliaciones (F-16, H-08.2). `RutaConPermiso` solo mira el módulo M-03;
 * aquí se exige capturar o aprobar antes de montar la bandeja y pedir sus datos.
 */
export function PantallaAfiliaciones() {
  return (
    <ConPermiso
      permiso={[CAPTURAR_AFILIACION, APROBAR_AFILIACION]}
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <PaginaAfiliaciones />
    </ConPermiso>
  )
}

/**
 * Bandeja y expediente del tarjetón. La Directiva captura y corrige mientras está SOLICITADA; la
 * Presidencia revisa y dictamina. La selección se conserva en la URL para que el detalle se pueda
 * enlazar y el botón «Volver» funcione en móvil.
 */
function PaginaAfiliaciones() {
  const { afiliacionId } = useParams()
  const navegar = useNavigate()
  const { capturar, aprobar } = usePermisosAfiliaciones()
  const [temporadaElegida, setTemporadaElegida] = useState<string | undefined>(undefined)
  const [filtro, setFiltro] = useState<FiltroEstadoAfiliacion>(() =>
    aprobar && !capturar ? 'SOLICITADA' : 'TODOS',
  )
  const [pagina, setPagina] = useState(0)
  const [creando, setCreando] = useState(false)
  const [editando, setEditando] = useState(false)
  const [decision, setDecision] = useState<DecisionAfiliacion | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)

  const contextoConsulta = useContextoAfiliaciones()
  const temporadas = contextoConsulta.data?.temporadas ?? []
  const porDefecto =
    temporadas.find(({ estado }) => estado === 'ACTIVA') ??
    temporadas.find(({ estado }) => estado !== 'CERRADA') ??
    temporadas[0]
  const temporadaId = temporadaElegida === undefined ? (porDefecto?.id ?? '') : temporadaElegida
  const temporadaFiltro = temporadaId === '' ? undefined : temporadaId
  const clubesConsulta = useClubesActivosTodos({ habilitada: capturar })
  const lista = useAfiliaciones({
    temporadaId: temporadaFiltro,
    estado: filtro === 'TODOS' ? undefined : filtro,
    pagina,
    tamano: TAMANO_AFILIACIONES,
  })
  const detalle = useAfiliacion(afiliacionId)
  const afiliacionSeleccionada =
    detalle.data ?? lista.data?.contenido.find(({ id }) => id === afiliacionId)
  const detalleVisible = Boolean(afiliacionId) || creando || editando
  const catalogoCargando = creando && (contextoConsulta.isPending || clubesConsulta.isPending)
  // Editar no necesita el catálogo: el tarjetón ya trae su temporada y sus categorías. Solo el
  // alta depende de que se puedan leer las temporadas y los clubes.
  const errorCatalogo = creando
    ? (contextoConsulta.error ?? clubesConsulta.error)
    : null

  if (esSinPermiso(lista.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const limpiarSeleccion = () => {
    setCreando(false)
    setEditando(false)
    setDecision(null)
    void navegar(RUTA_AFILIACIONES)
  }

  const nueva = () => {
    setMensaje(null)
    setCreando(true)
    setEditando(false)
    setDecision(null)
    void navegar(RUTA_AFILIACIONES)
  }

  const abrir = (afiliacion: Afiliacion) => {
    setCreando(false)
    setEditando(false)
    setDecision(null)
    setMensaje(null)
    void navegar(`${RUTA_AFILIACIONES}/${afiliacion.id}`)
  }

  const volver = () => {
    limpiarSeleccion()
    setMensaje(null)
  }

  const cambiarTemporada = (nuevaTemporada: string) => {
    setTemporadaElegida(nuevaTemporada)
    setPagina(0)
    setMensaje(null)
    limpiarSeleccion()
  }

  const cambiarFiltro = (nuevoFiltro: FiltroEstadoAfiliacion) => {
    setFiltro(nuevoFiltro)
    setPagina(0)
    setMensaje(null)
    limpiarSeleccion()
  }

  const cambiarPagina = (nuevaPagina: number) => {
    setPagina(nuevaPagina)
    setMensaje(null)
    limpiarSeleccion()
  }

  const alGuardar = (afiliacion: Afiliacion) => {
    const fueCreacion = creando

    setCreando(false)
    setEditando(false)
    setTemporadaElegida(afiliacion.temporada.id)
    if (fueCreacion) {
      setFiltro('SOLICITADA')
      setPagina(0)
    }
    setMensaje(
      fueCreacion
        ? `El tarjetón ${afiliacion.folio} quedó SOLICITADO. La Presidencia ya puede revisarlo.`
        : `Los cambios del tarjetón ${afiliacion.folio} se guardaron.`,
    )
    void navegar(`${RUTA_AFILIACIONES}/${afiliacion.id}`)
  }

  const alResolver = (resuelta: Afiliacion) => {
    setDecision(null)
    setMensaje(
      resuelta.estado === 'APROBADA'
        ? `«${resuelta.club.nombre}» quedó afiliado a ${resuelta.temporada.nombre}.`
        : `El tarjetón de «${resuelta.club.nombre}» se rechazó con observaciones.`,
    )
    void navegar(`${RUTA_AFILIACIONES}/${resuelta.id}`)
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Afiliaciones</h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Tarjetón del club por temporada. La Directiva lo captura y la Presidencia lo aprueba o
            lo rechaza con observaciones.
          </p>
        </div>
        {capturar && !creando && !editando && (
          <Button icono={Plus} onClick={nueva} className="shrink-0">
            Registrar afiliación
          </Button>
        )}
      </header>

      <section
        aria-label="Flujo de afiliación"
        className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4"
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-info/12 text-info-strong">
          <Info aria-hidden="true" className="size-5" />
        </span>
        <div>
          <h2 className="text-headline-sm text-text-main">Solicitada → Aprobada o Rechazada</h2>
          <p className="mt-0.5 max-w-3xl text-body-md text-text-muted">
            La Directiva registra colores y categorías; el sistema asigna el folio. La Presidencia
            revisa el expediente y, si rechaza, explica qué debe corregirse. El tarjetón no se
            borra: conserva su folio y su historial.
          </p>
        </div>
      </section>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <FileCheck2 aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[20rem_minmax(0,1fr)] lg:items-start">
        <div className={detalleVisible ? 'hidden min-w-0 lg:block' : 'min-w-0'}>
          <BandejaAfiliaciones
            temporadas={temporadas}
            temporadasCargando={contextoConsulta.isPending}
            temporadaId={temporadaId}
            filtro={filtro}
            pagina={pagina}
            datos={lista.data}
            cargando={lista.isFetching}
            error={lista.error}
            seleccionadaId={afiliacionId}
            puedeCapturar={capturar}
            onTemporada={cambiarTemporada}
            onFiltro={cambiarFiltro}
            onPagina={cambiarPagina}
            onSeleccionar={abrir}
            onNueva={nueva}
            onReintentar={() => void lista.refetch()}
          />
        </div>

        <div className={detalleVisible ? 'min-w-0' : 'hidden min-w-0 lg:block'}>
          {creando || editando ? (
            catalogoCargando ? (
              <EstadoCarga etiqueta="Cargando clubes y temporadas…" filas={4} columnas={2} />
            ) : errorCatalogo && esSinPermiso(contextoConsulta.error) ? (
              <AlertaBloqueo
                causa="No puedes consultar las temporadas de afiliación"
                conflicto="La captura del tarjetón necesita leer la temporada y sus categorías."
                accion={
                  <span className="flex flex-col items-start gap-2">
                    Pide a la Liga que habilite los permisos de afiliación para tu cuenta.
                    <Button variante="secundario" onClick={volver}>
                      Volver a la bandeja
                    </Button>
                  </span>
                }
                codigo="VAL-015"
              />
            ) : errorCatalogo ? (
              <ErrorConsulta
                error={errorCatalogo}
                onReintentar={() => {
                  void contextoConsulta.refetch()
                  if (capturar) {
                    void clubesConsulta.refetch()
                  }
                }}
              />
            ) : (
              <FormularioAfiliacion
                clubes={clubesConsulta.data ?? []}
                temporadas={temporadas}
                afiliacion={editando ? afiliacionSeleccionada : undefined}
                temporadaInicial={temporadaFiltro}
                onGuardada={alGuardar}
                onCancelar={volver}
              />
            )
          ) : afiliacionId ? (
            detalle.isPending && !afiliacionSeleccionada ? (
              <EstadoCarga etiqueta="Cargando el tarjetón…" filas={4} columnas={2} />
            ) : detalle.isError ? (
              <ErrorConsulta
                error={detalle.error}
                onReintentar={() => void detalle.refetch()}
                accion={
                  <Button variante="secundario" onClick={volver}>
                    Volver a la bandeja
                  </Button>
                }
              />
            ) : afiliacionSeleccionada ? (
              <DetalleAfiliacion
                key={afiliacionSeleccionada.id}
                afiliacion={afiliacionSeleccionada}
                puedeCapturar={capturar}
                puedeAprobar={aprobar}
                onEditar={() => {
                  setEditando(true)
                  setCreando(false)
                  setDecision(null)
                }}
                onResolver={setDecision}
                onVolver={volver}
              />
            ) : (
              <EstadoVacio
                icono={FileCheck2}
                titulo="No se encontró el tarjetón"
                descripcion="Puede que ya no esté disponible o que no tengas acceso a él."
                accion={
                  <Button variante="secundario" onClick={volver}>
                    Volver a la bandeja
                  </Button>
                }
              />
            )
          ) : (
            <EstadoVacio
              icono={FileCheck2}
              titulo="Elige un tarjetón"
              descripcion="Selecciona una fila de la bandeja para revisar su expediente y las acciones de su estado."
            />
          )}
        </div>
      </div>

      {decision && afiliacionSeleccionada && (
        <ModalDictamenAfiliacion
          key={`${afiliacionSeleccionada.id}-${decision}`}
          afiliacion={afiliacionSeleccionada}
          decision={decision}
          onCerrar={() => setDecision(null)}
          onResuelto={alResolver}
        />
      )}
    </div>
  )
}
