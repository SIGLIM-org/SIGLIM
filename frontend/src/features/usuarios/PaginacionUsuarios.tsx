import { Button } from '@/components/ui/Button'

type Props = {
  pagina: number
  totalPaginas: number
  onCambiarPagina: (pagina: number) => void
}

/** Navegación por páginas de F-12; la página empieza en 0 como en la API (convenciones §4). */
export function PaginacionUsuarios({ pagina, totalPaginas, onCambiarPagina }: Props) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <span className="order-first w-full text-center text-body-sm text-text-muted sm:order-none sm:w-auto">
        Página {pagina + 1} de {Math.max(totalPaginas, 1)}
      </span>
      <Button
        variante="secundario"
        disabled={pagina === 0}
        onClick={() => onCambiarPagina(pagina - 1)}
      >
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
  )
}
