import { Link } from 'react-router'
import { Lock, Users, type LucideIcon } from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { useClub } from '@/features/organizacion/api'
import { etiquetaEstadoClub, formatearDiaDeInstante } from '@/features/organizacion/etiquetas'
import { PestanaEquipos } from '@/features/organizacion/PestanaEquipos'
import { formatearFecha } from '@/features/temporadas/fechas'

type Props = {
  clubId: string
  /** Ofrece volver a la lista ante un 404; se omite cuando la sesión solo alcanza su club. */
  mostrarVolver: boolean
}

/** Pestañas del detalle. H-22 (Datos, Directiva, Afiliaciones…) agregará las suyas aquí. */
type PestanaClub = 'equipos'

const PESTANAS: readonly { valor: PestanaClub; texto: string; icono: LucideIcon }[] = [
  { valor: 'equipos', texto: 'Equipos', icono: Users },
]

/** Encabezado del club y sus pestañas (F-15, RF-008, RN-041). */
export function DetalleClub({ clubId, mostrarVolver }: Props) {
  const consulta = useClub(clubId)
  // Con una sola pestaña no hay estado que cambiar; H-22 la volverá estado o parámetro de URL.
  const pestana: PestanaClub = 'equipos'

  if (consulta.isPending) {
    return <EstadoCarga etiqueta="Cargando el club…" filas={3} columnas={2} />
  }

  if (consulta.isError) {
    return (
      <ErrorConsulta
        error={consulta.error}
        onReintentar={() => void consulta.refetch()}
        accion={
          mostrarVolver && (
            <Link to="/app/organizacion/clubes" className={clasesBoton('secundario')}>
              Volver a clubes
            </Link>
          )
        }
      />
    )
  }

  const club = consulta.data
  const estado = etiquetaEstadoClub[club.estado]

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <section className="flex min-w-0 flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:p-6">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="min-w-0 break-words text-headline-lg text-text-main">{club.nombre}</h2>
          <Insignia variante={estado.variante} icono={estado.icono}>
            {estado.texto}
          </Insignia>
        </div>
        <p className="text-body-md text-text-muted">
          Registrado el {formatearDiaDeInstante(club.creadoEn)}
        </p>
        {club.estado === 'CERRADO' && (
          <dl className="grid gap-x-6 gap-y-3 border-t border-border pt-3 text-body-md sm:grid-cols-[auto_minmax(0,1fr)]">
            <div>
              <dt className="text-label-md text-text-muted">Cerrado el</dt>
              <dd className="text-text-main">{club.cerradoEn ? formatearFecha(club.cerradoEn) : 'Sin fecha'}</dd>
            </div>
            <div className="min-w-0">
              <dt className="text-label-md text-text-muted">Motivo del cierre</dt>
              <dd className="whitespace-pre-line break-words text-text-main">
                {club.motivoCierre ?? 'Sin motivo registrado'}
              </dd>
            </div>
          </dl>
        )}
        {club.estado === 'CERRADO' && (
          <p className="flex items-start gap-2 rounded-md bg-background p-3 text-body-md text-text-muted">
            <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            Un club cerrado no recibe equipos nuevos.
          </p>
        )}
      </section>

      <div role="tablist" aria-label={`Información de ${club.nombre}`} className="flex min-w-0 overflow-x-auto border-b-2 border-border">
        {PESTANAS.map(({ valor, texto, icono: Icono }) => {
          const activa = pestana === valor

          return (
            <button
              key={valor}
              type="button"
              role="tab"
              id={`pestana-club-${valor}`}
              aria-selected={activa}
              aria-controls={`panel-club-${valor}`}
              tabIndex={activa ? 0 : -1}
              className={[
                '-mb-0.5 flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-label-md transition-colors sm:px-4',
                activa ? 'border-primary text-primary' : 'border-transparent text-text-muted hover:text-text-main',
              ].join(' ')}
            >
              <Icono aria-hidden="true" className="size-4 shrink-0" />
              {texto}
            </button>
          )
        })}
      </div>

      <div role="tabpanel" id="panel-club-equipos" aria-labelledby="pestana-club-equipos" className="min-w-0">
        <PestanaEquipos club={club} />
      </div>
    </div>
  )
}
