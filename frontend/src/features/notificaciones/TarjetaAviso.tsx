import { Link } from 'react-router'
import { useMarcarLeido } from '@/features/notificaciones/api'
import {
  enlaceInterno,
  fechaDetallada,
  presentacionDe,
  tiempoRelativo,
} from '@/features/notificaciones/presentacion'
import type { Aviso } from '@/features/notificaciones/tipos'

type Props = {
  aviso: Aviso
  /** Versión del panel de la campana: una línea de texto y sin acciones aparte. */
  compacto?: boolean
  /** Se llama al abrir el aviso, p. ej. para cerrar el panel. */
  alAbrir?: () => void
}

/**
 * Un aviso de F-09 (RF-142, UI-030). Abrirlo lleva a la pantalla del trámite y lo marca como leído
 * (H-25, criterio 2); la lectura sigue aunque la página cambie, porque la mutación vive en el
 * cliente de consultas.
 */
export function TarjetaAviso({ aviso, compacto = false, alAbrir }: Props) {
  const marcar = useMarcarLeido()
  const { icono: Icono, categoria } = presentacionDe(aviso.evento)
  const enlace = enlaceInterno(aviso.enlace)
  const sinLeer = aviso.leidaEn === null

  const abrir = () => {
    if (sinLeer) {
      marcar.mutate(aviso.id)
    }
    alAbrir?.()
  }

  const titulo = (
    <span className="flex items-baseline gap-2">
      {sinLeer && (
        <span className="relative top-[-0.125rem] size-2 shrink-0 rounded-full bg-info">
          <span className="sr-only">Sin leer: </span>
        </span>
      )}
      <span
        className={[
          'text-label-md text-text-main',
          sinLeer ? 'font-semibold' : '',
          compacto ? 'line-clamp-2' : '',
        ].join(' ')}
      >
        {aviso.titulo}
      </span>
    </span>
  )

  return (
    <article
      className={[
        'relative flex gap-3',
        compacto ? 'px-4 py-3' : 'rounded-lg border border-border p-4 sm:gap-4',
        sinLeer ? 'bg-surface' : compacto ? 'bg-surface' : 'bg-background',
        enlace ? 'transition-colors focus-within:bg-background hover:bg-background' : '',
      ].join(' ')}
    >
      <span
        aria-hidden="true"
        className={[
          'grid shrink-0 place-items-center rounded-md',
          compacto ? 'size-8' : 'size-10',
          sinLeer ? 'bg-primary/10 text-primary' : 'bg-border/60 text-text-muted',
        ].join(' ')}
      >
        <Icono className={compacto ? 'size-4' : 'size-5'} />
      </span>

      <div className="flex min-w-0 flex-1 flex-col gap-1 sm:flex-row sm:items-start sm:gap-4">
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <h3 className="text-label-md">
          {enlace ? (
            <Link
              to={enlace}
              onClick={abrir}
              className="rounded-sm outline-offset-2 after:absolute after:inset-0 after:content-[''] focus-visible:outline-2 focus-visible:outline-primary"
            >
              {titulo}
            </Link>
          ) : (
            titulo
          )}
        </h3>

        {aviso.cuerpo && (
          <p
            className={[
              'max-w-[70ch] text-body-md text-text-muted',
              compacto ? 'line-clamp-2' : '',
            ].join(' ')}
          >
            {aviso.cuerpo}
          </p>
        )}

        <p className="flex flex-wrap items-center gap-x-2 text-body-sm text-text-muted">
          <time dateTime={aviso.creadaEn} title={fechaDetallada(aviso.creadaEn)}>
            {tiempoRelativo(aviso.creadaEn)}
          </time>
          <span aria-hidden="true">·</span>
          <span>{categoria}</span>
          {!sinLeer && !compacto && (
            <>
              <span aria-hidden="true">·</span>
              <span>Leído</span>
            </>
          )}
        </p>
      </div>

      {!compacto && sinLeer && (
        // En móvil va debajo del texto, alineado con él; desde `sm`, a la derecha.
        <div className="relative z-10 -ml-3 shrink-0 self-start sm:-mt-3 sm:ml-0">
          <button
            type="button"
            onClick={() => marcar.mutate(aviso.id)}
            disabled={marcar.isPending}
            aria-label={`Marcar como leído: ${aviso.titulo}`}
            className="inline-flex min-h-11 items-center rounded-md px-3 text-label-md text-primary transition-colors hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Marcar como leído
          </button>
        </div>
      )}
      </div>
    </article>
  )
}
