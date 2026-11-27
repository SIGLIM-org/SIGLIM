import { useId } from 'react'
import { Link } from 'react-router'
import { ArrowRight, CalendarX2, ClipboardList, Flag, MapPin } from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaFuncion, nombrePartido } from '@/features/arbitraje/designaciones/etiquetas'
import type { MiDesignacion } from '@/features/arbitraje/designaciones/tipos'
import { estadoCedula } from '@/features/partidos/etiquetas'
import { formatearFechaCorta, formatearHora } from '@/features/programacion/fechas'
import {
  etiquetaEstadoPartido,
  varianteEstadoPartido,
} from '@/features/programacion/etiquetas'

type Props = {
  designacion: MiDesignacion
}

/**
 * Un partido de «Mis designaciones» (DEC-212): cuándo y dónde, la función, el estado del partido
 * (un cancelado dice que no se juega) y el de la cédula, con el enlace al detalle (F-25).
 */
export function ElementoMiDesignacion({ designacion }: Props) {
  const id = useId()
  const { partido, funcion, cedula } = designacion
  const cancelado = partido.estado === 'CANCELADO'
  const estadoDeCedula = cedula ? estadoCedula[cedula.estado] : undefined

  return (
    <article aria-labelledby={`${id}-titulo`} className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex items-baseline gap-2 text-headline-sm text-text-main">
          {formatearFechaCorta(partido.inicio)}
          <span className="font-mono tabular-nums">
            {formatearHora(partido.inicio)}
          </span>
        </p>
        <div className="flex flex-wrap gap-1.5">
          <Insignia variante={funcion === 'CENTRAL' ? 'informacion' : 'neutra'} icono={Flag}>
            {etiquetaFuncion[funcion]}
          </Insignia>
          {cancelado ? (
            <Insignia variante="error" icono={CalendarX2}>
              Cancelado: no se juega
            </Insignia>
          ) : (
            <Insignia variante={varianteEstadoPartido[partido.estado]}>
              {etiquetaEstadoPartido[partido.estado]}
            </Insignia>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-1">
        <h2
          id={`${id}-titulo`}
          className={`break-words text-body-lg font-semibold ${cancelado ? 'text-text-muted' : 'text-text-main'}`}
        >
          {nombrePartido(partido)}
        </h2>
        <p className="flex items-start gap-1.5 break-words text-body-md text-text-muted">
          <MapPin aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          {partido.cancha.nombre}
        </p>
        <p className="break-words text-body-sm text-text-muted">
          {partido.categoria.nombre} · {partido.grupo.nombre} · Jornada{' '}
          <span className="font-mono tabular-nums">{partido.jornada.numero}</span>
        </p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        {cedula ? (
          <p className="flex flex-wrap items-center gap-2 text-body-sm text-text-muted">
            <ClipboardList aria-hidden="true" className="size-4 shrink-0" />
            Cédula
            {estadoDeCedula ? (
              <Insignia variante={estadoDeCedula.variante}>{estadoDeCedula.texto}</Insignia>
            ) : (
              <span className="text-text-main">registrada</span>
            )}
          </p>
        ) : (
          <p className="text-body-sm text-text-muted">Sin cédula abierta</p>
        )}
        <Link
          to={`/app/partidos/${encodeURIComponent(partido.id)}`}
          aria-label={`Ver partido ${nombrePartido(partido)}`}
          className={`${clasesBoton('secundario')} w-full sm:w-auto`}
        >
          Ver partido
          <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
        </Link>
      </div>
    </article>
  )
}
