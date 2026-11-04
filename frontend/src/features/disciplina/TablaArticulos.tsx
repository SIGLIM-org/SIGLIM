import { Pencil } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import {
  etiquetaEfectoCorta,
  etiquetaSujeto,
  etiquetaTipoSancionCorta,
  valorSancion,
} from '@/features/disciplina/etiquetas'
import { EtiquetaTarjeta } from '@/features/disciplina/EtiquetaTarjeta'
import type { Articulo } from '@/features/disciplina/tipos'

type Props = {
  articulos: readonly Articulo[]
  onEditar: (articulo: Articulo) => void
}

/**
 * Tabla de artículos del reglamento (F-32, RF-129). La sanción muestra solo el
 * valor que corresponde a su tipo (DEC-30). Bajo `xl` cada fila se apila como
 * tarjeta; los roles explícitos conservan la semántica de tabla (RNF-008).
 */
export function TablaArticulos({ articulos, onEditar }: Props) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-surface">
      <table className="w-full border-collapse text-left">
        <caption className="sr-only">
          Artículos del reglamento con su sanción, sujeto y efecto
        </caption>
        <thead className="hidden border-b-2 border-border bg-background xl:table-header-group">
          <tr>
            <th scope="col" className="px-4 py-3 text-label-sm text-text-muted xl:px-3">
              Artículo
            </th>
            <th scope="col" className="px-4 py-3 text-label-sm text-text-muted xl:px-3">
              Sanción
            </th>
            <th scope="col" className="px-4 py-3 text-label-sm text-text-muted xl:px-3">
              Aplica a
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
          {articulos.map((articulo) => {
            const valor = valorSancion(articulo)

            return (
              <tr
                key={articulo.id}
                role="row"
                className="block border-b border-border px-4 py-3 transition-colors last:border-b-0 hover:bg-background xl:table-row xl:px-0 xl:py-0"
              >
                <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-mono-data font-bold text-primary">
                      {articulo.numero}
                    </span>
                    {articulo.esDemo && <InsigniaNoOficial />}
                  </span>
                  <span
                    className="block text-body-md text-text-main xl:line-clamp-2"
                    title={articulo.descripcion}
                  >
                    {articulo.descripcion}
                  </span>
                </td>
                <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3">
                  <EtiquetaTarjeta>Sanción</EtiquetaTarjeta>
                  <span className="block text-body-md text-text-main">
                    {etiquetaTipoSancionCorta[articulo.tipoSancion]}
                  </span>
                  {valor && (
                    <span className="block text-body-sm text-text-muted">{valor}</span>
                  )}
                  {articulo.tipoSancion === 'BAJA' && articulo.otorgaDefaultRestantes && (
                    <span className="block text-body-sm text-text-muted">
                      Otorga los partidos restantes al rival
                    </span>
                  )}
                  {articulo.aplicacionAutomatica && (
                    <span className="block text-body-sm text-text-muted">Automática</span>
                  )}
                </td>
                <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3">
                  <EtiquetaTarjeta>Aplica a</EtiquetaTarjeta>
                  <span className="block text-body-md text-text-main">
                    {etiquetaSujeto[articulo.sujeto]}
                  </span>
                  <span className="block text-body-sm text-text-muted">
                    {etiquetaEfectoCorta[articulo.efecto]}
                  </span>
                </td>
                <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3">
                  <EtiquetaTarjeta>Estado</EtiquetaTarjeta>
                  <Insignia variante={articulo.activo ? 'exito' : 'neutra'}>
                    {articulo.activo ? 'Activo' : 'Inactivo'}
                  </Insignia>
                </td>
                <td role="cell" className="block py-1 xl:table-cell xl:px-3 xl:py-3 xl:text-right">
                  <Button
                    variante="secundario"
                    icono={Pencil}
                    onClick={() => onEditar(articulo)}
                    aria-label={`Editar ${articulo.numero}`}
                    className="w-full xl:w-auto"
                  >
                    Editar
                  </Button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
