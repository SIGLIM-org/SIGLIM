import { useId, useMemo, useState, type FormEvent } from 'react'
import { CalendarClock, CircleAlert, RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useCanchas } from '@/features/canchas/api'
import { etiquetaUbicacion } from '@/features/canchas/etiquetas'
import { aIsoConDesplazamiento, valorDatetimeLocal } from '@/features/programacion/fechas'
import {
  useActualizarPartido,
  useCrearPartido,
} from '@/features/programacion/api'
import type {
  ActualizarPartidoPeticion,
  CrearPartidoPeticion,
  Jornada,
  Partido,
} from '@/features/programacion/tipos'
import { useEquipos } from '@/features/organizacion/api'
import type { Referencia } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  jornada: Jornada
  /** Partido a ajustar; sin él, el formulario registra uno nuevo (RF-033, RF-040). */
  partido?: Partido
  onCerrar: () => void
  onGuardado: (partido: Partido) => void
}

type Valores = {
  equipoLocalId: string
  equipoVisitanteId: string
  canchaId: string
  inicio: string
  motivo: string
}

type ErroresCampos = Partial<Record<keyof Valores, string>>

const CAMPOS_FORMULARIO = new Set<keyof Valores>([
  'equipoLocalId',
  'equipoVisitanteId',
  'canchaId',
  'inicio',
  'motivo',
])

function valoresDe(partido?: Partido): Valores {
  return {
    equipoLocalId: partido?.equipoLocal.id ?? '',
    equipoVisitanteId: partido?.equipoVisitante.id ?? '',
    canchaId: partido?.cancha.id ?? '',
    inicio: partido ? valorDatetimeLocal(partido.inicio) : '',
    motivo: '',
  }
}

/**
 * Solo lo que cambió (RF-040). El backend compara instantes, pero aquí se comparan los valores para
 * no pedir un motivo cuando la persona abrió el formulario y no tocó nada.
 */
function cambiosDe(partido: Partido, valores: Valores): ActualizarPartidoPeticion {
  const cambios: ActualizarPartidoPeticion = {}

  if (valores.equipoLocalId !== partido.equipoLocal.id) {
    cambios.equipoLocalId = valores.equipoLocalId
  }
  if (valores.equipoVisitanteId !== partido.equipoVisitante.id) {
    cambios.equipoVisitanteId = valores.equipoVisitanteId
  }
  if (valores.canchaId !== partido.cancha.id) {
    cambios.canchaId = valores.canchaId
  }
  if (valores.inicio) {
    const inicio = aIsoConDesplazamiento(valores.inicio)

    if (new Date(inicio).getTime() !== new Date(partido.inicio).getTime()) {
      cambios.inicio = inicio
    }
  }

  return cambios
}

function erroresDeApi(error: ApiError): ErroresCampos {
  const errores: ErroresCampos = {}

  for (const { campo, mensaje } of error.problema.errores ?? []) {
    if (CAMPOS_FORMULARIO.has(campo as keyof Valores)) {
      errores[campo as keyof Valores] = mensaje
    }
  }

  if (error.problema.codigo === 'RN-021') {
    errores.motivo = error.problema.detail
  }

  return errores
}

function opcionesDe(
  referencias: readonly Referencia[],
  actuales: readonly (Referencia | undefined)[],
): Referencia[] {
  const porId = new Map<string, Referencia>()

  for (const referencia of referencias) {
    porId.set(referencia.id, referencia)
  }
  for (const actual of actuales) {
    if (actual && !porId.has(actual.id)) {
      porId.set(actual.id, actual)
    }
  }

  return [...porId.values()]
}

/**
 * Alta y ajuste de un partido de la jornada (F-22, RF-033, RF-036, RF-040). El ajuste exige motivo
 * porque queda en el historial y la auditoría (RN-018). Los conflictos duros (VAL-003 a VAL-006)
 * llegan del backend y se muestran tal cual, sin códigos internos (DEC-63).
 */
