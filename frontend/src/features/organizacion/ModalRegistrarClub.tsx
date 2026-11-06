import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useCrearClub } from '@/features/organizacion/api'
import { detalleDeFallo, erroresDeCampos, MAXIMO_NOMBRE } from '@/features/organizacion/erroresFormulario'
import type { Club } from '@/features/organizacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  onCerrar: () => void
  onRegistrado: (club: Club) => void
}

/** Alta de un club en la liga; nace ACTIVO (F-15, RF-008). Un nombre repetido marca el campo. */
export function ModalRegistrarClub({ onCerrar, onRegistrado }: Props) {
  const dialogoRef = useDialogoModal()
  const crear = useCrearClub()
  const [nombre, setNombre] = useState('')
  const [errorNombre, setErrorNombre] = useState<string>()
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const limpio = nombre.trim()

    if (!limpio) {
      setErrorNombre('Escribe el nombre del club.')
      return
    }

    crear.mutate(
      { nombre: limpio },
      {
        onSuccess: onRegistrado,
        onError: (error) => {
          if (error instanceof ApiError && error.codigo === 'DUPLICADO') {
            setErrorNombre(error.problema.detail)
            return
          }

          const deCampos = error instanceof ApiError ? erroresDeCampos(error, ['nombre']) : {}
          setErrorNombre(deCampos.nombre)
          setErrorGeneral(detalleDeFallo(error, 'No se pudo registrar el club. Intenta de nuevo.'))
        },
      },
    )
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!crear.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h2 id={idTitulo} className="text-balance text-headline-md">
            Registrar club
          </h2>
          <p className="text-body-md text-text-muted">El club queda activo en la liga.</p>
        </div>

        <CampoTexto
          id={`${id}-nombre`}
          etiqueta="Nombre"
          required
          maxLength={MAXIMO_NOMBRE}
          error={errorNombre}
          value={nombre}
          onChange={(evento) => {
            setNombre(evento.target.value)
            setErrorNombre(undefined)
          }}
        />

        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={crear.isPending}>
            Cancelar
          </Button>
          <Button type="submit" cargando={crear.isPending}>
            Registrar
          </Button>
        </div>
      </form>
    </dialog>
  )
}
