import { Outlet, useLocation } from 'react-router'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { puedeVerModulo } from '@/features/seguridad/permisos'
import { seccionDeRuta } from '@/layouts/navegacion'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Barrera de las rutas de `/app` por módulo (H-02.4, VAL-015): si la URL
 * pertenece a una sección del Menú Ejecutivo y la sesión no tiene ningún
 * permiso de sus módulos, muestra la 403 sin montar la página del módulo ni
 * pedir sus datos. Las rutas desconocidas siguen su curso para que el router
 * muestre la 404 (VAL-024). Debe montarse dentro de `ProveedorSesionActual`,
 * como hace `AplicacionInterna`.
 */
export function RutaConPermiso() {
  const { yo } = useSesionActual()
  const { pathname } = useLocation()
  const seccion = seccionDeRuta(pathname)

  if (seccion && !puedeVerModulo(yo.permisos, seccion.modulos)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  return <Outlet />
}
