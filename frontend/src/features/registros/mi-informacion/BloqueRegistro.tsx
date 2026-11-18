import { useId, type ReactNode } from 'react'
import { ChevronDown } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import {
  ElegibilidadPropia,
  TarjetaSuspension,
} from '@/features/registros/mi-informacion/ElegibilidadPropia'
import { ListaDocumentosPropios } from '@/features/registros/mi-informacion/ListaDocumentosPropios'
import { SeccionPartidos } from '@/features/registros/mi-informacion/SeccionPartidos'
import type { RegistroPropio } from '@/features/registros/mi-informacion/tipos'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import { formatearFecha } from '@/features/temporadas/fechas'

type Props = {
  registro: RegistroPropio
  /** Con varios registros, el bloque va en un `<details>` (DEC-152). */
  plegable: boolean
  /** Solo aplica a `plegable`: el primero va abierto. */
  abierto?: boolean
}

function Dato({ termino, children }: { termino: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-body-sm text-text-muted">{termino}</dt>
      <dd className="min-w-0 break-words text-body-md text-text-main">{children}</dd>
    </div>
  )
}

/** Temporada, equipo e insignia de estado: la cabecera del bloque o el resumen del `<details>`. */
function Identidad({ registro }: { registro: RegistroPropio }) {
  const estado = etiquetaEstadoRegistro[registro.estado]

  return (
    <div className="flex min-w-0 flex-1 flex-col gap-1">
      <p className="text-body-sm text-text-muted">
        {registro.temporada.nombre} · {etiquetaEstadoTemporada[registro.temporada.estado].texto}
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <p className="min-w-0 break-words text-headline-md text-text-main">
          {registro.equipo.nombre}
        </p>
        <Insignia variante={estado.variante} icono={estado.icono}>
          {estado.texto}
        </Insignia>
      </div>
    </div>
  )
}

function Contenido({ registro }: { registro: RegistroPropio }) {
  const idDocumentos = useId()

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-3">
        <ElegibilidadPropia elegibilidad={registro.elegibilidad} />
        <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
          <Dato termino="Club">{registro.club.nombre}</Dato>
          <Dato termino="Categoría">{registro.categoria.nombre}</Dato>
          <Dato termino="Alta">{formatearFecha(registro.altaEn)}</Dato>
          {registro.bajaEn && <Dato termino="Baja">{formatearFecha(registro.bajaEn)}</Dato>}
          {registro.motivoBaja && (
            <div className="col-span-2">
              <Dato termino="Motivo de la baja">{registro.motivoBaja}</Dato>
            </div>
          )}
          {registro.observaciones && (
            <div className="col-span-2">
              <Dato termino="Observaciones">{registro.observaciones}</Dato>
            </div>
          )}
        </dl>
      </div>

      <section aria-labelledby={idDocumentos} className="flex flex-col gap-2">
        <h2 id={idDocumentos} className="text-headline-sm text-text-main">
          Documentos
        </h2>
        <ListaDocumentosPropios documentos={registro.documentos} />
      </section>

      <SeccionPartidos
        titulo="Próximos partidos"
        partidos={registro.proximosPartidos}
        textoVacio="Sin próximos partidos publicados"
      />
      <SeccionPartidos
        titulo="Mis partidos"
        partidos={registro.partidosJugados}
        textoVacio="Aún no hay partidos jugados"
        conMarcador
      />
    </div>
  )
}

/**
 * Un registro del titular: temporada, club, equipo, categoría, estado, elegibilidad, alta y baja,
 * observaciones, documentos y partidos. La tarjeta de suspensión va arriba de los detalles
 * (DEC-151). Con un solo registro el bloque va abierto; con varios, cada uno en un
 * `<details>` con el primero abierto (DEC-152).
 *
 * Refs: RF-144, RF-017, RF-019, RF-020, DEC-151, DEC-152
 */
export function BloqueRegistro({ registro, plegable, abierto = false }: Props) {
  const etiqueta = `${registro.temporada.nombre}, ${registro.equipo.nombre}`

  if (!plegable) {
    return (
      <article
        aria-label={etiqueta}
        className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4"
      >
        <Identidad registro={registro} />
        <TarjetaSuspension elegibilidad={registro.elegibilidad} />
        <Contenido registro={registro} />
      </article>
    )
  }

  return (
    <details
      open={abierto}
      aria-label={etiqueta}
      className="group rounded-lg border border-border bg-surface"
    >
      <summary className="flex min-h-11 cursor-pointer list-none items-center gap-3 rounded-lg p-4 [&::-webkit-details-marker]:hidden">
        <Identidad registro={registro} />
        <ChevronDown
          aria-hidden="true"
          className="size-5 shrink-0 text-text-muted transition-transform group-open:rotate-180 motion-reduce:transition-none"
        />
      </summary>
      <div className="flex flex-col gap-4 border-t border-border p-4">
        <TarjetaSuspension elegibilidad={registro.elegibilidad} />
        <Contenido registro={registro} />
      </div>
    </details>
  )
}
