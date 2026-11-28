import {
  CalendarPlus,
  CircleAlert,
  Coffee,
  Pencil,
  Sparkles,
  TriangleAlert,
  UserRound,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import {
  etiquetaConflicto,
  etiquetaEstadoJornada,
  varianteEstadoJornada,
} from '@/features/programacion/etiquetas'
import { horarioConDia } from '@/features/programacion/fechas'
import type {
  AvisoFecha,
  BloqueFecha as Bloque,
  FilaPartidoFecha,
  SinAsignarFecha,
} from '@/features/programacion/tipos'

type Props = {
  bloque: Bloque
  /** Conflictos duros de la verificación, por partido. */
  conflictosPorPartido: ReadonlyMap<string, readonly AvisoFecha[]>
  /** Causa por la que la última asignación no dio árbitro central a un partido (H-34.2, DEC-216). */
  causasSinArbitro?: ReadonlyMap<string, string>
  /** Se ajusta y se asigna solo con permiso de edición y la jornada del grupo en borrador. */
  puedeEditar: boolean
  onAjustar: (partido: FilaPartidoFecha, bloque: Bloque) => void
  onAsignar: (pendiente: SinAsignarFecha, bloque: Bloque) => void
}

const COLUMNAS =
  'lg:grid-cols-[minmax(0,2.2fr)_minmax(0,1.1fr)_minmax(0,1.1fr)_minmax(0,1.2fr)_7.5rem]'

function partidoTexto({ equipoLocal, equipoVisitante }: Pick<FilaPartidoFecha, 'equipoLocal' | 'equipoVisitante'>) {
  return `${equipoLocal.nombre} vs. ${equipoVisitante.nombre}`
}

/** Etiqueta de columna visible solo en móvil, donde la fila se apila (RNF-007). */
function Rotulo({ children }: { children: string }) {
  return (
    <span className="text-label-sm uppercase tracking-wide text-text-muted lg:hidden">{children}</span>
  )
}

/**
 * Un bloque «Categoría – Grupo» de la vista por fecha (F-22, H-35): Local vs Visitante, Cancha,
 * Horario y Árbitro, con el formato acordado con el profesor. Debajo de los partidos van los
 * equipos que descansan (RF-037) y los emparejamientos sin asignar con su causa y «Asignar»
 * (DEC-180). En escritorio es una tabla de cinco columnas; en móvil cada fila se apila con rótulos.
 */
export function BloqueFecha({
  bloque,
  conflictosPorPartido,
  causasSinArbitro,
  puedeEditar,
  onAjustar,
  onAsignar,
}: Props) {
  const titulo = `${bloque.categoria.nombre} – ${bloque.grupo.nombre}`
  const idTitulo = `bloque-${bloque.grupo.id}`
  const editable = puedeEditar && bloque.estadoPublicacion === 'BORRADOR' && Boolean(bloque.jornadaId)
  const vacio =
    bloque.partidos.length === 0 && bloque.descansos.length === 0 && bloque.sinAsignar.length === 0

  return (
    <section
      aria-labelledby={idTitulo}
      className="overflow-hidden rounded-lg border border-border bg-surface"
    >
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-background px-4 py-3">
        <h3 id={idTitulo} className="min-w-0 break-words text-headline-sm text-text-main">
          {titulo}
        </h3>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-body-sm text-text-muted">
            {bloque.partidos.length} {bloque.partidos.length === 1 ? 'partido' : 'partidos'}
          </span>
          {bloque.estadoPublicacion ? (
            <Insignia variante={varianteEstadoJornada[bloque.estadoPublicacion]}>
              {etiquetaEstadoJornada[bloque.estadoPublicacion]}
            </Insignia>
          ) : (
            <Insignia variante="neutra">Sin jornada</Insignia>
          )}
        </div>
      </header>

      {vacio ? (
        <p className="px-4 py-5 text-body-md text-text-muted">
          Este grupo todavía no tiene emparejamientos en la fecha.
        </p>
      ) : (
        <>
          <div
            aria-hidden="true"
            className={`hidden gap-4 border-b border-border px-4 py-2 text-label-sm uppercase tracking-wide text-text-muted lg:grid ${COLUMNAS}`}
          >
            <span>Local vs. Visitante</span>
            <span>Cancha</span>
            <span>Horario</span>
            <span>Árbitro</span>
          </div>
          <ul className="divide-y divide-border">

            {bloque.partidos.map((partido) => {
              const conflictos = conflictosPorPartido.get(partido.id) ?? []

              return (
                <li
                  key={partido.id}
                  aria-label={partidoTexto(partido)}
                  className={conflictos.length > 0 ? 'bg-error/5' : undefined}
                >
                  <div className={`grid gap-3 px-4 py-3 lg:items-center lg:gap-4 ${COLUMNAS}`}>
                    <div className="min-w-0">
                      <p className="break-words text-body-md font-semibold text-text-main">
                        {partido.equipoLocal.nombre}{' '}
                        <span className="font-normal text-text-muted">vs.</span>{' '}
                        {partido.equipoVisitante.nombre}
                      </p>
                      {(partido.asignadoAutomaticamente || partido.ajustado || partido.motivoLocalia) && (
                        <div className="mt-1.5 flex flex-wrap gap-1.5">
                          {partido.asignadoAutomaticamente && (
                            <Insignia variante="informacion" icono={Sparkles}>
                              Automático
                            </Insignia>
                          )}
                          {partido.ajustado && (
                            <Insignia variante="neutra" icono={Pencil}>
                              Ajustado
                            </Insignia>
                          )}
                          {partido.motivoLocalia && (
                            <Insignia variante="advertencia" icono={TriangleAlert}>
                              Localía
                            </Insignia>
                          )}
                        </div>
                      )}
                    </div>

                    <p className="flex min-w-0 flex-col gap-0.5 text-body-md text-text-main">
                      <Rotulo>Cancha</Rotulo>
                      <span className="break-words">{partido.cancha.nombre}</span>
                    </p>

                    <p className="flex flex-col gap-0.5 text-body-md text-text-main tabular-nums">
                      <Rotulo>Horario</Rotulo>
                      <span>{horarioConDia(partido)}</span>
                    </p>

                    <p className="flex min-w-0 flex-col gap-0.5 text-body-md">
                      <Rotulo>Árbitro</Rotulo>
                      {partido.arbitro ? (
                        <span className="inline-flex min-w-0 items-center gap-1.5 text-text-main">
                          <UserRound aria-hidden="true" className="size-4 shrink-0 text-text-muted" />
                          <span className="break-words">{partido.arbitro.nombre}</span>
                        </span>
                      ) : (
                        <>
                          <span className="inline-flex items-center gap-1.5 text-warning-strong">
                            <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
                            Sin árbitro
                          </span>
                          {causasSinArbitro?.get(partido.id) && (
                            <span className="break-words text-body-sm text-text-muted">
                              {causasSinArbitro.get(partido.id)}
                            </span>
                          )}
                        </>
                      )}
                    </p>

                    <div className="lg:justify-self-end">
                      {editable && (
                        <Button
                          variante="secundario"
                          icono={Pencil}
                          onClick={() => onAjustar(partido, bloque)}
                          className="w-full lg:w-auto"
                          aria-label={`Ajustar ${partidoTexto(partido)}`}
                        >
                          Ajustar
                        </Button>
                      )}
                    </div>
                  </div>

                  {conflictos.length > 0 && (
                    <ul
                      aria-label={`Conflictos de ${partidoTexto(partido)}`}
                      className="flex flex-col gap-1.5 px-4 pb-3"
                    >
                      {conflictos.map((conflicto, indice) => (
                        <li
                          key={`${conflicto.codigo}-${indice}`}
                          className="flex items-start gap-2 text-body-md text-error-strong"
                        >
                          <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                          <span className="min-w-0 break-words">
                            <span className="font-semibold">{etiquetaConflicto(conflicto.codigo)}:</span>{' '}
                            {conflicto.mensaje}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              )
            })}

            {bloque.descansos.map((descanso) => (
              <li
                key={descanso.emparejamientoId}
                className="flex items-center gap-2 px-4 py-3 text-body-md text-text-muted"
              >
                <Coffee aria-hidden="true" className="size-4 shrink-0" />
                <span className="min-w-0 break-words">
                  Descansa: <span className="font-medium text-text-main">{descanso.equipo.nombre}</span>
                </span>
              </li>
            ))}

            {bloque.sinAsignar.map((pendiente) => (
              <li
                key={pendiente.emparejamientoId}
                aria-label={`Sin asignar: ${partidoTexto(pendiente)}`}
                className="flex flex-col gap-3 bg-warning/6 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-body-md">
                    <span className="text-label-sm uppercase tracking-wide text-warning-strong">
                      Sin asignar
                    </span>
                    <span className="break-words font-semibold text-text-main">
                      {pendiente.equipoLocal.nombre}{' '}
                      <span className="font-normal text-text-muted">vs.</span>{' '}
                      {pendiente.equipoVisitante.nombre}
                    </span>
                  </p>
                  <p className="mt-0.5 break-words text-body-sm text-text-muted">
                    {pendiente.causa ?? 'Falta asignar cancha y horario.'}
                  </p>
                </div>
                {editable && (
                  <Button
                    variante="secundario"
                    icono={CalendarPlus}
                    onClick={() => onAsignar(pendiente, bloque)}
                    className="w-full shrink-0 sm:w-auto"
                    aria-label={`Asignar ${partidoTexto(pendiente)}`}
                  >
                    Asignar
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}
