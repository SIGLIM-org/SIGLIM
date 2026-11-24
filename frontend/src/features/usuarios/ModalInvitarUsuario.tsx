import { useId, useState, type FormEvent } from 'react'
import { Info, MailPlus, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { curpValida } from '@/features/organizacion/directiva'
import { normalizarCurp } from '@/features/registros/asistenteAlta'
import { useInvitarUsuario } from '@/features/usuarios/api'
import {
  CODIGO_ROL_JUGADOR_TUTOR,
  type Ambito,
  type ClubResumen,
  type CrearUsuarioPeticion,
  type RolCatalogo,
  type Usuario,
} from '@/features/usuarios/tipos'
import { ApiError } from '@/lib/api/ApiError'
import type { Problema } from '@/lib/api/tipos'

type Props = {
  roles: readonly RolCatalogo[]
  clubes: readonly ClubResumen[]
  onCerrar: () => void
  onInvitado: (usuario: Usuario) => void
  /** Abre el panel de una cuenta que ya existe con esa persona (409 `DUPLICADO`, DEC-168). */
  onAbrirCuenta: (perfilId: string) => void
}

type Valores = {
  nombreMostrado: string
  correo: string
  rolId: string
  ambito: Ambito | ''
  clubId: string
  curp: string
}

const VALORES_INICIALES: Valores = {
  nombreMostrado: '',
  correo: '',
  rolId: '',
  ambito: '',
  clubId: '',
  curp: '',
}

const CAMPOS_FORMULARIO = new Set(['nombreMostrado', 'correo', 'curp'])

/** Textos propios de los errores de la CURP (DEC-168); los demás usan `detail`. */
const MENSAJES_CURP: Readonly<Record<string, string>> = {
  REFERENCIA_INVALIDA:
    'No hay una persona registrada con esa CURP. Regístrala primero (alta de jugador o tutela) y vuelve a invitar.',
  'RF-106':
    'Esa CURP es de un menor de edad, que no tiene cuenta propia. Invita a su tutor con la CURP del tutor.',
  DUPLICADO:
    'Esa persona ya tiene una cuenta en SIGLIM, activa o desactivada. Ábrela para reactivarla o agregarle el rol.',
}

function correoValido(correo: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo)
}

/** Lee una extensión de texto del problema (`campo`, `perfilId`); cualquier otro tipo se ignora. */
function extensionTexto(problema: Problema, clave: string): string | undefined {
  const valor = problema.extensiones?.[clave]
  return typeof valor === 'string' ? valor : undefined
}

/**
 * Modal de invitación de F-12 (H-03.2, RF-094): nombre, correo, rol, ámbito y club si es de club.
 * La cuenta la crea el backend en Supabase Auth y envía el correo; aquí solo se captura la
 * asignación inicial. No promete plazos ni folios que el ERS no define.
 *
 * Con el rol `JUGADOR_TUTOR` pide la CURP de la persona para vincularla a la cuenta (DEF-10.2,
 * DEC-155, DEC-167): es obligatoria en la interfaz porque sin vínculo la cuenta no ve «Mi
 * información» y no se puede vincular después (DEC-159). Se normaliza y se valida antes de enviar,
 * y se descarta al cambiar a otro rol. Los errores de la CURP se muestran bajo su campo según
 * `codigo` y `extensiones.campo`; un 409 con `perfilId` ofrece abrir la cuenta existente (DEC-168).
 */
