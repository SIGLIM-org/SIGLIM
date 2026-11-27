import { useEffect, useId, useRef } from 'react'
import {
  Bot,
  CircleCheck,
  CircleDashed,
  Lock,
  MapPin,
  Repeat2,
  UserMinus,
  UserPlus,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { nombreArbitro, nombrePartido } from '@/features/arbitraje/designaciones/etiquetas'
import type {
  Designacion,
  FuncionArbitral,
  PartidoDesignable,
} from '@/features/arbitraje/designaciones/tipos'
import { formatearFechaCorta, formatearHora } from '@/features/programacion/fechas'
import {
  etiquetaEstadoPartido,
  varianteEstadoPartido,
} from '@/features/programacion/etiquetas'

/** Lugares de un partido: el central y dos asistentes [PROPUESTA] (DEC-201). */
const MAXIMO_ASISTENTES = 2

type Props = {
  partido: PartidoDesignable
  editar: boolean
  /** Confirmación de la última acción sobre este partido; al llegar, la tarjeta toma el foco. */
  mensaje?: string | null
  onDesignar: (partido: PartidoDesignable, funcion: FuncionArbitral, sustituye: Designacion | null) => void
  onRetirar: (partido: PartidoDesignable, designacion: Designacion) => void
}

type Lugar = {
  clave: string
  etiqueta: string
  funcion: FuncionArbitral
  designacion: Designacion | null
}

function lugaresDe(partido: PartidoDesignable): Lugar[] {
  const asistentes = [...(partido.asistentes ?? [])].sort((primero, segundo) =>
    primero.designadoEn.localeCompare(segundo.designadoEn),
  )

  return [
    { clave: 'central', etiqueta: 'Central', funcion: 'CENTRAL', designacion: partido.central ?? null },
    ...Array.from({ length: MAXIMO_ASISTENTES }, (_, indice) => ({
      clave: `asistente-${indice + 1}`,
      etiqueta: `Asistente ${indice + 1}`,
      funcion: 'ASISTENTE' as const,
      designacion: asistentes[indice] ?? null,
    })),
  ]
}

/** Qué le falta al partido, calculado con sus designaciones vigentes; nunca solo por color. */
function estadoTerna(partido: PartidoDesignable) {
  const asistentes = partido.asistentes?.length ?? 0

  if (!partido.central) {
    return { texto: 'Falta central', variante: 'advertencia' as const, icono: CircleDashed }
  }
  if (asistentes < MAXIMO_ASISTENTES) {
    return { texto: 'Faltan asistentes', variante: 'neutra' as const, icono: CircleDashed }
  }

  return { texto: 'Terna completa', variante: 'exito' as const, icono: CircleCheck }
}

/**
 * Partido de la jornada con su central y sus dos asistentes (F-24, UI-025). Solo con
 * `arbitraje.designacion:EDITAR` y un partido designable se ofrecen «Designar», «Cambiar» (el
 * central) y «Retirar» (DEC-211). En escritorio los tres lugares van en columnas, como el
 * prototipo; en móvil, apilados.
 */
export function TarjetaPartidoDesignacion({
  partido,
  editar,
  mensaje = null,
  onDesignar,
  onRetirar,
}: Props) {
  const id = useId()
  const tarjetaRef = useRef<HTMLElement>(null)
  const acciones = editar && partido.designable
  const partidoTexto = nombrePartido(partido)
  const terna = estadoTerna(partido)

  useEffect(() => {
    // El botón que abrió el diálogo ya no existe tras refrescar; el foco vuelve a la tarjeta.
    if (mensaje) {
      tarjetaRef.current?.focus()
    }
  }, [mensaje])

  return (
    <article
      ref={tarjetaRef}
      tabIndex={-1}
      aria-labelledby={`${id}-titulo`}
      className="flex scroll-mt-4 flex-col gap-3 rounded-lg border border-border bg-surface p-4"
    >
      <header className="flex flex-col gap-1.5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-body-sm text-text-muted">
            <span className="text-label-md text-text-main">
              {formatearFechaCorta(partido.inicio)}
            </span>
            <span className="font-mono text-mono-data tabular-nums text-text-main">
              {formatearHora(partido.inicio)}
            </span>
            <span aria-hidden="true">·</span>
            <span className="inline-flex items-center gap-1">
              <MapPin aria-hidden="true" className="size-3.5 shrink-0" />
              {partido.cancha.nombre}
            </span>
          </p>
          {partido.designable ? (
            <Insignia variante={terna.variante} icono={terna.icono}>
              {terna.texto}
            </Insignia>
          ) : (
            <Insignia variante={varianteEstadoPartido[partido.estado]}>
              {etiquetaEstadoPartido[partido.estado]}
            </Insignia>
          )}
        </div>
        <h3 id={`${id}-titulo`} className="break-words text-headline-sm text-text-main">
          {partido.equipoLocal.nombre}{' '}
          <span className="font-normal text-text-muted">vs</span>{' '}
          {partido.equipoVisitante.nombre}
        </h3>
      </header>

      {!partido.designable && editar && (
        <p className="flex items-start gap-2 text-body-sm text-text-muted">
          <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          Solo se designan árbitros a partidos programados o reprogramados.
        </p>
      )}

      <ul
        aria-label={`Árbitros de ${partidoTexto}`}
        className="flex flex-col divide-y divide-border rounded-md border border-border lg:grid lg:grid-cols-3 lg:divide-x lg:divide-y-0"
      >
        {lugaresDe(partido).map((lugar) => (
          <li
            key={lugar.clave}
            className="flex min-w-0 flex-col gap-2 px-3 py-2.5 sm:flex-row sm:items-center sm:justify-between sm:gap-4 lg:flex-col lg:items-stretch lg:justify-between lg:gap-3"
          >
            <div className="flex min-w-0 flex-col gap-0.5">
              <span className="text-label-sm uppercase text-text-muted">{lugar.etiqueta}</span>
              {lugar.designacion ? (
                <span className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                  <span className="break-words text-label-md text-text-main">
                    {nombreArbitro(lugar.designacion)}
                  </span>
                  <span className="break-words text-body-sm text-text-muted">
                    {lugar.designacion.arbitro.club.nombre}
                  </span>
                  {lugar.designacion.automatica && (
                    <Insignia variante="informacion" icono={Bot}>
                      Automática
                    </Insignia>
                  )}
                </span>
              ) : (
                <span className="text-body-md text-text-muted">Sin designar</span>
              )}
            </div>

            {acciones && (
              <div className="flex shrink-0 flex-wrap gap-2">
                {lugar.designacion ? (
                  <>
                    {lugar.funcion === 'CENTRAL' && (
                      <Button
                        variante="secundario"
                        icono={Repeat2}
                        aria-label={`Cambiar árbitro central de ${partidoTexto}`}
                        onClick={() => onDesignar(partido, 'CENTRAL', lugar.designacion)}
                      >
                        Cambiar
                      </Button>
                    )}
                    <Button
                      variante="secundario"
                      icono={UserMinus}
                      aria-label={`Retirar a ${nombreArbitro(lugar.designacion)} de ${partidoTexto}`}
                      onClick={() => lugar.designacion && onRetirar(partido, lugar.designacion)}
                    >
                      Retirar
                    </Button>
                  </>
                ) : (
                  <Button
                    variante={lugar.funcion === 'CENTRAL' ? 'primario' : 'secundario'}
                    icono={UserPlus}
                    aria-label={`Designar ${
                      lugar.funcion === 'CENTRAL' ? 'árbitro central' : lugar.etiqueta.toLowerCase()
                    } de ${partidoTexto}`}
                    onClick={() => onDesignar(partido, lugar.funcion, null)}
                  >
                    Designar
                  </Button>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-md bg-success/8 p-2.5 text-body-sm text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          {mensaje}
        </p>
      )}
    </article>
  )
}
