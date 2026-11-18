import { useId } from 'react'
import { Insignia } from '@/components/ui/Insignia'
import { formatearFechaCorta, formatearHora } from '@/features/programacion/fechas'
import { etiquetaEstadoPartido, varianteEstadoPartido } from '@/features/programacion/etiquetas'
import type { PartidoPropio } from '@/features/registros/mi-informacion/tipos'

type Props = {
  titulo: string
  partidos: readonly PartidoPropio[]
  textoVacio: string
  /** Muestra el marcador oficial (partidos jugados). */
  conMarcador?: boolean
}

/** «vs Lobos» si el equipo es local; «en casa de Pumas» si es visitante (DEC-151). */
function textoRival({ esLocal, local, visitante }: PartidoPropio): string {
  return esLocal ? `vs ${visitante.nombre}` : `en casa de ${local.nombre}`
}

function Marcador({ partido }: { partido: PartidoPropio }) {
  const { resultado, local, visitante } = partido

  if (!resultado) {
    return null
  }

  const { golesLocal, golesVisitante, penalesLocal, penalesVisitante } = resultado
  const conPenales = penalesLocal !== null && penalesVisitante !== null
  const lectura =
    `${local.nombre} ${golesLocal}, ${visitante.nombre} ${golesVisitante}` +
    (conPenales ? `; penales ${penalesLocal} a ${penalesVisitante}` : '')

  return (
    <div>
      <p className="sr-only">Marcador: {lectura}</p>
      <p
        aria-hidden="true"
        className="grid grid-cols-[1fr_auto_1fr] items-center gap-2 rounded-md bg-background px-3 py-2 text-body-md text-text-main"
      >
        <span className={`min-w-0 break-words text-right ${partido.esLocal ? 'font-semibold' : ''}`}>
          {local.nombre}
        </span>
        <span className="flex flex-col items-center">
          <span className="font-mono text-headline-md tabular-nums">
            {golesLocal} – {golesVisitante}
          </span>
          {conPenales && (
            <span className="text-body-sm text-text-muted">
              Penales{' '}
              <span className="font-mono tabular-nums">
                {penalesLocal} – {penalesVisitante}
              </span>
            </span>
          )}
        </span>
        <span className={`min-w-0 break-words ${partido.esLocal ? '' : 'font-semibold'}`}>
          {visitante.nombre}
        </span>
      </p>
    </div>
  )
}

/**
 * Lista de partidos del equipo del registro: jornada, fecha y hora en la zona de la Liga, cancha,
 * rival y estado; en los jugados, el marcador oficial (DEC-144, DEC-151).
 *
 * Refs: RF-144, RF-052, DEC-144, DEC-151
 */
export function SeccionPartidos({ titulo, partidos, textoVacio, conMarcador = false }: Props) {
  const id = useId()

  return (
    <section aria-labelledby={id} className="flex flex-col gap-2">
      <h2 id={id} className="text-headline-sm text-text-main">
        {titulo}
      </h2>

      {partidos.length === 0 ? (
        <p className="text-body-md text-text-muted">{textoVacio}</p>
      ) : (
        <ul className="flex flex-col divide-y divide-border rounded-lg border border-border">
          {partidos.map((partido) => (
            <li key={partido.id} className="flex flex-col gap-2 px-3 py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-body-sm text-text-muted">
                  Jornada <span className="font-mono tabular-nums">{partido.jornada}</span> ·{' '}
                  {formatearFechaCorta(partido.inicio)} ·{' '}
                  <span className="font-mono tabular-nums">{formatearHora(partido.inicio)}</span>
                </p>
                <Insignia variante={varianteEstadoPartido[partido.estado]}>
                  {etiquetaEstadoPartido[partido.estado]}
                </Insignia>
              </div>
              <p className="break-words text-label-md text-text-main">{textoRival(partido)}</p>
              <p className="break-words text-body-sm text-text-muted">Cancha: {partido.cancha}</p>
              {conMarcador && <Marcador partido={partido} />}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
