import { useState } from 'react'
import { CircleCheck, Info, SlidersHorizontal } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useCategoriasTemporada, useParametros } from '@/features/temporadas/api'
import { formatearFecha } from '@/features/temporadas/fechas'
import { FormularioParametros } from '@/features/temporadas/FormularioParametros'
import { desdePredeterminados, desdeVersion } from '@/features/temporadas/parametros'
import { HistorialParametros } from '@/features/temporadas/HistorialParametros'
import { InsigniaOficial } from '@/features/temporadas/InsigniaOficial'
import { ModalInstantaneaParametros } from '@/features/temporadas/ModalInstantaneaParametros'
import { ResumenParametros } from '@/features/temporadas/ResumenParametros'
import type { Temporada, VersionParametros } from '@/features/temporadas/tipos'
import { reintentarConsulta } from '@/lib/api/errores'

type Props = {
  temporada: Temporada
  /** `temporadas.parametros:EDITAR` y la temporada no está cerrada (D8). */
  puedeEditar: boolean
}

/**
 * Pestaña Parámetros de F-11 (H-05.3, RF-006, RF-100, DEC-24 a DEC-29): la versión vigente, el
 * formulario para registrar una versión nueva (o la primera) y el historial con instantáneas
 * (D7). Sin permiso de edición o con la temporada cerrada se muestra en solo lectura (D8).
 */
export function PestanaParametros({ temporada, puedeEditar }: Props) {
  const parametros = useParametros(temporada.id)
  const categorias = useCategoriasTemporada(temporada.id)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [instantanea, setInstantanea] = useState<number | null>(null)
  const cerrada = temporada.estado === 'CERRADA'

  if (parametros.isPending || (puedeEditar && categorias.isPending)) {
    return <EstadoCarga etiqueta="Cargando los parámetros…" filas={6} columnas={3} />
  }

  if (parametros.isError || (puedeEditar && categorias.isError)) {
    const error = parametros.error ?? categorias.error
    return (
      <ErrorConsulta
        error={error}
        onReintentar={
          reintentarConsulta(0, error)
            ? () => {
                void parametros.refetch()
                void categorias.refetch()
              }
            : undefined
        }
      />
    )
  }

  const { vigente, historial, predeterminados } = parametros.data
  const inicial = vigente
    ? desdeVersion(vigente)
    : predeterminados
      ? desdePredeterminados(predeterminados)
      : null

  const alGuardar = (version: VersionParametros) => {
    setMensaje(`Se guardó la versión ${version.version}.`)
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      <section aria-labelledby="titulo-parametros" className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-col gap-2">
          <h2 id="titulo-parametros" className="text-headline-md text-text-main">
            Parámetros deportivos
          </h2>
          {vigente ? (
            <p className="flex flex-wrap items-center gap-2 text-body-md text-text-main">
              <span>
                Versión {vigente.version} vigente · desde{' '}
                {formatearFecha(vigente.creadoEn.slice(0, 10))}
              </span>
              <InsigniaOficial esDemo={vigente.esDemo} />
            </p>
          ) : (
            puedeEditar && (
              <p className="flex items-start gap-2 rounded-lg border border-warning/30 bg-warning/8 p-3 text-body-md text-text-main">
                <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-strong" />
                Aún no hay parámetros. La temporada no podrá activarse hasta guardar la versión 1
                (RF-100).
              </p>
            )
          )}
          {!puedeEditar && !cerrada && (
            <p className="text-body-sm text-text-muted">Solo consulta</p>
          )}
        </div>

        {mensaje && (
          <p
            role="status"
            className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
          >
            <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            {mensaje}
          </p>
        )}

        {puedeEditar && inicial && (
          <FormularioParametros
            key={vigente?.version ?? 0}
            temporadaId={temporada.id}
            vigente={vigente}
            inicial={inicial}
            categorias={categorias.data ?? []}
            onGuardada={alGuardar}
          />
        )}

        {!puedeEditar &&
          (vigente ? (
            <div className="rounded-lg border border-border bg-surface p-4 sm:p-5">
              <ResumenParametros version={vigente} />
            </div>
          ) : (
            <EstadoVacio
              icono={SlidersHorizontal}
              titulo="Aún no hay parámetros"
              descripcion="La temporada no tiene ninguna versión de parámetros registrada."
            />
          ))}
      </section>

      <aside className="min-w-0">
        <HistorialParametros historial={historial} onVerInstantanea={setInstantanea} />
      </aside>

      {instantanea !== null && (
        <ModalInstantaneaParametros
          key={instantanea}
          temporadaId={temporada.id}
          version={instantanea}
          onCerrar={() => setInstantanea(null)}
        />
      )}
    </div>
  )
}
