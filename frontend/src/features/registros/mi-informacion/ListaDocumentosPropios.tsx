import { CircleDashed } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { etiquetaEstadoDocumento } from '@/features/registros/documentos/etiquetas'
import type { DocumentosPropios, RequisitoPropio } from '@/features/registros/mi-informacion/tipos'
import { formatearFechaHora } from '@/features/temporadas/fechas'

/** Un requisito está completo con todos sus documentos cargados, igual que `completo` (DEC-141). */
function estaCompleto({ faltan }: RequisitoPropio): boolean {
  return faltan === 0
}

/**
 * Documentos del registro en solo lectura: estado, observaciones y faltantes. No ofrece ver ni
 * descargar: el contrato no trae datos del archivo y la consulta propia no expone enlaces (RNF-018).
 * No reutiliza `TarjetaRequisito` porque esa tarjeta necesita `tipoMime` y `tamanoBytes`, que este
 * contrato no trae (DEC-150).
 *
 * Refs: RF-108, RF-144, RNF-018, DEC-141, DEC-150
 */
export function ListaDocumentosPropios({ documentos }: { documentos: DocumentosPropios }) {
  const { requisitos } = documentos
  const completos = documentos.completo
    ? requisitos.length
    : requisitos.filter(estaCompleto).length

  return (
    <div className="flex flex-col gap-2">
      <p className="text-body-sm text-text-muted">
        <span className="font-mono tabular-nums text-text-main">{completos}</span> de{' '}
        <span className="font-mono tabular-nums text-text-main">{requisitos.length}</span> completos
      </p>

      {requisitos.length === 0 ? (
        <p className="text-body-md text-text-muted">Tu categoría no pide documentos.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-border rounded-lg border border-border">
          {requisitos.map((requisito) => (
            <li key={requisito.tipoDocumento.id} className="flex flex-col gap-2 px-3 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <p className="min-w-0 break-words text-label-md text-text-main">
                  {requisito.tipoDocumento.nombre}
                </p>
                {requisito.esDemo && <InsigniaNoOficial />}
              </div>

              {requisito.documentos.map((documento) => {
                const estado = etiquetaEstadoDocumento[documento.estadoValidacion]

                return (
                  <div key={documento.id} className="flex flex-col gap-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Insignia variante={estado.variante} icono={estado.icono}>
                        {estado.texto}
                      </Insignia>
                      <span className="text-body-sm text-text-muted">
                        Cargado el {formatearFechaHora(documento.cargadoEn)}
                      </span>
                    </div>
                    {documento.observaciones && (
                      <p
                        className={[
                          'whitespace-pre-line break-words text-body-md',
                          documento.estadoValidacion === 'RECHAZADO'
                            ? 'text-error-strong'
                            : 'text-text-main',
                        ].join(' ')}
                      >
                        <span className="font-medium">
                          {documento.estadoValidacion === 'RECHAZADO'
                            ? 'Motivo del rechazo: '
                            : 'Observaciones: '}
                        </span>
                        {documento.observaciones}
                      </p>
                    )}
                  </div>
                )
              })}

              {requisito.faltan > 0 && (
                <p className="flex items-center gap-2 text-body-md text-warning-strong">
                  <CircleDashed aria-hidden="true" className="size-4 shrink-0" />
                  {requisito.faltan === 1 ? 'Falta 1' : `Faltan ${requisito.faltan}`}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
