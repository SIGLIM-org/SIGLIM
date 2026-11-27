import { Link } from 'react-router'
import { ClipboardList, Flag, PencilLine, UserCheck } from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { Insignia } from '@/components/ui/Insignia'
import { EDITAR_DESIGNACION } from '@/features/arbitraje/designaciones/permisos'
import { rutaJornadaDesignaciones } from '@/features/arbitraje/designaciones/rutas'
import { estadoCedula, etiquetaFuncionArbitro } from '@/features/partidos/etiquetas'
import type { PartidoDetalle } from '@/features/partidos/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'

type Props = {
  partido: PartidoDetalle
}

/**
 * Árbitros designados y estado de la cédula (F-25, UI-010). Solo lo que guarda el sistema: el club
 * que presenta a cada árbitro y su designación son de H-14 (D-02), y la captura, de F-26. Con
 * `arbitraje.designacion:EDITAR`, «Designar árbitros» abre la jornada del partido en F-24 (DEC-211).
 */
export function PestanaResumenPartido({ partido }: Props) {
  const cedula = partido.cedula ? (estadoCedula[partido.cedula.estado] ?? null) : null

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <section aria-labelledby="titulo-arbitros" className="rounded-lg border border-border bg-surface p-4 sm:p-5">
        <h2 id="titulo-arbitros" className="flex items-center gap-2 text-headline-sm text-text-main">
          <Flag aria-hidden="true" className="size-5 shrink-0 text-text-muted" />
          Árbitros designados
        </h2>
        {partido.arbitros.length === 0 ? (
          <p className="mt-3 text-body-md text-text-muted">
            Todavía no hay árbitros designados para este partido.
          </p>
        ) : (
          <ul className="mt-3 flex flex-col divide-y divide-border">
            {partido.arbitros.map((arbitro) => (
              <li key={arbitro.id} className="flex flex-col gap-0.5 py-2.5 first:pt-0 last:pb-0">
                <span className="text-label-sm uppercase tracking-wide text-text-muted">
                  {etiquetaFuncionArbitro[arbitro.funcion] ?? arbitro.funcion}
                </span>
                <span className="break-words text-body-md text-text-main">
                  {arbitro.nombre ?? 'Nombre reservado'}
                </span>
              </li>
            ))}
          </ul>
        )}
        <ConPermiso permiso={EDITAR_DESIGNACION}>
          <Link
            to={rutaJornadaDesignaciones(partido.jornada.id)}
            className={`${clasesBoton('secundario')} mt-4 w-full sm:w-auto`}
          >
            <UserCheck aria-hidden="true" className="size-4 shrink-0" />
            Designar árbitros
          </Link>
        </ConPermiso>
      </section>

      <section aria-labelledby="titulo-cedula" className="rounded-lg border border-border bg-surface p-4 sm:p-5">
        <h2 id="titulo-cedula" className="flex items-center gap-2 text-headline-sm text-text-main">
          <ClipboardList aria-hidden="true" className="size-5 shrink-0 text-text-muted" />
          Cédula
        </h2>
        {cedula ? (
          <div className="mt-3">
            <Insignia variante={cedula.variante}>{cedula.texto}</Insignia>
          </div>
        ) : (
          <p className="mt-3 text-body-md text-text-muted">
            {partido.cedula
              ? 'La cédula está registrada.'
              : 'La cédula se abre cuando el partido está en curso.'}
          </p>
        )}
        {(partido.cedula || partido.estado === 'EN_CURSO') && (
          <ConPermiso permiso="partidos.cedula:CAPTURAR">
            <Link to={`/app/partidos/${partido.id}/cedula`} className={`${clasesBoton('secundario')} mt-4 w-full sm:w-auto`}>
              <PencilLine aria-hidden="true" className="size-4 shrink-0" />
              {partido.cedula ? 'Abrir la captura' : 'Capturar cédula'}
            </Link>
          </ConPermiso>
        )}
      </section>
    </div>
  )
}
