import { useId, useState, type FormEvent } from 'react'
import { UserPlus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useAsignarRol } from '@/features/usuarios/api'
import type {
  Ambito,
  AsignacionPeticion,
  AsignacionUsuario,
  ClubResumen,
  RolCatalogo,
} from '@/features/usuarios/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  usuarioId: string
  nombreUsuario: string
  roles: readonly RolCatalogo[]
  clubes: readonly ClubResumen[]
  onCerrar: () => void
  onAgregada: (asignacion: AsignacionUsuario) => void
}

type Valores = {
  rolId: string
  ambito: Ambito | ''
  clubId: string
}

const VALORES_INICIALES: Valores = { rolId: '', ambito: '', clubId: '' }

/** Alta de una asignación adicional desde el panel de F-12 (H-03.2, RF-094, RN-054). */
export function ModalAgregarAsignacion({
  usuarioId,
  nombreUsuario,
  roles,
  clubes,
  onCerrar,
  onAgregada,
}: Props) {
  const dialogoRef = useDialogoModal()
  const asignar = useAsignarRol(usuarioId)
  const [valores, setValores] = useState<Valores>(VALORES_INICIALES)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const rolesAsignables = roles.filter((rol) => rol.asignable)
  const guardando = asignar.isPending

  const limpiarErrores = (campos: readonly string[]) => {
    setErrores((actuales) => {
      const siguiente = { ...actuales }
      for (const campo of campos) {
        delete siguiente[campo]
      }
      return siguiente
    })
  }

  const cambiarRol = (rolId: string) => {
    const rol = rolesAsignables.find((candidato) => candidato.id === rolId)
    const ambito = rol?.ambitoPredeterminado ?? ''

    setValores((actuales) => ({
      ...actuales,
      rolId,
      ambito,
      clubId: ambito === 'CLUB' ? actuales.clubId : '',
    }))
    limpiarErrores(['rolId', 'ambito', 'clubId'])
    setErrorGeneral(null)
  }

  const cambiarAmbito = (ambito: Ambito | '') => {
    setValores((actuales) => ({
      ...actuales,
      ambito,
      clubId: ambito === 'CLUB' ? actuales.clubId : '',
    }))
    limpiarErrores(['ambito', 'clubId'])
    setErrorGeneral(null)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo agregar la asignación. Intenta de nuevo.')
      return
    }

    setErrorGeneral(error.problema.detail)
  }

  const validar = (): Record<string, string> => {
    const encontrados: Record<string, string> = {}

    if (!valores.rolId) {
      encontrados.rolId = 'Elige un rol.'
    }
    if (!valores.ambito) {
      encontrados.ambito = 'Elige un ámbito.'
    }
    if (valores.ambito === 'CLUB' && !valores.clubId) {
      encontrados.clubId = 'Elige el club de la asignación.'
    }

    return encontrados
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0 || !valores.ambito) {
      return
    }

    const peticion: AsignacionPeticion = {
      rolId: valores.rolId,
      ambito: valores.ambito,
      ...(valores.ambito === 'CLUB' ? { clubId: valores.clubId } : {}),
    }

    asignar.mutate(peticion, { onSuccess: onAgregada, onError: alFallar })
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
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate>
        <div className="flex items-start gap-3 border-b border-border p-5">
          <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
            <UserPlus aria-hidden="true" className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 id={idTitulo} className="text-headline-md">
              Agregar asignación
            </h2>
            <p className="mt-1 text-body-sm text-text-muted">
              La cuenta «{nombreUsuario}» recibirá los permisos del rol en el ámbito elegido.
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-4 p-5">
          <CampoSeleccion
            id={`${id}-rol`}
            etiqueta="Rol"
            required
            error={errores.rolId}
            value={valores.rolId}
            onChange={(evento) => cambiarRol(evento.target.value)}
          >
            <option value="">Elige un rol</option>
            {rolesAsignables.map((rol) => (
              <option key={rol.id} value={rol.id}>
                {rol.nombre}
              </option>
            ))}
          </CampoSeleccion>

          <CampoSeleccion
            id={`${id}-ambito`}
            etiqueta="Ámbito"
            required
            error={errores.ambito}
            ayuda="El ámbito acota qué información puede ver y modificar la cuenta."
            value={valores.ambito}
            onChange={(evento) => cambiarAmbito(evento.target.value as Ambito | '')}
          >
            <option value="">Elige un ámbito</option>
            <option value="LIGA">Liga</option>
            <option value="CLUB">Club</option>
            <option value="PROPIO">Propio</option>
          </CampoSeleccion>

          {valores.ambito === 'CLUB' && (
            <CampoSeleccion
              id={`${id}-club`}
              etiqueta="Club"
              required
              error={errores.clubId}
              value={valores.clubId}
              onChange={(evento) => {
                setValores((actuales) => ({ ...actuales, clubId: evento.target.value }))
                limpiarErrores(['clubId'])
                setErrorGeneral(null)
              }}
            >
              <option value="">Elige el club</option>
              {clubes.map((club) => (
                <option key={club.id} value={club.id}>
                  {club.nombre}
                </option>
              ))}
            </CampoSeleccion>
          )}

          {errorGeneral && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorGeneral}
            </p>
          )}

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
              Cancelar
            </Button>
            <Button type="submit" icono={UserPlus} cargando={guardando}>
              Agregar asignación
            </Button>
          </div>
        </div>
      </form>
    </dialog>
  )
}
