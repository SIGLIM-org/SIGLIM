import { useId, useState, type FormEvent } from 'react'
import { CircleAlert, Save, Send, ShieldAlert } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useGuardarPermisos } from '@/features/roles/api'
import { codigosDeRol, diferenciaPermisos } from '@/features/roles/reglas'
import type {
  ParIncompatible,
  ResultadoGuardado,
  RolDetalle,
} from '@/features/roles/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  rol: RolDetalle
  permisos: readonly string[]
  paresNuevos: readonly ParIncompatible[]
  /** Código del permiso → descripción legible, para explicar el par incompatible. */
  descripciones: ReadonlyMap<string, string>
  onCerrar: () => void
  onGuardado: (resultado: ResultadoGuardado) => void
}

/**
 * Confirmación de la matriz de F-13 (RF-095, RF-096): toda edición lleva motivo y, si crea un par
 * incompatible, el botón advierte que el cambio quedará pendiente del Presidente (VAL-023).
 */
export function ModalGuardarPermisos({
  rol,
  permisos,
  paresNuevos,
  descripciones,
  onCerrar,
  onGuardado,
}: Props) {
  const dialogoRef = useDialogoModal()
  const guardar = useGuardarPermisos(rol.id)
  const [motivo, setMotivo] = useState('')
  const [mostrarError, setMostrarError] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idCampo = `${id}-motivo`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const pendiente = paresNuevos.length > 0
  const texto = motivo.trim()
  const valido = texto.length >= MINIMO_MOTIVO
  const { agregados, quitados } = diferenciaPermisos(codigosDeRol(rol.permisos), new Set(permisos))

  const confirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setError(null)

    if (!valido) {
      setMostrarError(true)
      return
    }

    guardar.mutate(
      { permisos: [...permisos], motivo: texto },
      {
        onSuccess: onGuardado,
        onError: (fallo) => {
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo guardar la matriz. Intenta de nuevo.',
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
        if (!guardar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={confirmar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            {pendiente ? 'Solicitar aprobación' : 'Guardar cambios'}
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            {pendiente
              ? 'El cambio no se aplica todavía: quedará pendiente de la aprobación del Presidente del Comité Ejecutivo.'
              : 'La matriz del rol se actualiza de inmediato. El cambio y su motivo quedarán en la auditoría.'}
          </p>
        </div>

        <div className="rounded-md border border-border bg-background p-3">
          <p className="text-label-md text-text-main">{rol.nombre}</p>
          <p className="break-all font-mono text-body-sm text-text-muted">{rol.codigo}</p>
          <dl className="mt-3 grid gap-2 text-body-md sm:grid-cols-[auto_1fr] sm:gap-x-3">
            <dt className="text-label-md text-text-main">Se agregan:</dt>
            <dd>{agregados.length > 0 ? agregados.join(', ') : 'Ninguno'}</dd>
            <dt className="text-label-md text-text-main">Se quitan:</dt>
            <dd>{quitados.length > 0 ? quitados.join(', ') : 'Ninguno'}</dd>
          </dl>
        </div>

        {pendiente && (
          <div className="flex gap-3 rounded-lg border border-warning/40 bg-warning/10 p-3 text-text-main">
            <ShieldAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-strong" />
            <div className="flex min-w-0 flex-col gap-1">
              <p className="text-label-md text-warning-strong">
                Control dual: este cambio combina permisos incompatibles
              </p>
              <ul className="flex list-disc flex-col gap-1 pl-4 text-body-md">
                {paresNuevos.map(({ permisoA, permisoB, motivo: causa }) => (
                  <li key={`${permisoA}-${permisoB}`}>
                    {descripciones.get(permisoA) ?? permisoA} con{' '}
                    {descripciones.get(permisoB) ?? permisoB}. {causa}.
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}

        <div className="flex flex-col gap-1">
          <label htmlFor={idCampo} className="text-label-md">
            Motivo <span aria-hidden="true" className="text-error">*</span>
          </label>
          <textarea
            id={idCampo}
            value={motivo}
            required
            rows={4}
            maxLength={MAXIMO_MOTIVO}
            aria-invalid={mostrarError && !valido}
            aria-describedby={mostrarError && !valido ? `${idError} ${idAyuda}` : idAyuda}
            onChange={(evento) => setMotivo(evento.target.value)}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md aria-invalid:border-error"
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>Mínimo {MINIMO_MOTIVO} caracteres. Quedará en el historial.</span>
            <span className="font-mono">
              {texto.length}/{MAXIMO_MOTIVO}
            </span>
          </p>
          {mostrarError && !valido && (
            <p id={idError} role="alert" className="text-body-sm text-error-strong">
              Escribe un motivo de al menos {MINIMO_MOTIVO} caracteres.
            </p>
          )}
        </div>

        {error && (
          <p
            role="alert"
            className="flex items-start gap-2 rounded-md bg-error/8 p-3 text-body-md text-error-strong"
          >
            <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            {error}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={guardar.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            icono={pendiente ? Send : Save}
            cargando={guardar.isPending}
            disabled={guardar.isPending}
          >
            {pendiente ? 'Enviar solicitud' : 'Guardar cambios'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
