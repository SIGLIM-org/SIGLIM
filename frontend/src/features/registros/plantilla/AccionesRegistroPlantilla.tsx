import { FileUp, FileSearch } from 'lucide-react'
import { Link } from 'react-router'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { rutaDocumentosRegistro, rutaValidacionRegistro } from '@/features/registros/rutas'
import type { RegistroJugadorFila } from '@/features/registros/tipos'

export type PermisosAccionesPlantilla = {
  /** `registros.documento:CREAR`. */
  cargar: boolean
  /** `registros.registro:VALIDAR`. */
  validar: boolean
}

type Props = {
  registro: RegistroJugadorFila
  permisos: PermisosAccionesPlantilla
}

/** ¿La fila ofrece alguna acción? Solo un registro pendiente las tiene (DEC-117). */
export function tieneAcciones(registro: RegistroJugadorFila, permisos: PermisosAccionesPlantilla) {
  return registro.estado === 'PENDIENTE_VALIDACION' && (permisos.cargar || permisos.validar)
}

/**
 * Acciones de una fila de la plantilla (DEC-111, DEC-117): «Cargar documentos» con
 * `registros.documento:CREAR` y «Revisar» con `registros.registro:VALIDAR`, solo en los registros
 * `PENDIENTE_VALIDACION`. Son enlaces cuyo nombre accesible incluye al jugador.
 */
export function AccionesRegistroPlantilla({ registro, permisos }: Props) {
  if (!tieneAcciones(registro, permisos)) {
    return null
  }

  const nombre = registro.persona.nombreCompleto

  return (
    <div className="flex flex-wrap gap-2 lg:flex-nowrap lg:justify-end">
      {permisos.cargar && (
        <Link
          to={rutaDocumentosRegistro(registro.id)}
          aria-label={`Cargar documentos de ${nombre}`}
          className={`${clasesBoton('secundario')} whitespace-nowrap`}
        >
          <FileUp aria-hidden="true" className="size-4 shrink-0" />
          Cargar documentos
        </Link>
      )}
      {permisos.validar && (
        <Link
          to={rutaValidacionRegistro(registro.id)}
          aria-label={`Revisar el registro de ${nombre}`}
          className={`${clasesBoton('secundario')} whitespace-nowrap`}
        >
          <FileSearch aria-hidden="true" className="size-4 shrink-0" />
          Revisar
        </Link>
      )}
    </div>
  )
}
