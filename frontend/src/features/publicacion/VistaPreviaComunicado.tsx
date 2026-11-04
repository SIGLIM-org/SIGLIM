import { Eye, Info } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoPublicacion } from '@/features/publicacion/etiquetas'
import { formatearFechaHora } from '@/features/publicacion/fechas'
import type { EstadoPublicacion } from '@/features/publicacion/tipos'

type Props = {
  titulo: string
  cuerpo: string
  estado: EstadoPublicacion
  publicadoEn?: string
}

/**
 * Vista previa del comunicado con su disposición editorial (UI-031). No
 * afirma que esté publicado: el aviso y la insignia lo distinguen mientras siga
 * en borrador o en revisión (ERS §3.1.1).
 */
export function VistaPreviaComunicado({ titulo, cuerpo, estado, publicadoEn }: Props) {
  const etiqueta = etiquetaEstadoPublicacion[estado]
  const publicado = estado === 'PUBLICADO'

  return (
    <article
      aria-label="Vista previa del comunicado"
      className="overflow-hidden rounded-lg border border-border bg-surface"
    >
      <header className="border-b border-border bg-primary/5 p-5 sm:p-6">
        <p className="text-label-sm uppercase text-primary">
          Liga Municipal de Futbol Amateur Morelia
        </p>
        <p className="mt-1 text-body-sm text-text-muted">
          {publicado ? 'Comunicado oficial' : 'Vista previa del comunicado'}
        </p>
        <h3 className="mt-4 text-headline-lg text-text-main">
          {titulo.trim() || 'Título del comunicado'}
        </h3>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Insignia variante={etiqueta.variante} icono={etiqueta.icono}>
            {etiqueta.texto}
          </Insignia>
          {publicado && publicadoEn && (
            <span className="text-body-sm text-text-muted">
              Publicado el <time dateTime={publicadoEn}>{formatearFechaHora(publicadoEn)}</time>
            </span>
          )}
        </div>
      </header>

      <div className="p-5 sm:p-6">
        {!publicado && (
          <p
            role="note"
            className="mb-4 flex items-start gap-2 rounded-md border border-info/30 bg-info/8 p-3 text-body-md text-info-strong"
          >
            <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            <span>
              Esta es una vista previa. El comunicado solo quedará publicado cuando la
              Presidencia lo apruebe.
            </span>
          </p>
        )}
        <div className="whitespace-pre-wrap text-body-lg text-text-main">
          {cuerpo.trim() || 'El cuerpo del comunicado aparecerá aquí.'}
        </div>
      </div>

      <footer className="flex items-center gap-2 border-t border-border p-5 text-body-sm text-text-muted sm:px-6">
        <Eye aria-hidden="true" className="size-4 shrink-0" />
        Liga Municipal de Futbol Amateur Morelia
      </footer>
    </article>
  )
}
