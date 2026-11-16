import { Check } from 'lucide-react'
import type { Jornada } from '@/features/programacion/tipos'
import { formatearFechaHora } from '@/features/publicacion/fechas'

type Props = {
  jornada: Jornada
  /** Nombre de quien envió la jornada, si se conoce. */
  enviadaPor?: string
}

type Paso = {
  titulo: string
  detalle: string
  situacion: 'hecho' | 'actual' | 'siguiente'
}

/**
 * Los tres pasos del flujo de publicación de la jornada (RF-090, RF-121): Borrador → Revisión →
 * Publicado. Muestra dónde está la jornada y quién hizo cada paso (ERS §3.1.1, «el estado manda»).
 */
export function CicloPublicacionJornada({ jornada, enviadaPor }: Props) {
  const estado = jornada.estadoPublicacion
  const pasos: Paso[] = [
    {
      titulo: 'Programación',
      detalle:
        estado === 'BORRADOR'
          ? 'En borrador con el Programador'
          : [
              enviadaPor ? `Enviada por ${enviadaPor}` : 'Enviada a aprobación',
              jornada.enviadaEn ? formatearFechaHora(jornada.enviadaEn) : null,
            ]
              .filter(Boolean)
              .join(' · '),
      situacion: estado === 'BORRADOR' ? 'actual' : 'hecho',
    },
    {
      titulo: 'Dictamen de la Presidencia',
      detalle:
        estado === 'PUBLICADO'
          ? `Aprobada${jornada.aprobadaEn ? ` · ${formatearFechaHora(jornada.aprobadaEn)}` : ''}`
          : estado === 'REVISION'
            ? 'Pendiente de aprobar o devolver'
            : 'Después del envío',
      situacion: estado === 'PUBLICADO' ? 'hecho' : estado === 'REVISION' ? 'actual' : 'siguiente',
    },
    {
      titulo: 'Publicación en el portal',
      detalle:
        estado === 'PUBLICADO' && jornada.publicadaEn
          ? formatearFechaHora(jornada.publicadaEn)
          : 'Al aprobarse',
      situacion: estado === 'PUBLICADO' ? 'hecho' : 'siguiente',
    },
  ]

  return (
    <ol
      aria-label="Flujo de publicación de la jornada"
      className="grid gap-3 rounded-lg border border-border bg-surface p-4 sm:grid-cols-3 sm:gap-0"
    >
      {pasos.map((paso, indice) => (
        <li
          key={paso.titulo}
          aria-current={paso.situacion === 'actual' ? 'step' : undefined}
          className={[
            'flex items-start gap-3 sm:px-4 sm:first:pl-0 sm:last:pr-0',
            indice > 0 ? 'sm:border-l sm:border-border' : '',
          ].join(' ')}
        >
          <span
            aria-hidden="true"
            className={[
              'flex size-7 shrink-0 items-center justify-center rounded-full font-mono text-label-sm tabular-nums',
              paso.situacion === 'hecho'
                ? 'bg-primary text-on-primary'
                : paso.situacion === 'actual'
                  ? 'border-2 border-primary bg-surface text-primary'
                  : 'border border-border bg-background text-text-muted',
            ].join(' ')}
          >
            {paso.situacion === 'hecho' ? <Check className="size-4" /> : indice + 1}
          </span>
          <div className="min-w-0">
            <p
              className={[
                'text-label-md font-semibold',
                paso.situacion === 'siguiente' ? 'text-text-muted' : 'text-text-main',
              ].join(' ')}
            >
              {paso.titulo}
              <span className="sr-only">
                {paso.situacion === 'hecho'
                  ? ' (concluido)'
                  : paso.situacion === 'actual'
                    ? ' (paso actual)'
                    : ' (pendiente)'}
              </span>
            </p>
            <p className="mt-0.5 break-words text-body-sm text-text-muted">{paso.detalle}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}
