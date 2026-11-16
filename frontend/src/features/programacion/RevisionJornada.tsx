import { useId, useState } from 'react'
import { Link } from 'react-router'
import {
  ArrowLeft,
  CheckCircle2,
  CircleCheck,
  CornerUpLeft,
  Info,
  ShieldCheck,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { CicloPublicacionJornada } from '@/features/programacion/CicloPublicacionJornada'
import { ModalDictamenJornada } from '@/features/programacion/ModalDictamenJornada'
import { TablaPartidosRevision } from '@/features/programacion/TablaPartidosRevision'
import { useJornada } from '@/features/programacion/api'
import {
  etiquetaEstadoJornada,
  varianteEstadoJornada,
} from '@/features/programacion/etiquetas'
import { rangoDePartidos } from '@/features/programacion/fechas'
import { usePermisosProgramacion } from '@/features/programacion/permisos'
import type { DecisionJornada, Jornada } from '@/features/programacion/tipos'
import { usePublicacionesPendientes } from '@/features/publicacion/api'
import { formatearFechaHora } from '@/features/publicacion/fechas'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'

type Props = {
  jornadaId: string
}

/*
 * [PROPUESTA] La bandeja trae el nombre de quien envió y el contexto (temporada · categoría ·
 * grupo); el detalle de la jornada solo trae ids. Se busca la jornada en la primera página de 100
 * pendientes: la misma consulta que la bandeja, ya en caché al llegar desde ahí.
 */
const PENDIENTES_POR_PAGINA = 100

/**
 * Revisión de la jornada por la Presidencia (F-23, H-13.2, RF-121, RF-090, UI-009): resumen, rol
 * de juegos de solo lectura y los dictámenes «Aprobar y publicar» o «Devolver con observaciones».
 *
 * Quien programa no aprueba (par incompatible EDITAR/APROBAR del Apéndice E): si la sesión también
 * puede editar jornadas o fue quien envió esta, los botones se deshabilitan con la causa. El backend
 * es la barrera real (`403 RF-121`).
 */
export function RevisionJornada({ jornadaId }: Props) {
  const permisos = usePermisosProgramacion()
  const { yo } = useSesionActual()
  const id = useId()
  const detalle = useJornada(jornadaId)
  const pendientes = usePublicacionesPendientes(0, PENDIENTES_POR_PAGINA, {
    habilitada: permisos.aprobar,
  })
  const [decision, setDecision] = useState<DecisionJornada | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)

  const jornada = detalle.data
  const pendiente = pendientes.data?.contenido.find(
    ({ tipo, id: publicacion }) => tipo === 'JORNADA' && publicacion === jornadaId,
  )
  const partidos = jornada?.partidos ?? []
  const vigentes = partidos.filter(({ estado }) => estado !== 'CANCELADO')
  const canchas = new Set(vigentes.map(({ cancha }) => cancha.id)).size
  const enviadaPor = (() => {
    if (!jornada?.enviadaPor) {
      return undefined
    }
    if (jornada.enviadaPor === yo.perfil.id) {
      return `${yo.perfil.nombreMostrado} (tú)`
    }
    return pendiente?.enviadoPor.nombre
  })()

  const bloqueoSeparacion = (() => {
    if (!jornada) {
      return null
    }
    if (permisos.editar) {
      return 'Tu cuenta también puede editar jornadas. Quien programa no puede aprobar ni devolver una jornada: el dictamen corresponde a otra persona.'
    }
    if (jornada.enviadaPor === yo.perfil.id) {
      return 'Tú enviaste esta jornada a aprobación. El dictamen corresponde a otra persona.'
    }
    return null
  })()
  const puedeDictaminar =
    permisos.aprobar && jornada?.estadoPublicacion === 'REVISION' && !bloqueoSeparacion

  const alResolver = (resuelta: Jornada) => {
    setDecision(null)
    setMensaje(
      resuelta.estadoPublicacion === 'PUBLICADO'
        ? `Jornada ${resuelta.numero} aprobada y publicada. Sus partidos ya aparecen en el portal.`
        : `Jornada ${resuelta.numero} devuelta a borrador. El Programador verá tus observaciones.`,
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <Link
        to="/app/programacion/jornadas"
        className="inline-flex w-fit items-center gap-1.5 rounded-sm text-label-md text-primary underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Jornadas por revisar
      </Link>

      {detalle.isPending && <EstadoCarga etiqueta="Cargando la jornada…" filas={5} columnas={4} />}

      {detalle.isError && (
        <ErrorConsulta error={detalle.error} onReintentar={() => void detalle.refetch()} />
      )}

      {jornada && (
        <>
          <header className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
                  Revisión de la jornada {jornada.numero}
                </h1>
                <Insignia variante={varianteEstadoJornada[jornada.estadoPublicacion]}>
                  {etiquetaEstadoJornada[jornada.estadoPublicacion]}
                </Insignia>
              </div>
              <p className="mt-1 text-body-md text-text-muted">
                {[
                  pendiente?.contexto,
                  enviadaPor && `Enviada por ${enviadaPor}`,
                  jornada.enviadaEn && formatearFechaHora(jornada.enviadaEn),
                ]
                  .filter(Boolean)
                  .join(' · ') || 'Programación de la jornada'}
              </p>
            </div>

            {permisos.aprobar && jornada.estadoPublicacion === 'REVISION' && (
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

          {bloqueoSeparacion && jornada.estadoPublicacion === 'REVISION' && permisos.aprobar ? (
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
              Separación de funciones: quien programa la jornada no la dictamina. Lo que apruebes es
              exactamente lo que se publicará en el portal.
            </p>
          )}

          {jornada.estadoPublicacion !== 'REVISION' && !mensaje && (
            <p
              role="status"
              className="flex items-start gap-2 rounded-lg border border-info/30 bg-info/10 p-3 text-body-md text-info-strong"
            >
              <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
              {jornada.estadoPublicacion === 'PUBLICADO'
                ? 'Esta jornada ya está publicada. No hay dictamen pendiente.'
                : 'Esta jornada está en borrador con el Programador. Podrás dictaminarla cuando la envíe a aprobación.'}
            </p>
          )}

          {jornada.observaciones && (
            <section
              aria-labelledby={`${id}-observaciones`}
              className="rounded-lg border border-border bg-surface p-4"
            >
              <h2 id={`${id}-observaciones`} className="text-headline-sm text-text-main">
                {jornada.estadoPublicacion === 'PUBLICADO'
                  ? 'Nota de aprobación'
                  : 'Observaciones de la última devolución'}
              </h2>
              <p className="mt-1 whitespace-pre-line break-words text-body-md text-text-main">
                {jornada.observaciones}
              </p>
            </section>
          )}

          <CicloPublicacionJornada jornada={jornada} enviadaPor={enviadaPor} />

          <section aria-labelledby={`${id}-resumen`}>
            <h2 id={`${id}-resumen`} className="sr-only">
              Resumen de la jornada
            </h2>
            <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-border bg-border lg:grid-cols-4">
              <div className="bg-surface p-4">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">Partidos</dt>
                <dd className="mt-1 font-mono text-headline-lg tabular-nums text-text-main">
                  {vigentes.length}
                </dd>
                {partidos.length > vigentes.length && (
                  <dd className="text-body-sm text-text-muted">
                    {partidos.length - vigentes.length} cancelado
                    {partidos.length - vigentes.length === 1 ? '' : 's'}
                  </dd>
                )}
              </div>
              <div className="bg-surface p-4">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">Canchas</dt>
                <dd className="mt-1 font-mono text-headline-lg tabular-nums text-text-main">
                  {canchas}
                </dd>
              </div>
              <div className="bg-surface p-4">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">Fechas</dt>
                <dd className="mt-1 text-headline-sm text-text-main">
                  {partidos.length > 0 ? rangoDePartidos(partidos) : 'Sin partidos'}
                </dd>
              </div>
              <div className="bg-surface p-4">
                <dt className="text-label-sm uppercase tracking-wide text-text-muted">
                  Conflictos
                </dt>
                <dd className="mt-1 text-headline-sm text-text-main">Verificados al enviar</dd>
                <dd className="text-body-sm text-text-muted">
                  El envío se rechaza si hay equipos o canchas ocupados.
                </dd>
              </div>
            </dl>
          </section>

          <section aria-labelledby={`${id}-partidos`} className="flex flex-col gap-3">
            <h2 id={`${id}-partidos`} className="text-headline-md text-text-main">
              Rol de juegos
            </h2>
            {partidos.length === 0 ? (
              <EstadoVacio
                titulo="La jornada no tiene partidos"
                descripcion="No hay encuentros programados en esta jornada."
              />
            ) : (
              <TablaPartidosRevision partidos={partidos} etiquetadaPor={`${id}-partidos`} />
            )}
          </section>
        </>
      )}

      {jornada && decision && (
        <ModalDictamenJornada
          jornada={jornada}
          decision={decision}
          partidos={vigentes.length}
          onCerrar={() => setDecision(null)}
          onResuelta={alResolver}
        />
      )}
    </div>
  )
}
