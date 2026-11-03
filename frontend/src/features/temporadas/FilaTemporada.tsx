import { useId } from 'react'
import { Lock, LockKeyhole, Pencil, Play } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import { formatearFecha, formatearPeriodo } from '@/features/temporadas/fechas'
import type { Temporada } from '@/features/temporadas/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'

type Props = {
  temporada: Temporada
  /** La activación de esta fila está en curso. */
  activando: boolean
  /** Hay una acción en curso: se evita disparar otra. */
  accionesDeshabilitadas: boolean
  onEditar: (temporada: Temporada) => void
  onActivar: (temporada: Temporada) => void
  onCerrar: (temporada: Temporada) => void
}

const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

function cantidad(total: number, singular: string, plural: string): string {
  return `${total} ${total === 1 ? singular : plural}`
}

/**
 * Fila de la tabla de temporadas (F-10, H-04.2). Bajo `lg` se apila como tarjeta con sus
 * etiquetas visibles (RNF-007). Las acciones dependen del estado y del permiso de edición; el
 * botón de activar se deshabilita con la causa cuando la lista de verificación no está completa
 * (RF-100, RN-058).
 */
export function FilaTemporada({
  temporada,
  activando,
  accionesDeshabilitadas,
  onEditar,
  onActivar,
  onCerrar,
}: Props) {
  const idAyuda = useId()
  const estado = etiquetaEstadoTemporada[temporada.estado]
  const requisitos = temporada.activacion?.requisitos ?? []
  const faltantes = requisitos.filter((requisito) => !requisito.cumple)
  const puedeActivarse = temporada.activacion?.puedeActivarse ?? false
  const esConfiguracion = temporada.estado === 'CONFIGURACION'
  const esActiva = temporada.estado === 'ACTIVA'

  return (
    <tr
      role="row"
      className="block border-b border-border p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:border-b lg:p-0"
    >
      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Temporada</span>
        <span className="block break-words text-headline-sm text-text-main">{temporada.nombre}</span>
        <span className="block text-body-sm text-text-muted">
          Creada el {formatearFecha(temporada.creadoEn.slice(0, 10))}
        </span>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Periodo</span>
        <span className="block font-mono text-mono-data text-text-main">
          {formatearPeriodo(temporada.fechaInicio, temporada.fechaFin)}
        </span>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Estado</span>
        <Insignia variante={estado.variante} icono={estado.icono}>
          {estado.texto}
        </Insignia>
      </td>

      <td role="cell" className="block py-1 lg:table-cell lg:px-4 lg:py-3">
        <span className={CLASE_ETIQUETA}>Estructura</span>
        <span className="block text-body-md text-text-main">
          {cantidad(temporada.categoriasAsignadas, 'categoría', 'categorías')}
        </span>
        <span className="block text-body-sm text-text-muted">
          {cantidad(temporada.gruposCreados, 'grupo', 'grupos')}
        </span>
        <span className="block font-mono text-mono-data text-text-muted">
          {temporada.versionParametros !== undefined
            ? `Parámetros v${temporada.versionParametros}`
            : 'Sin parámetros'}
        </span>
      </td>

      <td role="cell" className="block pt-3 lg:table-cell lg:px-4 lg:py-3 lg:text-right">
        <span className={CLASE_ETIQUETA}>Acciones</span>
        {temporada.estado === 'CERRADA' ? (
          <span className="inline-flex items-center gap-1.5 text-body-sm text-text-muted">
            <Lock aria-hidden="true" className="size-4 shrink-0" />
            Solo lectura
          </span>
        ) : (
          <ConPermiso
            permiso="temporadas.temporada:EDITAR"
            alternativa={<span className="text-body-sm text-text-muted">Solo consulta</span>}
          >
            <div className="flex flex-col items-start gap-2 lg:items-end">
              {esConfiguracion && (
                <>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variante="secundario"
                      icono={Pencil}
                      disabled={accionesDeshabilitadas}
                      onClick={() => onEditar(temporada)}
                    >
                      Editar
                    </Button>
                    <Button
                      icono={Play}
                      disabled={!puedeActivarse || accionesDeshabilitadas}
                      cargando={activando}
                      aria-describedby={!puedeActivarse ? idAyuda : undefined}
                      onClick={() => onActivar(temporada)}
                    >
                      Activar
                    </Button>
                  </div>
                  {!puedeActivarse && (
                    <div id={idAyuda} className="max-w-xs text-left lg:text-right">
                      <p className="text-body-sm text-text-muted">No se puede activar todavía:</p>
                      {faltantes.length > 0 ? (
                        <ul className="mt-1 list-disc space-y-0.5 pl-4 text-left text-body-sm text-text-muted">
                          {faltantes.map((requisito) => (
                            <li key={requisito.codigo}>{requisito.descripcion}</li>
                          ))}
                        </ul>
                      ) : (
                        <p className="mt-1 text-body-sm text-text-muted">
                          No se pudo verificar la lista de requisitos.
                        </p>
                      )}
                      <a
                        href={`#panel-${temporada.id}`}
                        className="mt-1 inline-flex min-h-11 items-center text-body-sm text-primary underline-offset-2 hover:underline"
                      >
                        Ver lista de verificación
                      </a>
                    </div>
                  )}
                </>
              )}
              {esActiva && (
                <div className="flex flex-wrap gap-2">
                  <Button
                    variante="secundario"
                    icono={Pencil}
                    disabled={accionesDeshabilitadas}
                    onClick={() => onEditar(temporada)}
                  >
                    Editar
                  </Button>
                  <Button
                    variante="destructivo"
                    icono={LockKeyhole}
                    disabled={accionesDeshabilitadas}
                    onClick={() => onCerrar(temporada)}
                  >
                    Cerrar temporada
                  </Button>
                </div>
              )}
            </div>
          </ConPermiso>
        )}
      </td>
    </tr>
  )
}
