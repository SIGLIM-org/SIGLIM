import { Insignia } from '@/components/ui/Insignia'
import {
  etiquetaEstadoPartido,
  varianteEstadoPartido,
} from '@/features/programacion/etiquetas'
import { formatearFechaCorta, horarioPartido } from '@/features/programacion/fechas'
import type { Partido } from '@/features/programacion/tipos'

type Props = {
  partidos: readonly Partido[]
  /** Id del encabezado que nombra la tabla. */
  etiquetadaPor: string
}

/**
 * Rol de juegos de solo lectura de F-23 (H-13.2): lo que el Presidente aprueba es exactamente lo
 * que se publicará. Tabla en pantallas medianas y anchas; en el celular, una lista con los mismos
 * datos para no obligar al desplazamiento horizontal (RNF-007).
 */
export function TablaPartidosRevision({ partidos, etiquetadaPor }: Props) {
  return (
    <>
      <div className="hidden overflow-hidden rounded-lg border border-border bg-surface md:block">
        <table aria-labelledby={etiquetadaPor} className="w-full border-collapse text-left">
          <thead className="bg-background">
            <tr className="text-label-sm uppercase tracking-wide text-text-muted">
              <th scope="col" className="w-12 px-4 py-3 font-medium">
                #
              </th>
              <th scope="col" className="px-4 py-3 font-medium">
                Encuentro
              </th>
              <th scope="col" className="px-4 py-3 font-medium">
                Cancha
              </th>
              <th scope="col" className="px-4 py-3 font-medium">
                Fecha y bloque
              </th>
              <th scope="col" className="px-4 py-3 font-medium">
                Estado
              </th>
            </tr>
          </thead>
          <tbody>
            {partidos.map((partido, indice) => (
              <tr key={partido.id} className="border-t border-border align-top">
                <td className="px-4 py-3 font-mono text-body-md tabular-nums text-text-muted">
                  {indice + 1}
                </td>
                <th scope="row" className="px-4 py-3 text-left font-normal">
                  <span className="block text-label-md font-semibold text-text-main">
                    {partido.equipoLocal.nombre}
                  </span>
                  <span className="block text-body-md text-text-muted">
                    vs. {partido.equipoVisitante.nombre}
                  </span>
                </th>
                <td className="px-4 py-3 text-body-md text-text-main">{partido.cancha.nombre}</td>
                <td className="px-4 py-3 text-body-md text-text-main">
                  <span className="block">{formatearFechaCorta(partido.inicio)}</span>
                  <span className="block font-mono tabular-nums text-text-muted">
                    {horarioPartido(partido)}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <Insignia variante={varianteEstadoPartido[partido.estado]}>
                    {etiquetaEstadoPartido[partido.estado]}
                  </Insignia>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ol aria-labelledby={etiquetadaPor} className="flex flex-col gap-3 md:hidden">
        {partidos.map((partido, indice) => (
          <li key={partido.id} className="rounded-lg border border-border bg-surface p-4">
            <p className="text-label-sm uppercase tracking-wide text-text-muted">
              Partido {indice + 1}
            </p>
            <p className="mt-0.5 break-words text-headline-sm text-text-main">
              {partido.equipoLocal.nombre}{' '}
              <span className="font-normal text-text-muted">vs.</span>{' '}
              {partido.equipoVisitante.nombre}
            </p>
            <dl className="mt-2 grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-body-md">
              <dt className="text-text-muted">Cancha</dt>
              <dd className="break-words text-text-main">{partido.cancha.nombre}</dd>
              <dt className="text-text-muted">Fecha</dt>
              <dd className="text-text-main">
                {formatearFechaCorta(partido.inicio)} ·{' '}
                <span className="font-mono tabular-nums">{horarioPartido(partido)}</span>
              </dd>
            </dl>
            <div className="mt-2">
              <Insignia variante={varianteEstadoPartido[partido.estado]}>
                {etiquetaEstadoPartido[partido.estado]}
              </Insignia>
            </div>
          </li>
        ))}
      </ol>
    </>
  )
}
