import { useState } from 'react'
import { CalendarClock, Ban, Info, PauseCircle } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useTransicionarPartido } from '@/features/partidos/api'
import { ModalReprogramarPartido } from '@/features/partidos/ModalReprogramarPartido'
import type { AccionPartido, PartidoDetalle } from '@/features/partidos/tipos'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  partido: PartidoDetalle
}

/** Suspender y cancelar solo piden motivo; reprogramar tiene su propio modal. */
const CON_MOTIVO = {
  SUSPENDER: {
    estadoNuevo: 'SUSPENDIDO',
    titulo: 'Suspender partido',
    descripcion:
      'El partido en curso se detiene. Después se podrá reprogramar o cancelar. El cambio queda en el historial.',
    confirmar: 'Suspender',
    variante: 'primario',
  },
  CANCELAR: {
    estadoNuevo: 'CANCELADO',
    titulo: 'Cancelar partido',
    descripcion:
      'El partido no se jugará y ya no admitirá cambios. El partido no se borra: queda en el historial con el motivo.',
    confirmar: 'Cancelar partido',
    variante: 'destructivo',
  },
} as const

/**
 * Acciones del partido según su estado y los permisos de la sesión (F-25, RF-046, DEC-162). Solo
 * muestra las `accionesPermitidas` que devuelve el API; si la sesión puede cambiar estados pero no
 * hay ninguna, explica por qué (principio «Todo bloqueo se explica»).
 */
export function BarraAccionesPartido({ partido }: Props) {
  const { yo } = useSesionActual()
  const puedeCambiar = tienePermiso(yo.permisos, 'partidos.partido:CAMBIAR_ESTADO')
  const transicion = useTransicionarPartido(partido.id)
  const [abierta, setAbierta] = useState<AccionPartido | null>(null)
  const [error, setError] = useState<string | null>(null)
  const acciones = partido.accionesPermitidas
  const conMotivo = abierta === 'SUSPENDER' || abierta === 'CANCELAR' ? CON_MOTIVO[abierta] : null

  const abrir = (accion: AccionPartido) => {
    setError(null)
    setAbierta(accion)
  }

  const cerrar = () => {
    setAbierta(null)
    setError(null)
  }

  const confirmar = (motivo: string) => {
    if (!conMotivo) {
      return
    }
    transicion.mutate(
      { estadoNuevo: conMotivo.estadoNuevo, motivo },
      {
        onSuccess: cerrar,
        onError: (fallo) =>
          setError(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo cambiar el estado del partido. Intenta de nuevo.',
          ),
      },
    )
  }

  const nota = (
    <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-sm text-info-strong lg:max-w-md">
      <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <span>
        El partido pasa a En curso a su hora y a Finalizado cuando se aprueba su cédula; esos cambios
        no se hacen a mano.
      </span>
    </p>
  )

  // Quien solo consulta no tiene acciones que mostrar ni explicar: basta la nota, sin tarjeta.
  if (acciones.length === 0 && !puedeCambiar) {
    return nota
  }

  return (
    <section
      aria-label="Acciones del partido"
      className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-5 lg:flex-row lg:items-center lg:justify-between"
    >
      {acciones.length > 0 ? (
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          {acciones.includes('REPROGRAMAR') && (
            <Button icono={CalendarClock} onClick={() => abrir('REPROGRAMAR')}>
              Reprogramar
            </Button>
          )}
          {acciones.includes('SUSPENDER') && (
            <Button variante="secundario" icono={PauseCircle} onClick={() => abrir('SUSPENDER')}>
              Suspender
            </Button>
          )}
          {acciones.includes('CANCELAR') && (
            <Button variante="secundario" icono={Ban} onClick={() => abrir('CANCELAR')}>
              Cancelar partido
            </Button>
          )}
        </div>
      ) : (
        <p className="text-body-md text-text-main">
          {partido.jornada.estadoPublicacion === 'PUBLICADO'
            ? 'Este estado ya no admite cambios manuales.'
            : `La jornada ${partido.jornada.numero} todavía no está publicada: ajusta el partido desde la programación de jornadas.`}
        </p>
      )}

      {nota}

      {conMotivo && (
        <ModalMotivo
          abierto
          titulo={conMotivo.titulo}
          descripcion={conMotivo.descripcion}
          etiquetaConfirmar={conMotivo.confirmar}
          varianteConfirmar={conMotivo.variante}
          procesando={transicion.isPending}
          aviso={
            error && (
              <p
                role="alert"
                className="rounded-md border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
              >
                {error}
              </p>
            )
          }
          onConfirmar={confirmar}
          onCancelar={cerrar}
        />
      )}
      {abierta === 'REPROGRAMAR' && <ModalReprogramarPartido partido={partido} onCerrar={cerrar} />}
    </section>
  )
}
