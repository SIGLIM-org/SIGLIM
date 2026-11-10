import { useId, useState, type FormEvent } from 'react'
import { AlertaBloqueo } from '@/components/ui/AlertaBloqueo'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import { esBloqueoAceptable, partidosAfectadosDe, useRegistrarDisponibilidad } from '@/features/canchas/api'
import { AvisoPartidosAfectados } from '@/features/canchas/AvisoPartidosAfectados'
import { etiquetaBloque } from '@/features/canchas/etiquetas'
import { aIsoConDesplazamiento } from '@/features/canchas/fechas'
import type {
  PartidoEnCancha,
  RegistrarDisponibilidadPeticion,
  RegistroDisponibilidad,
  TipoRestriccion,
} from '@/features/canchas/tipos'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  canchaId: string
  canchaNombre: string
  /** Modo del modal; en `BLOQUEO` se elige además el tipo de restricción (RF-031). */
  tipo: 'DISPONIBLE' | 'BLOQUEO'
  onCerrar: () => void
  onRegistrado: (registro: RegistroDisponibilidad) => void
}

type ErroresCampos = { inicio?: string; fin?: string; motivo?: string }
type Aviso = { detalle: string; partidos: PartidoEnCancha[] }

/** [PROPUESTA] E4: valida el valor del selector sin `as`. */
function esTipoRestriccion(valor: string): valor is TipoRestriccion {
  return valor === 'BLOQUEO' || valor === 'MANTENIMIENTO'
}

/** Lleva un error de la API a su campo; `RN-021` siempre se refiere al motivo. */
function erroresDeApi(error: ApiError): ErroresCampos {
  const errores: ErroresCampos = {}

  for (const { campo, mensaje } of error.problema.errores ?? []) {
    if (campo === 'periodo.desde' || campo === 'periodo') {
      errores.inicio = mensaje
    } else if (campo === 'periodo.hasta') {
      errores.fin = mensaje
    } else if (campo === 'motivo') {
      errores.motivo = mensaje
    }
  }

  if (error.problema.codigo === 'RN-021') {
    errores.motivo = error.problema.detail
  }

  return errores
}

/**
 * Registra disponibilidad (RF-029), un bloqueo con motivo (RF-030) o un periodo de mantenimiento
 * (RF-031), que bloquea la cancha igual que un bloqueo. Una restricción sobre partidos se hace en
 * dos pasos: el 409 `RF-030` lista los afectados y solo se confirma ese mismo intervalo con ese
 * mismo tipo y motivo; cualquier cambio vuelve al primer paso (PEN-007).
 *
 * [PROPUESTA] E1/E2/E3/E5: el mantenimiento es una opción del modal de bloqueo (mismo título y
 * motivo obligatorio), no un modal aparte; solo cambia el botón de confirmación.
 */
