import { useId, useState, type FormEvent } from 'react'
import { BadgeCheck, Info, Search, Send, UserRoundPlus, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useCrearArbitro } from '@/features/arbitraje/api'
import type { Arbitro, ClubParaArbitro } from '@/features/arbitraje/tipos'
import { useBuscarPersona, useCrearPersona } from '@/features/organizacion/api'
import {
  curpValida,
  hoyEnZonaNegocio,
  nombreCompletoPersona,
} from '@/features/organizacion/directiva'
import { detalleDeFallo, erroresDeCampos } from '@/features/organizacion/erroresFormulario'
import type { Persona } from '@/features/organizacion/tipos'
import { ApiError } from '@/lib/api/ApiError'
import { esNoEncontrado } from '@/lib/api/errores'

type Props = {
  clubes: readonly ClubParaArbitro[]
  clubInicial?: string
  /** La Directiva puede registrar personas; sin el permiso, solo elige una existente. */
  puedeRegistrarPersona: boolean
  onCerrar: () => void
  onPresentado: (arbitro: Arbitro) => void
}

type DatosPersona = {
  nombre: string
  apellidoPaterno: string
  apellidoMaterno: string
  fechaNacimiento: string
}

type Errores = {
  clubId?: string
  curp?: string
  nombre?: string
  apellidoPaterno?: string
  fechaNacimiento?: string
}

const DATOS_INICIALES: DatosPersona = {
  nombre: '',
  apellidoPaterno: '',
  apellidoMaterno: '',
  fechaNacimiento: '',
}

const CAMPOS_PERSONA = ['curp', 'nombre', 'apellidoPaterno', 'fechaNacimiento'] as const

/**
 * Presentación de un árbitro por la Directiva (F-20, RF-112). La persona se busca por CURP para
 * reutilizar su identidad (RF-010); si no existe, se registra una sola vez y se presenta. El
 * árbitro nace PROPUESTO y la Comisión lo valida después.
 */
