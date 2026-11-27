import { useId, useRef, useState } from 'react'
import { ClipboardCheck } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useMisDesignaciones } from '@/features/arbitraje/designaciones/api'
import { inicioDeHoy } from '@/features/arbitraje/designaciones/fechas'
import { ElementoMiDesignacion } from '@/features/arbitraje/mis-designaciones/ElementoMiDesignacion'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

export { RUTA_MIS_DESIGNACIONES } from '@/features/arbitraje/designaciones/rutas'

/**
 * «Mis designaciones» (H-14.2, DEC-205, DEC-212): partidos de jornadas publicadas en los que el
 * árbitro de la sesión está designado, desde hoy por omisión. Una columna, diseñada primero a
 * 390 px, como «Mi información» (DEC-147). Desde aquí se abre el partido (F-25) y su cédula (F-27).
 *
 * Refs: RF-023, RF-123, DEC-205, DEC-212
 */
export function PantallaMisDesignaciones() {
  const id = useId()
  const [desdeHoy] = useState(() => inicioDeHoy())
  const [conAnteriores, setConAnteriores] = useState(false)
  const [pagina, setPagina] = useState(0)
  const listaRef = useRef<HTMLUListElement>(null)
  const consulta = useMisDesignaciones({
    desde: conAnteriores ? undefined : desdeHoy,
    pagina,
  })

  if (esSinPermiso(consulta.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const datos = consulta.data

  /** Cambia de página y lleva el foco al inicio de la lista, que cambió por completo. */
  const irAPagina = (nueva: number) => {
    setPagina(nueva)
    listaRef.current?.focus()
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl min-w-0 flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
          Mis designaciones
        </h1>
        <p className="text-body-md text-text-muted">
          Partidos de jornadas publicadas en los que la Comisión de Arbitraje te designó.
        </p>
      </header>

      <div className="flex items-center gap-3">
        <input
          id={`${id}-anteriores`}
          type="checkbox"
          checked={conAnteriores}
          onChange={(evento) => {
            setConAnteriores(evento.target.checked)
            setPagina(0)
          }}
          className="size-[18px] shrink-0 accent-primary"
        />
        <label htmlFor={`${id}-anteriores`} className="min-h-11 content-center text-label-md text-text-main">
          Ver también anteriores
        </label>
      </div>

      {consulta.isPending && (
        <EstadoCarga etiqueta="Cargando tus designaciones…" filas={3} columnas={2} />
      )}

      {consulta.isError && (
        <ErrorConsulta error={consulta.error} onReintentar={() => void consulta.refetch()} />
      )}

      {datos && datos.contenido.length === 0 && (
        <EstadoVacio
          icono={ClipboardCheck}
          titulo="No tienes designaciones publicadas."
          descripcion={
            conAnteriores
              ? 'Cuando la Comisión te designe a un partido y publique la jornada, aparecerá aquí.'
              : 'No hay partidos desde hoy. Marca «Ver también anteriores» para revisar los pasados.'
          }
        />
      )}

      {datos && datos.contenido.length > 0 && (
        <>
          <ul
            ref={listaRef}
            tabIndex={-1}
            aria-label="Partidos designados"
            aria-busy={consulta.isFetching || undefined}
            className="flex flex-col divide-y divide-border rounded-lg border border-border bg-surface"
          >
            {datos.contenido.map((designacion) => (
              <li key={designacion.designacionId}>
                <ElementoMiDesignacion designacion={designacion} />
              </li>
            ))}
          </ul>

          {datos.totalPaginas > 1 && (
            <nav
              aria-label="Páginas de designaciones"
              className="flex items-center justify-between gap-2"
            >
              <Button
                variante="secundario"
                disabled={pagina === 0}
                onClick={() => irAPagina(pagina - 1)}
              >
                Anterior
              </Button>
              <span className="text-body-sm text-text-muted">
                Página <span className="font-mono tabular-nums">{datos.pagina + 1}</span> de{' '}
                <span className="font-mono tabular-nums">{datos.totalPaginas}</span>
              </span>
              <Button
                variante="secundario"
                disabled={pagina + 1 >= datos.totalPaginas}
                onClick={() => irAPagina(pagina + 1)}
              >
                Siguiente
              </Button>
            </nav>
          )}
        </>
      )}
    </div>
  )
}
