import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { useCategoriasTemporada } from '@/features/temporadas/api'
import type { Referencia } from '@/features/temporadas/tipos'

type Props = {
  id: string
  temporadaId: string
  /** Se muestra aunque la lista aún no cargue, para no perder el valor. */
  grupoActual: Referencia
  value: string
  error?: string
  deshabilitado: boolean
  onChange: (grupoId: string) => void
}

/** Grupos de una misma temporada agrupados por categoría, para mover un equipo (RN-007). */
export function SelectorGrupoTemporada({ id, temporadaId, grupoActual, value, error, deshabilitado, onChange }: Props) {
  const consulta = useCategoriasTemporada(temporadaId)
  const categorias = consulta.data ?? []
  const actualEnLista = categorias.some(({ grupos }) => grupos.some(({ id: grupo }) => grupo === grupoActual.id))

  return (
    <CampoSeleccion
      id={id}
      etiqueta="Grupo"
      disabled={deshabilitado || consulta.isPending}
      error={error ?? (consulta.isError ? 'No se pudieron cargar los grupos de la temporada.' : undefined)}
      ayuda="Solo se cambia mientras la temporada está en configuración."
      value={value}
      onChange={(evento) => onChange(evento.target.value)}
    >
      {!actualEnLista && <option value={grupoActual.id}>{grupoActual.nombre}</option>}
      {categorias.map((categoria) => (
        <optgroup key={categoria.id} label={categoria.categoria.nombre}>
          {categoria.grupos.map((grupo) => (
            <option key={grupo.id} value={grupo.id}>
              {categoria.categoria.nombre} · Grupo {grupo.nombre}
            </option>
          ))}
        </optgroup>
      ))}
    </CampoSeleccion>
  )
}
