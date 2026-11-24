import { useId, useState } from 'react'
import { Save } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useGuardarObservaciones } from '@/features/partidos/cedula/api'
import { mensajeDeError } from '@/features/partidos/cedula/errores'
import type { Cedula } from '@/features/partidos/cedula/tipos'

type Props = {
  cedula: Cedula
  editable: boolean
}

const MAXIMO = 2000

/** Observaciones generales del partido (F-26, RF-025); vacías se quitan. */
export function PanelObservaciones({ cedula, editable }: Props) {
  const id = useId()
  const guardar = useGuardarObservaciones(cedula.partido.id, cedula.id)
  const [texto, setTexto] = useState(cedula.observaciones ?? '')
  const [error, setError] = useState<string | null>(null)
  const sinCambios = texto.trim() === (cedula.observaciones ?? '')

  return (
    <section aria-labelledby={`${id}-titulo`} className="flex min-w-0 flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:p-5">
      <h2 id={`${id}-titulo`} className="text-headline-md text-text-main">
        <label htmlFor={`${id}-texto`}>Observaciones</label>
      </h2>
      <textarea
        id={`${id}-texto`}
        rows={6}
        maxLength={MAXIMO}
        value={texto}
        readOnly={!editable}
        placeholder={editable ? 'Estado de la cancha, retrasos, uniformes…' : undefined}
        aria-describedby={`${id}-ayuda`}
        onChange={(evento) => setTexto(evento.target.value)}
        className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main read-only:bg-background"
      />
      <p id={`${id}-ayuda`} className="text-right font-mono text-body-sm tabular-nums text-text-muted">
        {texto.trim().length}/{MAXIMO}
      </p>
      {error && (
        <p role="alert" className="text-body-sm text-error-strong">
          {error}
        </p>
      )}
      {editable && (
        <Button
          variante="secundario"
          icono={Save}
          disabled={sinCambios}
          cargando={guardar.isPending}
          className="sm:self-start"
          onClick={() => {
            setError(null)
            guardar.mutate(texto.trim(), {
              onError: (fallo) => setError(mensajeDeError(fallo, 'No se pudieron guardar las observaciones.')),
            })
          }}
        >
          Guardar observaciones
        </Button>
      )}
    </section>
  )
}
