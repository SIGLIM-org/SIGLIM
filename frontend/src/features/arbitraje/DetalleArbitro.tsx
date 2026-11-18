import { useEffect, useId, useRef, useState } from 'react'
import { Building2, CalendarClock, CircleCheck, CircleSlash, Info, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { useDisponibilidadArbitro } from '@/features/arbitraje/api'
import { etiquetaEstadoArbitro, inicialesArbitro } from '@/features/arbitraje/etiquetas'
import {
  agruparPorDia,
  describirPeriodo,
  formatearDiaConAnio,
} from '@/features/arbitraje/fechasDisponibilidad'
import { FormularioDisponibilidad } from '@/features/arbitraje/FormularioDisponibilidad'
import { ModalRetirarDisponibilidad } from '@/features/arbitraje/ModalRetirarDisponibilidad'
import type { Arbitro, DisponibilidadArbitral } from '@/features/arbitraje/tipos'

type Props = {
  arbitro: Arbitro
  onCerrar: () => void
}

/**
 * Detalle del árbitro en F-20 (UI-007) con su sección «Disponibilidad» (B-09.2, RF-022). Solo se
 * abre con `arbitraje.disponibilidad:EDITAR`; el backend vuelve a decidir el ámbito de la persona.
 */
export function DetalleArbitro({ arbitro, onCerrar }: Props) {
  const id = useId()
  const tituloRef = useRef<HTMLHeadingElement>(null)
  const estado = etiquetaEstadoArbitro[arbitro.estado]

  useEffect(() => {
    tituloRef.current?.focus()
  }, [])

  return (
    <section
      aria-labelledby={`${id}-titulo`}
      className="flex min-w-0 flex-col gap-4 rounded-lg border border-border bg-surface p-4"
    >
      <header className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span
            aria-hidden="true"
            className="grid size-10 shrink-0 place-items-center rounded-md border border-border bg-background text-label-md font-semibold text-text-main"
          >
            {inicialesArbitro(arbitro.persona.nombreCompleto)}
          </span>
          <div className="flex min-w-0 flex-col gap-1">
            <h2
              id={`${id}-titulo`}
              ref={tituloRef}
              tabIndex={-1}
              className="break-words text-headline-sm text-text-main outline-none"
            >
              {arbitro.persona.nombreCompleto}
            </h2>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <span className="flex items-center gap-1.5 text-body-sm text-text-muted">
                <Building2 aria-hidden="true" className="size-4 shrink-0" />
                {arbitro.club.nombre}
              </span>
              <Insignia variante={estado.variante} icono={estado.icono}>
                {estado.texto}
              </Insignia>
            </div>
          </div>
        </div>
        <Button
          variante="secundario"
          icono={X}
          aria-label="Cerrar el detalle del árbitro"
          onClick={onCerrar}
          className="shrink-0 px-3"
        />
      </header>

      <SeccionDisponibilidad arbitro={arbitro} />
    </section>
  )
}

function SeccionDisponibilidad({ arbitro }: { arbitro: Arbitro }) {
  const id = useId()
  const consulta = useDisponibilidadArbitro(arbitro.id)
  const [retirando, setRetirando] = useState<DisponibilidadArbitral | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const nombre = arbitro.persona.nombreCompleto
  const puedeRegistrar = arbitro.estado === 'VALIDADO'
  const periodos = consulta.data ?? []
  const grupos = agruparPorDia(periodos)

  const anunciar = (verbo: string, disponibilidad: DisponibilidadArbitral) => {
    const { fecha, horario } = describirPeriodo(disponibilidad.periodo)
    setMensaje(`Se ${verbo} el periodo del ${fecha}, ${horario}.`)
  }

  return (
    <section aria-labelledby={`${id}-titulo`} className="flex min-w-0 flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2 border-t border-border pt-4">
        <h3
          id={`${id}-titulo`}
          className="flex items-center gap-2 text-label-md font-semibold text-text-main"
        >
          <CalendarClock aria-hidden="true" className="size-5 text-primary" />
          Disponibilidad
        </h3>
        {periodos.length > 0 && (
          <p className="text-body-sm text-text-muted">
            {periodos.length === 1 ? '1 periodo registrado' : `${periodos.length} periodos registrados`}
          </p>
        )}
      </div>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-md border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
        <div className="flex min-w-0 flex-col gap-3">
          {consulta.isPending && (
            <EstadoCarga etiqueta="Cargando disponibilidad…" filas={3} columnas={3} />
          )}

          {consulta.isError && (
            <ErrorConsulta error={consulta.error} onReintentar={() => void consulta.refetch()} />
          )}

          {consulta.data && periodos.length === 0 && (
            <EstadoVacio
              icono={CalendarClock}
              titulo="Sin disponibilidad registrada"
              descripcion={
                puedeRegistrar
                  ? 'Registra la fecha y el horario en que este árbitro puede ser designado.'
                  : 'Este árbitro no tiene periodos registrados.'
              }
            />
          )}

          {grupos.map(({ dia, periodos: delDia }) => (
            <div key={dia} className="flex flex-col gap-1">
              <h4 className="text-label-md text-text-main">{formatearDiaConAnio(dia)}</h4>
              <ul
                aria-label={`Periodos del ${formatearDiaConAnio(dia)}`}
                className="divide-y divide-border rounded-md border border-border"
              >
                {delDia.map((disponibilidad) => {
                  const { horario } = describirPeriodo(disponibilidad.periodo)

                  return (
                    <li
                      key={disponibilidad.id}
                      className="flex items-center justify-between gap-3 px-3 py-2"
                    >
                      <div className="flex min-w-0 flex-col items-start gap-1 sm:flex-row sm:items-center sm:gap-3">
                        <span className="font-mono text-mono-data tabular-nums text-text-main">
                          {horario}
                        </span>
                        {disponibilidad.disponible ? (
                          <Insignia variante="exito" icono={CircleCheck}>
                            Disponible
                          </Insignia>
                        ) : (
                          <Insignia variante="neutra" icono={CircleSlash}>
                            No disponible
                          </Insignia>
                        )}
                      </div>
                      <Button
                        variante="secundario"
                        icono={Trash2}
                        aria-label={`Retirar el periodo del ${formatearDiaConAnio(dia)}, ${horario}`}
                        className="shrink-0"
                        onClick={() => {
                          setMensaje(null)
                          setRetirando(disponibilidad)
                        }}
                      >
                        Retirar
                      </Button>
                    </li>
                  )
                })}
              </ul>
            </div>
          ))}
        </div>

        {puedeRegistrar ? (
          <FormularioDisponibilidad
            arbitroId={arbitro.id}
            onRegistrada={(registrada) => anunciar('registró', registrada)}
          />
        ) : (
          <p className="flex items-start gap-2 rounded-md border border-border bg-background p-3 text-body-sm text-text-muted">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            Solo se registra disponibilidad para árbitros validados. Los periodos que ya tenga se
            pueden consultar y retirar.
          </p>
        )}
      </div>

      {retirando && (
        <ModalRetirarDisponibilidad
          key={retirando.id}
          arbitroId={arbitro.id}
          nombreArbitro={nombre}
          disponibilidad={retirando}
          onCerrar={() => setRetirando(null)}
          onRetirada={(retirada) => {
            setRetirando(null)
            anunciar('retiró', retirada)
          }}
        />
      )}
    </section>
  )
}
