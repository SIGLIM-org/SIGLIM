import { useId, type ReactNode } from 'react'
import { CircleDashed, Eye, FileImage, FileText, Replace, TriangleAlert, Upload } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { esMimeImagen, tamanoLegible } from '@/features/registros/documentos/archivoDocumento'
import { etiquetaEstadoDocumento } from '@/features/registros/documentos/etiquetas'
import type { DocumentoRegistro, Requisito } from '@/features/registros/tipos'
import { TEXTO_FALTANTE, type MotivoFaltante } from '@/features/registros/validacion/faltantes'
import { formatearFechaHora } from '@/features/temporadas/fechas'

type Props = {
  requisito: Requisito
  /** Muestra «Cargar» y «Reemplazar» (registro pendiente y temporada abierta, DEC-112). */
  editable: boolean
  /** Muestra «Ver» (`registros.documento:CONSULTAR`). */
  puedeVer: boolean
  /** En la bandeja: por qué el requisito aún no cumple VAL-001 (DEC-114). */
  faltante?: MotivoFaltante
  /** Lugar con el panel de carga abierto: `null` = un lugar libre; un id = reemplazo de ese documento. */
  cargaAbierta?: { documentoId: string | null } | null
  panelCarga?: ReactNode
  onCargar?: () => void
  onReemplazar?: (documento: DocumentoRegistro) => void
  onVer: (documento: DocumentoRegistro) => void
}

/**
 * Requisito de documento con sus lugares hasta `cantidad` (DEC-112). Cada documento vigente
 * muestra estado, tamaño, fecha de carga y observaciones; un lugar libre ofrece «Cargar», un
 * documento PENDIENTE o RECHAZADO «Reemplazar», y uno APROBADO ninguna acción de carga. Un rechazado
 * ocupa su lugar hasta que se reemplaza, así que los libres son `cantidad − documentos`.
 *
 * Refs: RF-108, RF-109, DEC-92, DEC-93, DEC-112
 */
export function TarjetaRequisito({
  requisito,
  editable,
  puedeVer,
  faltante,
  cargaAbierta,
  panelCarga,
  onCargar,
  onReemplazar,
  onVer,
}: Props) {
  const id = useId()
  const { tipoDocumento, cantidad, documentos, aprobados } = requisito
  const libres = Math.max(0, cantidad - documentos.length)
  const nombre = tipoDocumento.nombre

  return (
    <section
      aria-labelledby={`${id}-titulo`}
      className="@container flex min-w-0 flex-col rounded-lg border border-border bg-surface"
    >
      <header className="flex flex-col gap-2 border-b border-border px-4 py-3">
        <div className="flex flex-wrap items-center gap-2">
          <h3 id={`${id}-titulo`} className="min-w-0 break-words text-headline-sm text-text-main">
            {nombre}
          </h3>
          {requisito.esDemo && <InsigniaNoOficial />}
          {faltante && (
            <Insignia variante={faltante === 'cargar' ? 'error' : 'advertencia'} icono={TriangleAlert}>
              {TEXTO_FALTANTE[faltante]}
            </Insignia>
          )}
        </div>
        <p className="text-body-sm text-text-muted">
          {cantidad === 1 ? 'Se pide 1 archivo' : `Se piden ${cantidad} archivos`}
          {requisito.soloMenores && ' · solo para menores de edad'}
          {' · '}
          <span className="font-mono tabular-nums text-text-main">
            {aprobados}/{cantidad}
          </span>{' '}
          {aprobados === 1 ? 'aprobado' : 'aprobados'}
        </p>
      </header>

      <ul className="flex flex-col divide-y divide-border">
        {documentos.map((documento) => {
          const estado = etiquetaEstadoDocumento[documento.estadoValidacion]
          const Icono = esMimeImagen(documento.tipoMime) ? FileImage : FileText
          const reemplazable = editable && documento.estadoValidacion !== 'APROBADO'
          const abierto = cargaAbierta?.documentoId === documento.id

          return (
            <li key={documento.id} className="flex flex-col gap-3 px-4 py-3">
              <div className="flex flex-col gap-3 @lg:flex-row @lg:items-start @lg:justify-between">
                <div className="flex min-w-0 items-start gap-3">
                  <Icono aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-text-muted" />
                  <div className="flex min-w-0 flex-col gap-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Insignia variante={estado.variante} icono={estado.icono}>
                        {estado.texto}
                      </Insignia>
                      <span className="font-mono text-mono-data tabular-nums text-text-muted">
                        {tamanoLegible(documento.tamanoBytes)}
                      </span>
                    </div>
                    <p className="text-body-sm text-text-muted">
                      Cargado el {formatearFechaHora(documento.cargadoEn)}
                    </p>
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
                </div>

                <div className="flex shrink-0 flex-wrap gap-2">
                  {puedeVer && (
                    <Button
                      variante="secundario"
                      icono={Eye}
                      aria-label={`Ver ${nombre}`}
                      onClick={() => onVer(documento)}
                    >
                      Ver
                    </Button>
                  )}
                  {reemplazable && !abierto && onReemplazar && (
                    <Button
                      variante="secundario"
                      icono={Replace}
                      aria-label={`Reemplazar ${nombre}`}
                      disabled={Boolean(cargaAbierta)}
                      onClick={() => onReemplazar(documento)}
                    >
                      Reemplazar
                    </Button>
                  )}
                </div>
              </div>
              {abierto && panelCarga}
            </li>
          )
        })}

        {Array.from({ length: libres }, (_, indice) => {
          const abierto = indice === 0 && cargaAbierta?.documentoId === null

          return (
            <li key={`libre-${indice}`} className="flex flex-col gap-3 px-4 py-3">
              {abierto ? (
                panelCarga
              ) : (
                <div className="flex flex-col gap-3 @lg:flex-row @lg:items-center @lg:justify-between">
                  <p className="flex items-center gap-3 text-body-md text-text-muted">
                    <CircleDashed aria-hidden="true" className="size-5 shrink-0" />
                    Sin cargar
                  </p>
                  {editable && onCargar && (
                    <Button
                      icono={Upload}
                      aria-label={`Cargar ${nombre}`}
                      disabled={Boolean(cargaAbierta)}
                      onClick={onCargar}
                    >
                      Cargar
                    </Button>
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
