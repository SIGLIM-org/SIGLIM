import { ProveedorSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { InternalLayout } from '@/layouts/InternalLayout'

/**
 * Envoltura de la aplicación interna (H-01.3, F-07). Vive en su propio
 * fragmento para que el visitante del portal no descargue este código (DT-01).
 */
export function AplicacionInterna() {
  return (
    <ProveedorSesionActual>
      <InternalLayout />
    </ProveedorSesionActual>
  )
}