export function ModalPresentarArbitro({
  clubes,
  clubInicial,
  puedeRegistrarPersona,
  onCerrar,
  onPresentado,
}: Props) {
  const dialogoRef = useDialogoModal()
  const buscarPersona = useBuscarPersona()
  const registrarPersona = useCrearPersona()
  const crearArbitro = useCrearArbitro()
  const [clubId, setClubId] = useState(clubInicial ?? clubes[0]?.id ?? '')
  const [curp, setCurp] = useState('')
  const [persona, setPersona] = useState<Persona | null>(null)
  const [modoNueva, setModoNueva] = useState(false)
  const [datos, setDatos] = useState<DatosPersona>(DATOS_INICIALES)
  const [errores, setErrores] = useState<Errores>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const guardando =
    buscarPersona.isPending || registrarPersona.isPending || crearArbitro.isPending
  const clubSeleccionado = clubes.find(({ id: actual }) => actual === clubId)
  const puedeEnviar =
    !guardando &&
    Boolean(clubId) &&
    (Boolean(persona) || (modoNueva && puedeRegistrarPersona))

  const limpiarError = (campo: keyof Errores) => {
    setErrores((actuales) => ({ ...actuales, [campo]: undefined }))
  }

  const presentar = (personaId: string) => {
    crearArbitro.mutate(
      { personaId, clubId },
      {
        onSuccess: onPresentado,
        onError: (error) => {
          const existente =
            error instanceof ApiError && error.codigo === 'RF-010'
              ? error.problema.extensiones?.personaId
              : undefined

          if (typeof existente === 'string' && existente !== personaId) {
            presentar(existente)
            return
          }

          setErrorGeneral(
            detalleDeFallo(error, 'No se pudo presentar al árbitro. Intenta de nuevo.'),
          )
        },
      },
    )
  }

  const cambiarCurp = (valor: string) => {
    const normalizada = valor.toUpperCase().replace(/\s/g, '')
    setCurp(normalizada)
    limpiarError('curp')
    setErrorGeneral(null)

    if (persona) {
      setPersona(null)
    }
    if (modoNueva) {
      setModoNueva(false)
    }
  }

  const buscar = () => {
    const normalizada = curp.trim().toUpperCase()

    if (!curpValida(normalizada)) {
      setErrores((actuales) => ({
        ...actuales,
        curp: 'La CURP debe tener 18 caracteres y el formato oficial.',
      }))
      return
    }

    setErrorGeneral(null)
    buscarPersona.mutate(normalizada, {
      onSuccess: (encontrada) => {
        setPersona(encontrada)
        setModoNueva(false)
        setCurp(encontrada.curp)
        setErrores({})
      },
      onError: (error) => {
        setPersona(null)
        if (esNoEncontrado(error)) {
          setModoNueva(true)
          return
        }
        setErrorGeneral(
          detalleDeFallo(error, 'No se pudo buscar la persona. Intenta de nuevo.'),
        )
      },
    })
  }

  const registrarYPresentar = () => {
    registrarPersona.mutate(
      {
        curp: curp.trim(),
        nombre: datos.nombre.trim(),
        apellidoPaterno: datos.apellidoPaterno.trim(),
        ...(datos.apellidoMaterno.trim()
          ? { apellidoMaterno: datos.apellidoMaterno.trim() }
          : {}),
        fechaNacimiento: datos.fechaNacimiento,
      },
      {
        onSuccess: (nueva) => {
          setPersona(nueva)
          presentar(nueva.id)
        },
        onError: (error) => {
          const existente =
            error instanceof ApiError && error.codigo === 'RF-010'
              ? error.problema.extensiones?.personaId
              : undefined

          if (typeof existente === 'string') {
            presentar(existente)
            return
          }

          if (!(error instanceof ApiError)) {
            setErrorGeneral(
              detalleDeFallo(error, 'No se pudo registrar a la persona. Intenta de nuevo.'),
            )
            return
          }

          const campos = erroresDeCampos(error, CAMPOS_PERSONA)
          setErrores(campos)
          setErrorGeneral(
            Object.keys(campos).length > 0 ? null : error.problema.detail,
          )
        },
      },
    )
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados: Errores = {}

    if (!clubId) {
      encontrados.clubId = 'Elige el club que presenta al árbitro.'
    }
    if (!persona && !modoNueva) {
      encontrados.curp = 'Busca a la persona por su CURP antes de presentarla.'
    }
    if (modoNueva) {
      if (!puedeRegistrarPersona) {
        setErrorGeneral(
          'No puedes registrar personas nuevas. Pide a la Liga que registre su CURP y vuelve a buscar.',
        )
        return
      }
      if (!curpValida(curp)) {
        encontrados.curp = 'La CURP debe tener 18 caracteres y el formato oficial.'
      }
      if (!datos.nombre.trim()) {
        encontrados.nombre = 'Escribe el nombre de la persona.'
      }
      if (!datos.apellidoPaterno.trim()) {
        encontrados.apellidoPaterno = 'Escribe el apellido paterno.'
      }
      if (!datos.fechaNacimiento) {
        encontrados.fechaNacimiento = 'Indica la fecha de nacimiento.'
      }
    }

    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    if (persona) {
      presentar(persona.id)
      return
    }

    registrarYPresentar()
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
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
            <UserRoundPlus aria-hidden="true" className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 id={idTitulo} className="text-headline-md">
              Presentar árbitro
            </h2>
            <p id={idDescripcion} className="mt-1 text-body-sm text-text-muted">
              El club propone a la persona; queda <strong>PROPUESTO</strong> hasta que la Comisión
              de Arbitraje lo valide.
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-4 p-5">
          {clubes.length === 1 ? (
            <div className="flex flex-col gap-1.5">
              <span className="text-label-md text-text-main">Club que lo presenta</span>
              <p className="flex min-h-11 items-center rounded-md border border-border bg-background px-3 text-body-md text-text-main">
                {clubSeleccionado?.nombre ?? 'Club asignado'}
              </p>
            </div>
          ) : (
            <CampoSeleccion
              id={`${id}-club`}
              etiqueta="Club que lo presenta"
              required
              value={clubId}
              error={errores.clubId}
              disabled={guardando}
              onChange={(evento) => {
                setClubId(evento.target.value)
                limpiarError('clubId')
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

          {persona ? (
            <div className="flex flex-col gap-3 rounded-md border border-border bg-background p-3 sm:flex-row sm:items-start sm:justify-between">
              <div className="flex min-w-0 items-start gap-3">
                <span className="grid size-9 shrink-0 place-items-center rounded-full bg-success/12 text-success-strong">
                  <BadgeCheck aria-hidden="true" className="size-5" />
                </span>
                <div className="min-w-0">
                  <p className="text-label-sm uppercase text-text-muted">Persona</p>
                  <p className="break-words text-headline-sm text-text-main">
                    {nombreCompletoPersona(persona)}
                  </p>
                  <p className="font-mono text-body-sm text-text-muted">{persona.curp}</p>
                </div>
              </div>
              <Button
                variante="secundario"
                icono={X}
                disabled={guardando}
                onClick={() => {
                  setPersona(null)
                  setCurp('')
                }}
              >
                Cambiar persona
              </Button>
            </div>
          ) : (
            <>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
                <div className="min-w-0 flex-1">
                  <CampoTexto
                    id={`${id}-curp`}
                    etiqueta="CURP de la persona"
                    required
                    maxLength={18}
                    autoComplete="off"
                    spellCheck={false}
                    value={curp}
                    error={errores.curp}
                    ayuda="18 caracteres, tal como aparecen en la credencial."
                    disabled={guardando}
                    onChange={(evento) => cambiarCurp(evento.target.value)}
                    onKeyDown={(evento) => {
                      if (evento.key === 'Enter') {
                        evento.preventDefault()
                        buscar()
                      }
                    }}
                  />
                </div>
                <Button
                  variante="secundario"
                  icono={Search}
                  cargando={buscarPersona.isPending}
                  disabled={guardando}
                  onClick={buscar}
                  className="shrink-0"
                >
                  Buscar persona
                </Button>
              </div>

              {modoNueva && (
                <section
                  aria-labelledby={`${id}-nueva`}
                  className="flex flex-col gap-3 rounded-md border border-border bg-background p-4"
                >
                  <div>
                    <h3
                      id={`${id}-nueva`}
                      className="flex items-center gap-2 text-headline-sm text-text-main"
                    >
                      <UserRoundPlus aria-hidden="true" className="size-5 text-primary" />
                      Registrar persona
                    </h3>
                    <p className="mt-1 flex items-start gap-2 text-body-sm text-text-muted">
                      <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                      No encontramos esa CURP. Captura la identidad una sola vez; después podrá
                      reutilizarse como árbitro, jugador o directivo.
                    </p>
                  </div>

                  {puedeRegistrarPersona ? (
                    <div className="grid gap-3 sm:grid-cols-2">
                      <CampoTexto
                        id={`${id}-nombre`}
                        etiqueta="Nombre"
                        required
                        maxLength={100}
                        autoComplete="given-name"
                        value={datos.nombre}
                        error={errores.nombre}
                        disabled={guardando}
                        onChange={(evento) => {
                          setDatos((actuales) => ({ ...actuales, nombre: evento.target.value }))
                          limpiarError('nombre')
                        }}
                      />
                      <CampoTexto
                        id={`${id}-paterno`}
                        etiqueta="Apellido paterno"
                        required
                        maxLength={100}
                        autoComplete="family-name"
                        value={datos.apellidoPaterno}
                        error={errores.apellidoPaterno}
                        disabled={guardando}
                        onChange={(evento) => {
                          setDatos((actuales) => ({
                            ...actuales,
                            apellidoPaterno: evento.target.value,
                          }))
                          limpiarError('apellidoPaterno')
                        }}
                      />
                      <CampoTexto
                        id={`${id}-materno`}
                        etiqueta="Apellido materno (opcional)"
                        maxLength={100}
                        autoComplete="additional-name"
                        value={datos.apellidoMaterno}
                        disabled={guardando}
                        onChange={(evento) =>
                          setDatos((actuales) => ({
                            ...actuales,
                            apellidoMaterno: evento.target.value,
                          }))
                        }
                      />
                      <CampoTexto
                        id={`${id}-nacimiento`}
                        etiqueta="Fecha de nacimiento"
                        type="date"
                        required
                        max={hoyEnZonaNegocio()}
                        value={datos.fechaNacimiento}
                        error={errores.fechaNacimiento}
                        disabled={guardando}
                        onChange={(evento) => {
                          setDatos((actuales) => ({
                            ...actuales,
                            fechaNacimiento: evento.target.value,
                          }))
                          limpiarError('fechaNacimiento')
                        }}
                      />
                    </div>
                  ) : (
                    <p className="rounded-md bg-warning/14 p-3 text-body-sm text-warning-strong">
                      No puedes registrar personas nuevas. Pide a la Liga que registre su CURP y
                      vuelve a buscar.
                    </p>
                  )}
                </section>
              )}
            </>
          )}

          <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-sm text-info-strong">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            El club que presenta al árbitro queda registrado. La Comisión puede validarlo o
            rechazarlo con observaciones.
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
            <Button
              type="submit"
              icono={persona ? Send : UserRoundPlus}
              cargando={guardando}
              disabled={!puedeEnviar}
            >
              {persona ? 'Presentar árbitro' : 'Registrar y presentar'}
            </Button>
          </div>
        </div>
      </form>
    </dialog>
  )
}
