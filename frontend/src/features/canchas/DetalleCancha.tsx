import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { Ban, CalendarPlus, CheckCircle2, Pencil, Power, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { useCancha, useOcupacion } from '@/features/canchas/api'
import { AvisoPartidosAfectados } from '@/features/canchas/AvisoPartidosAfectados'
import { CalendarioSemanal } from '@/features/canchas/CalendarioSemanal'
import { etiquetaUbicacion, nombrePropietario } from '@/features/canchas/etiquetas'
import { rangoSemana } from '@/features/canchas/fechas'
import { FormularioCancha } from '@/features/canchas/FormularioCancha'
import { ModalEstadoCancha } from '@/features/canchas/ModalEstadoCancha'
import { ModalIntervalo } from '@/features/canchas/ModalIntervalo'
import { usePermisosHorarios } from '@/features/canchas/permisosHorarios'
import { SeccionHorariosClub } from '@/features/canchas/SeccionHorariosClub'
import type { PartidoEnCancha, TipoDisponibilidad } from '@/features/canchas/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'

type Props = {
  canchaId: string
  semana: string
  /** Ruta del catálogo, conservando la semana. */
  rutaCatalogo: string
  onCambiarSemana: (lunes: string) => void
}

type Modal = 'disponibilidad' | 'bloqueo' | 'editar' | 'estado' | null
type Restriccion = 'bloqueo' | 'mantenimiento'
type Aviso = { mensaje: string; afectados: PartidoEnCancha[]; restriccion?: Restriccion | null }

const EDITAR = 'canchas.cancha:EDITAR'

/** [PROPUESTA] E6: aviso de éxito por tipo registrado; el `Record` obliga a cubrir cada tipo. */
const avisoPorTipo: Record<TipoDisponibilidad, { exito: string; restriccion: Restriccion | null }> = {
  DISPONIBLE: { exito: 'Disponibilidad registrada.', restriccion: null },
  BLOQUEO: { exito: 'Bloqueo registrado.', restriccion: 'bloqueo' },
  MANTENIMIENTO: { exito: 'Mantenimiento registrado.', restriccion: 'mantenimiento' },
}

/** Datos de la cancha, sus acciones y su calendario (RF-027, RF-029, RF-030, RF-031, VAL-005). */
export function DetalleCancha({ canchaId, semana, rutaCatalogo, onCambiarSemana }: Props) {
  const consulta = useCancha(canchaId)
  const rango = useMemo(() => rangoSemana(semana), [semana])
  const ocupacion = useOcupacion(canchaId, rango)
  const [modal, setModal] = useState<Modal>(null)
  const [aviso, setAviso] = useState<Aviso | null>(null)
  // Sin cancha cargada se evalúa como EXTERNO: no se ve nada hasta saber el propietario.
  const permisosHorarios = usePermisosHorarios(consulta.data?.propietario ?? 'EXTERNO')

  if (consulta.isPending) {
    return <EstadoCarga etiqueta="Cargando la cancha…" filas={3} columnas={2} />
  }

  if (consulta.isError) {
    return (
      <ErrorConsulta
        error={consulta.error}
        onReintentar={() => void consulta.refetch()}
        accion={
          <Link to={rutaCatalogo} className={clasesBoton('secundario')}>
            Volver al catálogo
          </Link>
        }
      />
    )
  }

  const cancha = consulta.data

  return (
    <div className="flex min-w-0 flex-col gap-6 lg:contents">
      <section className="flex min-w-0 flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-6 lg:self-start">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="min-w-0 break-words text-headline-lg text-text-main">{cancha.nombre}</h2>
          {cancha.activa ? (
            <Insignia variante="exito">Activa</Insignia>
          ) : (
            <Insignia variante="neutra">Inactiva</Insignia>
          )}
        </div>

        <dl className="grid gap-x-6 gap-y-3 text-body-md sm:grid-cols-2">
          {/* [PROPUESTA] D8: la sede siempre se muestra, con «Sin sede» si no tiene (RF-028). */}
          <div>
            <dt className="text-label-md text-text-muted">Sede</dt>
            <dd className={cancha.sede ? 'break-words text-text-main' : 'text-text-muted'}>
              {cancha.sede?.nombre ?? 'Sin sede'}
            </dd>
          </div>
          <div>
            <dt className="text-label-md text-text-muted">Ubicación</dt>
            <dd className="text-text-main">{etiquetaUbicacion[cancha.ubicacion]}</dd>
          </div>
          <div>
            <dt className="text-label-md text-text-muted">Propietario</dt>
            <dd className="break-words text-text-main">{nombrePropietario(cancha)}</dd>
          </div>
          {cancha.direccion && (
            <div>
              <dt className="text-label-md text-text-muted">Dirección</dt>
              <dd className="break-words text-text-main">{cancha.direccion}</dd>
            </div>
          )}
          {cancha.caracteristicas && (
            <div>
              <dt className="text-label-md text-text-muted">Características</dt>
              <dd className="whitespace-pre-line break-words text-text-main">{cancha.caracteristicas}</dd>
            </div>
          )}
        </dl>

        {!cancha.activa && (
          <p className="flex items-start gap-2 rounded-md bg-warning/14 p-3 text-body-md text-warning-strong">
            <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            Cancha inactiva: no se puede usar en la programación
          </p>
        )}

        <ConPermiso permiso={EDITAR}>
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button variante="secundario" icono={CalendarPlus} onClick={() => setModal('disponibilidad')}>
              Agregar disponibilidad
            </Button>
            <Button variante="secundario" icono={Ban} onClick={() => setModal('bloqueo')}>
              Bloquear intervalo
            </Button>
            <Button variante="secundario" icono={Pencil} onClick={() => setModal('editar')}>
              Editar
            </Button>
            <Button
              variante={cancha.activa ? 'destructivo' : 'secundario'}
              icono={cancha.activa ? Power : CheckCircle2}
              onClick={() => setModal('estado')}
            >
              {cancha.activa ? 'Desactivar' : 'Activar'}
            </Button>
          </div>
        </ConPermiso>

        {aviso && (
          <div role="status" className="flex flex-col gap-3">
            <p className="flex items-center gap-2 text-label-md text-success-strong">
              <CheckCircle2 aria-hidden="true" className="size-4 shrink-0" />
              {aviso.mensaje}
            </p>
            {aviso.afectados.length > 0 && (
              <AvisoPartidosAfectados
                mensaje={`${aviso.afectados.length} partido(s) quedaron dentro del ${aviso.restriccion ?? 'bloqueo'} y no se reprogramaron: reprográmalos manualmente.`}
                nota="Los partidos conservan su horario y su estado."
                partidos={aviso.afectados}
              />
            )}
          </div>
        )}
      </section>

      <CalendarioSemanal
        semana={semana}
        bloques={ocupacion.data?.bloques}
        cargando={ocupacion.isPending}
        error={ocupacion.error}
        onReintentar={() => void ocupacion.refetch()}
        onCambiarSemana={onCambiarSemana}
      />

      {/* B-13.2: horarios de club o comodato; una cancha EXTERNO no admite horarios (DEC-33). */}
      {cancha.propietario !== 'EXTERNO' && permisosHorarios.ver && <SeccionHorariosClub cancha={cancha} />}

      {(modal === 'disponibilidad' || modal === 'bloqueo') && (
        <ModalIntervalo
          canchaId={cancha.id}
          canchaNombre={cancha.nombre}
          tipo={modal === 'bloqueo' ? 'BLOQUEO' : 'DISPONIBLE'}
          onCerrar={() => setModal(null)}
          onRegistrado={({ disponibilidad, partidosAfectados }) => {
            const { exito, restriccion } = avisoPorTipo[disponibilidad.tipo]
            setModal(null)
            setAviso({ mensaje: exito, afectados: partidosAfectados, restriccion })
          }}
        />
      )}
      {modal === 'editar' && (
        <FormularioCancha
          cancha={cancha}
          onCerrar={() => setModal(null)}
          onGuardada={() => {
            setModal(null)
            setAviso({ mensaje: 'Cancha actualizada.', afectados: [] })
          }}
        />
      )}
      {modal === 'estado' && (
        <ModalEstadoCancha
          cancha={cancha}
          onCerrar={() => setModal(null)}
          onActualizada={({ activa }) => {
            setModal(null)
            setAviso({ mensaje: activa ? 'Cancha activada.' : 'Cancha desactivada.', afectados: [] })
          }}
        />
      )}
    </div>
  )
}
