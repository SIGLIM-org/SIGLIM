import { Pencil } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { EtiquetaTarjeta } from '@/features/disciplina/EtiquetaTarjeta'
import type { TipoIncidencia } from '@/features/disciplina/tipos'

type Props = {
  tipos: readonly TipoIncidencia[]
  onEditar: (tipo: TipoIncidencia) => void
}

/**
 * Tabla de tipos de incidencia (F-32, RF-128). Bajo `xl` cada fila se apila como
 * tarjeta; los roles explícitos conservan la semántica de tabla (RNF-008).
 */
export function TablaTiposIncidencia({ tipos, onEditar }: Props) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-surface">
      <table className="w-full border-collapse text-left">
        <caption className="sr-only">Tipos de incidencia del catálogo disciplinario</caption>
        <thead className="hidden border-b-2 border-border bg-background xl:table-header-group">
          <tr>
            <th scope="col" className="px-4 py-3 text-label-sm text-text-muted xl:px-3">
              Tipo de incidencia
            </th>
            <th scope="col" className="px-4 py-3 text-label-sm text-text-muted xl:px-3">
              Abre expediente
            </th>
            <th scope="col" className="px-4 py-3 text-label-sm text-text-muted xl:px-3">
              Estado
            </th>
            <th scope="col" className="px-4 py-3 text-right text-label-sm text-text-muted xl:px-3">
              Acción
            </th>
          </tr>
        </thead>
        <tbody>
          {tipos.map((tipo) => (
            <tr
              key={tipo.id}
              role="row"
              className="block border-b border-border px-4 py-3 transition-colors last:border-b-0 hover:bg-background xl:table-row xl:px-0 xl:py-0"
            >
              <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3">
                <span className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-mono-data font-bold text-primary">
                    {tipo.codigo}
                  </span>
                  {tipo.esDemo && <InsigniaNoOficial />}
                </span>
                <span className="block text-body-md text-text-main">{tipo.nombre}</span>
              </td>
              <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3">
                <EtiquetaTarjeta>Abre expediente</EtiquetaTarjeta>
                <span className="text-body-md text-text-main">
                  {tipo.abreExpediente ? 'Sí' : 'No'}
                </span>
              </td>
              <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3">
                <EtiquetaTarjeta>Estado</EtiquetaTarjeta>
                <Insignia variante={tipo.activo ? 'exito' : 'neutra'}>
                  {tipo.activo ? 'Activo' : 'Inactivo'}
                </Insignia>
              </td>
              <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3 xl:text-right">
                <Button
                  variante="secundario"
                  icono={Pencil}
                  onClick={() => onEditar(tipo)}
                  aria-label={`Editar ${tipo.nombre}`}
                  className="w-full xl:w-auto"
                >
                  Editar
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
