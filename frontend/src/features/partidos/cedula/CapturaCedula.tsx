import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, ClipboardPlus, Lock, Send } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { usePartido } from '@/features/partidos/api'
import { useAbrirCedula, useCedula, useEnviarCedula, useJugadoresDisponibles } from '@/features/partidos/cedula/api'
import { EditorAlineaciones } from '@/features/partidos/cedula/EditorAlineaciones'
import { mensajeDeError } from '@/features/partidos/cedula/errores'
import { LineaEventos } from '@/features/partidos/cedula/LineaEventos'
import { PanelIncidencias } from '@/features/partidos/cedula/PanelIncidencias'
import { PanelMarcador } from '@/features/partidos/cedula/PanelMarcador'
import { PanelObservaciones } from '@/features/partidos/cedula/PanelObservaciones'
import { faltantesDeCedula } from '@/features/partidos/cedula/reglas'
import type { Faltante } from '@/features/partidos/cedula/tipos'
import { ValidacionPrevia } from '@/features/partidos/cedula/ValidacionPrevia'
import { estadoCedula } from '@/features/partidos/etiquetas'
import { formatearFechaHora } from '@/features/temporadas/fechas'
import { ApiError } from '@/lib/api/ApiError'
import { esNoEncontrado } from '@/lib/api/errores'

type Props = {
  partidoId: string
}

/** Faltantes del `422 VAL-020` (DEC-170), para mostrar lo que dijo el backend. */
function faltantesDelError(error: unknown): Faltante[] | null {
  const faltantes = error instanceof ApiError ? error.problema.extensiones?.faltantes : undefined
  return Array.isArray(faltantes) ? (faltantes as Faltante[]) : null
}

/**
 * Captura de la cédula del partido (F-26, UI-011, H-16.3): alineaciones de los dos equipos con su
 * elegibilidad, marcador, eventos, incidencias y observaciones, con la validación previa al envío
 * (VAL-020). Fuera de `EN_CAPTURA` la cédula se muestra sin edición (RF-122).
 */
