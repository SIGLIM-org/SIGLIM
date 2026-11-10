import { useId, useState, type FormEvent } from 'react'
import { Info, Search, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useBuscarPersona, useRegistrarCargoDirectiva } from '@/features/organizacion/api'
import {
  CARGOS_DIRECTIVA,
  curpValida,
  hoyEnZonaNegocio,
  nombreCompletoPersona,
} from '@/features/organizacion/directiva'
import { detalleDeFallo, erroresDeCampos } from '@/features/organizacion/erroresFormulario'
import { etiquetaCargoDirectiva } from '@/features/organizacion/etiquetas'
import type {
  CargoDirectiva,
  Club,
  Persona,
  TipoCargoDirectiva,
} from '@/features/organizacion/tipos'
import { formatearFecha } from '@/features/temporadas/fechas'
import { ApiError } from '@/lib/api/ApiError'
import { esNoEncontrado } from '@/lib/api/errores'

type Props = {
  club: Club
  /** Cargos ya registrados, para avisar del titular vigente y de los cambios pendientes. */
  cargos: readonly CargoDirectiva[]
  /** Cargo preseleccionado cuando se abre desde una fila de la tabla. */
  cargoInicial?: TipoCargoDirectiva
  onCerrar: () => void
  onRegistrado: (cargo: CargoDirectiva) => void
}

type ErroresCampos = { curp?: string; cargo?: string; desde?: string }

/**
 * Registro de un cambio de directiva (F-15, RF-102). La persona se identifica por su CURP y se
 * confirma su nombre antes de enviar el `personaId`; el cambio nace pendiente de autorización y la
 * Presidencia decide después. La vigencia nueva debe iniciar después de la del titular vigente, la
 * misma regla que aplica el servidor al autorizar (VAL-003).
 */
