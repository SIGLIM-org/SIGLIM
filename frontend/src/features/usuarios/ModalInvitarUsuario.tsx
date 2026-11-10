import { useId, useState, type FormEvent } from 'react'
import { Info, MailPlus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useInvitarUsuario } from '@/features/usuarios/api'
import type {
  Ambito,
  ClubResumen,
  CrearUsuarioPeticion,
  RolCatalogo,
  Usuario,
} from '@/features/usuarios/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  roles: readonly RolCatalogo[]
  clubes: readonly ClubResumen[]
  onCerrar: () => void
  onInvitado: (usuario: Usuario) => void
}

type Valores = {
  nombreMostrado: string
  correo: string
  rolId: string
  ambito: Ambito | ''
  clubId: string
}

const VALORES_INICIALES: Valores = {
  nombreMostrado: '',
  correo: '',
  rolId: '',
  ambito: '',
  clubId: '',
}

const CAMPOS_FORMULARIO = new Set(['nombreMostrado', 'correo'])

function correoValido(correo: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo)
}

/**
 * Modal de invitación de F-12 (H-03.2, RF-094): nombre, correo, rol, ámbito y club si es de club.
 * La cuenta la crea el backend en Supabase Auth y envía el correo; aquí solo se captura la
 * asignación inicial. No promete plazos ni folios que el ERS no define.
 */
export function ModalInvitarUsuario({ roles, clubes, onCerrar, onInvitado }: Props) {
  const dialogoRef = useDialogoModal()
  const invitar = useInvitarUsuario()
  const [valores, setValores] = useState<Valores>(VALORES_INICIALES)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const rolesAsignables = roles.filter((rol) => rol.asignable)
  const guardando = invitar.isPending

  const limpiarErrores = (campos: readonly string[]) => {
    setErrores((actuales) => {
      const siguiente = { ...actuales }
      for (const campo of campos) {
        delete siguiente[campo]
      }
      return siguiente
    })
  }

  const cambiar = (cambios: Partial<Valores>) => {
    setValores((actuales) => ({ ...actuales, ...cambios }))
    limpiarErrores(Object.keys(cambios))
    setErrorGeneral(null)
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
      setErrorGeneral('No se pudo enviar la invitación. Intenta de nuevo.')
      return
    }

    if (error.problema.codigo === 'DUPLICADO') {
      setErrores({ correo: error.problema.detail })
      setErrorGeneral(null)
      return
    }

    const deCampos = Object.fromEntries(
      (error.problema.errores ?? [])
        .filter(({ campo }) => CAMPOS_FORMULARIO.has(campo))
        .map(({ campo, mensaje }) => [campo, mensaje]),
    )

    setErrores(deCampos)
    setErrorGeneral(error.problema.detail)
  }

  const validar = (): Record<string, string> => {
    const encontrados: Record<string, string> = {}
    const correo = valores.correo.trim()

    if (!valores.nombreMostrado.trim()) {
      encontrados.nombreMostrado = 'Escribe el nombre de la persona.'
    }
    if (!correo) {
      encontrados.correo = 'Escribe el correo de la invitación.'
    } else if (!correoValido(correo)) {
      encontrados.correo = 'Escribe un correo con formato válido.'
    }
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

    const peticion: CrearUsuarioPeticion = {
      nombreMostrado: valores.nombreMostrado.trim(),
      correo: valores.correo.trim().toLowerCase(),
      asignaciones: [
        {
          rolId: valores.rolId,
          ambito: valores.ambito,
          ...(valores.ambito === 'CLUB' ? { clubId: valores.clubId } : {}),
        },
      ],
    }

    invitar.mutate(peticion, { onSuccess: onInvitado, onError: alFallar })
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
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate>
        <div className="flex items-start gap-3 border-b border-border p-5">
          <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
            <MailPlus aria-hidden="true" className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 id={idTitulo} className="text-headline-md">
              Invitar usuario
            </h2>
            <p className="mt-1 text-body-sm text-text-muted">
              Se creará la cuenta y recibirá un correo para establecer su contraseña.
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-4 p-5">
          <CampoTexto
            id={`${id}-nombre`}
            etiqueta="Nombre completo"
            required
            maxLength={150}
            autoComplete="name"
            error={errores.nombreMostrado}
            value={valores.nombreMostrado}
            onChange={(evento) => cambiar({ nombreMostrado: evento.target.value })}
          />
          <CampoTexto
            id={`${id}-correo`}
            etiqueta="Correo electrónico"
            type="email"
            required
            maxLength={254}
            autoComplete="email"
            placeholder="nombre@club.mx"
            ayuda="Ahí llegará la invitación para crear la contraseña."
            error={errores.correo}
            value={valores.correo}
            onChange={(evento) => cambiar({ correo: evento.target.value })}
          />

          <div className="grid gap-3 sm:grid-cols-2">
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
              value={valores.ambito}
              onChange={(evento) => cambiarAmbito(evento.target.value as Ambito | '')}
            >
              <option value="">Elige un ámbito</option>
              <option value="LIGA">Liga</option>
              <option value="CLUB">Club</option>
              <option value="PROPIO">Propio</option>
            </CampoSeleccion>
          </div>

          {valores.ambito === 'CLUB' && (
            <CampoSeleccion
              id={`${id}-club`}
              etiqueta="Club"
              required
              error={errores.clubId}
              ayuda="La cuenta solo operará la información de este club."
              value={valores.clubId}
              onChange={(evento) => cambiar({ clubId: evento.target.value })}
            >
              <option value="">Elige el club</option>
              {clubes.map((club) => (
                <option key={club.id} value={club.id}>
                  {club.nombre}
                </option>
              ))}
            </CampoSeleccion>
          )}

          <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-sm text-info-strong">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            La cuenta queda activa con los permisos del rol. Podrás ajustar sus asignaciones
            desde el panel de la cuenta.
          </p>

          {errorGeneral && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorGeneral}
            </p>
          )}

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
              Cancelar
            </Button>
            <Button type="submit" icono={MailPlus} cargando={guardando}>
              Enviar invitación
            </Button>
          </div>
        </div>
      </form>
    </dialog>
  )
}
