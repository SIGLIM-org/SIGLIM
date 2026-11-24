import { useId, useState, type FormEvent } from 'react'
import { Save } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { useGuardarMarcador } from '@/features/partidos/cedula/api'
import { mensajeDeError } from '@/features/partidos/cedula/errores'
import type { Cedula, GuardarMarcadorPeticion } from '@/features/partidos/cedula/tipos'

type Props = {
  cedula: Cedula
  editable: boolean
}

type Presentacion = 'AMBOS' | 'LOCAL' | 'VISITANTE'

const texto = (valor: number | null) => (valor === null ? '' : String(valor))

/**
 * Marcador del partido (F-26, RF-047, RF-126, DEC-24, DEC-28): goles de los dos equipos, penales solo
 * con empate y la no presentación, cuyo marcador por default asigna la Liga con los parámetros de la
 * temporada (se rotula «No oficial» si son de demostración, DEC-72).
 */
export function PanelMarcador({ cedula, editable }: Props) {
  const { marcador, partido } = cedula
  const id = useId()
  const guardar = useGuardarMarcador(partido.id, cedula.id)
  const [presentacion, setPresentacion] = useState<Presentacion>(
    marcador.noPresentado?.id === partido.equipoLocal.id
      ? 'LOCAL'
      : marcador.noPresentado?.id === partido.equipoVisitante.id
        ? 'VISITANTE'
        : 'AMBOS',
  )
  const [golesLocal, setGolesLocal] = useState(marcador.porDefault ? '' : texto(marcador.golesLocal))
  const [golesVisitante, setGolesVisitante] = useState(marcador.porDefault ? '' : texto(marcador.golesVisitante))
  const [conPenales, setConPenales] = useState(marcador.penalesLocal !== null)
  const [penalesLocal, setPenalesLocal] = useState(texto(marcador.penalesLocal))
  const [penalesVisitante, setPenalesVisitante] = useState(texto(marcador.penalesVisitante))
  const [error, setError] = useState<string | null>(null)
  const jugaron = presentacion === 'AMBOS'
  const empate = golesLocal !== '' && golesLocal === golesVisitante
  const bloqueado = !editable || guardar.isPending

  const enviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    let peticion: GuardarMarcadorPeticion
    if (!jugaron) {
      peticion = { noPresentadoEquipoId: presentacion === 'LOCAL' ? partido.equipoLocal.id : partido.equipoVisitante.id }
    } else if (golesLocal === '' || golesVisitante === '') {
      setError('Indica los goles de los dos equipos.')
      return
    } else {
      peticion = { golesLocal: Number(golesLocal), golesVisitante: Number(golesVisitante) }
      if (conPenales && empate) {
        if (penalesLocal === '' || penalesVisitante === '') {
          setError('Indica los penales de los dos equipos.')
          return
        }
        peticion = { ...peticion, penalesLocal: Number(penalesLocal), penalesVisitante: Number(penalesVisitante) }
      }
    }
    guardar.mutate(peticion, {
      onError: (fallo) => setError(mensajeDeError(fallo, 'No se pudo guardar el marcador. Intenta de nuevo.')),
    })
  }

  const campoGoles = (etiqueta: string, valor: string, cambiar: (valor: string) => void, sufijo: string) => (
    <label className="flex min-w-0 flex-col items-center gap-2">
      <span className="line-clamp-2 text-center text-body-sm text-on-surface-dark/80">{etiqueta}</span>
      <input
        id={`${id}-${sufijo}`}
        type="number"
        inputMode="numeric"
        min={0}
        max={99}
        value={jugaron ? valor : texto(sufijo === 'local' ? marcador.golesLocal : marcador.golesVisitante)}
        disabled={bloqueado || !jugaron}
        aria-label={`Goles de ${etiqueta}`}
        onChange={(evento) => cambiar(evento.target.value)}
        className="h-16 w-20 rounded-md border border-on-surface-dark/25 bg-on-surface-dark/10 text-center font-mono text-mono-score tabular-nums text-on-surface-dark disabled:opacity-70"
      />
    </label>
  )

  return (
    <form onSubmit={enviar} noValidate aria-labelledby={`${id}-titulo`} className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-surface">
      <div className="flex flex-col gap-4 bg-surface-dark p-4 text-on-surface-dark">
        <h2 id={`${id}-titulo`} className="text-label-sm uppercase tracking-wide text-on-surface-dark/80">
          Marcador
        </h2>
        <div className="grid grid-cols-[1fr_auto_1fr] items-end gap-2">
          {campoGoles(partido.equipoLocal.nombre, golesLocal, setGolesLocal, 'local')}
          <span aria-hidden="true" className="pb-5 font-mono text-headline-md text-on-surface-dark/60">
            –
          </span>
          {campoGoles(partido.equipoVisitante.nombre, golesVisitante, setGolesVisitante, 'visitante')}
        </div>
        {marcador.porDefault && (
          <div className="flex flex-wrap items-center gap-2 text-body-sm text-on-surface-dark/80">
            Marcador por default por no presentación {marcador.valorDemo && <InsigniaNoOficial />}
          </div>
        )}
      </div>

      <div className="flex flex-col gap-4 p-4">
        <fieldset disabled={bloqueado} className="flex flex-col gap-2">
          <legend className="mb-1 text-label-md text-text-main">Presentación</legend>
          {(
            [
              ['AMBOS', 'Se presentaron los dos equipos'],
              ['LOCAL', `No se presentó ${partido.equipoLocal.nombre}`],
              ['VISITANTE', `No se presentó ${partido.equipoVisitante.nombre}`],
            ] as const
          ).map(([valor, etiqueta]) => (
            <label key={valor} className="flex min-h-11 items-center gap-2 text-body-md text-text-main">
              <input
                type="radio"
                name={`${id}-presentacion`}
                value={valor}
                checked={presentacion === valor}
                onChange={() => setPresentacion(valor)}
                className="size-[18px] accent-primary"
              />
              <span className="break-words">{etiqueta}</span>
            </label>
          ))}
          {!jugaron && (
            <p className="text-body-sm text-text-muted">
              La Liga asigna el marcador por default con los parámetros de la temporada.
            </p>
          )}
        </fieldset>

        {jugaron && (
          <fieldset disabled={bloqueado || !empate} className="flex flex-col gap-2">
            <label className="flex min-h-11 items-center gap-2 text-body-md text-text-main">
              <input
                type="checkbox"
                checked={conPenales && empate}
                onChange={(evento) => setConPenales(evento.target.checked)}
                className="size-[18px] accent-primary"
              />
              Hubo tanda de penales
            </label>
            {!empate && <p className="text-body-sm text-text-muted">Los penales solo se registran con empate.</p>}
            {conPenales && empate && (
              <div className="grid grid-cols-2 gap-3">
                {(
                  [
                    [partido.equipoLocal.nombre, penalesLocal, setPenalesLocal],
                    [partido.equipoVisitante.nombre, penalesVisitante, setPenalesVisitante],
                  ] as const
                ).map(([nombre, valor, cambiar]) => (
                  <label key={nombre} className="flex min-w-0 flex-col gap-1 text-body-sm text-text-muted">
                    <span className="truncate">Penales de {nombre}</span>
                    <input
                      type="number"
                      inputMode="numeric"
                      min={0}
                      max={99}
                      value={valor}
                      onChange={(evento) => cambiar(evento.target.value)}
                      className="min-h-11 rounded-md border border-border bg-surface px-3 font-mono text-body-md tabular-nums text-text-main"
                    />
                  </label>
                ))}
              </div>
            )}
          </fieldset>
        )}

        {error && (
          <p role="alert" className="text-body-sm text-error-strong">
            {error}
          </p>
        )}
        {editable && (
          <Button type="submit" variante="secundario" icono={Save} cargando={guardar.isPending}>
            Guardar marcador
          </Button>
        )}
      </div>
    </form>
  )
}
