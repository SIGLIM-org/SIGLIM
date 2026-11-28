import { useId, useState } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  CheckCircle2,
  CircleCheck,
  CornerUpLeft,
  Info,
  ShieldCheck,
  TriangleAlert,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { BloqueFecha } from '@/features/programacion/BloqueFecha'
import { ModalDictamenFecha } from '@/features/programacion/ModalDictamenFecha'
import { useFecha } from '@/features/programacion/api'
import {
  etiquetaEstadoJornada,
  varianteEstadoJornada,
} from '@/features/programacion/etiquetas'
import { diasDeFecha } from '@/features/programacion/fechas'
import { usePermisosProgramacion } from '@/features/programacion/permisos'
import type { DecisionJornada, FechaProgramacion } from '@/features/programacion/tipos'
import { usePublicacionesPendientes } from '@/features/publicacion/api'
import { formatearFechaHora } from '@/features/publicacion/fechas'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'

type Props = {
  fechaId: string
}

/*
 * [PROPUESTA] El detalle de la fecha solo trae el id de quien la envió; su nombre sale de la bandeja
 * de pendientes (primera página de 100), la misma consulta que la bandeja, ya en caché al llegar desde
 * ahí. Igual que la revisión de una jornada (H-13.2).
 */
const PENDIENTES_POR_PAGINA = 100

/** La revisión es de solo lectura: los bloques no ofrecen ajustar ni asignar. */
const sinAccion = () => undefined

/** Conteos de la fecha para el resumen de F-23. */
function conteos(fecha: FechaProgramacion) {
  const bloques = fecha.bloques ?? []
  const partidos = bloques.flatMap(({ partidos: delBloque }) => delBloque)

  return {
    grupos: bloques.length,
    canchas: new Set(partidos.map(({ cancha }) => cancha.id)).size,
    sinArbitro: partidos.filter(({ arbitro }) => !arbitro).length,
  }
}

/**
 * Revisión conjunta de la fecha por la Presidencia (F-23, H-35.3, RF-121, DEC-175, UI-009): la fecha
 * completa en solo lectura, con los mismos bloques «Categoría – Grupo» de F-22, y los dictámenes
 * «Aprobar y publicar» o «Devolver con observaciones», que cambian todas sus jornadas juntas.
 *
 * Quien programa no aprueba (par incompatible EDITAR/APROBAR del Apéndice E): si la sesión también
 * puede editar la programación o fue quien envió la fecha, los botones se deshabilitan con la causa.
 * El backend es la barrera real (`403 RF-121`).
 */
