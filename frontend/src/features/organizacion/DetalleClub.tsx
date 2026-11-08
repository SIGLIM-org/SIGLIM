import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { History, Lock, PencilLine, Users, type LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { useClub } from '@/features/organizacion/api'
import { etiquetaEstadoClub, formatearDiaDeInstante } from '@/features/organizacion/etiquetas'
import { ModalCambiarNombreClub } from '@/features/organizacion/ModalCambiarNombreClub'
import { usePermisosClubes } from '@/features/organizacion/permisosClubes'
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

const RUTA_CLUBES = '/app/organizacion/clubes'

/** Encabezado del club y sus pestañas (F-15, RF-008, RF-105, RN-041, DEC-15). */
export function DetalleClub({ clubId, mostrarVolver }: Props) {
  const consulta = useClub(clubId)
  const permisos = usePermisosClubes()
  const navegar = useNavigate()
  const [cambiandoNombre, setCambiandoNombre] = useState(false)
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
            <Link to={RUTA_CLUBES} className={clasesBoton('secundario')}>
              Volver a clubes
            </Link>
          )
        }
      />
    )
  }

  const club = consulta.data
  const estado = etiquetaEstadoClub[club.estado]
  // Solo un club activo cambia de nombre; uno cerrado ya es historial (RN-041).
  const puedeCambiarNombre = permisos.cambiarNombreClub && club.estado === 'ACTIVO'

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <section className="flex min-w-0 flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:p-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex min-w-0 flex-wrap items-center gap-3">
            <h2 className="min-w-0 break-words text-headline-lg text-text-main">{club.nombre}</h2>
            <Insignia variante={estado.variante} icono={estado.icono}>
              {estado.texto}
            </Insignia>
          </div>
          {/* Única acción del encabezado; H-22 puede moverla a un menú junto con «Editar». */}
          {puedeCambiarNombre && (
            <Button
              variante="secundario"
              icono={PencilLine}
              onClick={() => setCambiandoNombre(true)}
              className="w-full shrink-0 sm:w-auto"
            >
              Cambiar nombre
            </Button>
          )}
        </div>
        <div className="flex flex-col gap-1">
          <p className="text-body-md text-text-muted">
            Registrado el {formatearDiaDeInstante(club.creadoEn)}
          </p>
          {/* DEC-15: el club nuevo enlaza al cerrado, que conserva el historial. */}
          {club.clubAnterior && (
            <p className="flex items-start gap-2 text-body-md text-text-muted">
              <History aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              <span className="min-w-0 break-words">
                Proviene de{' '}
                <Link
                  to={`${RUTA_CLUBES}/${club.clubAnterior.id}`}
                  className="rounded-sm text-label-md text-primary underline underline-offset-2 hover:text-primary-hover"
                >
                  «{club.clubAnterior.nombre}»
                </Link>
              </span>
            </p>
          )}
        </div>
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

      {cambiandoNombre && (
        <ModalCambiarNombreClub
          club={club}
          onCerrar={() => setCambiandoNombre(false)}
          onCambiado={(nuevo) => {
            setCambiandoNombre(false)
            void navegar(`${RUTA_CLUBES}/${nuevo.id}`)
          }}
        />
      )}
    </div>
  )
}