export function ModalInvitarUsuario({
  roles,
  clubes,
  onCerrar,
  onInvitado,
  onAbrirCuenta,
}: Props) {
  const dialogoRef = useDialogoModal()
  const invitar = useInvitarUsuario()
  const [valores, setValores] = useState<Valores>(VALORES_INICIALES)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const [cuentaExistente, setCuentaExistente] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const rolesAsignables = roles.filter((rol) => rol.asignable)
  const guardando = invitar.isPending
  const pideCurp =
    rolesAsignables.find((rol) => rol.id === valores.rolId)?.codigo === CODIGO_ROL_JUGADOR_TUTOR

  const limpiarErrores = (campos: readonly string[]) => {
    setErrores((actuales) => {
      const siguiente = { ...actuales }
      for (const campo of campos) {
        delete siguiente[campo]
      }
      return siguiente
    })
    if (campos.includes('curp')) {
      setCuentaExistente(null)
    }
  }

  const cambiar = (cambios: Partial<Valores>) => {
    setValores((actuales) => ({ ...actuales, ...cambios }))
    limpiarErrores(Object.keys(cambios))
    setErrorGeneral(null)
  }

  const cambiarRol = (rolId: string) => {
    const rol = rolesAsignables.find((candidato) => candidato.id === rolId)
    const ambito = rol?.ambitoPredeterminado ?? ''
    const conservaCurp = rol?.codigo === CODIGO_ROL_JUGADOR_TUTOR

    setValores((actuales) => ({
      ...actuales,
      rolId,
      ambito,
      clubId: ambito === 'CLUB' ? actuales.clubId : '',
      curp: conservaCurp ? actuales.curp : '',
    }))
    limpiarErrores(conservaCurp ? ['rolId', 'ambito', 'clubId'] : ['rolId', 'ambito', 'clubId', 'curp'])
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
    setCuentaExistente(null)

    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo enviar la invitación. Intenta de nuevo.')
      return
    }

    const { problema } = error
    const campo = extensionTexto(problema, 'campo')

    if (campo === 'curp') {
      setErrores({ curp: MENSAJES_CURP[problema.codigo] ?? problema.detail })
      setErrorGeneral(null)
      if (problema.codigo === 'DUPLICADO') {
        setCuentaExistente(extensionTexto(problema, 'perfilId') ?? null)
      }
      return
    }

    if (problema.codigo === 'DUPLICADO' && (campo === undefined || campo === 'correo')) {
      setErrores({ correo: problema.detail })
      setErrorGeneral(null)
      return
    }

    const deCampos: Record<string, string> = Object.fromEntries(
      (problema.errores ?? [])
        .filter(({ campo: nombre }) => CAMPOS_FORMULARIO.has(nombre))
        .map(({ campo: nombre, mensaje }) => [nombre, mensaje]),
    )

    if (
      problema.codigo === 'VALIDACION' &&
      campo !== undefined &&
      CAMPOS_FORMULARIO.has(campo) &&
      !deCampos[campo]
    ) {
      deCampos[campo] = problema.detail
    }

    setErrores(deCampos)
    setErrorGeneral(problema.detail)
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
    if (pideCurp) {
      const curp = normalizarCurp(valores.curp)
      if (!curp) {
        encontrados.curp =
          'Escribe la CURP de la persona: sin ella la cuenta no verá «Mi información».'
      } else if (!curpValida(curp)) {
        encontrados.curp = 'La CURP debe tener 18 caracteres y el formato oficial.'
      }
    }

    return encontrados
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)
    setCuentaExistente(null)

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0 || !valores.ambito) {
      return
    }

    const peticion: CrearUsuarioPeticion = {
      nombreMostrado: valores.nombreMostrado.trim(),
      correo: valores.correo.trim().toLowerCase(),
      ...(pideCurp ? { curp: normalizarCurp(valores.curp) } : {}),
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

          {pideCurp && (
            <div className="flex flex-col gap-2">
              <CampoTexto
                id={`${id}-curp`}
                etiqueta="CURP de la persona"
                required
                maxLength={18}
                autoComplete="off"
                spellCheck={false}
                ayuda="18 caracteres. Debe ser de una persona adulta ya registrada; con ella la cuenta verá «Mi información»."
                error={errores.curp}
                value={valores.curp}
                onChange={(evento) => cambiar({ curp: evento.target.value })}
              />
              {errores.curp && cuentaExistente && (
                <div>
                  <Button
                    variante="secundario"
                    icono={UserRound}
                    onClick={() => onAbrirCuenta(cuentaExistente)}
                  >
                    Ver la cuenta
                  </Button>
                </div>
              )}
            </div>
          )}

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
