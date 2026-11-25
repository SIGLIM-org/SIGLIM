import { useId, useState, type FormEvent } from 'react'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { Insignia } from '@/components/ui/Insignia'
import { useRegistrarIncidencia, useTiposIncidencia } from '@/features/partidos/cedula/api'
import { mensajeDeError } from '@/features/partidos/cedula/errores'
import type { Cedula } from '@/features/partidos/cedula/tipos'

type Props = {
  cedula: Cedula
  editable: boolean
}

/**
 * Incidencias de la cédula (F-26, RF-026): tipo del catálogo, minuto, descripción y al menos un
 * involucrado, que es un jugador alineado o uno de los dos equipos (DEC-171). Un tipo que abre
 * expediente se rotula para que el Capturista sepa que irá a la Comisión Disciplinaria (DEC-22).
 */
export function PanelIncidencias({ cedula, editable }: Props) {
  const id = useId()
  const tipos = useTiposIncidencia(editable ? cedula.id : undefined)
  const registrar = useRegistrarIncidencia(cedula.partido.id, cedula.id)
  const [tipo, setTipo] = useState('')
  const [minuto, setMinuto] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [involucrados, setInvolucrados] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const { equipoLocal, equipoVisitante } = cedula.partido
  const equipos = [equipoLocal, equipoVisitante]

  const alternar = (clave: string, marcado: boolean) =>
    setInvolucrados((actuales) => (marcado ? [...actuales, clave] : actuales.filter((valor) => valor !== clave)))

  const agregar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    if (!tipo || !descripcion.trim() || involucrados.length === 0) {
      setError('Elige el tipo, describe lo que pasó y marca al menos un involucrado.')
      return
    }
    registrar.mutate(
      {
        tipoIncidenciaId: Number(tipo),
        descripcion: descripcion.trim(),
        ...(minuto !== '' && { minuto: Number(minuto) }),
        involucrados: involucrados.map((clave) =>
          clave.startsWith('equipo:') ? { equipoId: clave.slice(7) } : { personaId: clave.slice(8) }),
      },
      {
        onSuccess: () => {
          setTipo('')
          setMinuto('')
          setDescripcion('')
          setInvolucrados([])
        },
        onError: (fallo) => setError(mensajeDeError(fallo, 'No se pudo registrar la incidencia. Intenta de nuevo.')),
      },
    )
  }

  return (
    <section aria-labelledby={`${id}-titulo`} className="flex min-w-0 flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-5">
      <h2 id={`${id}-titulo`} className="text-headline-md text-text-main">
        Incidencias
      </h2>

      {editable && (
        <form onSubmit={agregar} noValidate className="flex flex-col gap-3">
          <div className="grid gap-3 sm:grid-cols-[1fr_6rem]">
            <CampoSeleccion id={`${id}-tipo`} etiqueta="Tipo de incidencia" required value={tipo} onChange={(evento) => setTipo(evento.target.value)}>
              <option value="">{tipos.isPending ? 'Cargando tipos…' : 'Elige un tipo'}</option>
              {(tipos.data ?? []).map((opcion) => (
                <option key={opcion.id} value={opcion.id}>
                  {opcion.nombre}
                  {opcion.abreExpediente ? ' · abre expediente' : ''}
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
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${id}-descripcion`} className="text-label-md text-text-main">
              Descripción <span aria-hidden="true" className="text-error">*</span>
            </label>
            <textarea
              id={`${id}-descripcion`}
              rows={3}
              maxLength={1000}
              value={descripcion}
              onChange={(evento) => setDescripcion(evento.target.value)}
              className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main"
            />
          </div>
          <fieldset className="flex flex-col gap-1">
            <legend className="mb-1 text-label-md text-text-main">
              Involucrados <span aria-hidden="true" className="text-error">*</span>
            </legend>
            <div className="grid max-h-56 gap-x-4 overflow-y-auto sm:grid-cols-2">
              {equipos.map((equipo) => (
                <label key={equipo.id} className="flex min-h-11 items-center gap-2 text-body-md font-medium text-text-main">
                  <input
                    type="checkbox"
                    checked={involucrados.includes(`equipo:${equipo.id}`)}
                    onChange={(evento) => alternar(`equipo:${equipo.id}`, evento.target.checked)}
                    className="size-[18px] accent-primary"
                  />
                  <span className="break-words">{equipo.nombre} (equipo)</span>
                </label>
              ))}
              {cedula.alineaciones.map((fila) => (
                <label key={fila.id} className="flex min-h-11 items-center gap-2 text-body-md text-text-main">
                  <input
                    type="checkbox"
                    checked={involucrados.includes(`persona:${fila.personaId}`)}
                    onChange={(evento) => alternar(`persona:${fila.personaId}`, evento.target.checked)}
                    className="size-[18px] accent-primary"
                  />
                  <span className="break-words">{fila.nombre}</span>
                </label>
              ))}
            </div>
          </fieldset>
          {error && (
            <p role="alert" className="text-body-sm text-error-strong">
              {error}
            </p>
          )}
          <Button type="submit" variante="secundario" icono={Plus} cargando={registrar.isPending} className="sm:self-start">
            Agregar incidencia
          </Button>
        </form>
      )}

      {cedula.incidencias.length === 0 ? (
        <p className="text-body-md text-text-muted">Sin incidencias capturadas.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {cedula.incidencias.map((incidencia) => (
            <li key={incidencia.id} className="flex flex-col gap-1.5 rounded-md border border-border p-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-mono text-mono-data tabular-nums text-text-muted">
                  {incidencia.minuto !== null ? `${incidencia.minuto}'` : '—'}
                </span>
                <span className="text-label-md text-text-main">{incidencia.tipo.nombre}</span>
                {incidencia.tipo.abreExpediente && <Insignia variante="advertencia">Abre expediente</Insignia>}
              </div>
              <p className="whitespace-pre-line break-words text-body-md text-text-main">{incidencia.descripcion}</p>
              <p className="text-body-sm text-text-muted">
                Involucrados: {incidencia.involucrados.map(({ nombre }) => nombre ?? 'Persona reservada').join(', ')}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
