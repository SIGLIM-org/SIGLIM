import { useId, useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, CalendarX2, Info } from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { AvanceDesignacion } from '@/features/arbitraje/designaciones/AvanceDesignacion'
import {
  useDesignacionesJornada,
  useRetirarDesignacion,
} from '@/features/arbitraje/designaciones/api'
import {
  nombreArbitro,
  nombrePartido,
  textoFuncion,
} from '@/features/arbitraje/designaciones/etiquetas'
import { lunesDeInstante } from '@/features/arbitraje/designaciones/fechas'
import { ModalDesignarArbitro } from '@/features/arbitraje/designaciones/ModalDesignarArbitro'
import { RUTA_DESIGNACIONES } from '@/features/arbitraje/designaciones/rutas'
import { TarjetaPartidoDesignacion } from '@/features/arbitraje/designaciones/TarjetaPartidoDesignacion'
import type {
  Designacion,
  FuncionArbitral,
  PartidoDesignable,
} from '@/features/arbitraje/designaciones/tipos'
import {
  etiquetaEstadoJornada,
  varianteEstadoJornada,
} from '@/features/programacion/etiquetas'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  jornadaId: string
  /** Lunes de `?semana`; sin él, «Volver» usa la semana del primer partido. */
  semana: string | null
  editar: boolean
}

type Designando = {
  partido: PartidoDesignable
  funcion: FuncionArbitral
  sustituye: Designacion | null
}

type Retirando = {
  partido: PartidoDesignable
  designacion: Designacion
}

/**
 * Jornada abierta en F-24 (H-14.2, UI-025, DEC-211): encabezado con categoría, grupo y estado de
 * publicación, avance de la designación y una tarjeta por partido en el orden del backend. Cada
 * designación se guarda al confirmarla; no hay «Guardar designaciones».
 */
export function JornadaDesignaciones({ jornadaId, semana, editar }: Props) {
  const id = useId()
  const consulta = useDesignacionesJornada(jornadaId)
  const retirar = useRetirarDesignacion()
  const [designando, setDesignando] = useState<Designando | null>(null)
  const [retirando, setRetirando] = useState<Retirando | null>(null)
  const [errorRetiro, setErrorRetiro] = useState<string | null>(null)
  /** Confirmación de la última acción, en la tarjeta del partido que cambió. */
  const [mensaje, setMensaje] = useState<{ partidoId: string; texto: string } | null>(null)

  const primerInicio = consulta.data?.partidos[0]?.inicio
  const semanaVuelta = semana ?? (primerInicio ? lunesDeInstante(primerInicio) : null)
  const rutaVuelta = semanaVuelta
    ? `${RUTA_DESIGNACIONES}?semana=${semanaVuelta}`
    : RUTA_DESIGNACIONES

  const volver = (
    <Link to={rutaVuelta} className={`${clasesBoton('secundario')} self-start`}>
      <ArrowLeft aria-hidden="true" className="size-4 shrink-0" />
      Jornadas de la semana
    </Link>
  )

  if (consulta.isPending) {
    return (
      <div className="flex flex-col gap-4">
        {volver}
        <EstadoCarga etiqueta="Cargando la jornada…" filas={4} columnas={3} />
      </div>
    )
  }

  if (consulta.isError) {
    return (
      <div className="flex flex-col gap-4">
        {volver}
        <ErrorConsulta
          error={consulta.error}
          onReintentar={() => void consulta.refetch()}
          accion={volver}
        />
      </div>
    )
  }

  const { jornada, avance, partidos } = consulta.data

  const confirmarRetiro = (motivo: string) => {
    if (!retirando) {
      return
    }

    const { partido, designacion } = retirando
    setErrorRetiro(null)
    retirar.mutate(
      { designacionId: designacion.id, partidoId: partido.id, motivo },
      {
        onSuccess: () => {
          setRetirando(null)
          setMensaje({
            partidoId: partido.id,
            texto: `Se retiró a ${nombreArbitro(designacion)} de ${nombrePartido(partido)}.`,
          })
        },
        onError: (fallo) =>
          setErrorRetiro(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo retirar la designación. Intenta de nuevo.',
          ),
      },
    )
  }

  return (
    <div className="flex flex-col gap-4">
      {volver}

      <section
        aria-labelledby={`${id}-titulo`}
        className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-5"
      >
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <h2 id={`${id}-titulo`} className="text-headline-lg text-text-main">
              Jornada <span className="font-mono tabular-nums">{jornada.numero}</span>
            </h2>
            <p className="break-words text-body-md text-text-muted">
              {jornada.categoria.nombre} · {jornada.grupo.nombre}
            </p>
          </div>
          <Insignia variante={varianteEstadoJornada[jornada.estadoPublicacion]}>
            {etiquetaEstadoJornada[jornada.estadoPublicacion]}
          </Insignia>
        </div>
        <AvanceDesignacion avance={avance} />
        {jornada.estadoPublicacion !== 'PUBLICADO' && (
          <p className="flex items-start gap-2 text-body-sm text-text-muted">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            Cada designación se guarda al confirmarla. Los árbitros la verán en «Mis
            designaciones» cuando se publique la jornada.
          </p>
        )}
      </section>


      {partidos.length === 0 ? (
        <EstadoVacio
          icono={CalendarX2}
          titulo="La jornada no tiene partidos por designar"
          descripcion="Los partidos cancelados no se designan. Cuando la jornada tenga partidos programados, aparecerán aquí."
        />
      ) : (
        <ul aria-label={`Partidos de la jornada ${jornada.numero}`} className="flex flex-col gap-3">
          {partidos.map((partido) => (
            <li key={partido.id}>
              <TarjetaPartidoDesignacion
                partido={partido}
                editar={editar}
                mensaje={mensaje?.partidoId === partido.id ? mensaje.texto : null}
                onDesignar={(elegido, funcion, sustituye) => {
                  setMensaje(null)
                  setDesignando({ partido: elegido, funcion, sustituye })
                }}
                onRetirar={(elegido, designacion) => {
                  setMensaje(null)
                  setErrorRetiro(null)
                  setRetirando({ partido: elegido, designacion })
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {designando && (
        <ModalDesignarArbitro
          key={`${designando.partido.id}-${designando.funcion}`}
          partido={designando.partido}
          funcion={designando.funcion}
          sustituye={designando.sustituye}
          onCerrar={() => setDesignando(null)}
          onDesignado={(nombre) => {
            const { partido, funcion } = designando
            setDesignando(null)
            setMensaje({
              partidoId: partido.id,
              texto: `Se designó a ${nombre} como ${textoFuncion[funcion]} de ${nombrePartido(partido)}.`,
            })
          }}
        />
      )}

      <ModalMotivo
        abierto={retirando !== null}
        titulo={
          retirando
            ? `Retirar a ${nombreArbitro(retirando.designacion)} del partido`
            : 'Retirar designación'
        }
        descripcion={
          retirando
            ? `${nombrePartido(retirando.partido)}. La designación queda en el historial con el motivo y el lugar queda libre para designar a otro árbitro.`
            : ''
        }
        aviso={
          errorRetiro ? (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorRetiro}
            </p>
          ) : undefined
        }
        etiquetaConfirmar="Retirar"
        varianteConfirmar="destructivo"
        procesando={retirar.isPending}
        onConfirmar={confirmarRetiro}
        onCancelar={() => {
          setRetirando(null)
          setErrorRetiro(null)
        }}
      />
    </div>
  )
}
