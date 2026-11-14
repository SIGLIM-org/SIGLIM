import { useId } from 'react'
import {
  CircleAlert,
  CircleCheck,
  LoaderCircle,
  RotateCw,
  Send,
  TriangleAlert,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { etiquetaConflicto } from '@/features/programacion/etiquetas'
import type {
  AvisoVerificacion,
  EstadoJornada,
} from '@/features/programacion/tipos'

type Props = {
  estadoJornada: EstadoJornada
  partidos: number
  conflictos: readonly AvisoVerificacion[]
  advertencias: readonly AvisoVerificacion[]
  cargando: boolean
  error: unknown
  puedeVerificar: boolean
  /** La sesión puede enviar jornadas a aprobación (`programacion.jornada:ENVIAR`). */
  tienesPermisoEnvio: boolean
  puedeEnviar: boolean
  onVerificar: () => void
  onEnviar: () => void
}

/**
 * Panel de validación de F-22 (H-12.4): resume la verificación en línea y controla el envío. El
 * envío real llega con H-13.2; aquí solo se deshabilita mientras haya conflictos o la jornada no
 * esté en borrador.
 */
export function ResumenVerificacion({
  estadoJornada,
  partidos,
  conflictos,
  advertencias,
  cargando,
  error,
  puedeVerificar,
  tienesPermisoEnvio,
  puedeEnviar,
  onVerificar,
  onEnviar,
}: Props) {
  const id = useId()
  const idAyudaEnvio = `${id}-ayuda-envio`
  const conteos = new Map<string, number>()

  for (const conflicto of conflictos) {
    conteos.set(conflicto.codigo, (conteos.get(conflicto.codigo) ?? 0) + 1)
  }

  const partidosConConflicto = new Set(conflictos.map(({ partidoId }) => partidoId)).size

  const ayudaEnvio = (() => {
    if (!tienesPermisoEnvio) {
      return 'Tu cuenta no tiene permiso para enviar jornadas a aprobación.'
    }
    if (estadoJornada !== 'BORRADOR') {
      return 'Solo se envía una jornada en borrador.'
    }
    if (partidos === 0) {
      return 'Programa al menos un partido antes de enviar la jornada.'
    }
    if (cargando) {
      return 'Espera a que termine la verificación de conflictos.'
    }
    if (error) {
      return 'No se pudo verificar la jornada. Vuelve a intentarlo.'
    }
    if (conflictos.length > 0) {
      return 'Resuelve los conflictos para enviar la jornada.'
    }
    return 'La jornada no tiene conflictos y está lista para enviarse.'
  })()

  return (
    <aside
      aria-labelledby={`${id}-titulo`}
      className="flex min-w-0 flex-col gap-4 lg:sticky lg:top-6 lg:self-start"
    >
      <section className="rounded-lg border border-border bg-surface p-4">
        <h2 id={`${id}-titulo`} className="text-headline-sm text-text-main">
          Validación de la jornada
        </h2>
        <p className="mt-1 text-body-sm text-text-muted">
          La verificación revisa equipos, canchas y horarios. No guarda cambios.
        </p>

        <div aria-live="polite" className="mt-4">
          {cargando && (
            <p role="status" className="flex items-center gap-2 text-body-md text-text-muted">
              <LoaderCircle aria-hidden="true" className="size-4 animate-spin motion-reduce:animate-none" />
              Verificando conflictos…
            </p>
          )}

          {!cargando && Boolean(error) && (
            <ErrorConsulta error={error} onReintentar={onVerificar} />
          )}

          {!cargando && !error && conflictos.length === 0 && (
            <div className="rounded-md bg-success/10 p-3">
              <p className="flex items-center gap-2 text-label-md font-semibold text-success-strong">
                <CircleCheck aria-hidden="true" className="size-4 shrink-0" />
                Sin conflictos
              </p>
              <p className="mt-1 text-body-md text-text-muted">
                No hay choques de equipos ni de canchas en esta jornada.
              </p>
            </div>
          )}

          {!cargando && !error && conflictos.length > 0 && (
            <div className="rounded-md bg-error/8 p-3">
              <p className="flex items-center gap-2 text-label-md font-semibold text-error-strong">
                <CircleAlert aria-hidden="true" className="size-4 shrink-0" />
                {conflictos.length === 1
                  ? '1 conflicto por resolver'
                  : `${conflictos.length} conflictos por resolver`}
              </p>
              <p className="mt-1 text-body-md text-text-muted">
                {partidosConConflicto === 1
                  ? 'Afecta a 1 partido de la jornada.'
                  : `Afecta a ${partidosConConflicto} partidos de la jornada.`}
              </p>
              <dl className="mt-3 flex flex-col gap-1.5 border-t border-error/20 pt-3">
                {[...conteos.entries()].map(([codigo, cantidad]) => (
                  <div
                    key={codigo}
                    className="flex items-baseline justify-between gap-3 text-body-md"
                  >
                    <dt className="text-text-muted">{etiquetaConflicto(codigo)}</dt>
                    <dd className="font-mono text-text-main">{cantidad}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}

          {!cargando && !error && advertencias.length > 0 && (
            <div className="mt-3 rounded-md bg-warning/10 p-3">
              <p className="flex items-center gap-2 text-label-md font-semibold text-warning-strong">
                <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
                Advertencias de localía
              </p>
              <ul className="mt-2 flex flex-col gap-1.5">
                {advertencias.map((advertencia, indice) => (
                  <li key={`${advertencia.partidoId}-${indice}`} className="text-body-md text-warning-strong">
                    {advertencia.mensaje}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        <Button
          variante="secundario"
          icono={RotateCw}
          onClick={onVerificar}
          disabled={!puedeVerificar}
          cargando={cargando}
          className="mt-4 w-full"
        >
          Verificar de nuevo
        </Button>
        {!puedeVerificar && (
          <p className="mt-2 text-body-sm text-text-muted">
            Necesitas permiso para editar la jornada y verificar sus conflictos.
          </p>
        )}
      </section>

      <section className="rounded-lg border border-border bg-surface p-4">
        <h2 className="text-headline-sm text-text-main">Envío a aprobación</h2>
        <Button
          icono={Send}
          onClick={onEnviar}
          disabled={!puedeEnviar}
          aria-describedby={idAyudaEnvio}
          className="mt-3 w-full"
        >
          Enviar a aprobación
        </Button>
        <p id={idAyudaEnvio} className="mt-2 text-body-sm text-text-muted">
          {ayudaEnvio}
        </p>
      </section>
    </aside>
  )
}
