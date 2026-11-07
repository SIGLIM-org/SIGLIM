import { useId, useState, type KeyboardEvent } from 'react'
import { CircleCheck, Plus, RotateCw, SearchX, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'
import { useAdeudos, useClubesTesoreria, useCobros } from '@/features/tesoreria/api'
import { formatearMonto } from '@/features/tesoreria/fechas'
import {
  FILTROS_COBROS_INICIALES,
  TAMANO_COBROS,
  hayFiltrosCobros,
} from '@/features/tesoreria/filtros'
import { FormularioFiltrosCobros } from '@/features/tesoreria/FormularioFiltrosCobros'
import { ModalNuevoCobro } from '@/features/tesoreria/ModalNuevoCobro'
import { ModalPagoSimulado } from '@/features/tesoreria/ModalPagoSimulado'
import { PaginacionCobros } from '@/features/tesoreria/PaginacionCobros'
import { PestanaAdeudos } from '@/features/tesoreria/PestanaAdeudos'
import { ResumenAdeudos } from '@/features/tesoreria/ResumenAdeudos'
import { TablaCobros } from '@/features/tesoreria/TablaCobros'
import type { Cobro, FiltrosCobros } from '@/features/tesoreria/tipos'
import { esSinPermiso, reintentarConsulta } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

type Pestana = 'COBROS' | 'ADEUDOS'

const PESTANAS: readonly { valor: Pestana; texto: string }[] = [
  { valor: 'COBROS', texto: 'Cobros' },
  { valor: 'ADEUDOS', texto: 'Adeudos por club' },
]

const formatoEntero = new Intl.NumberFormat('es-MX')

function contar(total: number, singular: string, plural: string): string {
  return `${formatoEntero.format(total)} ${total === 1 ? singular : plural}`
}

/**
 * Pantalla de tesorería F-33 (H-29.2, RF-137, RF-140, UI-027): resumen de adeudos, cobros con
 * filtros y paginación, desglose por club y equipo, alta de un cobro manual y pago simulado
 * (H-30.2, RF-138). Las acciones de escritura se ocultan sin permiso; el backend vuelve a
 * autorizar cada una (VAL-015).
 */
export function GestionTesoreria() {
  const { yo } = useSesionActual()
  const puedePagar = tienePermiso(yo.permisos, 'tesoreria.pago:PAGAR')
  const [pestana, setPestana] = useState<Pestana>('COBROS')
  const [filtros, setFiltros] = useState<FiltrosCobros>(FILTROS_COBROS_INICIALES)
  const [creando, setCreando] = useState(false)
  const [cobroAPagar, setCobroAPagar] = useState<Cobro | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const id = useId()
  const cobros = useCobros(filtros)
  const adeudos = useAdeudos()
  const clubes = useClubesTesoreria()

  if (esSinPermiso(cobros.error) || esSinPermiso(adeudos.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const datos = cobros.data
  const filtrosAplicados = hayFiltrosCobros(filtros)

  const aplicarFiltros = (nuevos: FiltrosCobros) => {
    setFiltros({ ...nuevos, pagina: 0, tamano: TAMANO_COBROS })
    setMensaje(null)
  }

  const limpiarFiltros = () => {
    setFiltros(FILTROS_COBROS_INICIALES)
    setMensaje(null)
  }

  const abrirModal = () => {
    setMensaje(null)
    setCreando(true)
  }

  const alCrear = (cobro: Cobro) => {
    setCreando(false)
    setPestana('COBROS')
    setFiltros(FILTROS_COBROS_INICIALES)
    setMensaje(
      `Cobro «${cobro.concepto}» registrado para ${
        cobro.equipo?.nombre ?? cobro.club.nombre
      } por ${formatearMonto(cobro.monto)}.`,
    )
  }

  const navegarPestanas = (evento: KeyboardEvent<HTMLDivElement>) => {
    const actual = PESTANAS.findIndex(({ valor }) => valor === pestana)
    let siguiente: number

    if (evento.key === 'ArrowRight') {
      siguiente = (actual + 1) % PESTANAS.length
    } else if (evento.key === 'ArrowLeft') {
      siguiente = (actual - 1 + PESTANAS.length) % PESTANAS.length
    } else if (evento.key === 'Home') {
      siguiente = 0
    } else if (evento.key === 'End') {
      siguiente = PESTANAS.length - 1
    } else {
      return
    }

    evento.preventDefault()
    const destino = PESTANAS[siguiente].valor
    setPestana(destino)
    document.getElementById(`${id}-tab-${destino.toLowerCase()}`)?.focus()
  }

  const conteoDe = (valor: Pestana): number | undefined =>
    valor === 'COBROS' ? datos?.totalElementos : adeudos.data?.clubes.length

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
            Tesorería
          </h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Cobros a clubes y equipos, y adeudos según el ámbito de tu cuenta.
          </p>
        </div>
        <ConPermiso permiso="tesoreria.cobro:CREAR">
          <Button icono={Plus} onClick={abrirModal} className="shrink-0">
            Nuevo cobro
          </Button>
        </ConPermiso>
      </header>

      {clubes.isError && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-surface px-4 py-2"
        >
          <p className="text-body-md text-text-main">
            No se pudieron cargar los clubes para los filtros; la lista de cobros sigue
            disponible.
          </p>
          <Button variante="secundario" icono={RotateCw} onClick={() => void clubes.refetch()}>
            Reintentar
          </Button>
        </div>
      )}

      {adeudos.isError && !esSinPermiso(adeudos.error) && pestana === 'COBROS' && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-surface px-4 py-2"
        >
          <p className="text-body-md text-text-main">
            No se pudieron cargar los adeudos; los cobros siguen disponibles.
          </p>
          <Button
            variante="secundario"
            icono={RotateCw}
            onClick={() => void adeudos.refetch()}
          >
            Reintentar
          </Button>
        </div>
      )}

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      <ResumenAdeudos adeudos={adeudos.data} cargando={adeudos.isPending} />

      <div className="flex flex-col gap-4">
        <div
          role="tablist"
          aria-label="Vistas de tesorería"
          onKeyDown={navegarPestanas}
          className="flex flex-wrap gap-1 border-b border-border"
        >
          {PESTANAS.map(({ valor, texto }) => {
            const activa = pestana === valor
            const conteo = conteoDe(valor)

            return (
              <button
                key={valor}
                type="button"
                role="tab"
                id={`${id}-tab-${valor.toLowerCase()}`}
                aria-selected={activa}
                aria-controls={`${id}-panel-${valor.toLowerCase()}`}
                tabIndex={activa ? 0 : -1}
                onClick={() => {
                  setPestana(valor)
                  setMensaje(null)
                }}
                className={[
                  'inline-flex min-h-11 items-center gap-2 border-b-2 px-3 text-label-md transition-colors',
                  activa
                    ? 'border-primary text-primary'
                    : 'border-transparent text-text-muted hover:text-text-main',
                ].join(' ')}
              >
                {texto}
                {conteo !== undefined && (
                  <span className="inline-flex min-w-5 justify-center rounded-full bg-background px-1.5 text-label-sm text-text-muted">
                    {formatoEntero.format(conteo)}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        {pestana === 'COBROS' ? (
          <div
            role="tabpanel"
            id={`${id}-panel-cobros`}
            aria-labelledby={`${id}-tab-cobros`}
            className="flex flex-col gap-4"
          >
            <FormularioFiltrosCobros
              aplicados={filtros}
              clubes={clubes.data ?? []}
              onAplicar={aplicarFiltros}
            />

            {cobros.isPending && (
              <EstadoCarga etiqueta="Cargando cobros…" filas={6} columnas={5} />
            )}

            {cobros.isError && (
              <ErrorConsulta
                error={cobros.error}
                onReintentar={
                  reintentarConsulta(0, cobros.error)
                    ? () => void cobros.refetch()
                    : undefined
                }
              />
            )}

            {!cobros.isError && datos?.totalElementos === 0 && (
              filtrosAplicados ? (
                <EstadoVacio
                  icono={SearchX}
                  titulo="Ningún cobro coincide con los filtros"
                  descripcion="Prueba con otro club, tipo, estado o rango de fechas."
                  accion={
                    <Button variante="secundario" onClick={limpiarFiltros}>
                      Limpiar filtros
                    </Button>
                  }
                />
              ) : (
                <EstadoVacio
                  icono={Wallet}
                  titulo="Aún no hay cobros registrados"
                  descripcion="Los cobros manuales a clubes y equipos aparecerán aquí."
                  accion={
                    <ConPermiso permiso="tesoreria.cobro:CREAR">
                      <Button icono={Plus} onClick={abrirModal}>
                        Nuevo cobro
                      </Button>
                    </ConPermiso>
                  }
                />
              )
            )}

            {!cobros.isError &&
              datos &&
              datos.contenido.length === 0 &&
              datos.totalElementos > 0 && (
                <EstadoVacio
                  titulo="Esta página ya no tiene cobros"
                  accion={
                    <Button
                      variante="secundario"
                      onClick={() => setFiltros(FILTROS_COBROS_INICIALES)}
                    >
                      Ir a la primera página
                    </Button>
                  }
                />
              )}

            {!cobros.isError && datos && datos.contenido.length > 0 && (
              <section aria-label="Cobros registrados" className="flex flex-col gap-3">
                <p aria-live="polite" className="text-label-md text-text-main">
                  {contar(datos.totalElementos, 'cobro', 'cobros')}
                  {filtrosAplicados ? ' con los filtros aplicados' : ''}
                </p>
                <TablaCobros
                  cobros={datos.contenido}
                  actualizando={cobros.isPlaceholderData}
                  puedePagar={puedePagar}
                  onPagar={setCobroAPagar}
                />
                {datos.totalPaginas > 1 && (
                  <PaginacionCobros
                    pagina={datos.pagina}
                    totalPaginas={datos.totalPaginas}
                    onCambiarPagina={(pagina) =>
                      setFiltros((actuales) => ({ ...actuales, pagina }))
                    }
                  />
                )}
              </section>
            )}
          </div>
        ) : (
          <div
            role="tabpanel"
            id={`${id}-panel-adeudos`}
            aria-labelledby={`${id}-tab-adeudos`}
            className="flex flex-col gap-4"
          >
            <PestanaAdeudos
              adeudos={adeudos.data}
              cargando={adeudos.isPending}
              error={adeudos.isError ? adeudos.error : null}
              onReintentar={
                adeudos.isError && reintentarConsulta(0, adeudos.error)
                  ? () => void adeudos.refetch()
                  : undefined
              }
            />
          </div>
        )}
      </div>

      {creando && (
        <ModalNuevoCobro
          clubes={clubes.data ?? []}
          cargandoClubes={clubes.isPending}
          errorClubes={clubes.isError ? clubes.error : null}
          onReintentarClubes={() => void clubes.refetch()}
          onCerrar={() => setCreando(false)}
          onCreado={alCrear}
        />
      )}

      {cobroAPagar && (
        <ModalPagoSimulado
          cobro={cobroAPagar}
          onCerrar={() => setCobroAPagar(null)}
        />
      )}
    </div>
  )
}
