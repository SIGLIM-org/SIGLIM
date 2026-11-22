import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useCrearJornada } from '@/features/programacion/api'
import type { GrupoProgramacion, Jornada } from '@/features/programacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  grupo: GrupoProgramacion
  /** Siguiente número libre que sugiere la pantalla; la persona puede cambiarlo. */
  numeroSugerido: number
  onCerrar: () => void
  onCreada: (jornada: Jornada) => void
}

/** Alta de una jornada en borrador (RF-033). */
export function ModalNuevaJornada({ grupo, numeroSugerido, onCerrar, onCreada }: Props) {
  const dialogoRef = useDialogoModal()
  const crear = useCrearJornada()
  const id = useId()
  const idTitulo = `${id}-titulo`
  const [numero, setNumero] = useState(String(numeroSugerido))
  const [errorNumero, setErrorNumero] = useState<string | null>(null)
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)

  const guardando = crear.isPending

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo crear la jornada. Intenta de nuevo.')
      return
    }

    setErrorNumero(error.mensajeDe('numero') ?? null)
    setErrorGeneral(error.problema.detail)
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    const valor = Number(numero)

    if (!Number.isInteger(valor) || valor < 1) {
      setErrorNumero('Escribe un número de jornada mayor que cero.')
      setErrorGeneral(null)
      return
    }

    setErrorNumero(null)
    setErrorGeneral(null)
    crear.mutate(
      { grupoId: grupo.id, numero: valor },
      { onSuccess: onCreada, onError: alFallar },
    )
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!guardando) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-md overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-balance text-headline-md">
            Nueva jornada
          </h2>
          <p className="mt-1 text-body-md text-text-muted">
            Se creará en {grupo.categoria} · {grupo.nombre} y quedará en borrador.
          </p>
        </div>

        <CampoTexto
          id={`${id}-numero`}
          etiqueta="Número de jornada"
          type="number"
          min={1}
          step={1}
          required
          inputMode="numeric"
          ayuda="Sugerido: el siguiente número libre del grupo."
          error={errorNumero ?? undefined}
          value={numero}
          onChange={(evento) => {
            setNumero(evento.target.value)
            setErrorNumero(null)
            setErrorGeneral(null)
          }}
        />

        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </Button>
          <Button type="submit" cargando={guardando}>
            Crear jornada
          </Button>
        </div>
      </form>
    </dialog>
  )
}
