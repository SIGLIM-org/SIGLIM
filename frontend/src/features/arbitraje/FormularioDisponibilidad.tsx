import { useId, useState, type FormEvent } from 'react'
import { CalendarPlus } from 'lucide-react'
import { AlertaBloqueo } from '@/components/ui/AlertaBloqueo'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useRegistrarDisponibilidadArbitro } from '@/features/arbitraje/api'
import { periodoDelFormulario } from '@/features/arbitraje/fechasDisponibilidad'
import type { DisponibilidadArbitral } from '@/features/arbitraje/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  arbitroId: string
  onRegistrada: (disponibilidad: DisponibilidadArbitral) => void
}

type Campo = 'fecha' | 'horaInicial' | 'horaFinal'
type Errores = Partial<Record<Campo, string>>

/** Lleva los errores de campo del `400 VALIDACION` a la fecha y las horas del formulario. */
function erroresDeApi(error: ApiError): Errores {
  const errores: Errores = {}

  for (const { campo, mensaje } of error.errores) {
    if (campo === 'periodo.desde') {
      errores.horaInicial = mensaje
    } else if (campo === 'periodo.hasta' || campo === 'periodo') {
      errores.horaFinal = mensaje
    }
  }

  return errores
}

/**
 * Alta de un periodo de disponibilidad (B-09.2, RF-022): una fecha y un horario de Morelia. El
 * periodo es `[hora inicial, hora final)` del mismo día, así que la hora final debe ser posterior a
 * la inicial; el backend lo vuelve a validar (`400 VALIDACION`) y solo admite árbitros `VALIDADO`
 * (`409 RF-022`). Tras registrar se conserva la fecha para capturar otro horario del mismo día.
 */
export function FormularioDisponibilidad({ arbitroId, onRegistrada }: Props) {
  const id = useId()
  const registrar = useRegistrarDisponibilidadArbitro(arbitroId)
  const [fecha, setFecha] = useState('')
  const [horaInicial, setHoraInicial] = useState('')
  const [horaFinal, setHoraFinal] = useState('')
  const [disponible, setDisponible] = useState(true)
  const [errores, setErrores] = useState<Errores>({})
  const [errorApi, setErrorApi] = useState<ApiError | null>(null)

  const cambiar = (asignar: (valor: string) => void, campo: Campo) => (valor: string) => {
    asignar(valor)
    setErrorApi(null)
    setErrores((actuales) => ({ ...actuales, [campo]: undefined }))
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    const encontrados: Errores = {}

    if (!fecha) {
      encontrados.fecha = 'Indica la fecha.'
    }
    if (!horaInicial) {
      encontrados.horaInicial = 'Indica la hora inicial.'
    }
    if (!horaFinal) {
      encontrados.horaFinal = 'Indica la hora final.'
    } else if (horaInicial && horaFinal <= horaInicial) {
      encontrados.horaFinal = 'La hora final debe ser posterior a la inicial.'
    }

    setErrores(encontrados)
    setErrorApi(null)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    registrar.mutate(
      { periodo: periodoDelFormulario(fecha, horaInicial, horaFinal), disponible },
      {
        onSuccess: (registrada) => {
          setHoraInicial('')
          setHoraFinal('')
          onRegistrada(registrada)
        },
        onError: (error) => {
          if (error instanceof ApiError) {
            setErrores(erroresDeApi(error))
            setErrorApi(error)
          }
        },
      },
    )
  }

  const codigo = errorApi?.problema.codigo
  const hayErrorDeCampo = Object.values(errores).some(Boolean)

  return (
    <form
      onSubmit={alEnviar}
      noValidate
      aria-labelledby={`${id}-titulo`}
      className="flex flex-col gap-4 rounded-lg border border-border bg-background p-4"
    >
      <div>
        <h4 id={`${id}-titulo`} className="text-label-md font-semibold text-text-main">
          Registrar periodo
        </h4>
        <p className="text-body-sm text-text-muted">Fecha y horario en hora de Morelia.</p>
      </div>

      <CampoTexto
        id={`${id}-fecha`}
        etiqueta="Fecha"
        type="date"
        required
        error={errores.fecha}
        value={fecha}
        onChange={(evento) => cambiar(setFecha, 'fecha')(evento.target.value)}
      />

      <div className="grid grid-cols-2 gap-3">
        <CampoTexto
          id={`${id}-hora-inicial`}
          etiqueta="Hora inicial"
          type="time"
          required
          error={errores.horaInicial}
          value={horaInicial}
          onChange={(evento) => cambiar(setHoraInicial, 'horaInicial')(evento.target.value)}
        />
        <CampoTexto
          id={`${id}-hora-final`}
          etiqueta="Hora final"
          type="time"
          required
          error={errores.horaFinal}
          value={horaFinal}
          onChange={(evento) => cambiar(setHoraFinal, 'horaFinal')(evento.target.value)}
        />
      </div>

      <CampoSeleccion
        id={`${id}-disponible`}
        etiqueta="Registrar como"
        value={disponible ? 'SI' : 'NO'}
        ayuda={
          disponible
            ? 'Puede ser designado en este horario.'
            : 'Declara que no puede ser designado en este horario.'
        }
        onChange={(evento) => {
          setDisponible(evento.target.value === 'SI')
          setErrorApi(null)
        }}
      >
        <option value="SI">Disponible</option>
        <option value="NO">No disponible</option>
      </CampoSeleccion>

      {errorApi && codigo === 'RF-022' && (
        <AlertaBloqueo
          causa="No se puede registrar la disponibilidad"
          codigo={codigo}
          conflicto={errorApi.problema.detail}
          accion="Solo se registra para árbitros validados. Los periodos que ya tiene se pueden consultar y retirar."
        />
      )}
      {errorApi && codigo !== 'RF-022' && !hayErrorDeCampo && (
        <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
          {errorApi.problema.detail}
        </p>
      )}

      <Button type="submit" icono={CalendarPlus} cargando={registrar.isPending}>
        Agregar periodo
      </Button>
    </form>
  )
}
