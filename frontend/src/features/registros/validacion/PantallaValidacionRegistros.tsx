import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { FileCheck2, UsersRound } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useClubesActivosTodos } from '@/features/organizacion/api'
import {
  TAMANO_REGISTROS,
  useRegistro,
  useRegistros,
  useRequisitosRegistro,
} from '@/features/registros/api'
import type { FiltroEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import { usePermisosRegistros, VALIDAR_REGISTRO } from '@/features/registros/permisos'
import { RUTA_VALIDACION_REGISTROS } from '@/features/registros/rutas'
import { BandejaRegistros } from '@/features/registros/validacion/BandejaRegistros'
import { DetalleValidacionRegistro } from '@/features/registros/validacion/DetalleValidacionRegistro'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Bandeja de validación de registros (F-19, H-10.3). Solo con `registros.registro:VALIDAR`; sin él,
 * la 403 sin pedir datos (DEC-111). El backend vuelve a autorizar cada validación (DEC-97).
 */
export function PantallaValidacionRegistros() {
  return (
    <ConPermiso permiso={VALIDAR_REGISTRO} alternativa={<PaginaSinPermiso rutaInicio="/app" />}>
      <PaginaValidacionRegistros />
    </ConPermiso>
  )
}

/**
 * Lista y detalle con la selección en la URL (`/registros/:registroId?`), como afiliaciones: el
 * detalle se puede enlazar y en móvil «Volver a la bandeja» regresa a la lista.
 *
 * Refs: RF-017, RF-109, VAL-001, DEC-109, DEC-110, DEC-111, DEC-114
 */
function PaginaValidacionRegistros() {
  const { registroId } = useParams()
  const navegar = useNavigate()
  const { ver, validarDocumento } = usePermisosRegistros()
  const [filtro, setFiltro] = useState<FiltroEstadoRegistro>('PENDIENTE_VALIDACION')
  const [clubId, setClubId] = useState('')
  const [pagina, setPagina] = useState(0)
  const [mensaje, setMensaje] = useState<string | null>(null)

  const clubes = useClubesActivosTodos()
  const lista = useRegistros({
    estado: filtro === 'TODOS' ? undefined : filtro,
    clubId: clubId || undefined,
    orden: 'creadoEn,asc',
    pagina,
    tamano: TAMANO_REGISTROS,
  })
  const detalle = useRegistro(registroId)
  const requisitos = useRequisitosRegistro(registroId)

  if (esSinPermiso(lista.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const volver = () => {
    setMensaje(null)
    void navegar(RUTA_VALIDACION_REGISTROS)
  }

  const cambiarFiltros = (cambio: () => void) => {
    cambio()
    setPagina(0)
    volver()
  }

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
          Validación de registros
        </h1>
        <p className="mt-1 max-w-2xl text-body-md text-text-muted">
          Revisa los documentos de cada jugador, apruébalos o recházalos con motivo y activa el
          registro cuando todo esté aprobado.
        </p>
      </header>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <FileCheck2 aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[22rem_minmax(0,1fr)] lg:items-start">
        <div className={registroId ? 'hidden min-w-0 lg:block' : 'min-w-0'}>
          <BandejaRegistros
            clubes={clubes.data ?? []}
            clubesCargando={clubes.isPending}
            clubId={clubId}
            filtro={filtro}
            pagina={pagina}
            datos={lista.data}
            cargando={lista.isFetching}
            error={lista.error}
            seleccionadoId={registroId}
            onClub={(nuevo) => cambiarFiltros(() => setClubId(nuevo))}
            onFiltro={(nuevo) => cambiarFiltros(() => setFiltro(nuevo))}
            onPagina={(nueva) => {
              setPagina(nueva)
              volver()
            }}
            onSeleccionar={(registro) => {
              setMensaje(null)
              void navegar(`${RUTA_VALIDACION_REGISTROS}/${registro.id}`)
            }}
            onReintentar={() => void lista.refetch()}
          />
        </div>

        <div className={registroId ? 'min-w-0' : 'hidden min-w-0 lg:block'}>
          {!registroId ? (
            <EstadoVacio
              icono={UsersRound}
              titulo="Elige un registro"
              descripcion="Selecciona una fila de la bandeja para revisar sus documentos y validarlo."
            />
          ) : detalle.isError ? (
            <ErrorConsulta
              error={detalle.error}
              onReintentar={() => void detalle.refetch()}
              accion={
                <Button variante="secundario" onClick={volver}>
                  Volver a la bandeja
                </Button>
              }
            />
          ) : !detalle.data ? (
            <EstadoCarga etiqueta="Cargando el registro…" filas={4} columnas={2} />
          ) : (
            <DetalleValidacionRegistro
              key={detalle.data.id}
              registro={detalle.data}
              requisitos={requisitos.data}
              requisitosError={requisitos.error}
              onReintentarRequisitos={() => void requisitos.refetch()}
              puedeVer={ver}
              puedeValidarDocumentos={validarDocumento}
              onVolver={volver}
              onResuelto={setMensaje}
            />
          )}
        </div>
      </div>
    </div>
  )
}
