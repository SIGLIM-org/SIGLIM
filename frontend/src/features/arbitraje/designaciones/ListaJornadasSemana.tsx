import { CalendarX2, TriangleAlert } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import {
  MAXIMO_PAGINAS_SEMANA,
  useJornadasDeSemana,
  useResumenesJornadas,
} from '@/features/arbitraje/designaciones/api'
import { FilaJornadaSemana } from '@/features/arbitraje/designaciones/FilaJornadaSemana'

type Props = {
  semana: string
}

/**
 * Jornadas con partidos en la semana (F-24, DEC-209). Cada una enlaza a su designación y muestra
 * su estado de publicación y cuántos partidos tienen ya árbitro central.
 */
export function ListaJornadasSemana({ semana }: Props) {
  const consulta = useJornadasDeSemana(semana)
  const jornadas = consulta.data?.jornadas ?? []
  const resumenes = useResumenesJornadas(jornadas.map(({ jornadaId }) => jornadaId))

  if (consulta.isPending) {
    return <EstadoCarga etiqueta="Cargando las jornadas de la semana…" filas={4} columnas={3} />
  }

  if (consulta.isError) {
    return <ErrorConsulta error={consulta.error} onReintentar={() => void consulta.refetch()} />
  }

  return (
    <div className="flex flex-col gap-3">
      {consulta.data.truncado && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-warning/30 bg-warning/14 p-3 text-body-md text-warning-strong"
        >
          <TriangleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          La semana tiene más de {MAXIMO_PAGINAS_SEMANA * 100} partidos y la lista puede omitir
          jornadas. Abre las que falten desde el detalle de uno de sus partidos.
        </p>
      )}

      {jornadas.length === 0 ? (
        <EstadoVacio
          icono={CalendarX2}
          titulo="No hay partidos programados esa semana."
          descripcion="Cambia de semana o revisa la programación de las jornadas."
        />
      ) : (
        <ul
          aria-label="Jornadas de la semana"
          className="flex flex-col divide-y divide-border overflow-hidden rounded-lg border border-border bg-surface"
        >
          {jornadas.map((jornada, indice) => (
            <FilaJornadaSemana
              key={jornada.jornadaId}
              semana={semana}
              jornada={jornada}
              resumen={resumenes[indice]?.data}
              falloResumen={resumenes[indice]?.isError ?? false}
            />
          ))}
        </ul>
      )}
    </div>
  )
}