export function ModalRegistrarCargoDirectiva({
  club,
  cargos,
  cargoInicial,
  onCerrar,
  onRegistrado,
}: Props) {
  const dialogoRef = useDialogoModal()
  const buscar = useBuscarPersona()
  const registrar = useRegistrarCargoDirectiva(club.id)
  const [curp, setCurp] = useState('')
  const [persona, setPersona] = useState<Persona | null>(null)
  const [cargo, setCargo] = useState<TipoCargoDirectiva | ''>(cargoInicial ?? '')
  const [desde, setDesde] = useState(hoyEnZonaNegocio())
  const [errores, setErrores] = useState<ErroresCampos>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idAviso = `${id}-aviso`

  const vigente = cargo
    ? cargos.find(
        (registrado) =>
          registrado.cargo === cargo &&
          registrado.estado === 'AUTORIZADO' &&
          !registrado.hasta,
      )
    : undefined
  const pendiente = cargo
    ? cargos.find(
        (registrado) => registrado.cargo === cargo && registrado.estado === 'PENDIENTE',
      )
    : undefined

  const ayudaCargo = pendiente
    ? 'Ya hay un cambio pendiente para este cargo; la Presidencia debe resolverlo.'
    : vigente
      ? `Titular actual: ${vigente.nombrePersona}, desde ${formatearFecha(vigente.desde)}. La nueva vigencia debe iniciar después.`
      : 'No hay un titular vigente para este cargo.'

  const limpiarError = (campo: keyof ErroresCampos) => {
    setErrores((actuales) => ({ ...actuales, [campo]: undefined }))
  }

  const cambiarCurp = (valor: string) => {
    const normalizada = valor.toUpperCase().replace(/\s/g, '')
    setCurp(normalizada)
    limpiarError('curp')
    setErrorGeneral(null)
    if (persona && persona.curp !== normalizada) {
      setPersona(null)
    }
  }

  const buscarPersona = () => {
    const normalizada = curp.trim().toUpperCase()

    if (!curpValida(normalizada)) {
      setErrores((actuales) => ({
        ...actuales,
        curp: 'La CURP debe tener 18 caracteres y el formato oficial.',
      }))
      return
    }

    setErrorGeneral(null)
    buscar.mutate(normalizada, {
      onSuccess: (encontrada) => {
        setPersona(encontrada)
        setCurp(encontrada.curp)
        limpiarError('curp')
      },
      onError: (error) => {
        setPersona(null)
        if (esNoEncontrado(error)) {
          setErrores((actuales) => ({
            ...actuales,
            curp: 'No encontramos una persona con esa CURP. Verifícala o pide a la Liga que la registre.',
          }))
          return
        }
        setErrorGeneral(detalleDeFallo(error, 'No se pudo buscar la persona. Intenta de nuevo.'))
      },
    })
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral(detalleDeFallo(error, 'No se pudo registrar el cambio. Intenta de nuevo.'))
      return
    }

    switch (error.codigo) {
      case 'VALIDACION': {
        const campos = erroresDeCampos(error, ['personaId', 'cargo', 'desde'])
        setErrores({ curp: campos.personaId, cargo: campos.cargo, desde: campos.desde })
        setErrorGeneral(
          campos.personaId || campos.cargo || campos.desde ? null : error.problema.detail,
        )
        return
      }
      case 'REFERENCIA_INVALIDA':
        setPersona(null)
        setErrores((actuales) => ({ ...actuales, curp: error.problema.detail }))
        return
      case 'VAL-003':
        // La vigencia se traslapa con la del titular actual o ya hay un cambio autorizado.
        setErrorGeneral(error.problema.detail)
        return
      default:
        setErrorGeneral(error.problema.detail)
    }
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados: ErroresCampos = {}

    if (!persona) {
      encontrados.curp = 'Busca y confirma a la persona antes de registrar el cambio.'
    }
    if (!cargo) {
      encontrados.cargo = 'Elige el cargo.'
    }
    if (!desde) {
      encontrados.desde = 'Elige la fecha de inicio.'
    } else if (vigente && desde <= vigente.desde) {
      encontrados.desde = `La nueva vigencia debe iniciar después del ${formatearFecha(vigente.desde)}.`
    }

    setErrores(encontrados)

    if (!persona || !cargo || !desde || encontrados.desde) {
      return
    }

    registrar.mutate(
      { personaId: persona.id, cargo, desde },
      { onSuccess: onRegistrado, onError: alFallar },
    )
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idAviso}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!registrar.isPending && !buscar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h2 id={idTitulo} className="text-balance text-headline-md">
            Registrar cambio de directiva
          </h2>
          <p className="break-words text-body-md text-text-muted">
            Club {club.nombre}.
          </p>
        </div>

        <p id={idAviso} className="flex items-start gap-2 rounded-md bg-background p-3 text-body-md text-text-main">
          <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-info-strong" />
          <span>
            El cambio nace <strong className="font-semibold">pendiente de autorización</strong>. Al
            autorizarlo, el cargo vigente termina el día anterior al inicio que indiques.
          </span>
        </p>

        {persona ? (
          <div className="flex flex-col gap-3 rounded-md border border-border bg-background p-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0">
              <p className="text-label-sm uppercase text-text-muted">Persona</p>
              <p className="break-words text-headline-sm text-text-main">
                {nombreCompletoPersona(persona)}
              </p>
              <p className="font-mono text-body-sm text-text-muted">{persona.curp}</p>
            </div>
            <Button
              variante="secundario"
              icono={X}
              disabled={registrar.isPending}
              onClick={() => {
                setPersona(null)
                setCurp('')
              }}
            >
              Cambiar persona
            </Button>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
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
              onChange={(evento) => cambiarCurp(evento.target.value)}
              onKeyDown={(evento) => {
                if (evento.key === 'Enter') {
                  evento.preventDefault()
                  buscarPersona()
                }
              }}
            />
            <div>
              <Button
                variante="secundario"
                icono={Search}
                cargando={buscar.isPending}
                onClick={buscarPersona}
                className="w-full sm:w-auto"
              >
                Buscar persona
              </Button>
            </div>
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <CampoSeleccion
            id={`${id}-cargo`}
            etiqueta="Cargo"
            required
            value={cargo}
            error={errores.cargo}
            ayuda={ayudaCargo}
            disabled={registrar.isPending}
            onChange={(evento) => {
              setCargo(evento.target.value as TipoCargoDirectiva | '')
              limpiarError('cargo')
            }}
          >
            <option value="">Selecciona…</option>
            {CARGOS_DIRECTIVA.map((tipo) => (
              <option key={tipo} value={tipo}>
                {etiquetaCargoDirectiva[tipo].texto}
              </option>
            ))}
          </CampoSeleccion>

          <CampoTexto
            id={`${id}-desde`}
            etiqueta="Inicio de la vigencia"
            type="date"
            required
            value={desde}
            error={errores.desde}
            ayuda="El cambio queda pendiente hasta que lo autorice la Presidencia."
            disabled={registrar.isPending}
            onChange={(evento) => {
              setDesde(evento.target.value)
              limpiarError('desde')
            }}
          />
        </div>

        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button
            variante="secundario"
            onClick={onCerrar}
            disabled={registrar.isPending || buscar.isPending}
          >
            Cancelar
          </Button>
          <Button type="submit" cargando={registrar.isPending} disabled={buscar.isPending}>
            Registrar cambio
          </Button>
        </div>
      </form>
    </dialog>
  )
}
