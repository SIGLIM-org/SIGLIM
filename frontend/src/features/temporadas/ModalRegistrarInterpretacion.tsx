import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useRegistrarInterpretacion } from '@/features/temporadas/api'
import type { InterpretacionRegla } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  temporadaId: string
  onCerrar: () => void
  onRegistrada: (interpretacion: InterpretacionRegla) => void
}

/*
 * [PROPUESTA] Ni el ERS ni la BD fijan un máximo para la descripción; 2000 caracteres coinciden
 * con la validación del backend y evitan textos desmedidos. Ajustar cuando la Liga lo defina.
 */
export const MAXIMO_DESCRIPCION = 2000

/**
 * Registro de una interpretación administrativa (F-11, B-03.2, RF-007, RN-006). Nace siempre
 * PENDIENTE: no hay estado ni autoría en el formulario, y la resolución corresponde a otra persona
 * (DEC-57). Una temporada cerrada no llega a este modal (DEC-59).
 */
export function ModalRegistrarInterpretacion({ temporadaId, onCerrar, onRegistrada }: Props) {
  const dialogoRef = useDialogoModal()
  const registrar = useRegistrarInterpretacion()
  const [descripcion, setDescripcion] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const [errorCampo, setErrorCampo] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idCampo = `${id}-interpretacion`
  const idAyuda = `${id}-ayuda`
  const idErrorCampo = `${id}-error-campo`
  const texto = descripcion.trim()
  const valido = texto.length > 0

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)
    setErrorCampo(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    registrar.mutate(
      { temporadaId, descripcion: texto },
      {
        onSuccess: onRegistrada,
        onError: (fallo) => {
          if (fallo instanceof ApiError) {
            const mensajeCampo = fallo.mensajeDe('descripcion')

            if (mensajeCampo) {
              setErrorCampo(mensajeCampo)
              return
            }

            setError(fallo.problema.detail)
            return
          }

          setError('No se pudo registrar la interpretación. Intenta de nuevo.')
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
        if (!registrar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            Registrar interpretación
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-sm text-text-muted">
            Explica qué regla no es explícita y cómo debe interpretarse. Quedará pendiente hasta que
            la Presidencia la autorice o la rechace; no cambia los parámetros ni las reglas
            versionadas de la temporada (RF-006).
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md text-text-main">
            Interpretación <span aria-hidden="true" className="ml-0.5 text-error">*</span>
          </label>
          <textarea
            id={idCampo}
            value={descripcion}
            required
            rows={5}
            maxLength={MAXIMO_DESCRIPCION}
            aria-invalid={Boolean((mostrarError && !valido) || errorCampo)}
            aria-describedby={
              (mostrarError && !valido) || errorCampo ? `${idErrorCampo} ${idAyuda}` : idAyuda
            }
            onChange={(evento) => {
              setDescripcion(evento.target.value)
              setMostrarError(false)
              setErrorCampo(null)
            }}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>Describe la regla y la interpretación que se propone.</span>
            <span className="font-mono">
              {descripcion.length}/{MAXIMO_DESCRIPCION}
            </span>
          </p>
          {errorCampo ? (
            <p id={idErrorCampo} role="alert" className="text-body-sm text-error-strong">
              {errorCampo}
            </p>
          ) : (
            mostrarError &&
            !valido && (
              <p id={idErrorCampo} role="alert" className="text-body-sm text-error-strong">
                Escribe la interpretación.
              </p>
            )
          )}
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={registrar.isPending}>
            Cancelar
          </Button>
          <Button type="submit" cargando={registrar.isPending}>
            Registrar interpretación
          </Button>
        </div>
      </form>
    </dialog>
  )
}
