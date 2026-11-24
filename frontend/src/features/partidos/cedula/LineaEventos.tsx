import { useId, useState, type FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { useEliminarEvento, useRegistrarEvento } from '@/features/partidos/cedula/api'
import { mensajeDeError } from '@/features/partidos/cedula/errores'
import { etiquetaTipoEvento } from '@/features/partidos/cedula/reglas'
import type { Cedula, TipoEvento } from '@/features/partidos/cedula/tipos'

type Props = {
  cedula: Cedula
  editable: boolean
}

const TIPOS = Object.keys(etiquetaTipoEvento) as TipoEvento[]

/** Color de la marca del minuto según el evento, siempre con su texto (RNF-008). */
const tonoTipo: Record<TipoEvento, string> = {
  GOL: 'bg-success/12 text-success-strong',
  AUTOGOL: 'bg-border text-text-main',
  TARJETA_AMARILLA: 'bg-warning/14 text-warning-strong',
  TARJETA_ROJA: 'bg-error/12 text-error-strong',
  SUSTITUCION: 'bg-info/12 text-info-strong',
}

/**
 * Línea de eventos de la cédula (F-26, RF-025): goles, autogoles, tarjetas y sustituciones de los
 * jugadores alineados, por minuto. Un evento capturado por error se quita mientras la cédula está en
 * captura.
 */
export function LineaEventos({ cedula, editable }: Props) {
  const id = useId()
  const registrar = useRegistrarEvento(cedula.partido.id, cedula.id)
  const eliminar = useEliminarEvento(cedula.partido.id, cedula.id)
  const [tipo, setTipo] = useState<TipoEvento>('GOL')
  const [minuto, setMinuto] = useState('')
  const [jugador, setJugador] = useState('')
  const [entra, setEntra] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { equipoLocal, equipoVisitante } = cedula.partido
  const nombreEquipo = (equipoId: string) => (equipoId === equipoLocal.id ? equipoLocal.nombre : equipoVisitante.nombre)
  const delJugador = cedula.alineaciones.find(({ id: fila }) => fila === jugador)
  const companeros = cedula.alineaciones.filter((fila) => fila.equipoId === delJugador?.equipoId && fila.id !== jugador)

  const agregar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    if (!jugador || (tipo === 'SUSTITUCION' && !entra)) {
      setError(tipo === 'SUSTITUCION' ? 'Elige al jugador que sale y al que entra.' : 'Elige al jugador del evento.')
      return
    }
    registrar.mutate(
      {
        tipo,
        alineacionId: jugador,
        ...(minuto !== '' && { minuto: Number(minuto) }),
        ...(tipo === 'SUSTITUCION' && { alineacionEntraId: entra }),
      },
      {
        onSuccess: () => {
          setMinuto('')
          setJugador('')
          setEntra('')
        },
        onError: (fallo) => setError(mensajeDeError(fallo, 'No se pudo registrar el evento. Intenta de nuevo.')),
      },
    )
  }

  return (
    <section aria-labelledby={`${id}-titulo`} className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-5">
      <h2 id={`${id}-titulo`} className="text-headline-md text-text-main">
        Eventos del partido
      </h2>

      {editable && (
        <form
          onSubmit={agregar}
          noValidate
          className={[
            'grid gap-3 sm:grid-cols-2 lg:items-end',
            tipo === 'SUSTITUCION' ? 'lg:grid-cols-[12rem_6rem_1fr_1fr_auto]' : 'lg:grid-cols-[12rem_6rem_1fr_auto]',
          ].join(' ')}
        >
          <CampoSeleccion id={`${id}-tipo`} etiqueta="Tipo" value={tipo} onChange={(evento) => {
            setTipo(evento.target.value as TipoEvento)
            setEntra('')
          }}>
            {TIPOS.map((valor) => (
              <option key={valor} value={valor}>
                {etiquetaTipoEvento[valor]}
              </option>
            ))}
          </CampoSeleccion>
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${id}-minuto`} className="text-label-md text-text-main">
              Minuto
            </label>
            <input
              id={`${id}-minuto`}
              type="number"
              inputMode="numeric"
              min={0}
              max={150}
              value={minuto}
              onChange={(evento) => setMinuto(evento.target.value)}
              className="min-h-11 rounded-md border border-border bg-surface px-3 font-mono text-body-md tabular-nums text-text-main"
            />
          </div>
          <CampoSeleccion
            id={`${id}-jugador`}
            etiqueta={tipo === 'SUSTITUCION' ? 'Sale' : 'Jugador'}
            value={jugador}
            onChange={(evento) => {
              setJugador(evento.target.value)
              setEntra('')
            }}
          >
            <option value="">{cedula.alineaciones.length ? 'Elige un jugador' : 'Captura primero la alineación'}</option>
            {[equipoLocal, equipoVisitante].map((equipo) => (
              <optgroup key={equipo.id} label={equipo.nombre}>
                {cedula.alineaciones
                  .filter(({ equipoId }) => equipoId === equipo.id)
                  .map((fila) => (
                    <option key={fila.id} value={fila.id}>
                      {fila.numeroPlayera !== null ? `${fila.numeroPlayera} · ` : ''}
                      {fila.nombre}
                    </option>
                  ))}
              </optgroup>
            ))}
          </CampoSeleccion>
          {tipo === 'SUSTITUCION' && (
            <CampoSeleccion id={`${id}-entra`} etiqueta="Entra" value={entra} disabled={!jugador} onChange={(evento) => setEntra(evento.target.value)}>
              <option value="">{jugador ? 'Elige al que entra' : 'Elige primero al que sale'}</option>
              {companeros.map((fila) => (
                <option key={fila.id} value={fila.id}>
                  {fila.numeroPlayera !== null ? `${fila.numeroPlayera} · ` : ''}
                  {fila.nombre}
                </option>
              ))}
            </CampoSeleccion>
          )}
          <Button type="submit" icono={Plus} cargando={registrar.isPending}>
            Agregar
          </Button>
          {error && (
            <p role="alert" className="text-body-sm text-error-strong sm:col-span-2 lg:col-span-5">
              {error}
            </p>
          )}
        </form>
      )}

      {cedula.eventos.length === 0 ? (
        <p className="text-body-md text-text-muted">Sin eventos capturados.</p>
      ) : (
        <ol className="flex flex-col divide-y divide-border rounded-md border border-border">
          {cedula.eventos.map((evento) => (
            <li key={evento.id} className="flex items-center gap-3 px-3 py-2.5">
              <span className={`inline-flex h-8 min-w-12 items-center justify-center rounded-md px-2 font-mono text-mono-data tabular-nums ${tonoTipo[evento.tipo]}`}>
                {evento.minuto !== null ? `${evento.minuto}'` : '—'}
              </span>
              <div className="min-w-0 flex-1">
                <p className="break-words text-body-md text-text-main">
                  <span className="font-medium">{etiquetaTipoEvento[evento.tipo]}</span> · {evento.jugador.nombre}
                  {evento.entra && <> → {evento.entra.nombre}</>}
                </p>
                <p className="text-body-sm text-text-muted">{nombreEquipo(evento.equipoId)}</p>
              </div>
              {editable && (
                <button
                  type="button"
                  onClick={() => eliminar.mutate(evento.id, {
                    onError: (fallo) => setError(mensajeDeError(fallo, 'No se pudo quitar el evento. Intenta de nuevo.')),
                  })}
                  disabled={eliminar.isPending}
                  aria-label={`Quitar ${etiquetaTipoEvento[evento.tipo].toLowerCase()} de ${evento.jugador.nombre}`}
                  className="inline-flex size-11 shrink-0 items-center justify-center rounded-md text-text-muted hover:bg-background hover:text-error-strong disabled:opacity-50"
                >
                  <Trash2 aria-hidden="true" className="size-4" />
                </button>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
