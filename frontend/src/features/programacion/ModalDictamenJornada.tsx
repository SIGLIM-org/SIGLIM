import { useId, useState, type FormEvent } from 'react'
import { CheckCircle2, CornerUpLeft } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useResolverJornada } from '@/features/programacion/api'
import type { DecisionJornada, Jornada } from '@/features/programacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  jornada: Jornada
  decision: DecisionJornada
  partidos: number
  onCerrar: () => void
  onResuelta: (jornada: Jornada) => void
}

/** Mínimo de las observaciones al devolver (convenciones de API §6.7, H-13.1). */
const MINIMO_OBSERVACIONES = 10

/*
 * [PROPUESTA] Ni el ERS ni la BD fijan un máximo para las observaciones; 500 caracteres, como en
 * los demás motivos del sistema y en el dictamen de comunicados. Ajustar cuando la Liga lo defina.
 */
const MAXIMO_OBSERVACIONES = 500

/**
 * Dictamen del Presidente sobre una jornada en revisión (F-23, H-13.2, RF-121, RF-090): aprobarla
 * y publicarla en el portal, o devolverla a borrador con observaciones para el Programador.
 * El backend rechaza el dictamen de quien programó la jornada (`403 RF-121`); el modal muestra su
 * explicación tal cual.
 */
export function ModalDictamenJornada({ jornada, decision, partidos, onCerrar, onResuelta }: Props) {
  const dialogoRef = useDialogoModal()
  const resolver = useResolverJornada()
  const [observaciones, setObservaciones] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idCampo = `${id}-observaciones`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const esDevolucion = decision === 'BORRADOR'
  const texto = observaciones.trim()
  const valido = !esDevolucion || texto.length >= MINIMO_OBSERVACIONES
  const titulo = esDevolucion ? 'Devolver con observaciones' : 'Aprobar y publicar'

  const alConfirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    resolver.mutate(
      {
        id: jornada.id,
        decision,
        ...(texto ? { observaciones: texto } : {}),
      },
      {
        onSuccess: onResuelta,
        onError: (fallo) => {
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo registrar el dictamen. Intenta de nuevo.',
          )
        },
      },
    )
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!resolver.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alConfirmar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            {titulo}
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            {esDevolucion
              ? `La jornada ${jornada.numero} volverá a borrador para que el Programador la corrija. Explica qué debe cambiar: las observaciones quedan en el historial.`
              : `Los ${partidos} ${partidos === 1 ? 'partido' : 'partidos'} de la jornada ${jornada.numero} aparecerán en el portal público y la jornada ya no podrá editarse.`}
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md text-text-main">
            {esDevolucion ? 'Observaciones para el Programador' : 'Nota de aprobación (opcional)'}
            {esDevolucion && (
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            )}
          </label>
          <textarea
            id={idCampo}
            value={observaciones}
            required={esDevolucion}
            rows={4}
            maxLength={MAXIMO_OBSERVACIONES}
            aria-invalid={mostrarError && !valido}
            aria-describedby={mostrarError && !valido ? `${idError} ${idAyuda}` : idAyuda}
            onChange={(evento) => setObservaciones(evento.target.value)}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>
              {esDevolucion
                ? `Mínimo ${MINIMO_OBSERVACIONES} caracteres. Quedarán en el historial.`
                : 'Si escribes una nota, queda en el historial de la jornada.'}
            </span>
            <span className="font-mono tabular-nums">
              {texto.length}/{MAXIMO_OBSERVACIONES}
            </span>
          </p>
          {mostrarError && !valido && (
            <p id={idError} role="alert" className="text-body-sm text-error-strong">
              Escribe observaciones de al menos {MINIMO_OBSERVACIONES} caracteres para devolver la
              jornada.
            </p>
          )}
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={resolver.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            variante={esDevolucion ? 'secundario' : 'primario'}
            icono={esDevolucion ? CornerUpLeft : CheckCircle2}
            cargando={resolver.isPending}
            disabled={resolver.isPending}
          >
            {titulo}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