export function ModalIntervalo({ canchaId, canchaNombre, tipo, onCerrar, onRegistrado }: Props) {
  const dialogoRef = useDialogoModal()
  const registrar = useRegistrarDisponibilidad(canchaId)
  const [inicio, setInicio] = useState('')
  const [fin, setFin] = useState('')
  const [motivo, setMotivo] = useState('')
  const [tipoRestriccion, setTipoRestriccion] = useState<TipoRestriccion>('BLOQUEO')
  const [errores, setErrores] = useState<ErroresCampos>({})
  const [errorApi, setErrorApi] = useState<ApiError | null>(null)
  const [aviso, setAviso] = useState<Aviso | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idInicio = `${id}-inicio`
  const idMotivo = `${id}-motivo`
  const idAyuda = `${id}-ayuda`
  const idErrorMotivo = `${id}-motivo-error`

  const esBloqueo = tipo === 'BLOQUEO'
  const motivoLimpio = motivo.trim()

  const cuerpo = (confirmar: boolean): RegistrarDisponibilidadPeticion => ({
    tipo: tipo === 'DISPONIBLE' ? 'DISPONIBLE' : tipoRestriccion,
    periodo: { desde: aIsoConDesplazamiento(inicio), hasta: aIsoConDesplazamiento(fin) },
    ...(motivoLimpio ? { motivo: motivoLimpio } : {}),
    ...(confirmar ? { confirmarPartidosAfectados: true } : {}),
  })

  const enviar = (confirmar: boolean) => {
    setErrorApi(null)
    registrar.mutate(cuerpo(confirmar), {
      onSuccess: onRegistrado,
      onError: (error) => {
        if (esBloqueoAceptable(error) && error instanceof ApiError) {
          setAviso({ detalle: error.problema.detail, partidos: partidosAfectadosDe(error) })
        } else if (error instanceof ApiError) {
          setAviso(null)
          setErrores(erroresDeApi(error))
          setErrorApi(error)
        }
      },
    })
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    if (aviso) {
      enviar(true)
      return
    }

    const encontrados: ErroresCampos = {}

    if (!inicio) {
      encontrados.inicio = 'Indica el inicio.'
    }
    if (!fin) {
      encontrados.fin = 'Indica el fin.'
    } else if (inicio && inicio >= fin) {
      encontrados.fin = 'El fin debe ser posterior al inicio.'
    }
    if (esBloqueo && motivoLimpio.length < MINIMO_MOTIVO) {
      encontrados.motivo = `Escribe un motivo de al menos ${MINIMO_MOTIVO} caracteres.`
    }

    setErrores(encontrados)

    if (Object.keys(encontrados).length === 0) {
      enviar(false)
    }
  }

  /** Cambiar un dato invalida el aviso: nunca se confirma algo distinto a lo que se avisó. */
  const invalidarAviso = () => {
    setAviso(null)
    setErrorApi(null)
  }

  const alCambiar = (asignar: (valor: string) => void, campo: keyof ErroresCampos) => (valor: string) => {
    asignar(valor)
    invalidarAviso()
    setErrores((actuales) => ({ ...actuales, [campo]: undefined }))
  }

  const volverAlFormulario = () => {
    setAviso(null)
    document.getElementById(idInicio)?.focus()
  }

  const codigo = errorApi?.problema.codigo
  const hayErrorDeCampo = Boolean(errores.inicio || errores.fin || errores.motivo)

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!registrar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <h2 id={idTitulo} className="text-balance text-headline-md">
          {esBloqueo ? 'Bloquear intervalo' : 'Agregar disponibilidad'} — {canchaNombre}
        </h2>

        {esBloqueo && (
          <CampoSeleccion
            id={`${id}-tipo`}
            etiqueta="Tipo de restricción"
            required
            value={tipoRestriccion}
            ayuda={
              tipoRestriccion === 'MANTENIMIENTO'
                ? 'Bloquea la cancha igual que un bloqueo y se distingue en el calendario.'
                : undefined
            }
            onChange={(evento) => {
              const valor = evento.target.value
              if (esTipoRestriccion(valor)) {
                setTipoRestriccion(valor)
                invalidarAviso()
              }
            }}
          >
            <option value="BLOQUEO">{etiquetaBloque.BLOQUEO}</option>
            <option value="MANTENIMIENTO">{etiquetaBloque.MANTENIMIENTO}</option>
          </CampoSeleccion>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <CampoTexto
            id={idInicio}
            etiqueta="Inicio"
            type="datetime-local"
            required
            ayuda="Hora de Morelia"
            error={errores.inicio}
            value={inicio}
            onChange={(evento) => alCambiar(setInicio, 'inicio')(evento.target.value)}
          />
          <CampoTexto
            id={`${id}-fin`}
            etiqueta="Fin"
            type="datetime-local"
            required
            ayuda="Hora de Morelia"
            error={errores.fin}
            value={fin}
            onChange={(evento) => alCambiar(setFin, 'fin')(evento.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor={idMotivo} className="text-label-md">
            {esBloqueo ? 'Motivo' : 'Nota (opcional)'}
            {esBloqueo && (
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            )}
          </label>
          <textarea
            id={idMotivo}
            rows={3}
            required={esBloqueo}
            maxLength={MAXIMO_MOTIVO}
            value={motivo}
            aria-invalid={errores.motivo ? true : undefined}
            aria-describedby={errores.motivo ? `${idErrorMotivo} ${idAyuda}` : idAyuda}
            onChange={(evento) => alCambiar(setMotivo, 'motivo')(evento.target.value)}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md aria-invalid:border-error"
          />
          <p id={idAyuda} className="flex justify-between gap-2 text-body-sm text-text-muted">
            <span>{esBloqueo ? `Mínimo ${MINIMO_MOTIVO} caracteres. Quedará en el historial.` : 'Se guarda con el registro.'}</span>
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

        {aviso && (
          <div role="status">
            <AvisoPartidosAfectados
              mensaje={aviso.detalle}
              nota="Los partidos no se reprogramarán ni se cancelarán."
              partidos={aviso.partidos}
            />
          </div>
        )}

        {errorApi && codigo === 'RF-029' && (
          <AlertaBloqueo
            causa="No se puede registrar la disponibilidad"
            codigo={codigo}
            conflicto={errorApi.problema.detail}
            accion="Elige un horario que no se traslape con la disponibilidad registrada."
          />
        )}
        {errorApi && codigo !== 'RF-029' && !(codigo === 'RN-021' && hayErrorDeCampo) && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorApi.problema.detail}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={registrar.isPending}>
            Cancelar
          </Button>
          {aviso ? (
            <>
              <Button variante="secundario" onClick={volverAlFormulario} disabled={registrar.isPending}>
                Cambiar horario
              </Button>
              <Button type="submit" variante="destructivo" cargando={registrar.isPending}>
                {tipoRestriccion === 'MANTENIMIENTO' ? 'Registrar de todos modos' : 'Bloquear de todos modos'}
              </Button>
            </>
          ) : (
            <Button type="submit" cargando={registrar.isPending}>
              Guardar
            </Button>
          )}
        </div>
      </form>
    </dialog>
  )
}
