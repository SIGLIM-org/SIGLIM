import { useEffect, useId, useState, type FormEvent } from 'react'
import { CircleAlert, Send } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useEquipos } from '@/features/organizacion/api'
import {
  useCrearOperacionEquipo,
  useContextoOperaciones,
} from '@/features/organizacion/operaciones/api'
import { etiquetaTipoOperacion } from '@/features/organizacion/operaciones/etiquetas'
import type {
  OperacionEquipo,
  TipoOperacionEquipo,
} from '@/features/organizacion/operaciones/tipos'
import { detalleDeFallo, erroresDeCampos } from '@/features/organizacion/erroresFormulario'
import { ApiError } from '@/lib/api/ApiError'

/** Tope de las observaciones, igual que las demás transiciones del sistema. */
const MAXIMO_OBSERVACIONES = 500

type Props = {
  onCreada: (operacion: OperacionEquipo) => void
  onCerrar: () => void
}

/**
 * Formulario de B-09 para registrar una venta o una conservación de plaza (RF-103, RF-104). La
 * operación nace SOLICITADA y el cambio de club se aplica al cerrar la temporada (RN-040); por eso
 * el texto lo dice antes de enviar. Solo se ofrecen equipos activos de temporadas abiertas.
 */
export function FormularioOperacion({ onCreada, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const id = useId()
  const equiposConsulta = useEquipos({ estado: 'ACTIVO' })
  const contextoConsulta = useContextoOperaciones()
  const crear = useCrearOperacionEquipo()
  const [tipo, setTipo] = useState<TipoOperacionEquipo>('VENTA')
  const [equipoId, setEquipoId] = useState('')
  const [clubDestinoId, setClubDestinoId] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const equipos = (equiposConsulta.data?.contenido ?? []).filter(
    (equipo) => equipo.temporada.estado !== 'CERRADA',
  )
  const clubes = contextoConsulta.data?.clubes ?? []
  const equipo = equipos.find(({ id: actual }) => actual === equipoId)
  const clubOrigenId = equipo?.club.id
  const clubesDestino = clubes.filter(({ id: actual }) => actual !== clubOrigenId)
  const primerEquipoId = equipos[0]?.id
  const cargando = equiposConsulta.isPending || contextoConsulta.isPending
  const fallo = equiposConsulta.error ?? contextoConsulta.error

  useEffect(() => {
    if (!equipoId && primerEquipoId) {
      setEquipoId(primerEquipoId)
    }
  }, [equipoId, primerEquipoId])

  useEffect(() => {
    if (tipo !== 'VENTA') {
      return
    }

    const disponibles = clubes.filter(({ id: actual }) => actual !== clubOrigenId)
    if (!clubDestinoId || !disponibles.some(({ id: actual }) => actual === clubDestinoId)) {
      setClubDestinoId(disponibles[0]?.id ?? '')
    }
  }, [tipo, clubDestinoId, clubOrigenId, clubes])

  const limpiarError = (campo: string) => {
    setErrores((actuales) => {
      if (!(campo in actuales)) {
        return actuales
      }

      const resto = { ...actuales }
      delete resto[campo]
      return resto
    })
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral(detalleDeFallo(error, 'No se pudo registrar la operación. Intenta de nuevo.'))
      return
    }

    if (error.codigo === 'VAL-015') {
      setErrorGeneral(error.problema.detail)
      return
    }

    setErrores(erroresDeCampos(error, ['tipo', 'equipoId', 'clubDestinoId', 'observaciones']))
    setErrorGeneral(error.problema.detail)
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados: Record<string, string> = {}
    const textoObservaciones = observaciones.trim()

    if (!equipoId) {
      encontrados.equipoId = 'Elige el equipo de la operación.'
    }
    if (tipo === 'VENTA') {
      if (!clubDestinoId) {
        encontrados.clubDestinoId = 'Elige el club que recibirá el equipo.'
      } else if (clubDestinoId === clubOrigenId) {
        encontrados.clubDestinoId = 'El club destino debe ser distinto del club actual del equipo.'
      }
    }
    if (textoObservaciones.length > MAXIMO_OBSERVACIONES) {
      encontrados.observaciones = `Las observaciones admiten hasta ${MAXIMO_OBSERVACIONES} caracteres.`
    }

    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    crear.mutate(
      {
        tipo,
        equipoId,
        ...(tipo === 'VENTA' ? { clubDestinoId } : {}),
        ...(textoObservaciones ? { observaciones: textoObservaciones } : {}),
      },
      { onSuccess: onCreada, onError: alFallar },
    )
  }

  const idTipo = `${id}-tipo`
  const idTipoVenta = `${id}-tipo-venta`
  const idTipoPlaza = `${id}-tipo-plaza`
  const idEquipo = `${id}-equipo`
  const idDestino = `${id}-destino`
  const idObservaciones = `${id}-observaciones`
  const idAyudaObservaciones = `${id}-observaciones-ayuda`
  const idErrorObservaciones = `${id}-observaciones-error`

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      aria-describedby={`${id}-descripcion`}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!crear.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-5 p-6">
        <div>
          <h2 id={`${id}-titulo`} className="text-balance text-headline-md">
            Registrar operación
          </h2>
          <p id={`${id}-descripcion`} className="mt-1 text-body-md text-text-muted">
            Registra una venta o la conservación de plaza de un equipo. La operación queda
            SOLICITADA y la Presidencia la autoriza o la rechaza; el cambio de club se aplica al
            cerrar la temporada.
          </p>
        </div>

        {cargando && <EstadoCarga etiqueta="Cargando equipos y clubes…" filas={2} columnas={1} />}

        {fallo != null && <ErrorConsulta error={fallo} onReintentar={() => void Promise.all([
          equiposConsulta.refetch(),
          contextoConsulta.refetch(),
        ])} />}

        {!cargando && fallo == null && equipos.length === 0 && (
          <EstadoVacio
            icono={CircleAlert}
            titulo="No hay equipos disponibles"
            descripcion="Necesitas un equipo activo en una temporada abierta para registrar una operación."
            accion={
              <Button variante="secundario" onClick={onCerrar}>
                Cerrar
              </Button>
            }
          />
        )}

        {!cargando && fallo == null && equipos.length > 0 && (
          <>
            <fieldset className="flex flex-col gap-2">
              <legend className="text-label-md text-text-main">
                Tipo de operación <span className="text-error" aria-hidden="true">*</span>
              </legend>
              <div className="grid gap-2 sm:grid-cols-2">
                {(['VENTA', 'CONSERVACION_PLAZA'] as const).map((opcion) => {
                  const etiqueta = etiquetaTipoOperacion[opcion]
                  const Icono = etiqueta.icono
                  const seleccionado = tipo === opcion
                  const idOpcion = opcion === 'VENTA' ? idTipoVenta : idTipoPlaza

                  return (
                    <label
                      key={opcion}
                      htmlFor={idOpcion}
                      className={[
                        'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors focus-within:ring-2 focus-within:ring-primary focus-within:ring-offset-2',
                        seleccionado
                          ? 'border-primary bg-primary/8'
                          : 'border-border bg-surface hover:bg-background',
                      ].join(' ')}
                    >
                      <input
                        id={idOpcion}
                        type="radio"
                        name={idTipo}
                        value={opcion}
                        checked={seleccionado}
                        onChange={() => {
                          setTipo(opcion)
                          limpiarError('tipo')
                        }}
                        className="mt-1 size-4 accent-primary"
                      />
                      <span className="min-w-0">
                        <span className="flex items-center gap-1.5 text-label-md text-text-main">
                          <Icono aria-hidden="true" className="size-4 shrink-0 text-primary" />
                          {etiqueta.texto}
                        </span>
                        <span className="mt-0.5 block text-body-sm text-text-muted">
                          {etiqueta.descripcion}
                        </span>
                      </span>
                    </label>
                  )
                })}
              </div>
              {errores.tipo && (
                <p role="alert" className="text-body-sm text-error-strong">
                  {errores.tipo}
                </p>
              )}
            </fieldset>

            <CampoSeleccion
              id={idEquipo}
              etiqueta="Equipo"
              required
              value={equipoId}
              error={errores.equipoId}
              ayuda="Solo equipos activos de temporadas abiertas."
              onChange={(evento) => {
                setEquipoId(evento.target.value)
                limpiarError('equipoId')
                limpiarError('clubDestinoId')
              }}
            >
              {equipos.map((opcion) => (
                <option key={opcion.id} value={opcion.id}>
                  {opcion.club.nombre} · {opcion.nombre} · {opcion.categoria.nombre} /{' '}
                  {opcion.grupo.nombre}
                </option>
              ))}
            </CampoSeleccion>

            {tipo === 'VENTA' && (
              <CampoSeleccion
                id={idDestino}
                etiqueta="Club destino"
                required
                value={clubDestinoId}
                error={errores.clubDestinoId}
                ayuda={
                  clubesDestino.length === 0
                    ? 'No hay otro club activo en la liga para recibir el equipo.'
                    : 'Solo aparecen clubes activos distintos del club actual del equipo.'
                }
                disabled={clubesDestino.length === 0}
                onChange={(evento) => {
                  setClubDestinoId(evento.target.value)
                  limpiarError('clubDestinoId')
                }}
              >
                {clubesDestino.length === 0 ? (
                  <option value="">Sin clubes destino disponibles</option>
                ) : (
                  clubesDestino.map((club) => (
                    <option key={club.id} value={club.id}>
                      {club.nombre}
                    </option>
                  ))
                )}
              </CampoSeleccion>
            )}

            <div className="flex flex-col gap-1">
              <label htmlFor={idObservaciones} className="text-label-md text-text-main">
                Observaciones <span className="text-text-muted">(opcional)</span>
              </label>
              <textarea
                id={idObservaciones}
                value={observaciones}
                rows={3}
                maxLength={MAXIMO_OBSERVACIONES}
                aria-invalid={errores.observaciones ? true : undefined}
                aria-describedby={`${idAyudaObservaciones}${
                  errores.observaciones ? ` ${idErrorObservaciones}` : ''
                }`}
                onChange={(evento) => {
                  setObservaciones(evento.target.value)
                  limpiarError('observaciones')
                }}
                className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
                placeholder="Por ejemplo: acuerdo de asamblea o condición de la operación."
              />
              <p
                id={idAyudaObservaciones}
                className="flex justify-between gap-2 text-body-sm text-text-muted"
              >
                <span>Quedarán en el historial de la operación.</span>
                <span className="font-mono">
                  {observaciones.trim().length}/{MAXIMO_OBSERVACIONES}
                </span>
              </p>
              {errores.observaciones && (
                <p id={idErrorObservaciones} role="alert" className="text-body-sm text-error-strong">
                  {errores.observaciones}
                </p>
              )}
            </div>
          </>
        )}

        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={crear.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            icono={Send}
            cargando={crear.isPending}
            disabled={
              crear.isPending ||
              cargando ||
              fallo != null ||
              equipos.length === 0 ||
              (tipo === 'VENTA' && clubesDestino.length === 0)
            }
          >
            Enviar a la Presidencia
          </Button>
        </div>
      </form>
    </dialog>
  )
}
