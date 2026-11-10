import type { ReactNode } from 'react'
import { CircleAlert, Lock, RotateCw, SearchX, type LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ApiError } from '@/lib/api/ApiError'
import { esNoEncontrado, esSinPermiso } from '@/lib/api/errores'

type Props = {
  error: unknown
  onReintentar?: () => void
  /** Acción para un 404, p. ej. volver al catálogo. */
  accion?: ReactNode
}

type Contenido = { icono: LucideIcon; titulo: string; detalle?: string }

function contenidoDe(error: unknown): Contenido {
  if (esSinPermiso(error)) {
    return {
      icono: Lock,
      titulo: 'No tienes permiso para consultar esto',
      detalle: (error as ApiError).problema.detail,
    }
  }

  if (esNoEncontrado(error)) {
    return {
      icono: SearchX,
      titulo: 'No encontramos esta información. Puede que no exista o que no tengas acceso',
    }
  }

  return {
    icono: CircleAlert,
    titulo:
      error instanceof ApiError
        ? error.problema.detail
        : 'No se pudo cargar la información. Intenta de nuevo.',
  }
}

/**
 * Error de una consulta (H-02.4): 403 con su causa, 404 sin revelar si el recurso
 * existe (VAL-024) y, en lo demás, la causa con «Reintentar».
 */
export function ErrorConsulta({ error, onReintentar, accion }: Props) {
  const { icono: Icono, titulo, detalle } = contenidoDe(error)
  const reintentable = !esSinPermiso(error) && !esNoEncontrado(error)

  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-lg border border-border bg-surface px-4 py-10 text-center"
    >
      <Icono aria-hidden="true" className="size-10 text-text-muted" />
      <p className="max-w-md text-headline-sm text-text-main">{titulo}</p>
      {detalle && <p className="max-w-md text-body-md text-text-muted">{detalle}</p>}
      {esNoEncontrado(error) && accion && <div className="mt-2">{accion}</div>}
      {reintentable && onReintentar && (
        <Button variante="secundario" icono={RotateCw} onClick={onReintentar} className="mt-2">
          Reintentar
        </Button>
      )}
    </div>
  )
}
