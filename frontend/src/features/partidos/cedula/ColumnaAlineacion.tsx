import { useId } from 'react'
import { Ban } from 'lucide-react'
import type { JugadorDisponible } from '@/features/partidos/cedula/tipos'
import type { Referencia } from '@/features/temporadas/tipos'

/** Jugador marcado en la alineación: playera como texto del campo y si es titular. */
export type Seleccion = { numeroPlayera: string; titular: boolean }

type Props = {
  lado: 'Local' | 'Visita'
  equipo: Referencia
  jugadores: readonly JugadorDisponible[]
  seleccion: ReadonlyMap<string, Seleccion>
  /** Error del último guardado por registro (VAL-002), con la causa en texto. */
  errores: ReadonlyMap<string, string>
  editable: boolean
  noPresentado: boolean
  onCambiar: (registroId: string, valor: Seleccion | null) => void
}

/**
 * Alineación de un equipo (F-26, RF-019, RF-110): los jugadores del equipo y de la categoría inmediata
 * inferior de su club. Quien no puede jugar aparece deshabilitado con la causa (DEC-172); se marca la
 * playera y si es titular.
 */
export function ColumnaAlineacion({ lado, equipo, jugadores, seleccion, errores, editable, noPresentado, onCambiar }: Props) {
  const id = useId()
  const marcados = jugadores.filter(({ registroJugadorId }) => seleccion.has(registroJugadorId))
  const titulares = marcados.filter(({ registroJugadorId }) => seleccion.get(registroJugadorId)?.titular).length

  return (
    <section aria-labelledby={`${id}-titulo`} className="flex min-w-0 flex-col rounded-lg border border-border bg-surface">
      <header className="flex flex-col gap-1 border-b border-border p-4">
        <p className="text-label-sm uppercase tracking-wide text-text-muted">{lado}</p>
        <h2 id={`${id}-titulo`} className="break-words text-headline-md text-text-main">
          {equipo.nombre}
        </h2>
        <p className="text-body-sm text-text-muted">
          <span className="font-mono tabular-nums text-text-main">{marcados.length}</span> alineados ·{' '}
          <span className="font-mono tabular-nums text-text-main">{titulares}</span> titulares
        </p>
      </header>

      {noPresentado ? (
        <p className="p-4 text-body-md text-text-muted">No se presentó: no necesita alineación.</p>
      ) : jugadores.length === 0 ? (
        <p className="p-4 text-body-md text-text-muted">No hay jugadores registrados en este equipo.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-border">
          {jugadores.map((jugador) => {
            const marcado = seleccion.get(jugador.registroJugadorId)
            const error = errores.get(jugador.registroJugadorId)
            const bloqueado = !editable || (!jugador.elegible && !marcado)
            const idFila = `${id}-${jugador.registroJugadorId}`

            return (
              <li key={jugador.registroJugadorId} className={['flex flex-col gap-2 px-4 py-3', !jugador.elegible && 'bg-background'].filter(Boolean).join(' ')}>
                <div className="flex items-start gap-3">
                  <input
                    id={`${idFila}-alineado`}
                    type="checkbox"
                    checked={Boolean(marcado)}
                    disabled={bloqueado}
                    aria-describedby={jugador.causa || error ? `${idFila}-causa` : undefined}
                    onChange={(evento) =>
                      onCambiar(jugador.registroJugadorId, evento.target.checked ? { numeroPlayera: '', titular: true } : null)
                    }
                    className="mt-0.5 size-[18px] shrink-0 accent-primary disabled:cursor-not-allowed"
                  />
                  <div className="min-w-0 flex-1">
                    <label
                      htmlFor={`${idFila}-alineado`}
                      className={['break-words text-body-md', jugador.elegible ? 'text-text-main' : 'text-text-muted line-through'].join(' ')}
                    >
                      {jugador.nombre}
                    </label>
                    {jugador.categoriaSuperior && (
                      <p className="text-body-sm text-info-strong">Sube de {jugador.equipoRegistro.nombre}</p>
                    )}
                    {(jugador.causa || error) && (
                      <p id={`${idFila}-causa`} className="mt-0.5 flex items-start gap-1 text-body-sm text-error-strong">
                        <Ban aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
                        <span>{error ?? jugador.causa?.mensaje}</span>
                      </p>
                    )}
                  </div>
                </div>
                {marcado && (
                  <div className="flex items-center gap-4 pl-[30px]">
                    <label className="flex items-center gap-2 text-body-sm text-text-muted">
                      Playera
                      <input
                        type="number"
                        inputMode="numeric"
                        min={0}
                        max={99}
                        value={marcado.numeroPlayera}
                        disabled={!editable}
                        aria-label={`Número de playera de ${jugador.nombre}`}
                        onChange={(evento) => onCambiar(jugador.registroJugadorId, { ...marcado, numeroPlayera: evento.target.value })}
                        className="min-h-11 w-16 rounded-md border border-border bg-surface px-2 text-center font-mono text-body-md tabular-nums text-text-main"
                      />
                    </label>
                    <label className="flex min-h-11 items-center gap-2 text-body-sm text-text-main">
                      <input
                        type="checkbox"
                        checked={marcado.titular}
                        disabled={!editable}
                        aria-label={`${jugador.nombre} es titular`}
                        onChange={(evento) => onCambiar(jugador.registroJugadorId, { ...marcado, titular: evento.target.checked })}
                        className="size-[18px] accent-primary"
                      />
                      Titular
                    </label>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
