import { useId } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { TAMANOS_PAGINA } from '@/features/auditoria/filtros'

type Props = {
  pagina: number
  totalPaginas: number
  tamano: number
  onCambiarPagina: (pagina: number) => void
  onCambiarTamano: (tamano: number) => void
}

/** Misma navegación por páginas que el catálogo de canchas, con selector de tamaño. */
export function PaginacionAuditoria({
  pagina,
  totalPaginas,
  tamano,
  onCambiarPagina,
  onCambiarTamano,
}: Props) {
  const id = useId()

  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="sm:w-44">
        <CampoSeleccion
          id={`${id}-tamano`}
          etiqueta="Registros por página"
          value={tamano}
          onChange={(evento) => onCambiarTamano(Number(evento.target.value))}
        >
          {TAMANOS_PAGINA.map((opcion) => (
            <option key={opcion} value={opcion}>
              {opcion}
            </option>
          ))}
        </CampoSeleccion>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 sm:justify-end sm:gap-3">
        <span className="order-first w-full text-center text-body-sm text-text-muted sm:order-none sm:w-auto">
          Página {pagina + 1} de {Math.max(totalPaginas, 1)}
        </span>
        <Button variante="secundario" disabled={pagina === 0} onClick={() => onCambiarPagina(pagina - 1)}>
          Anterior
        </Button>
        <Button
          variante="secundario"
          disabled={pagina + 1 >= totalPaginas}
          onClick={() => onCambiarPagina(pagina + 1)}
        >
          Siguiente
        </Button>
      </div>
    </div>
  )
}
