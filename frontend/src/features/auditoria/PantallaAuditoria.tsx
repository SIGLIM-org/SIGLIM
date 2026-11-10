import { ConsultaAuditoria } from '@/features/auditoria/ConsultaAuditoria'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de auditoría (F-14, H-21.2). `RutaConPermiso` solo mira el módulo, así que
 * aquí se exige el permiso de consultar antes de montar la consulta y pedir sus datos.
 */
export function PantallaAuditoria() {
  return (
    <ConPermiso
      permiso="auditoria.registro:CONSULTAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <ConsultaAuditoria />
    </ConPermiso>
  )
}
