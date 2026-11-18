import { FileUp, FileSearch, History, UserMinus } from 'lucide-react'
import { Link } from 'react-router'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { rutaDocumentosRegistro, rutaValidacionRegistro } from '@/features/registros/rutas'
import type { RegistroJugadorFila } from '@/features/registros/tipos'

export type PermisosAccionesPlantilla = {
  /** `registros.documento:CREAR`. */
  cargar: boolean
  /** `registros.registro:VALIDAR`. */
  validar: boolean
  /** `registros.registro:EDITAR` (DEC-119, DEC-125). */
  darBaja: boolean
}

type Props = {
  registro: RegistroJugadorFila
  permisos: PermisosAccionesPlantilla
  onDarDeBaja: (registro: RegistroJugadorFila) => void
  /** Abre el historial del registro (B-07.2). */
  onVerHistorial: (registro: RegistroJugadorFila) => void
}

function accionesPendiente(registro: RegistroJugadorFila, permisos: PermisosAccionesPlantilla) {
  return registro.estado === 'PENDIENTE_VALIDACION' && (permisos.cargar || permisos.validar)
}

function puedeDarDeBaja(registro: RegistroJugadorFila, permisos: PermisosAccionesPlantilla) {
  return (
    registro.estado === 'ACTIVO' && permisos.darBaja && registro.temporada.estado !== 'CERRADA'
  )
}

/**
 * Acciones de una fila de la plantilla. En `PENDIENTE_VALIDACION` (DEC-111, DEC-117): «Cargar
 * documentos» con `registros.documento:CREAR` y «Revisar» con `registros.registro:VALIDAR`, como
 * enlaces. En `ACTIVO` con la temporada no `CERRADA` (DEC-125): «Dar de baja» con
 * `registros.registro:EDITAR`, un botón que abre el modal de la plantilla. En cualquier estado,
 * «Ver historial» (B-07.2, RF-018): basta `registros.registro:CONSULTAR`, que la plantilla ya exige
 * (DEC-116). Todos llevan el nombre del jugador en su nombre accesible.
 */
export function AccionesRegistroPlantilla({ registro, permisos, onDarDeBaja, onVerHistorial }: Props) {
  const nombre = registro.persona.nombreCompleto
  const pendiente = accionesPendiente(registro, permisos)

  return (
    <div className="flex flex-wrap gap-2 lg:justify-end">
      {pendiente && permisos.cargar && (
        <Link
          to={rutaDocumentosRegistro(registro.id)}
          aria-label={`Cargar documentos de ${nombre}`}
          className={`${clasesBoton('secundario')} whitespace-nowrap`}
        >
          <FileUp aria-hidden="true" className="size-4 shrink-0" />
          Cargar documentos
        </Link>
      )}
      {pendiente && permisos.validar && (
        <Link
          to={rutaValidacionRegistro(registro.id)}
          aria-label={`Revisar el registro de ${nombre}`}
          className={`${clasesBoton('secundario')} whitespace-nowrap`}
        >
          <FileSearch aria-hidden="true" className="size-4 shrink-0" />
          Revisar
        </Link>
      )}
      {puedeDarDeBaja(registro, permisos) && (
        <button
          type="button"
          aria-label={`Dar de baja a ${nombre}`}
          onClick={() => onDarDeBaja(registro)}
          className={`${clasesBoton('secundario')} whitespace-nowrap`}
        >
          <UserMinus aria-hidden="true" className="size-4 shrink-0" />
          Dar de baja
        </button>
      )}
      <button
        type="button"
        aria-label={`Ver historial de ${nombre}`}
        onClick={() => onVerHistorial(registro)}
        className={`${clasesBoton('secundario')} whitespace-nowrap`}
      >
        <History aria-hidden="true" className="size-4 shrink-0" />
        Ver historial
      </button>
    </div>
  )
}
