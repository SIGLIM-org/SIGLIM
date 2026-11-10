import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Button } from '@/components/ui/Button'

/** Mínimo de caracteres del motivo, contados sin espacios al inicio ni al final (F-36). */
export const MINIMO_MOTIVO = 10

/*
 * [PROPUESTA] Ni el ERS ni la BD fijan un máximo para el motivo; 500 caracteres
 * evita textos desmedidos en el historial. Ajustar cuando la Liga lo defina.
 */
export const MAXIMO_MOTIVO = 500

type Props = {
  abierto: boolean
  titulo: string
  /** Explica qué efecto tendrá el cambio de estado. */
  descripcion: string
  /** Aviso entre la descripción y el motivo, p. ej. los partidos afectados. */
  aviso?: ReactNode
  etiquetaConfirmar?: string
  varianteConfirmar?: 'primario' | 'destructivo'
  /** Mantiene el modal abierto con «Confirmar» en espera mientras se guarda. */
  procesando?: boolean
  /** Recibe el motivo ya recortado. */
  onConfirmar: (motivo: string) => void
  onCancelar: () => void
}

/**
 * Modal genérico de cambio de estado (F-36): toda transición lleva motivo
 * (RF-045, RN-021). Usa `<dialog>` con `showModal()` para la trampa de foco y Esc
 * nativos, y devuelve el foco al elemento que lo abrió (RNF-008).
 */
export function ModalMotivo({
  abierto,
  titulo,
  descripcion,
  aviso,
  etiquetaConfirmar = 'Confirmar',
  varianteConfirmar = 'primario',
  procesando = false,
  onConfirmar,
  onCancelar,
}: Props) {
  const dialogoRef = useRef<HTMLDialogElement>(null)
  const [motivo, setMotivo] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idCampo = `${id}-motivo`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`

  useEffect(() => {
    const dialogo = dialogoRef.current

    if (!dialogo || !abierto) {
      return
    }

    const elementoPrevio =
      document.activeElement instanceof HTMLElement ? document.activeElement : null

    setMotivo('')
    setMostrarError(false)
    dialogo.showModal()

    return () => {
      dialogo.close()
      elementoPrevio?.focus()
    }
  }, [abierto])

  const longitud = motivo.trim().length
  const motivoValido = longitud >= MINIMO_MOTIVO

  const confirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    if (!motivoValido) {
      setMostrarError(true)
      return
    }

    onConfirmar(motivo.trim())
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
      onCancel={(evento) => {
        // Esc: el estado lo controla el padre, así que se avisa en lugar de cerrar aquí.
        evento.preventDefault()
        if (!procesando) {
          onCancelar()
        }
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      {abierto && (
        <form onSubmit={confirmar} noValidate className="flex flex-col gap-4 p-6">
          <h2 id={idTitulo} className="text-headline-md">
            {titulo}
          </h2>
          <p id={idDescripcion} className="text-body-md text-text-muted">
            {descripcion}
          </p>
          {aviso}

          <div className="flex flex-col gap-1">
            <label htmlFor={idCampo} className="text-label-md">
              Motivo <span className="text-error" aria-hidden="true">*</span>
            </label>
            <textarea
              id={idCampo}
              value={motivo}
              required
              rows={4}
              maxLength={MAXIMO_MOTIVO}
              aria-invalid={mostrarError && !motivoValido}
              aria-describedby={
                mostrarError && !motivoValido ? `${idError} ${idAyuda}` : idAyuda
              }
              onChange={(evento) => setMotivo(evento.target.value)}
              className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md aria-invalid:border-error"
            />
            <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
              <span>Mínimo {MINIMO_MOTIVO} caracteres. Quedará en el historial.</span>
              <span className="font-mono">
                {longitud}/{MAXIMO_MOTIVO}
              </span>
            </p>
            {mostrarError && !motivoValido && (
              <p id={idError} role="alert" className="text-body-sm text-error-strong">
                Escribe un motivo de al menos {MINIMO_MOTIVO} caracteres.
              </p>
            )}
          </div>

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variante="secundario" onClick={onCancelar} disabled={procesando}>
              Cancelar
            </Button>
            <Button type="submit" variante={varianteConfirmar} cargando={procesando}>
              {etiquetaConfirmar}
            </Button>
          </div>
        </form>
      )}
    </dialog>
  )
}
