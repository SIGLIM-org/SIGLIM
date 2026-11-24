import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useCanchas } from '@/features/canchas/api'
import { useTransicionarPartido } from '@/features/partidos/api'
import type { PartidoDetalle } from '@/features/partidos/tipos'
import { aIsoConDesplazamiento, valorDatetimeLocal } from '@/features/programacion/fechas'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  partido: PartidoDetalle
  onCerrar: () => void
}

type Errores = Partial<Record<'inicio' | 'motivo', string>>

/**
 * Reprogramación del partido con nueva fecha, cancha y motivo (F-25, RF-043, DEC-160). Los
 * conflictos de la nueva fecha (VAL-003 a VAL-006) llegan del backend con su explicación y se
 * muestran tal cual, sin códigos internos (DEC-63).
 */
export function ModalReprogramarPartido({ partido, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const canchas = useCanchas({ pagina: 0, tamano: 100, activa: true })
  const transicion = useTransicionarPartido(partido.id)
  const id = useId()
  const [inicio, setInicio] = useState(() => valorDatetimeLocal(partido.inicio))
  const [canchaId, setCanchaId] = useState(partido.cancha.id)
  const [motivo, setMotivo] = useState('')
  const [errores, setErrores] = useState<Errores>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const guardando = transicion.isPending
  const opcionesCanchas = [
    partido.cancha,
    ...(canchas.data?.contenido ?? [])
      .filter(({ id: otraId }) => otraId !== partido.cancha.id)
      .map(({ id: otraId, nombre }) => ({ id: otraId, nombre })),
  ]

  const enviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    const encontrados: Errores = {}

    if (!inicio) {
      encontrados.inicio = 'Indica la nueva fecha y hora del partido.'
    } else if (new Date(aIsoConDesplazamiento(inicio)).getTime() <= Date.now()) {
      encontrados.inicio = 'La nueva fecha y hora debe ser posterior a este momento.'
    }
    if (motivo.trim().length < MINIMO_MOTIVO) {
      encontrados.motivo = `Escribe un motivo de al menos ${MINIMO_MOTIVO} caracteres.`
    }
    setErrores(encontrados)
    setErrorGeneral(null)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    transicion.mutate(
      {
        estadoNuevo: 'REPROGRAMADO',
        motivo: motivo.trim(),
        inicio: aIsoConDesplazamiento(inicio),
        ...(canchaId !== partido.cancha.id && { canchaId }),
      },
      {
        onSuccess: onCerrar,
        onError: (error) => {
          if (!(error instanceof ApiError)) {
            setErrorGeneral('No se pudo reprogramar el partido. Intenta de nuevo.')
            return
          }
          if (error.problema.codigo === 'RN-021') {
            setErrores({ motivo: error.problema.detail })
            return
          }
          setErrorGeneral(error.problema.detail)
        },
      },
    )
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      aria-describedby={`${id}-descripcion`}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!guardando) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={enviar} noValidate className="flex flex-col gap-4 p-5 sm:p-6">
        <div>
          <h2 id={`${id}-titulo`} className="text-balance text-headline-md">
            Reprogramar partido
          </h2>
          <p id={`${id}-descripcion`} className="mt-1 text-body-md text-text-muted">
            {partido.equipoLocal.nombre} vs. {partido.equipoVisitante.nombre}. El partido queda
            reprogramado y pasa a En curso a su nueva hora. El cambio queda en el historial.
          </p>
        </div>

        <CampoTexto
          id={`${id}-inicio`}
          etiqueta="Nueva fecha y hora"
          type="datetime-local"
          required
          value={inicio}
          disabled={guardando}
          error={errores.inicio}
          ayuda="Hora de Morelia."
          onChange={(evento) => {
            setInicio(evento.target.value)
            setErrores(({ inicio: _quitado, ...resto }) => resto)
          }}
        />

        <CampoSeleccion
          id={`${id}-cancha`}
          etiqueta="Cancha"
          value={canchaId}
          disabled={guardando || canchas.isPending}
          ayuda={
            canchas.isError
              ? 'No se pudo cargar el catálogo de canchas: se conserva la cancha actual.'
              : 'Por omisión se conserva la cancha actual.'
          }
          onChange={(evento) => setCanchaId(evento.target.value)}
        >
          {opcionesCanchas.map((cancha) => (
            <option key={cancha.id} value={cancha.id}>
              {cancha.id === partido.cancha.id ? `${cancha.nombre} (actual)` : cancha.nombre}
            </option>
          ))}
        </CampoSeleccion>

        <div className="flex flex-col gap-1">
          <label htmlFor={`${id}-motivo`} className="text-label-md">
            Motivo <span className="text-error" aria-hidden="true">*</span>
          </label>
          <textarea
            id={`${id}-motivo`}
            value={motivo}
            required
            rows={3}
            maxLength={MAXIMO_MOTIVO}
            disabled={guardando}
            aria-invalid={errores.motivo ? true : undefined}
            aria-describedby={errores.motivo ? `${id}-motivo-error ${id}-motivo-ayuda` : `${id}-motivo-ayuda`}
            onChange={(evento) => {
              setMotivo(evento.target.value)
              setErrores(({ motivo: _quitado, ...resto }) => resto)
            }}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md aria-invalid:border-error"
          />
          <p id={`${id}-motivo-ayuda`} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>Mínimo {MINIMO_MOTIVO} caracteres.</span>
            <span className="font-mono">
              {motivo.trim().length}/{MAXIMO_MOTIVO}
            </span>
          </p>
          {errores.motivo && (
            <p id={`${id}-motivo-error`} role="alert" className="text-body-sm text-error-strong">
              {errores.motivo}
            </p>
          )}
        </div>

        {errorGeneral && (
          <p
            role="alert"
            className="rounded-md border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
          >
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </Button>
          <Button type="submit" cargando={guardando}>
            Reprogramar
          </Button>
        </div>
      </form>
    </dialog>
  )
}
