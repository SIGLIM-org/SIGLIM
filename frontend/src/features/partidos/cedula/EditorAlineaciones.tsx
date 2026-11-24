import { useState, type ReactNode } from 'react'
import { Save } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useGuardarAlineaciones } from '@/features/partidos/cedula/api'
import { ColumnaAlineacion, type Seleccion } from '@/features/partidos/cedula/ColumnaAlineacion'
import type { Cedula, JugadorDisponible } from '@/features/partidos/cedula/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  cedula: Cedula
  jugadores: readonly JugadorDisponible[]
  editable: boolean
  /** Columna central entre las dos alineaciones (el marcador). */
  centro: ReactNode
}

function seleccionInicial(cedula: Cedula): Map<string, Seleccion> {
  return new Map(
    cedula.alineaciones.map((fila) => [
      fila.registroJugadorId,
      { numeroPlayera: fila.numeroPlayera === null ? '' : String(fila.numeroPlayera), titular: fila.titular },
    ]),
  )
}

/** Errores por jugador del `422 VAL-002` (DEC-165): registro → mensaje con su causa. */
function erroresPorJugador(error: unknown): Map<string, string> {
  const errores: unknown[] = error instanceof ApiError ? (error.problema.errores ?? []) : []
  const mapa = new Map<string, string>()

  for (const item of errores as { registroJugadorId?: unknown; mensaje?: unknown }[]) {
    if (typeof item.registroJugadorId === 'string' && typeof item.mensaje === 'string') {
      mapa.set(item.registroJugadorId, item.mensaje)
    }
  }

  return mapa
}

/**
 * Las tres columnas de F-26 (H-16.3): alineación local, marcador y alineación visitante. La
 * alineación de los dos equipos se guarda junta (`PUT /cedulas/{id}/alineaciones`, reemplazo completo);
 * si un jugador no puede jugar no se guarda nadie y la causa aparece en su fila (VAL-002).
 */
export function EditorAlineaciones({ cedula, jugadores, editable, centro }: Props) {
  const guardar = useGuardarAlineaciones(cedula.partido.id, cedula.id)
  const [seleccion, setSeleccion] = useState(() => seleccionInicial(cedula))
  const [errores, setErrores] = useState<Map<string, string>>(new Map())
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [cambios, setCambios] = useState(false)
  const { equipoLocal, equipoVisitante } = cedula.partido
  const noPresentado = cedula.marcador.noPresentado?.id

  const cambiar = (registroId: string, valor: Seleccion | null) => {
    setSeleccion((actual) => {
      const siguiente = new Map(actual)
      if (valor) {
        siguiente.set(registroId, valor)
      } else {
        siguiente.delete(registroId)
      }
      return siguiente
    })
    setCambios(true)
  }

  const guardarAlineaciones = () => {
    setMensaje(null)
    setErrores(new Map())
    const equipoDe = new Map(jugadores.map((jugador) => [jugador.registroJugadorId, jugador.equipoId]))
    const playeraInvalida = [...seleccion.values()].some(
      ({ numeroPlayera }) => numeroPlayera !== '' && !/^\d{1,2}$/.test(numeroPlayera),
    )
    if (playeraInvalida) {
      setMensaje('El número de playera va de 0 a 99.')
      return
    }
    guardar.mutate(
      {
        jugadores: [...seleccion.entries()].flatMap(([registroJugadorId, valor]) => {
          const equipoId = equipoDe.get(registroJugadorId)
          return equipoId
            ? [{ registroJugadorId, equipoId, titular: valor.titular, numeroPlayera: valor.numeroPlayera === '' ? null : Number(valor.numeroPlayera) }]
            : []
        }),
      },
      {
        onSuccess: (guardada) => {
          setSeleccion(seleccionInicial(guardada))
          setCambios(false)
        },
        onError: (fallo) => {
          setErrores(erroresPorJugador(fallo))
          setMensaje(fallo instanceof ApiError ? fallo.problema.detail : 'No se pudo guardar la alineación. Intenta de nuevo.')
        },
      },
    )
  }

  const columna = (lado: 'Local' | 'Visita', equipo: typeof equipoLocal) => (
    <ColumnaAlineacion
      lado={lado}
      equipo={equipo}
      jugadores={jugadores.filter(({ equipoId }) => equipoId === equipo.id)}
      seleccion={seleccion}
      errores={errores}
      editable={editable && !guardar.isPending}
      noPresentado={noPresentado === equipo.id}
      onCambiar={cambiar}
    />
  )

  return (
    <div className="flex flex-col gap-3">
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(17rem,20rem)_minmax(0,1fr)]">
        {columna('Local', equipoLocal)}
        <div className="flex flex-col gap-4 lg:order-none">{centro}</div>
        {columna('Visita', equipoVisitante)}
      </div>
      {editable && (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-end">
          {mensaje && (
            <p role="alert" className="text-body-sm text-error-strong sm:mr-auto">
              {mensaje}
            </p>
          )}
          {cambios && !mensaje && <p className="text-body-sm text-text-muted">Hay cambios sin guardar en la alineación.</p>}
          <Button icono={Save} disabled={!cambios} cargando={guardar.isPending} onClick={guardarAlineaciones}>
            Guardar alineaciones
          </Button>
        </div>
      )}
    </div>
  )
}
