import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { Bell, CheckCheck, CircleCheck } from 'lucide-react'
import { useAvisosNoLeidos, useMarcarTodos } from '@/features/notificaciones/api'
import { contadorVisible } from '@/features/notificaciones/presentacion'
import { TarjetaAviso } from '@/features/notificaciones/TarjetaAviso'
import { ApiError } from '@/lib/api/ApiError'

const RUTA_AVISOS = '/app/notificaciones'

/**
 * Campana de la barra superior con el contador de avisos sin leer y su panel (F-09, UI-030). El
 * contador se actualiza al cargar y cada 60 s mientras la pestaña está visible (H-25, criterio 3).
 * El panel no es modal: se cierra con Escape, al hacer clic fuera o al cambiar de página.
 */
export function CampanaAvisos() {
  const [abierto, setAbierto] = useState(false)
  const botonRef = useRef<HTMLButtonElement>(null)
  const contenedorRef = useRef<HTMLDivElement>(null)
  const idPanel = useId()
  const idTitulo = useId()
  const { pathname } = useLocation()
  const noLeidos = useAvisosNoLeidos()
  const marcarTodos = useMarcarTodos()
  const total = noLeidos.data?.totalElementos ?? 0
  const avisos = noLeidos.data?.contenido ?? []

  const cerrar = useCallback((devolverFoco: boolean) => {
    setAbierto(false)
    if (devolverFoco) {
      botonRef.current?.focus()
    }
  }, [])

  useEffect(() => {
    setAbierto(false)
  }, [pathname])

  useEffect(() => {
    if (!abierto) {
      return
    }

    const alPresionar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') {
        cerrar(true)
      }
    }
    const alPulsarFuera = (evento: PointerEvent) => {
      if (!contenedorRef.current?.contains(evento.target as Node)) {
        cerrar(false)
      }
    }

    window.addEventListener('keydown', alPresionar)
    document.addEventListener('pointerdown', alPulsarFuera)

    return () => {
      window.removeEventListener('keydown', alPresionar)
      document.removeEventListener('pointerdown', alPulsarFuera)
    }
  }, [abierto, cerrar])

  const etiqueta =
    total === 0
      ? 'Avisos, ninguno sin leer'
      : total === 1
        ? 'Avisos, 1 sin leer'
        : `Avisos, ${total} sin leer`

  return (
    <div ref={contenedorRef} className="relative">
      <button
        ref={botonRef}
        type="button"
        aria-label={etiqueta}
        aria-expanded={abierto}
        aria-controls={idPanel}
        onClick={() => setAbierto((estado) => !estado)}
        className={[
          'relative inline-flex size-11 items-center justify-center rounded-md border transition-colors',
          abierto
            ? 'border-primary bg-primary/10 text-primary'
            : 'border-border text-text-main hover:bg-background',
        ].join(' ')}
      >
        <Bell aria-hidden="true" className="size-5" />
        {total > 0 && (
          <span
            aria-hidden="true"
            className="absolute -right-1.5 -top-1.5 grid h-5 min-w-5 place-items-center rounded-full bg-error-strong px-1 font-mono text-[0.6875rem] font-semibold leading-none text-on-primary ring-2 ring-surface"
          >
            {contadorVisible(total)}
          </span>
        )}
      </button>

      {abierto && (
        <div
          id={idPanel}
          role="dialog"
          aria-labelledby={idTitulo}
          className="fixed inset-x-4 top-[4.5rem] z-[55] flex max-h-[calc(100dvh-6rem)] flex-col overflow-hidden rounded-lg border border-border bg-surface shadow-[0_4px_12px_rgba(15,23,42,0.08)] sm:absolute sm:inset-x-auto sm:right-0 sm:top-[calc(100%+0.5rem)] sm:w-[24rem]"
        >
          <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-2">
            <div className="flex flex-col">
              <h2 id={idTitulo} className="text-headline-sm text-text-main">
                Avisos
              </h2>
              <p aria-live="polite" className="text-body-sm text-text-muted">
                {total === 0
                  ? 'Ninguno sin leer'
                  : total === 1
                    ? '1 sin leer'
                    : `${total} sin leer`}
              </p>
            </div>
            {total > 0 && (
              <button
                type="button"
                onClick={() => marcarTodos.mutate()}
                disabled={marcarTodos.isPending}
                className="inline-flex min-h-11 items-center gap-1.5 rounded-md px-2 text-label-md text-primary transition-colors hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <CheckCheck aria-hidden="true" className="size-4" />
                Marcar todos como leídos
              </button>
            )}
          </div>

          {marcarTodos.isError && (
            <p role="alert" className="border-b border-border px-4 py-2 text-body-sm text-error-strong">
              {marcarTodos.error instanceof ApiError
                ? marcarTodos.error.problema.detail
                : 'No se pudieron marcar los avisos. Intenta de nuevo.'}
            </p>
          )}

          <div className="min-h-0 flex-1 overflow-y-auto">
            {noLeidos.isPending ? (
              <p role="status" className="px-4 py-6 text-body-md text-text-muted">
                Cargando avisos…
              </p>
            ) : noLeidos.isError ? (
              <p role="alert" className="px-4 py-6 text-body-md text-error-strong">
                {noLeidos.error instanceof ApiError
                  ? noLeidos.error.problema.detail
                  : 'No se pudieron cargar los avisos. Intenta de nuevo.'}
              </p>
            ) : avisos.length === 0 ? (
              <div className="flex flex-col items-center gap-2 px-4 py-8 text-center">
                <CircleCheck aria-hidden="true" className="size-8 text-success" />
                <p className="text-headline-sm text-text-main">Estás al día</p>
                <p className="text-body-md text-text-muted">No tienes avisos sin leer.</p>
              </div>
            ) : (
              <ul className="divide-y divide-border">
                {avisos.map((aviso) => (
                  <li key={aviso.id}>
                    <TarjetaAviso aviso={aviso} compacto alAbrir={() => cerrar(false)} />
                  </li>
                ))}
              </ul>
            )}
          </div>

          <Link
            to={RUTA_AVISOS}
            className="flex min-h-11 items-center justify-center border-t border-border px-4 text-label-md text-primary transition-colors hover:bg-background"
          >
            {total > avisos.length ? `Ver los ${total} avisos sin leer` : 'Ver todos los avisos'}
          </Link>
        </div>
      )}
    </div>
  )
}
