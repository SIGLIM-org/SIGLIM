import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigation } from 'react-router'
import {
  LogOut,
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  UserRound,
  X,
} from 'lucide-react'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import {
  descripcionDeAcceso,
  useCerrarSesion,
} from '@/features/seguridad/sesion'
import { seccionesPermitidas } from '@/layouts/navegacion'

const clasesSalto =
  'sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-label-md focus:text-on-primary'

/** Elementos que pueden recibir el foco con Tab dentro del menú móvil. */
const selectorEnfocables =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

const clasesSeccion = ({ isActive }: { isActive: boolean }) =>
  [
    'flex min-h-11 items-center gap-3 rounded-md px-3 text-label-md transition-colors',
    isActive
      ? 'bg-primary text-on-primary'
      : 'text-text-main hover:bg-background',
  ].join(' ')

/**
 * Barra superior y barra lateral colapsable de la aplicación interna (F-07).
 * La identidad y las secciones visibles salen de `GET /api/v1/yo` (H-01.3).
 */
export function InternalLayout() {
  const [menuAbierto, setMenuAbierto] = useState(false)
  const [colapsada, setColapsada] = useState(false)
  const cerrarRef = useRef<HTMLButtonElement>(null)
  const abrirRef = useRef<HTMLButtonElement>(null)
  const dialogoRef = useRef<HTMLDivElement>(null)
  const { pathname } = useLocation()
  const navegacion = useNavigation()
  const { yo } = useSesionActual()
  const { cerrar, cerrando } = useCerrarSesion()
  const cargandoModulo = navegacion.state === 'loading'
  const secciones = seccionesPermitidas(yo.permisos)
  const detalleAcceso = descripcionDeAcceso(yo.asignaciones)

  const cerrarMenu = useCallback(() => {
    setMenuAbierto(false)
    abrirRef.current?.focus()
  }, [])

  useEffect(() => {
    setMenuAbierto(false)
  }, [pathname])

  useEffect(() => {
    if (!menuAbierto) {
      return
    }

    cerrarRef.current?.focus()

    const overflowPrevio = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const alPresionar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') {
        cerrarMenu()
        return
      }

      if (evento.key === 'Tab') {
        atraparFoco(evento)
      }
    }

    /*
     * Trampa de foco del diálogo modal (WCAG 2.1: 2.4.3, RNF-008): con el menú
     * abierto, Tab y Mayús+Tab recorren solo sus elementos y dan la vuelta.
     */
    const atraparFoco = (evento: KeyboardEvent) => {
      const dialogo = dialogoRef.current

      if (!dialogo) {
        return
      }

      const enfocables = Array.from(
        dialogo.querySelectorAll<HTMLElement>(selectorEnfocables),
      )

      if (enfocables.length === 0) {
        return
      }

      const primero = enfocables[0]
      const ultimo = enfocables[enfocables.length - 1]
      const activo = document.activeElement
      const fueraDelDialogo = !dialogo.contains(activo)

      if (evento.shiftKey && (activo === primero || fueraDelDialogo)) {
        evento.preventDefault()
        ultimo.focus()
      } else if (!evento.shiftKey && (activo === ultimo || fueraDelDialogo)) {
        evento.preventDefault()
        primero.focus()
      }
    }

    window.addEventListener('keydown', alPresionar)

    return () => {
      document.body.style.overflow = overflowPrevio
      window.removeEventListener('keydown', alPresionar)
    }
  }, [menuAbierto, cerrarMenu])

  return (
    <div className="min-h-dvh bg-background">
      <a href="#contenido-principal" className={clasesSalto}>
        Saltar al contenido
      </a>

      <header className="fixed inset-x-0 top-0 z-50 flex h-16 items-center gap-2 border-b border-border bg-surface px-4 lg:px-6">
        <button
          ref={abrirRef}
          type="button"
          aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
          aria-expanded={menuAbierto}
          aria-controls="menu-movil"
          onClick={() => (menuAbierto ? cerrarMenu() : setMenuAbierto(true))}
          className="inline-flex size-11 items-center justify-center rounded-md border border-border text-text-main transition-colors hover:bg-background lg:hidden"
        >
          {menuAbierto ? (
            <X aria-hidden="true" className="size-5" />
          ) : (
            <Menu aria-hidden="true" className="size-5" />
          )}
        </button>

        <button
          type="button"
          aria-label={colapsada ? 'Expandir menú' : 'Colapsar menú'}
          aria-expanded={!colapsada}
          aria-controls="menu-ejecutivo"
          onClick={() => setColapsada((estado) => !estado)}
          className="hidden size-11 items-center justify-center rounded-md border border-border text-text-main transition-colors hover:bg-background lg:inline-flex"
        >
          {colapsada ? (
            <PanelLeftOpen aria-hidden="true" className="size-5" />
          ) : (
            <PanelLeftClose aria-hidden="true" className="size-5" />
          )}
        </button>

        <Link
          to="/app"
          className="flex min-h-11 items-center gap-2 rounded-md"
          aria-label="SIGLIM, aplicación interna"
        >
          <span
            aria-hidden="true"
            className="grid size-9 place-items-center rounded-md bg-primary text-headline-sm text-on-primary"
          >
            S
          </span>
          <span className="hidden flex-col leading-tight sm:flex">
            <span className="text-headline-sm text-text-main">SIGLIM</span>
            <span className="text-body-sm text-text-muted">
              Liga Municipal de Futbol Amateur Morelia
            </span>
          </span>
        </Link>

        <div className="ml-auto flex min-w-0 items-center gap-2">
          <div className="flex min-w-0 flex-col text-right">
            <span className="max-w-[7rem] truncate text-label-md text-text-main sm:max-w-[12rem]">
              {yo.perfil.nombreMostrado}
            </span>
            <span className="max-w-[7rem] truncate text-body-sm text-text-muted sm:max-w-[14rem]">
              {detalleAcceso}
            </span>
          </div>
          <span
            aria-hidden="true"
            className="grid size-8 shrink-0 place-items-center rounded-full bg-primary text-on-primary"
          >
            <UserRound className="size-4" />
          </span>
          <button
            type="button"
            aria-label="Cerrar sesión"
            aria-busy={cerrando || undefined}
            disabled={cerrando}
            onClick={() => void cerrar()}
            className="inline-flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-md border border-border px-2 text-label-md text-text-main transition-colors hover:bg-background disabled:cursor-not-allowed disabled:opacity-50 sm:px-3"
          >
            <LogOut aria-hidden="true" className="size-4 shrink-0" />
            <span className="hidden sm:inline">Cerrar sesión</span>
          </button>
        </div>
      </header>

      <aside
        id="menu-ejecutivo"
        className={[
          'fixed bottom-0 left-0 top-16 z-40 hidden flex-col border-r border-border bg-surface lg:flex',
          colapsada ? 'w-16' : 'w-[16.25rem]',
        ].join(' ')}
      >
        <nav
          aria-label="Menú Ejecutivo"
          className="flex flex-1 flex-col gap-1 overflow-y-auto p-2"
        >
          {!colapsada && (
            <p className="px-3 pb-1 pt-2 text-label-sm text-text-muted">
              Menú Ejecutivo
            </p>
          )}
          {secciones.map((seccion) => {
            const Icono = seccion.icono

            return (
              <NavLink
                key={seccion.ruta}
                to={seccion.ruta}
                end={seccion.ruta === '/app'}
                title={colapsada ? seccion.nombre : undefined}
                className={({ isActive }) =>
                  [
                    clasesSeccion({ isActive }),
                    colapsada ? 'justify-center' : '',
                  ].join(' ')
                }
              >
                <Icono aria-hidden="true" className="size-5 shrink-0" />
                <span className={colapsada ? 'sr-only' : undefined}>
                  {seccion.nombre}
                </span>
              </NavLink>
            )
          })}
        </nav>
      </aside>

      {menuAbierto && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            aria-hidden="true"
            onClick={cerrarMenu}
            className="absolute inset-0 bg-surface-dark/50"
          />
          <div
            ref={dialogoRef}
            id="menu-movil"
            role="dialog"
            aria-modal="true"
            aria-label="Menú Ejecutivo"
            className="absolute inset-y-0 left-0 flex w-[16.25rem] flex-col bg-surface"
          >
            <div className="flex h-16 items-center justify-between border-b border-border px-4">
              <span className="text-headline-sm text-text-main">
                Menú Ejecutivo
              </span>
              <button
                ref={cerrarRef}
                type="button"
                aria-label="Cerrar menú"
                onClick={cerrarMenu}
                className="inline-flex size-11 items-center justify-center rounded-md border border-border text-text-main transition-colors hover:bg-background"
              >
                <X aria-hidden="true" className="size-5" />
              </button>
            </div>
            <nav
              aria-label="Menú Ejecutivo"
              className="flex flex-1 flex-col gap-1 overflow-y-auto p-2"
            >
              {secciones.map((seccion) => {
                const Icono = seccion.icono

                return (
                  <NavLink
                    key={seccion.ruta}
                    to={seccion.ruta}
                    end={seccion.ruta === '/app'}
                    className={clasesSeccion}
                  >
                    <Icono aria-hidden="true" className="size-5 shrink-0" />
                    <span>{seccion.nombre}</span>
                  </NavLink>
                )
              })}
            </nav>
          </div>
        </div>
      )}

      <div className={colapsada ? 'lg:pl-16' : 'lg:pl-[16.25rem]'}>
        <main
          id="contenido-principal"
          className="min-h-dvh px-4 pb-8 pt-24 sm:px-6 lg:px-8"
        >
          {cargandoModulo ? (
            <EstadoCarga etiqueta="Cargando módulo…" filas={6} columnas={3} />
          ) : (
            <Outlet />
          )}
        </main>
      </div>
    </div>
  )
}
