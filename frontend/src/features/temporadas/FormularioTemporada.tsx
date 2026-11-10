import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useActualizarTemporada, useCrearTemporada } from '@/features/temporadas/api'
import type {
  ActualizarTemporadaPeticion,
  CrearTemporadaPeticion,
  Temporada,
} from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  /** Temporada a editar; sin ella, el formulario crea una nueva. */
  temporada?: Temporada
  onCerrar: () => void
  onGuardada: (temporada: Temporada) => void
}

type Valores = {
  nombre: string
  fechaInicio: string
  fechaFin: string
}

const CAMPOS_FORMULARIO = new Set<string>(['nombre', 'fechaInicio', 'fechaFin'])

function valoresDe(temporada?: Temporada): Valores {
  return {
    nombre: temporada?.nombre ?? '',
    fechaInicio: temporada?.fechaInicio ?? '',
    fechaFin: temporada?.fechaFin ?? '',
  }
}

/** Solo lo que cambió (RF-001): un campo ausente conserva su valor. */
function cambiosDe(temporada: Temporada, valores: Valores): ActualizarTemporadaPeticion {
  const cambios: ActualizarTemporadaPeticion = {}
  const nombre = valores.nombre.trim()

  if (nombre !== temporada.nombre) {
    cambios.nombre = nombre
  }
  if (valores.fechaInicio !== temporada.fechaInicio) {
    cambios.fechaInicio = valores.fechaInicio
  }
  if (valores.fechaFin && valores.fechaFin !== (temporada.fechaFin ?? '')) {
    cambios.fechaFin = valores.fechaFin
  }

  return cambios
}

/**
 * Alta y edición de una temporada (F-10, RF-001). El estado no se cambia aquí: la activación y el
 * cierre tienen sus propias acciones. La fecha de fin es opcional al crear; una vez definida no se
 * puede quitar porque el API solo cambia los campos presentes.
 */
export function FormularioTemporada({ temporada, onCerrar, onGuardada }: Props) {
  const dialogoRef = useDialogoModal()
  const crear = useCrearTemporada()
  const actualizar = useActualizarTemporada(temporada?.id ?? '')
  const [valores, setValores] = useState(() => valoresDe(temporada))
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const guardando = crear.isPending || actualizar.isPending

  const cambiar = (campo: keyof Valores, valor: string) => {
    setValores((actuales) => ({ ...actuales, [campo]: valor }))
    setErrores(({ [campo]: _quitado, ...resto }) => resto)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo guardar la temporada. Intenta de nuevo.')
      return
    }

    if (error.problema.codigo === 'DUPLICADO') {
      setErrores({ nombre: error.problema.detail })
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

    if (!valores.nombre.trim()) {
      encontrados.nombre = 'Escribe el nombre de la temporada.'
    }
    if (!valores.fechaInicio) {
      encontrados.fechaInicio = 'Elige la fecha de inicio.'
    }
    if (temporada?.fechaFin && !valores.fechaFin) {
      encontrados.fechaFin = 'La fecha de fin ya está definida; cámbiala o déjala como está.'
    } else if (valores.fechaFin && valores.fechaFin < valores.fechaInicio) {
      encontrados.fechaFin = 'La fecha de fin no puede ser anterior a la fecha de inicio.'
    }

    return encontrados
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    if (temporada) {
      const cambios = cambiosDe(temporada, valores)

      if (Object.keys(cambios).length === 0) {
        onCerrar()
        return
      }

      actualizar.mutate(cambios, { onSuccess: onGuardada, onError: alFallar })
      return
    }

    const peticion: CrearTemporadaPeticion = {
      nombre: valores.nombre.trim(),
      fechaInicio: valores.fechaInicio,
      ...(valores.fechaFin ? { fechaFin: valores.fechaFin } : {}),
    }

    crear.mutate(peticion, { onSuccess: onGuardada, onError: alFallar })
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
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            {temporada ? 'Editar temporada' : 'Nueva temporada'}
          </h2>
          <p className="mt-1 text-body-sm text-text-muted">
            {temporada
              ? 'Los cambios se guardan en la temporada; el estado no cambia desde aquí.'
              : 'La temporada se crea en configuración. Podrás activarla cuando cumpla la lista de verificación.'}
          </p>
        </div>

        <CampoTexto
          id={`${id}-nombre`}
          etiqueta="Nombre"
          required
          maxLength={100}
          error={errores.nombre}
          value={valores.nombre}
          onChange={(evento) => cambiar('nombre', evento.target.value)}
        />
        <CampoTexto
          id={`${id}-inicio`}
          etiqueta="Fecha de inicio"
          type="date"
          required
          error={errores.fechaInicio}
          value={valores.fechaInicio}
          onChange={(evento) => cambiar('fechaInicio', evento.target.value)}
        />
        <CampoTexto
          id={`${id}-fin`}
          etiqueta="Fecha de fin"
          type="date"
          min={valores.fechaInicio || undefined}
          ayuda={
            temporada?.fechaFin
              ? 'Ya está definida; no se puede quitar.'
              : 'Puedes definirla después.'
          }
          error={errores.fechaFin}
          value={valores.fechaFin}
          onChange={(evento) => cambiar('fechaFin', evento.target.value)}
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
            Guardar
          </Button>
        </div>
      </form>
    </dialog>
  )
}