export function RevisionFecha({ fechaId }: Props) {
  const permisos = usePermisosProgramacion()
  const { yo } = useSesionActual()
  const id = useId()
  const detalle = useFecha(fechaId)
  const pendientes = usePublicacionesPendientes(0, PENDIENTES_POR_PAGINA, {
    habilitada: permisos.aprobar,
  })
  const [decision, setDecision] = useState<DecisionJornada | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)

  const fecha = detalle.data
  const pendiente = pendientes.data?.contenido.find(
    ({ tipo, id: publicacion }) => tipo === 'FECHA' && publicacion === fechaId,
  )
  const enviadaPor = (() => {
    if (!fecha?.enviadaPor) {
      return undefined
    }
    if (fecha.enviadaPor === yo.perfil.id) {
      return `${yo.perfil.nombreMostrado} (tú)`
    }
    return pendiente?.enviadoPor.nombre
  })()

  const bloqueoSeparacion = (() => {
    if (!fecha) {
      return null
    }
    if (permisos.editar) {
      return 'Tu cuenta también puede editar la programación. Quien programa no puede aprobar ni devolver una fecha: el dictamen corresponde a otra persona.'
    }
    if (fecha.enviadaPor === yo.perfil.id) {
      return 'Tú enviaste esta fecha a aprobación. El dictamen corresponde a otra persona.'
    }
    return null
  })()
  const enRevision = fecha?.estadoPublicacion === 'REVISION'
  const puedeDictaminar = permisos.aprobar && enRevision && !bloqueoSeparacion

  const alResolver = (resuelta: FechaProgramacion) => {
    setDecision(null)
    setMensaje(
      resuelta.estadoPublicacion === 'PUBLICADO'
        ? `Fecha ${resuelta.numero} aprobada y publicada. Los partidos de sus ${resuelta.jornadas} jornadas ya aparecen en el portal.`
        : `Fecha ${resuelta.numero} devuelta a borrador. El Programador verá tus observaciones.`,
    )
  }

  const resumen = fecha ? conteos(fecha) : null

  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/app/programacion/jornadas"
        className="inline-flex w-fit items-center gap-1.5 rounded-sm text-label-md text-primary underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Programación por revisar
      </Link>

      {detalle.isPending && <EstadoCarga etiqueta="Cargando la fecha…" filas={5} columnas={4} />}

      {detalle.isError && (
        <ErrorConsulta error={detalle.error} onReintentar={() => void detalle.refetch()} />
      )}

      {fecha && resumen && (
        <>
          <header className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
                  Revisión de la fecha {fecha.numero}
                </h1>
                <Insignia variante={varianteEstadoJornada[fecha.estadoPublicacion]}>
                  {etiquetaEstadoJornada[fecha.estadoPublicacion]}
                </Insignia>
              </div>
              <p className="mt-1 text-body-md text-text-muted">
                {[
                  diasDeFecha(fecha.dias),
                  enviadaPor && `Enviada por ${enviadaPor}`,
                  fecha.enviadaEn && formatearFechaHora(fecha.enviadaEn),
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            </div>

            {permisos.aprobar && enRevision && (
              <div className="flex shrink-0 flex-col-reverse gap-2 sm:flex-row">
                <Button
                  variante="secundario"
                  icono={CornerUpLeft}
                  disabled={!puedeDictaminar}
                  onClick={() => {
                    setMensaje(null)
                    setDecision('BORRADOR')
                  }}
                >
                  Devolver con observaciones
                </Button>
                <Button
                  icono={CheckCircle2}
                  disabled={!puedeDictaminar}
                  onClick={() => {
                    setMensaje(null)
                    setDecision('PUBLICADO')
                  }}
                >
                  Aprobar y publicar
                </Button>
              </div>
            )}
          </header>

          {mensaje && (
            <p
              role="status"
              className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
            >
              <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              {mensaje}
            </p>
          )}

          {bloqueoSeparacion && enRevision && permisos.aprobar ? (
            <p
              role="note"
              className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 p-3 text-body-md text-warning-strong"
            >
              <ShieldCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              {bloqueoSeparacion}
            </p>
          ) : (
            <p className="flex items-start gap-2 text-body-md text-text-muted">
              <ShieldCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-primary" />
              La fecha se aprueba o se devuelve completa: todas sus jornadas cambian juntas. Quien la
              programó no la dictamina, y lo que apruebes es exactamente lo que se publicará.
            </p>
          )}

          {!enRevision && !mensaje && (
            <p
              role="status"
              className="flex items-start gap-2 rounded-lg border border-info/30 bg-info/10 p-3 text-body-md text-info-strong"
            >
              <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              {fecha.estadoPublicacion === 'PUBLICADO'
                ? 'Esta fecha ya está publicada. No hay dictamen pendiente.'
                : 'Esta fecha está en borrador con el Programador. Podrás dictaminarla cuando la envíe a aprobación.'}
            </p>
          )}

          {fecha.observaciones && (
            <section
              aria-labelledby={`${id}-observaciones`}
              className="rounded-lg border border-border bg-surface p-4"
            >
              <h2 id={`${id}-observaciones`} className="text-headline-sm text-text-main">
                {fecha.estadoPublicacion === 'PUBLICADO'
                  ? 'Nota de aprobación'
                  : 'Observaciones de la última devolución'}
              </h2>
              <p className="mt-1 whitespace-pre-line break-words text-body-md text-text-main">
                {fecha.observaciones}
              </p>
            </section>
          )}

          <section aria-labelledby={`${id}-resumen`}>
            <h2 id={`${id}-resumen`} className="sr-only">
              Resumen de la fecha
            </h2>
            <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-border bg-border lg:grid-cols-4">
              <div className="bg-surface p-4">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">Partidos</dt>
                <dd className="mt-1 font-mono text-headline-lg tabular-nums text-text-main">
                  {fecha.partidos}
                </dd>
                <dd className="text-body-sm text-text-muted">
                  En {fecha.jornadas} {fecha.jornadas === 1 ? 'jornada' : 'jornadas'}
                </dd>
              </div>
              <div className="bg-surface p-4">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">Canchas</dt>
                <dd className="mt-1 font-mono text-headline-lg tabular-nums text-text-main">
                  {resumen.canchas}
                </dd>
              </div>
              <div className="bg-surface p-4">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">
                  Sin asignar
                </dt>
                <dd className="mt-1 font-mono text-headline-lg tabular-nums text-text-main">
                  {fecha.sinAsignar}
                </dd>
                <dd className="text-body-sm text-text-muted">No se publican</dd>
              </div>
              <div className="bg-surface p-4">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">
                  Sin árbitro
                </dt>
                <dd className="mt-1 font-mono text-headline-lg tabular-nums text-text-main">
                  {resumen.sinArbitro}
                </dd>
                <dd className="text-body-sm text-text-muted">La Comisión los designa en F-24</dd>
              </div>
            </dl>
          </section>

          {enRevision && (fecha.sinAsignar > 0 || resumen.sinArbitro > 0) && (
            <p
              role="note"
              className="flex items-start gap-2 rounded-lg border border-warning/30 bg-warning/8 p-3 text-body-md text-warning-strong"
            >
              <TriangleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              <span>
                Advertencias que no impiden aprobar:
                {fecha.sinAsignar > 0 &&
                  ` ${fecha.sinAsignar} ${fecha.sinAsignar === 1 ? 'partido quedó' : 'partidos quedaron'} sin asignar y no se publicarán.`}
                {resumen.sinArbitro > 0 &&
                  ` ${resumen.sinArbitro} ${resumen.sinArbitro === 1 ? 'partido no tiene' : 'partidos no tienen'} árbitro central.`}{' '}
                Si deben corregirse antes de publicar, devuelve la fecha con observaciones.
              </span>
            </p>
          )}

          <section aria-labelledby={`${id}-bloques`} className="flex flex-col gap-4">
            <h2 id={`${id}-bloques`} className="text-headline-md text-text-main">
              Rol de juegos por categoría y grupo
            </h2>
            {resumen.grupos === 0 ? (
              <p className="rounded-lg border border-border bg-surface p-4 text-body-md text-text-muted">
                La fecha no tiene grupos con partidos.
              </p>
            ) : (
              (fecha.bloques ?? []).map((bloque) => (
                <BloqueFecha
                  key={bloque.grupo.id}
                  bloque={bloque}
                  conflictosPorPartido={new Map()}
                  puedeEditar={false}
                  onAjustar={sinAccion}
                  onAsignar={sinAccion}
                />
              ))
            )}
          </section>
        </>
      )}

      {fecha && decision && (
        <ModalDictamenFecha
          fecha={fecha}
          decision={decision}
          onCerrar={() => setDecision(null)}
          onResuelta={alResolver}
        />
      )}
    </div>
  )
}