export function CapturaCedula({ partidoId }: Props) {
  const consulta = useCedula(partidoId)
  const cedula = consulta.data
  const partido = usePartido(partidoId)
  const jugadores = useJugadoresDisponibles(cedula?.id)
  const abrir = useAbrirCedula(partidoId)
  const enviar = useEnviarCedula(partidoId, cedula?.id ?? '')
  const [errorEnvio, setErrorEnvio] = useState<{ mensaje: string; faltantes: Faltante[] | null } | null>(null)

  const volver = (
    <Link
      to={`/app/partidos/${partidoId}`}
      className="inline-flex w-fit items-center gap-1.5 rounded-sm text-label-md text-primary underline-offset-4 hover:underline"
    >
      <ArrowLeft aria-hidden="true" className="size-4" />
      Detalle del partido
    </Link>
  )

  if (consulta.isPending) {
    return (
      <div className="flex flex-col gap-4">
        {volver}
        <EstadoCarga etiqueta="Cargando la cédula…" filas={6} columnas={3} />
      </div>
    )
  }

  if (consulta.isError && esNoEncontrado(consulta.error) && partido.isSuccess) {
    const enCurso = partido.data.estado === 'EN_CURSO'

    return (
      <div className="flex flex-col gap-4">
        {volver}
        <EstadoVacio
          icono={ClipboardPlus}
          titulo={`${partido.data.equipoLocal.nombre} vs. ${partido.data.equipoVisitante.nombre} aún no tiene cédula`}
          descripcion={
            enCurso
              ? 'Ábrela para capturar las alineaciones, el marcador y los eventos. Quedarás como capturista y no podrás aprobarla.'
              : 'La cédula se abre cuando el partido está en curso.'
          }
          accion={
            enCurso ? (
              <div className="flex flex-col items-center gap-2">
                <Button icono={ClipboardPlus} cargando={abrir.isPending} onClick={() => abrir.mutate(undefined)}>
                  Abrir cédula
                </Button>
                {abrir.isError && (
                  <p role="alert" className="text-body-sm text-error-strong">
                    {mensajeDeError(abrir.error, 'No se pudo abrir la cédula. Intenta de nuevo.')}
                  </p>
                )}
              </div>
            ) : undefined
          }
        />
      </div>
    )
  }

  if (consulta.isError || !cedula) {
    return (
      <div className="flex flex-col gap-4">
        {volver}
        <ErrorConsulta error={consulta.error} onReintentar={() => void consulta.refetch()} />
      </div>
    )
  }

  const editable = cedula.estado === 'EN_CAPTURA'
  const estado = estadoCedula[cedula.estado]
  const faltantes = faltantesDeCedula(cedula)
  const { equipoLocal, equipoVisitante } = cedula.partido

  const enviarCedula = () => {
    setErrorEnvio(null)
    enviar.mutate(undefined, {
      onError: (fallo) =>
        setErrorEnvio({
          mensaje: mensajeDeError(fallo, 'No se pudo enviar la cédula. Intenta de nuevo.'),
          faltantes: faltantesDelError(fallo),
        }),
    })
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      {volver}

      <header className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Captura de cédula</h1>
            {estado && <Insignia variante={estado.variante}>{estado.texto}</Insignia>}
          </div>
          <p className="mt-1 break-words text-body-lg text-text-main">
            {equipoLocal.nombre} <span className="text-text-muted">vs.</span> {equipoVisitante.nombre}
          </p>
          <p className="text-body-md text-text-muted">
            <span className="font-mono tabular-nums">{formatearFechaHora(cedula.partido.inicio)}</span>
            {cedula.capturista?.nombre && <> · Capturista: {cedula.capturista.nombre}</>}
          </p>
        </div>
        {editable && (
          <div className="flex flex-col gap-1 lg:items-end">
            <Button icono={Send} disabled={faltantes.length > 0} cargando={enviar.isPending} onClick={enviarCedula}>
              Enviar a validación
            </Button>
            <p className="text-body-sm text-text-muted lg:text-right">Después ya no se edita.</p>
          </div>
        )}
      </header>

      {editable ? (
        <ValidacionPrevia faltantes={errorEnvio?.faltantes ?? faltantes} />
      ) : (
        <p className="flex items-start gap-2 rounded-lg border border-border bg-background p-3 text-body-md text-text-main">
          <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-text-muted" />
          <span>
            La cédula está {estado?.texto.toLowerCase() ?? 'fuera de captura'}: ya no se edita.
            {cedula.estado === 'PENDIENTE_VALIDACION' && cedula.plazoVenceEn && (
              <> El árbitro tiene hasta el <span className="font-mono tabular-nums">{formatearFechaHora(cedula.plazoVenceEn)}</span> para validarla.</>
            )}
          </span>
        </p>
      )}
      {errorEnvio && !errorEnvio.faltantes && (
        <p role="alert" className="rounded-lg border border-error/30 bg-error/8 p-3 text-body-md text-error-strong">
          {errorEnvio.mensaje}
        </p>
      )}

      {jugadores.isError ? (
        <ErrorConsulta error={jugadores.error} onReintentar={() => void jugadores.refetch()} />
      ) : jugadores.isPending ? (
        <EstadoCarga etiqueta="Cargando los jugadores…" filas={6} columnas={3} />
      ) : (
        <EditorAlineaciones
          key={`${cedula.estado}-${cedula.alineaciones.map(({ id, numeroPlayera, titular }) => `${id}${numeroPlayera}${titular}`).join()}`}
          cedula={cedula}
          jugadores={jugadores.data}
          editable={editable}
          centro={<PanelMarcador key={`${cedula.estado}-${JSON.stringify(cedula.marcador)}`} cedula={cedula} editable={editable} />}
        />
      )}

      <LineaEventos cedula={cedula} editable={editable} />

      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <PanelIncidencias cedula={cedula} editable={editable} />
        <PanelObservaciones key={`${cedula.estado}-${cedula.observaciones}`} cedula={cedula} editable={editable} />
      </div>
    </div>
  )
}
