import {
  Building2,
  CalendarDays,
  Gavel,
  Goal,
  LayoutDashboard,
  Settings,
  type LucideIcon,
} from 'lucide-react'
import { puedeVerModulo } from '@/features/seguridad/permisos'

export type EnlacePortal = {
  nombre: string
  ruta: string
}

export type SubseccionApp = {
  nombre: string
  ruta: string
  /** Permiso que hace visible el enlace en la página de la sección. */
  permiso: string
  descripcion: string
}

export type SeccionApp = EnlacePortal & {
  icono: LucideIcon
  /** Módulos del ERS que dan acceso a la sección. Vacío = siempre visible. */
  modulos: readonly string[]
  /** Pantallas a las que se entra desde la página de la sección; no van en el menú lateral. */
  subsecciones?: readonly SubseccionApp[]
}

/** Navegación del portal público según F-01. */
export const enlacesPortal: EnlacePortal[] = [
  { nombre: 'Programación', ruta: '/' },
  { nombre: 'Resultados', ruta: '/resultados' },
  { nombre: 'Tablas de posiciones', ruta: '/tabla' },
]

/**
 * Menú Ejecutivo de F-07 y correspondencia con los módulos del ERS.
 *
 * **[PROPUESTA]** La navegación final sigue abierta (PEN-014): esta tabla es una
 * hipótesis de trabajo, no una regla de negocio. Inicio se muestra siempre.
 *
 * M-14 (notificaciones) no tiene sección: los avisos se consultan desde la
 * campana (F-09, RF-142), no desde el menú lateral.
 */
export const seccionesApp: SeccionApp[] = [
  { nombre: 'Inicio', ruta: '/app', icono: LayoutDashboard, modulos: [] },
  {
    nombre: 'Organización',
    ruta: '/app/organizacion',
    icono: Building2,
    modulos: ['M-03', 'M-04'],
  },
  {
    nombre: 'Programación',
    ruta: '/app/programacion',
    icono: CalendarDays,
    modulos: ['M-05', 'M-06', 'M-07', 'M-10'],
    subsecciones: [
      {
        nombre: 'Canchas',
        ruta: '/app/programacion/canchas',
        permiso: 'canchas.cancha:CONSULTAR',
        descripcion: 'Catálogo, disponibilidad y bloqueos.',
      },
    ],
  },
  { nombre: 'Partidos', ruta: '/app/partidos', icono: Goal, modulos: ['M-08'] },
  { nombre: 'Disciplina', ruta: '/app/disciplina', icono: Gavel, modulos: ['M-09'] },
  {
    nombre: 'Administración',
    ruta: '/app/administracion',
    icono: Settings,
    modulos: ['M-01', 'M-02', 'M-11', 'M-12', 'M-13'],
    subsecciones: [
      {
        nombre: 'Temporadas',
        ruta: '/app/administracion/temporadas',
        permiso: 'temporadas.temporada:CONSULTAR',
        descripcion: 'Ciclos deportivos, activación y cierre.',
      },
      {
        nombre: 'Auditoría',
        ruta: '/app/administracion/auditoria',
        permiso: 'auditoria.registro:CONSULTAR',
        descripcion: 'Consulta de las operaciones críticas registradas.',
      },
    ],
  },
]

/**
 * Secciones que puede ver el usuario según los permisos de `GET /api/v1/yo`.
 * Inicio siempre está presente (H-01.3).
 */
export function seccionesPermitidas(
  permisos: readonly { codigo: string; modulo: string }[],
): SeccionApp[] {
  return seccionesApp.filter((seccion) =>
    puedeVerModulo(permisos, seccion.modulos),
  )
}

/**
 * Sección del Menú Ejecutivo a la que pertenece una ruta de `/app`. Devuelve
 * `undefined` para las rutas desconocidas, para que muestren la 404 y no la 403
 * (H-02.4, VAL-024).
 */
export function seccionDeRuta(pathname: string): SeccionApp | undefined {
  const inicio = seccionesApp.find(({ ruta }) => ruta === '/app')

  if (pathname === '/app' || pathname === '/app/') {
    return inicio
  }

  return seccionesApp.find(
    ({ ruta }) =>
      ruta !== '/app' && (pathname === ruta || pathname.startsWith(`${ruta}/`)),
  )
}
