import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useActualizarEquipo } from '@/features/organizacion/api'
import { detalleDeFallo, erroresDeCampos, MAXIMO_NOMBRE } from '@/features/organizacion/erroresFormulario'
import { SelectorGrupoTemporada } from '@/features/organizacion/SelectorGrupoTemporada'
import type { ActualizarEquipoPeticion, Equipo } from '@/features/organizacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  equipo: Equipo
  /** La sesión puede leer la estructura de la temporada (DEC-54). */
  puedeCambiarGrupo: boolean
  onCerrar: () => void
  onGuardado: (equipo: Equipo) => void
}

/**
 * Edita el nombre del equipo y, con la temporada en configuración, su grupo dentro de la misma
 * temporada (F-15, RF-009, RN-007). El PATCH lleva solo lo que cambió; sin cambios, cierra sin
 * llamar al servidor.
 */
export function ModalEditarEquipo({ equipo, puedeCambiarGrupo, onCerrar, onGuardado }: Props) {
  const dialogoRef = useDialogoModal()
  const actualizar = useActualizarEquipo(equipo.id)
  const [nombre, setNombre] = useState(equipo.nombre)
  const [grupoId, setGrupoId] = useState(equipo.grupo.id)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const mostrarGrupo = puedeCambiarGrupo && equipo.temporada.estado === 'CONFIGURACION'

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral(detalleDeFallo(error, 'No se pudo guardar el equipo. Intenta de nuevo.'))
      return
    }

    if (error.codigo === 'DUPLICADO') {
      setErrores({ nombre: error.problema.detail })
      return
    }

    if (mostrarGrupo && (error.codigo === 'RN-007' || error.codigo === 'REFERENCIA_INVALIDA')) {
      setErrores({ grupoId: error.problema.detail })
      return
    }

    setErrores(erroresDeCampos(error, ['nombre', 'grupoId']))
    setErrorGeneral(error.problema.detail)
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const limpio = nombre.trim()

    if (!limpio) {
      setErrores({ nombre: 'Escribe el nombre del equipo.' })
      return
    }

    const cambios: ActualizarEquipoPeticion = {}

    if (limpio !== equipo.nombre) {
      cambios.nombre = limpio
    }
    if (mostrarGrupo && grupoId !== equipo.grupo.id) {
      cambios.grupoId = grupoId
    }

    if (Object.keys(cambios).length === 0) {
      onCerrar()
      return
    }

    actualizar.mutate(cambios, { onSuccess: onGuardado, onError: alFallar })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!actualizar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h2 id={idTitulo} className="text-balance break-words text-headline-md">
            Editar — {equipo.nombre}
          </h2>
          <p className="text-body-md text-text-muted">
            {equipo.temporada.nombre} · {equipo.categoria.nombre} · Grupo {equipo.grupo.nombre}
          </p>
        </div>

        <CampoTexto
          id={`${id}-nombre`}
          etiqueta="Nombre del equipo"
          required
          maxLength={MAXIMO_NOMBRE}
          error={errores.nombre}
          value={nombre}
          onChange={(evento) => {
            setNombre(evento.target.value)
            setErrores(({ nombre: _quitado, ...resto }) => resto)
          }}
        />

        {mostrarGrupo && (
          <SelectorGrupoTemporada
            id={`${id}-grupo`}
            temporadaId={equipo.temporada.id}
            grupoActual={equipo.grupo}
            value={grupoId}
            error={errores.grupoId}
            deshabilitado={actualizar.isPending}
            onChange={(valor) => {
              setGrupoId(valor)
              setErrores(({ grupoId: _quitado, ...resto }) => resto)
            }}
          />
        )}

        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={actualizar.isPending}>
            Cancelar
          </Button>
          <Button type="submit" cargando={actualizar.isPending}>
            Guardar
          </Button>
        </div>
      </form>
    </dialog>
  )
}
