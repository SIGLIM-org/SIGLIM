import { useSearchParams } from 'react-router'
import { RotateCw, SearchX } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useOpcionesFiltroAuditoria, useRegistrosAuditoria } from '@/features/auditoria/api'
import {
  estadoDeUrl,
  FILTROS_VACIOS,
  hayFiltros,
  parametrosDeEstado,
  urlDeEstado,
  valoresDeEstado,
  type EstadoConsulta,
  type ValoresFiltros,
} from '@/features/auditoria/filtros'
import { FormularioFiltrosAuditoria } from '@/features/auditoria/FormularioFiltrosAuditoria'
import { PaginacionAuditoria } from '@/features/auditoria/PaginacionAuditoria'
import { TablaAuditoria } from '@/features/auditoria/TablaAuditoria'
import { esSinPermiso, reintentarConsulta } from '@/lib/api/errores'

const formatoEntero = new Intl.NumberFormat('es-MX')

function Encabezado() {
  return (
    <header>
      <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Auditoría</h1>
      <p className="mt-1 text-body-md text-text-muted">
        Consulta de las operaciones críticas registradas en el sistema.
      </p>
    </header>
  )
}

/**
 * Consulta de la auditoría (F-14, H-21.2, RF-134, RNF-015): solo lectura. Filtros, orden y página
 * viven en la URL, con días y no instantes, para que atrás y adelante funcionen; cualquier cambio
 * salvo el de página vuelve a la primera.
 */
export function ConsultaAuditoria() {
  const [url, setUrl] = useSearchParams()
  const estado = estadoDeUrl(url)
  const consulta = useRegistrosAuditoria(parametrosDeEstado(estado))
  const opciones = useOpcionesFiltroAuditoria()

  const actualizar = (cambios: Partial<EstadoConsulta>) => {
    setUrl(urlDeEstado({ ...estado, pagina: 0, ...cambios }))
  }
  const aplicarFiltros = (filtros: ValoresFiltros) => actualizar(filtros)
  const limpiarFiltros = () => actualizar(FILTROS_VACIOS)

  if (esSinPermiso(consulta.error)) {
    return (
      <div className="flex flex-col gap-6">
        <Encabezado />
        <ErrorConsulta error={consulta.error} />
      </div>
    )
  }

  const datos = consulta.data

  return (
    <div className="flex flex-col gap-6">
      <Encabezado />

      <FormularioFiltrosAuditoria
        aplicados={valoresDeEstado(estado)}
        opciones={opciones.data}
        onAplicar={aplicarFiltros}
      />

      {opciones.isError && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-surface px-4 py-2">
          <p className="text-body-md text-text-main">
            No se pudieron cargar las opciones de los filtros; los resultados siguen disponibles.
          </p>
          <Button variante="secundario" icono={RotateCw} onClick={() => void opciones.refetch()}>
            Reintentar
          </Button>
        </div>
      )}

      {consulta.isPending && <EstadoCarga etiqueta="Cargando registros de auditoría…" filas={8} columnas={6} />}

      {consulta.isError && (
        <ErrorConsulta
          error={consulta.error}
          onReintentar={reintentarConsulta(0, consulta.error) ? () => void consulta.refetch() : undefined}
        />
      )}

      {!consulta.isError && datos && datos.contenido.length === 0 && datos.totalElementos > 0 && (
        <EstadoVacio
          icono={SearchX}
          titulo="Esta página ya no tiene registros"
          accion={
            <Button variante="secundario" onClick={() => actualizar({})}>
              Ir a la primera página
            </Button>
          }
        />
      )}

      {!consulta.isError && datos?.totalElementos === 0 && (
        hayFiltros(estado) ? (
          <EstadoVacio
            icono={SearchX}
            titulo="Ningún registro coincide con los filtros"
            accion={
              <Button variante="secundario" onClick={limpiarFiltros}>
                Limpiar filtros
              </Button>
            }
          />
        ) : (
          <EstadoVacio titulo="No hay registros de auditoría para mostrar" />
        )
      )}

      {!consulta.isError && datos && datos.contenido.length > 0 && (
        <section aria-label="Resultados" className="flex flex-col gap-3">
          <p aria-live="polite" className="text-label-md text-text-main">
            {datos.totalElementos === 1
              ? '1 evento'
              : `${formatoEntero.format(datos.totalElementos)} eventos`}
          </p>
          <TablaAuditoria
            registros={datos.contenido}
            orden={estado.orden}
            actualizando={consulta.isPlaceholderData}
            onAlternarOrden={() => actualizar({ orden: estado.orden === 'desc' ? 'asc' : 'desc' })}
          />
          <PaginacionAuditoria
            pagina={estado.pagina}
            totalPaginas={datos.totalPaginas}
            tamano={estado.tamano}
            onCambiarPagina={(pagina) => actualizar({ pagina })}
            onCambiarTamano={(tamano) => actualizar({ tamano })}
          />
        </section>
      )}
    </div>
  )
}
