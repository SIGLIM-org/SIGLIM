import { useId, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { TriangleAlert } from 'lucide-react'
import { AlertaBloqueo } from '@/components/ui/AlertaBloqueo'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { clavesOrganizacion, useCambiarNombreClub } from '@/features/organizacion/api'
import { detalleDeFallo, erroresDeCampos, MAXIMO_NOMBRE } from '@/features/organizacion/erroresFormulario'
import type { Club } from '@/features/organizacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  club: Club
  onCerrar: () => void
  /** Recibe el club NUEVO que respondió el servidor. */
  onCambiado: (nuevo: Club) => void
}

type ErroresCampos = { nombre?: string; motivo?: string }

/**
 * Cambio de nombre de un club (F-15, RF-105, DEC-15, DEC-49): registra un club nuevo sin historial
 * y cierra el actual, que conserva el suyo; las asignaciones de rol del club pasan al nuevo. Solo
 * procede entre temporadas (RN-041, DEC-61). El motivo es obligatorio y queda en el historial (RN-021).
 */
export function ModalCambiarNombreClub({ club, onCerrar, onCambiado }: Props) {
  const dialogoRef = useDialogoModal()
  const clienteConsultas = useQueryClient()
  const cambiar = useCambiarNombreClub(club.id)
  const [nombre, setNombre] = useState('')
  const [motivo, setMotivo] = useState('')
  const [errores, setErrores] = useState<ErroresCampos>({})
  const [bloqueo, setBloqueo] = useState<string | null>(null)
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idAviso = `${id}-aviso`
  const idMotivo = `${id}-motivo`
  const idAyuda = `${id}-motivo-ayuda`
  const idErrorMotivo = `${id}-motivo-error`
  const motivoLimpio = motivo.trim()

  const alCambiar = (asignar: (valor: string) => void, campo: keyof ErroresCampos) => (valor: string) => {
    asignar(valor)
    setErrores((actuales) => ({ ...actuales, [campo]: undefined }))
  }

  const alFallar = (error: Error) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral(detalleDeFallo(error, 'No se pudo cambiar el nombre del club. Intenta de nuevo.'))
      return
    }

    switch (error.codigo) {
      case 'DUPLICADO':
        setErrores({ nombre: error.problema.detail })
        return
      case 'RN-021':
        setErrores({ motivo: error.mensajeDe('motivo') ?? error.problema.detail })
        return
      case 'VALIDACION':
        setErrores(erroresDeCampos(error, ['nombre', 'motivo']))
        return
      case 'RN-041':
        setBloqueo(error.problema.detail)
        // El club pudo cerrarse entretanto: el detalle debe reflejar su estado real.
        void clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.club(club.id) })
        return
      default:
        setErrorGeneral(detalleDeFallo(error, 'No se pudo cambiar el nombre del club. Intenta de nuevo.'))
    }
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setBloqueo(null)
    setErrorGeneral(null)

    const nombreLimpio = nombre.trim()
    const encontrados: ErroresCampos = {}

    if (!nombreLimpio) {
      encontrados.nombre = 'Escribe el nuevo nombre del club.'
    } else if (nombreLimpio === club.nombre.trim()) {
      encontrados.nombre = 'El nombre nuevo debe ser distinto del actual.'
    }
    if (motivoLimpio.length < MINIMO_MOTIVO) {
      encontrados.motivo = `Escribe un motivo de al menos ${MINIMO_MOTIVO} caracteres.`
    }

    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    cambiar.mutate({ nombre: nombreLimpio, motivo: motivoLimpio }, { onSuccess: onCambiado, onError: alFallar })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idAviso}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!cambiar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <h2 id={idTitulo} className="text-balance break-words text-headline-md">
          Cambiar nombre — {club.nombre}
        </h2>

        <div id={idAviso} className="flex gap-3 rounded-lg border border-warning/50 bg-warning/14 p-4 text-text-main">
          <TriangleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-strong" />
          <div className="flex min-w-0 flex-col gap-2">
            <p className="text-headline-sm text-warning-strong">Se registra un club nuevo; no se puede deshacer</p>
            <ul className="flex list-disc flex-col gap-1 pl-4 text-body-md">
              <li>El club nuevo lleva el nombre que indiques y empieza sin historial.</li>
              <li className="break-words">
                «{club.nombre}» queda <strong className="font-semibold">cerrado</strong> con todo su historial.
              </li>
              <li>Las asignaciones de rol del club pasan al club nuevo.</li>
              <li>Solo procede entre temporadas, sin equipos activos ni cobros pendientes.</li>
            </ul>
          </div>
        </div>

        <CampoTexto
          id={`${id}-nombre`}
          etiqueta="Nombre nuevo"
          required
          maxLength={MAXIMO_NOMBRE}
          error={errores.nombre}
          value={nombre}
          onChange={(evento) => alCambiar(setNombre, 'nombre')(evento.target.value)}
        />

        <div className="flex flex-col gap-1.5">
          <label htmlFor={idMotivo} className="text-label-md">
            Motivo
            <span aria-hidden="true" className="ml-0.5 text-error">
              *
            </span>
          </label>
          <textarea
            id={idMotivo}
            rows={3}
            required
            maxLength={MAXIMO_MOTIVO}
            value={motivo}
            aria-invalid={errores.motivo ? true : undefined}
            aria-describedby={errores.motivo ? `${idErrorMotivo} ${idAyuda}` : idAyuda}
            onChange={(evento) => alCambiar(setMotivo, 'motivo')(evento.target.value)}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md aria-invalid:border-error"
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>Mínimo {MINIMO_MOTIVO} caracteres. Quedará en el historial.</span>
            <span className="font-mono">
              {motivoLimpio.length}/{MAXIMO_MOTIVO}
            </span>
          </p>
          {errores.motivo && (
            <p id={idErrorMotivo} role="alert" className="text-body-sm text-error-strong">
              {errores.motivo}
            </p>
          )}
        </div>

        {bloqueo && (
          <AlertaBloqueo
            causa="No se puede cambiar el nombre del club"
            codigo="RN-041"
            conflicto={bloqueo}
            accion="El cambio de nombre se registra entre temporadas: el club no debe tener equipos activos en una temporada activa o en configuración ni cobros pendientes o vencidos."
          />
        )}
        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={cambiar.isPending}>
            Cancelar
          </Button>
          <Button type="submit" variante="destructivo" cargando={cambiar.isPending}>
            Cambiar nombre
          </Button>
        </div>
      </form>
    </dialog>
  )
}
