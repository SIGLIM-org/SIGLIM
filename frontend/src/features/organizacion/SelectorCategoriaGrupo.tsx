import { Link } from 'react-router'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { useCategoriasTemporada } from '@/features/temporadas/api'
import { rutaEstructura } from '@/features/temporadas/pestanas'

type Props = {
  /** Se monta solo con temporada elegida: `useCategoriasTemporada` no admite un id vacío. */
  temporadaId: string
  idBase: string
  categoriaId: string
  grupoId: string
  errorCategoria?: string
  errorGrupo?: string
  deshabilitado: boolean
  onCambiarCategoria: (categoriaTemporadaId: string) => void
  onCambiarGrupo: (grupoId: string) => void
}

/** Categoría y grupo de la temporada en cascada (RF-004, RF-005); al cambiar la categoría el padre limpia el grupo. */
export function SelectorCategoriaGrupo({
  temporadaId,
  idBase,
  categoriaId,
  grupoId,
  errorCategoria,
  errorGrupo,
  deshabilitado,
  onCambiarCategoria,
  onCambiarGrupo,
}: Props) {
  const consulta = useCategoriasTemporada(temporadaId)
  const categorias = consulta.data ?? []
  const elegida = categorias.find(({ id }) => id === categoriaId)
  const sinCategorias = consulta.isSuccess && categorias.length === 0
  const sinGrupos = elegida !== undefined && elegida.grupos.length === 0

  if (consulta.isError) {
    return (
      <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
        No se pudieron cargar las categorías de la temporada.{' '}
        <button
          type="button"
          onClick={() => void consulta.refetch()}
          className="min-h-11 font-medium underline underline-offset-2"
        >
          Reintentar
        </button>
      </p>
    )
  }

  return (
    <>
      <CampoSeleccion
        id={`${idBase}-categoria`}
        etiqueta="Categoría"
        required
        disabled={deshabilitado || consulta.isPending || sinCategorias}
        error={errorCategoria}
        value={categoriaId}
        onChange={(evento) => onCambiarCategoria(evento.target.value)}
      >
        <option value="">{consulta.isPending ? 'Cargando categorías…' : 'Selecciona…'}</option>
        {categorias.map((categoria) => (
          <option key={categoria.id} value={categoria.id}>
            {categoria.categoria.nombre}
          </option>
        ))}
      </CampoSeleccion>
      {sinCategorias && (
        <p className="text-body-sm text-text-muted">
          Esta temporada aún no tiene categorías.{' '}
          <Link to={rutaEstructura(temporadaId, 'categorias')} className="text-primary underline underline-offset-2">
            Asígnalas en su estructura
          </Link>
          .
        </p>
      )}

      <CampoSeleccion
        id={`${idBase}-grupo`}
        etiqueta="Grupo"
        required
        disabled={deshabilitado || !elegida || sinGrupos}
        error={errorGrupo}
        value={grupoId}
        onChange={(evento) => onCambiarGrupo(evento.target.value)}
      >
        <option value="">{elegida ? 'Selecciona…' : 'Elige primero la categoría'}</option>
        {elegida?.grupos.map((grupo) => (
          <option key={grupo.id} value={grupo.id}>
            {grupo.nombre}
          </option>
        ))}
      </CampoSeleccion>
      {sinGrupos && (
        <p className="text-body-sm text-text-muted">
          Esta categoría aún no tiene grupos.{' '}
          <Link to={rutaEstructura(temporadaId, 'grupos')} className="text-primary underline underline-offset-2">
            Créalos en la estructura de la temporada
          </Link>
          .
        </p>
      )}
    </>
  )
}
