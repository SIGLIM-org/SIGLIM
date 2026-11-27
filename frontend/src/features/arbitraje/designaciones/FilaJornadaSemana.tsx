import { Link } from 'react-router'
import { ChevronRight } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { RUTA_DESIGNACIONES } from '@/features/arbitraje/designaciones/rutas'
import type {
  DesignacionesJornada,
  JornadaDeSemana,
} from '@/features/arbitraje/designaciones/tipos'
import { formatearFechaCorta } from '@/features/programacion/fechas'
import {
  etiquetaEstadoJornada,
  varianteEstadoJornada,
} from '@/features/programacion/etiquetas'

type FilaProps = {
  semana: string
  jornada: JornadaDeSemana
  resumen: DesignacionesJornada | undefined
  falloResumen: boolean
}

/** Jornada de la semana en F-24: enlaza a su designación con su estado y su avance (DEC-209). */
export function FilaJornadaSemana({ semana, jornada, resumen, falloResumen }: FilaProps) {
  const ruta = `${RUTA_DESIGNACIONES}?semana=${semana}&jornadaId=${encodeURIComponent(jornada.jornadaId)}`
  const titulo = resumen
    ? `${resumen.jornada.categoria.nombre} · ${resumen.jornada.grupo.nombre} · Jornada ${jornada.numero}`
    : `Jornada ${jornada.numero}`
  // Completa solo con central y asistentes en todos los partidos, no con el central nada más.
  const completa =
    resumen &&
    resumen.avance.conCentral === resumen.avance.partidos &&
    resumen.avance.asistentesCompletos === resumen.avance.partidos

  return (
    <li>
      <Link
        to={ruta}
        className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-background focus-visible:bg-background focus-visible:-outline-offset-2"
      >
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="break-words text-label-md text-text-main">{titulo}</span>
            {resumen && (
              <Insignia variante={varianteEstadoJornada[resumen.jornada.estadoPublicacion]}>
                {etiquetaEstadoJornada[resumen.jornada.estadoPublicacion]}
              </Insignia>
            )}
          </div>
          <p className="flex flex-wrap gap-x-2 text-body-sm text-text-muted">
            <span>{formatearFechaCorta(jornada.inicio)}</span>
            <span aria-hidden="true">·</span>
            {resumen ? (
              <>
                <span className={completa ? 'text-success-strong' : undefined}>
                  <span className="font-mono tabular-nums">{resumen.avance.conCentral}</span> de{' '}
                  <span className="font-mono tabular-nums">{resumen.avance.partidos}</span> con
                  central
                </span>
                <span aria-hidden="true">·</span>
                <span className={completa ? 'text-success-strong' : undefined}>
                  <span className="font-mono tabular-nums">
                    {resumen.avance.asistentesCompletos}
                  </span>{' '}
                  con asistentes completos
                </span>
              </>
            ) : falloResumen ? (
              <span>No se pudo consultar el avance</span>
            ) : (
              <span>
                <span className="font-mono tabular-nums">{jornada.partidos}</span>{' '}
                {jornada.partidos === 1 ? 'partido' : 'partidos'}
              </span>
            )}
          </p>
        </div>
        <ChevronRight
          aria-hidden="true"
          className="size-5 shrink-0 text-text-muted transition-transform group-hover:translate-x-0.5 motion-reduce:transition-none"
        />
      </Link>
    </li>
  )
}