export function ModalPartido({ jornada, partido, onCerrar, onGuardado }: Props) {
  const dialogoRef = useDialogoModal()
  const equipos = useEquipos({ grupoId: jornada.grupoId, estado: 'ACTIVO' })
  const canchas = useCanchas({ pagina: 0, tamano: 100, activa: true })
  const crear = useCrearPartido(jornada.id)
  const actualizar = useActualizarPartido(partido?.id ?? '')
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idMotivo = `${id}-motivo`
  const idAyudaMotivo = `${id}-motivo-ayuda`
  const idErrorMotivo = `${id}-motivo-error`

  const [valores, setValores] = useState(() => valoresDe(partido))
  const [errores, setErrores] = useState<ErroresCampos>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const guardando = crear.isPending || actualizar.isPending
  const cargandoCatalogos = equipos.isPending || canchas.isPending
  const errorCatalogos = equipos.error ?? canchas.error
  const cambios = partido ? cambiosDe(partido, valores) : null
  const hayCambios = cambios ? Object.keys(cambios).length > 0 : true
  const motivoLimpio = valores.motivo.trim()
  const motivoValido = motivoLimpio.length >= MINIMO_MOTIVO

  const opcionesEquipos = useMemo(
    () =>
      opcionesDe(
        (equipos.data?.contenido ?? []).map(({ id: equipoId, nombre }) => ({
          id: equipoId,
          nombre,
        })),
        [partido?.equipoLocal, partido?.equipoVisitante],
      ),
    [equipos.data, partido],
  )
  const opcionesCanchas = useMemo(
    () =>
      opcionesDe(
        (canchas.data?.contenido ?? []).map(({ id: canchaId, nombre }) => ({
          id: canchaId,
          nombre,
        })),
        [partido?.cancha],
      ),
    [canchas.data, partido],
  )

  const equipoLocal = opcionesEquipos.find(({ id: equipoId }) => equipoId === valores.equipoLocalId)
  const equipoVisitante = opcionesEquipos.find(
    ({ id: equipoId }) => equipoId === valores.equipoVisitanteId,
  )
  const canchaElegida = canchas.data?.contenido.find(
    ({ id: canchaId }) => canchaId === valores.canchaId,
  )
  const faltanEquipos = equipos.isSuccess && opcionesEquipos.length < 2
  const faltanCanchas = canchas.isSuccess && opcionesCanchas.length === 0
  const catalogoIncompleto = Boolean(errorCatalogos) || faltanEquipos || faltanCanchas
  const puedeGuardar =
    !guardando &&
    !cargandoCatalogos &&
    !catalogoIncompleto &&
    (partido ? hayCambios && motivoValido : true)

  const cambiar = (campo: keyof Valores, valor: string) => {
    setValores((actuales) => ({ ...actuales, [campo]: valor }))
    setErrores(({ [campo]: _quitado, ...resto }) => resto)
    setErrorGeneral(null)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo guardar el partido. Intenta de nuevo.')
      return
    }

    setErrores(erroresDeApi(error))
    setErrorGeneral(error.problema.detail)
  }

  const validar = (): ErroresCampos => {
    const encontrados: ErroresCampos = {}

    if (!valores.equipoLocalId) {
      encontrados.equipoLocalId = 'Elige el equipo local.'
    }
    if (!valores.equipoVisitanteId) {
      encontrados.equipoVisitanteId = 'Elige el equipo visitante.'
    } else if (valores.equipoVisitanteId === valores.equipoLocalId) {
      encontrados.equipoVisitanteId = 'El equipo visitante debe ser distinto del local.'
    }
    if (!valores.canchaId) {
      encontrados.canchaId = 'Elige la cancha.'
    }
    if (!valores.inicio) {
      encontrados.inicio = 'Indica la fecha y la hora de inicio.'
    }
    if (partido && hayCambios && !motivoValido) {
      encontrados.motivo = `Escribe un motivo de al menos ${MINIMO_MOTIVO} caracteres.`
    }

    return encontrados
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    setErrorGeneral(null)

    if (partido) {
      if (!cambios || !hayCambios) {
        return
      }

      actualizar.mutate(
        { ...cambios, motivo: motivoLimpio },
        { onSuccess: onGuardado, onError: alFallar },
      )
      return
    }

    const peticion: CrearPartidoPeticion = {
      equipoLocalId: valores.equipoLocalId,
      equipoVisitanteId: valores.equipoVisitanteId,
      canchaId: valores.canchaId,
      inicio: aIsoConDesplazamiento(valores.inicio),
    }

    crear.mutate(peticion, { onSuccess: onGuardado, onError: alFallar })
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
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-5 sm:p-6">
        <div>
          <h2 id={idTitulo} className="text-balance text-headline-md">
            {partido ? 'Ajustar partido' : 'Programar partido'}
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-md text-text-muted">
            {partido
              ? 'El ajuste queda en el historial y la auditoría de la jornada.'
              : 'Elige los equipos del grupo, la cancha y el horario de inicio.'}
          </p>
        </div>

        {errorCatalogos ? (
          <div
            role="alert"
            className="flex flex-col gap-3 rounded-md border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
          >
            <p>No se pudieron cargar los equipos o las canchas para programar el partido.</p>
            <Button
              variante="secundario"
              icono={RotateCw}
              onClick={() => {
                void equipos.refetch()
                void canchas.refetch()
              }}
            >
              Reintentar
            </Button>
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <CampoSeleccion
              id={`${id}-local`}
              etiqueta="Equipo local"
              required
              value={valores.equipoLocalId}
              disabled={guardando || cargandoCatalogos}
              error={errores.equipoLocalId}
              ayuda={faltanEquipos ? 'El grupo necesita al menos dos equipos activos.' : undefined}
              onChange={(evento) => cambiar('equipoLocalId', evento.target.value)}
            >
              <option value="">{cargandoCatalogos ? 'Cargando equipos…' : 'Elige un equipo'}</option>
              {opcionesEquipos.map((equipo) => (
                <option
                  key={equipo.id}
                  value={equipo.id}
                  disabled={equipo.id === valores.equipoVisitanteId}
                >
                  {equipo.nombre}
                </option>
              ))}
            </CampoSeleccion>

            <CampoSeleccion
              id={`${id}-visitante`}
              etiqueta="Equipo visitante"
              required
              value={valores.equipoVisitanteId}
              disabled={guardando || cargandoCatalogos}
              error={errores.equipoVisitanteId}
              onChange={(evento) => cambiar('equipoVisitanteId', evento.target.value)}
            >
              <option value="">{cargandoCatalogos ? 'Cargando equipos…' : 'Elige un equipo'}</option>
              {opcionesEquipos.map((equipo) => (
                <option
                  key={equipo.id}
                  value={equipo.id}
                  disabled={equipo.id === valores.equipoLocalId}
                >
                  {equipo.nombre}
                </option>
              ))}
            </CampoSeleccion>

            <CampoSeleccion
              id={`${id}-cancha`}
              etiqueta="Cancha"
              required
              value={valores.canchaId}
              disabled={guardando || cargandoCatalogos}
              error={errores.canchaId}
              ayuda={faltanCanchas ? 'No hay canchas activas en la liga.' : undefined}
              onChange={(evento) => cambiar('canchaId', evento.target.value)}
            >
              <option value="">{cargandoCatalogos ? 'Cargando canchas…' : 'Elige una cancha'}</option>
              {opcionesCanchas.map((cancha) => {
                const completa = canchas.data?.contenido.find(({ id: canchaId }) => canchaId === cancha.id)

                return (
                  <option key={cancha.id} value={cancha.id}>
                    {completa
                      ? `${cancha.nombre} · ${etiquetaUbicacion[completa.ubicacion]}`
                      : cancha.nombre}
                  </option>
                )
              })}
            </CampoSeleccion>

            <CampoTexto
              id={`${id}-inicio`}
              etiqueta="Inicio"
              type="datetime-local"
              required
              value={valores.inicio}
              disabled={guardando || cargandoCatalogos}
              error={errores.inicio}
              ayuda="Hora de Morelia. El fin del bloque lo calcula la liga según la categoría."
              onChange={(evento) => cambiar('inicio', evento.target.value)}
            />
          </div>
        )}

        {partido && (
          <div className="flex flex-col gap-1">
            <label htmlFor={idMotivo} className="text-label-md text-text-main">
              Motivo del ajuste <span aria-hidden="true" className="text-error">*</span>
            </label>
            <textarea
              id={idMotivo}
              value={valores.motivo}
              required={hayCambios}
              rows={3}
              maxLength={MAXIMO_MOTIVO}
              disabled={guardando}
              aria-invalid={Boolean(errores.motivo)}
              aria-describedby={
                errores.motivo ? `${idErrorMotivo} ${idAyudaMotivo}` : idAyudaMotivo
              }
              onChange={(evento) => cambiar('motivo', evento.target.value)}
              className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
            />
            <p id={idAyudaMotivo} className="flex justify-between gap-2 text-body-sm text-text-muted">
              <span>
                {hayCambios
                  ? `Obligatorio al cambiar equipos, cancha o inicio. Mínimo ${MINIMO_MOTIVO} caracteres.`
                  : 'Ajusta algún dato para habilitar el guardado.'}
              </span>
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
        )}

        {partido && (
          <p className="flex items-start gap-2 rounded-md bg-background p-3 text-body-sm text-text-muted">
            <CalendarClock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <span>
              {equipoLocal && equipoVisitante
                ? `${equipoLocal.nombre} vs. ${equipoVisitante.nombre}`
                : 'Partido de la jornada'}
              {canchaElegida ? ` · ${canchaElegida.nombre}` : ''}
              {valores.inicio ? ` · ${valores.inicio.replace('T', ' ')}` : ''}
            </span>
          </p>
        )}

        {errorGeneral && (
          <p
            role="alert"
            className="flex items-start gap-2 rounded-md border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
          >
            <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <span>{errorGeneral}</span>
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </Button>
          <Button type="submit" cargando={guardando} disabled={!puedeGuardar}>
            {partido ? 'Guardar cambios' : 'Programar partido'}
          </Button>
        </div>
      </form>
    </dialog>
  )
}
