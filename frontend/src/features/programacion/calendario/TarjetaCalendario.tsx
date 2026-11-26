import { useId, useState } from 'react'
import { CalendarRange, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { ModalGenerarCalendario } from '@/features/programacion/calendario/ModalGenerarCalendario'
import { useCalendario } from '@/features/programacion/calendario/api'
import { resumenCalendario } from '@/features/programacion/calendario/reglas'
import type { CategoriaTemporada, Temporada } from '@/features/temporadas/tipos'

type Props = {
  temporada: Temporada
  categorias: CategoriaTemporada[]
}

function plural(cantidad: number, singular: string, varios: string): string {
  return `${cantidad} ${cantidad === 1 ? singular : varios}`
}

/**
 * Tarjeta «Calendario de la temporada» de F-22 (H-32.4, DEC-197): cuántos grupos tienen calendario
 * y el acceso al modal para generarlo o regenerarlo. La pantalla solo la monta con la temporada
 * `ACTIVA` y `programacion.jornada:EDITAR`.
 */
export function TarjetaCalendario({ temporada, categorias }: Props) {
  const id = useId()
  const calendario = useCalendario(temporada.id)
  const [abierto, setAbierto] = useState(false)

  // Un refresco fallido conserva los datos anteriores: el modal abierto no se desmonta.
  const datos = calendario.data

  if (!datos) {
    return calendario.isError ? (
      <ErrorConsulta error={calendario.error} onReintentar={() => void calendario.refetch()} />
    ) : (
      <EstadoCarga etiqueta="Cargando el calendario de la temporada…" filas={1} columnas={2} />
    )
  }

  const resumen = resumenCalendario(datos)
  const completo = resumen.grupos > 0 && resumen.generados === resumen.grupos
  const estado =
    resumen.generados === 0
      ? `Sin generar · ${plural(resumen.grupos, 'grupo', 'grupos')}`
      : [
          `${resumen.generados} de ${plural(resumen.grupos, 'grupo', 'grupos')} con calendario`,
          plural(resumen.totalFechas, 'fecha', 'fechas'),
          resumen.vueltas !== null ? plural(resumen.vueltas, 'vuelta', 'vueltas') : null,
        ]
          .filter(Boolean)
          .join(' · ')

  return (
    <section
      aria-labelledby={`${id}-titulo`}
      className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:flex-row sm:items-center sm:justify-between"
    >
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <h2 id={`${id}-titulo`} className="text-headline-sm text-text-main">
            Calendario de la temporada
          </h2>
          {completo && <Insignia variante="exito">Completo</Insignia>}
        </div>
        <p className="mt-1 text-body-md text-text-muted">{estado}</p>
      </div>

      {completo ? (
        <Button
          variante="secundario"
          icono={RefreshCw}
          onClick={() => setAbierto(true)}
          className="shrink-0"
        >
          Regenerar calendario
        </Button>
      ) : (
        <Button icono={CalendarRange} onClick={() => setAbierto(true)} className="shrink-0">
          Generar calendario
        </Button>
      )}

      {abierto && (
        <ModalGenerarCalendario
          temporada={temporada}
          categorias={categorias}
          calendario={datos}
          onCerrar={() => setAbierto(false)}
        />
      )}
    </section>
  )
}
