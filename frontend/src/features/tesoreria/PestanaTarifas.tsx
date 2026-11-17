import { useId, useState } from 'react'
import { CalendarX2, CircleCheck, Layers, Lock } from 'lucide-react'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import { useContextoTarifas, useTarifas } from '@/features/tesoreria/api'
import { FilaTarifas } from '@/features/tesoreria/FilaTarifas'
import { CONCEPTOS_TARIFA, indexarTarifas, temporadaInicial } from '@/features/tesoreria/tarifas'
import { reintentarConsulta } from '@/lib/api/errores'

/**
 * Pestaña «Tarifas» de F-33 (B-29.2, RF-135) [PROPUESTA: no hay pantalla aprobada]: tabla
 * categoría × concepto de la temporada elegida. Los montos son [DEMO] mientras PEN-042 siga
 * abierto y se rotulan «No oficial»; en una temporada cerrada la tabla es de solo lectura
 * (RF-003). Solo se monta con `tesoreria.tarifa:EDITAR`; el backend vuelve a autorizar.
 */
export function PestanaTarifas() {
  const id = useId()
  const contexto = useContextoTarifas(true)
  const [elegida, setElegida] = useState('')
  const [mensaje, setMensaje] = useState<string | null>(null)

  const temporadas = contexto.data?.temporadas ?? []
  const temporadaId = elegida || temporadaInicial(temporadas)
  const temporada = temporadas.find(({ id: idTemporada }) => idTemporada === temporadaId)
  const tarifas = useTarifas(temporada?.id ?? '')

  if (contexto.isPending) {
    return <EstadoCarga etiqueta="Cargando temporadas…" filas={4} columnas={3} />
  }

  if (contexto.isError) {
    return (
      <ErrorConsulta
        error={contexto.error}
        onReintentar={
          reintentarConsulta(0, contexto.error) ? () => void contexto.refetch() : undefined
        }
      />
    )
  }

  if (!temporada) {
    return (
      <EstadoVacio
        icono={CalendarX2}
        titulo="Aún no hay temporadas"
        descripcion="Las tarifas se configuran por temporada. Pide a Administración que registre la temporada y sus categorías."
      />
    )
  }

  const cerrada = temporada.estado === 'CERRADA'
  const estado = etiquetaEstadoTemporada[temporada.estado]
  const indice = indexarTarifas(tarifas.data ?? [])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="sm:w-80">
          <CampoSeleccion
            id={`${id}-temporada`}
            etiqueta="Temporada"
            value={temporada.id}
            onChange={(evento) => {
              setElegida(evento.target.value)
              setMensaje(null)
            }}
          >
            {temporadas.map((opcion) => (
              <option key={opcion.id} value={opcion.id}>
                {opcion.nombre} · {etiquetaEstadoTemporada[opcion.estado].texto}
              </option>
            ))}
          </CampoSeleccion>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Insignia variante={estado.variante} icono={estado.icono}>
            {estado.texto}
          </Insignia>
          <InsigniaNoOficial />
        </div>
      </div>

      <p className="max-w-3xl text-body-md text-text-muted">
        Montos de demostración: las tarifas reales de arbitraje y cancha están pendientes de
        aprobación por la Liga. Cada categoría tiene una tarifa por concepto.
      </p>

      {cerrada && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-border bg-surface p-3 text-body-md text-text-main"
        >
          <Lock aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-text-muted" />
          La temporada «{temporada.nombre}» está cerrada: sus tarifas son de solo lectura.
        </p>
      )}

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      {temporada.categorias.length === 0 ? (
        <EstadoVacio
          icono={Layers}
          titulo="Esta temporada no tiene categorías"
          descripcion="Las tarifas se capturan por categoría. Pide a Administración que asigne las categorías de la temporada."
        />
      ) : tarifas.isPending ? (
        <EstadoCarga etiqueta="Cargando tarifas…" filas={temporada.categorias.length} columnas={3} />
      ) : tarifas.isError ? (
        <ErrorConsulta
          error={tarifas.error}
          onReintentar={
            reintentarConsulta(0, tarifas.error) ? () => void tarifas.refetch() : undefined
          }
        />
      ) : (
        <div className="overflow-hidden rounded-lg border border-border bg-surface">
          <table className="w-full border-collapse max-sm:block">
            <caption className="sr-only">
              Tarifas de arbitraje y cancha por categoría de {temporada.nombre}
            </caption>
            <thead className="max-sm:hidden">
              <tr className="border-b-2 border-border bg-background">
                <th scope="col" className="px-4 py-2 text-left text-label-sm uppercase text-text-muted">
                  Categoría
                </th>
                {CONCEPTOS_TARIFA.map(({ valor, texto }) => (
                  <th
                    key={valor}
                    scope="col"
                    className="w-52 px-4 py-2 text-right text-label-sm uppercase text-text-muted"
                  >
                    {texto}
                  </th>
                ))}
                {!cerrada && (
                  <th scope="col" className="px-4 py-2 text-right text-label-sm uppercase text-text-muted">
                    <span className="sr-only">Acciones</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="max-sm:block">
              {temporada.categorias.map((categoria) => (
                <FilaTarifas
                  key={`${temporada.id}-${categoria.id}`}
                  temporadaId={temporada.id}
                  categoria={categoria}
                  tarifas={indice.get(categoria.id) ?? {}}
                  soloLectura={cerrada}
                  onGuardada={setMensaje}
                  onTemporadaCerrada={() => void contexto.refetch()}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
