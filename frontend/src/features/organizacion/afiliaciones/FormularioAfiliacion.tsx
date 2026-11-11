import { useEffect, useId, useState, type FormEvent } from 'react'
import { CircleAlert, FileCheck2, ListChecks, Send, Shirt } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { useActualizarAfiliacion, useCrearAfiliacion } from '@/features/organizacion/afiliaciones/api'
import { etiquetaEstadoAfiliacion } from '@/features/organizacion/afiliaciones/etiquetas'
import type {
  Afiliacion,
  CategoriaContextoAfiliacion,
  TemporadaContextoAfiliacion,
} from '@/features/organizacion/afiliaciones/tipos'
import { detalleDeFallo, erroresDeCampos } from '@/features/organizacion/erroresFormulario'
import type { Club } from '@/features/organizacion/tipos'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import { ApiError } from '@/lib/api/ApiError'

/** Límite del campo `colores_uniforme` en la base (varchar 120). */
const MAXIMO_COLORES = 120

type Props = {
  clubes: readonly Club[]
  temporadas: readonly TemporadaContextoAfiliacion[]
  afiliacion?: Afiliacion
  /** Temporada que el usuario tenía elegida en la bandeja; puede ser una cerrada. */
  temporadaInicial?: string
  onGuardada: (afiliacion: Afiliacion) => void
  onCancelar: () => void
}

/**
 * Formulario del tarjetón (F-16, RF-101, DEC-09). Se divide en club y temporada, categorías y
 * colores; el resto de datos del tarjetón (nombre del club y directiva) se consultan en F-15 y no
 * se duplican aquí. Al guardar por primera vez, el backend asigna el folio y deja SOLICITADA.
 */
export function FormularioAfiliacion({
  clubes,
  temporadas,
  afiliacion,
  temporadaInicial,
  onGuardada,
  onCancelar,
}: Props) {
  const id = useId()
  const editando = Boolean(afiliacion)
  const abiertas = temporadas.filter(({ estado }) => estado !== 'CERRADA')
  const activa = abiertas.find(({ estado }) => estado === 'ACTIVA')
  const temporadaPorDefecto =
    abiertas.find(({ id: actual }) => actual === temporadaInicial)?.id ??
    activa?.id ??
    abiertas[0]?.id ??
    ''
  const [temporadaId, setTemporadaId] = useState(
    afiliacion?.temporada.id ?? temporadaPorDefecto,
  )
  const [clubId, setClubId] = useState(afiliacion?.club.id ?? clubes[0]?.id ?? '')
  const [colores, setColores] = useState(afiliacion?.coloresUniformes ?? '')
  const [categorias, setCategorias] = useState<string[]>(
    afiliacion?.categorias.map(({ id: categoriaId }) => categoriaId) ?? [],
  )
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const crear = useCrearAfiliacion()
  const actualizar = useActualizarAfiliacion(afiliacion?.id ?? '')
  const guardando = crear.isPending || actualizar.isPending

  // Si el padre termina de cargar clubes o temporadas después del montaje, se elige el primero.
  useEffect(() => {
    if (!afiliacion && !clubId && clubes[0]) {
      setClubId(clubes[0].id)
    }
  }, [afiliacion, clubId, clubes])

  useEffect(() => {
    if (!afiliacion && !temporadaId && temporadaPorDefecto) {
      setTemporadaId(temporadaPorDefecto)
    }
  }, [afiliacion, temporadaId, temporadaPorDefecto])

  const clubSeleccionado = afiliacion
    ? { id: afiliacion.club.id, nombre: afiliacion.club.nombre }
    : clubes.find(({ id: actual }) => actual === clubId)
  const temporadaSeleccionada = afiliacion
    ? afiliacion.temporada
    : temporadas.find(({ id: actual }) => actual === temporadaId)
  const temporadaDelContexto = temporadas.find(
    ({ id: actual }) => actual === (afiliacion?.temporada.id ?? temporadaId),
  )
  const categoriasDisponibles: readonly CategoriaOpcion[] =
    (temporadaDelContexto?.categorias ?? []).length > 0
      ? (temporadaDelContexto?.categorias ?? []).map(aOpcion)
      : (afiliacion?.categorias.map((categoria) => ({
          id: categoria.id,
          nombre: categoria.nombre,
          detalle: 'Categoría registrada en el tarjetón',
        })) ?? [])
  const sinCategorias = categoriasDisponibles.length === 0
  const temporadaCerrada = temporadaSeleccionada?.estado === 'CERRADA'
  const puedeEnviar =
    !guardando &&
    Boolean(clubSeleccionado) &&
    Boolean(temporadaId) &&
    !sinCategorias &&
    !temporadaCerrada

  const limpiarError = (campo: string) => {
    setErrores((actuales) => {
      if (!(campo in actuales)) {
        return actuales
      }

      const resto = { ...actuales }
      delete resto[campo]
      return resto
    })
  }

  const cambiarTemporada = (nueva: string) => {
    setTemporadaId(nueva)
    setCategorias([])
    limpiarError('temporadaId')
    limpiarError('categoriaIds')
  }

  const alternarCategoria = (categoriaId: string, marcada: boolean) => {
    setCategorias((actuales) =>
      marcada ? [...actuales, categoriaId] : actuales.filter((idCategoria) => idCategoria !== categoriaId),
    )
    limpiarError('categoriaIds')
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral(detalleDeFallo(error, 'No se pudo guardar el tarjetón. Intenta de nuevo.'))
      return
    }

    if (error.codigo === 'VAL-015') {
      setErrorGeneral(error.problema.detail)
      return
    }

    const campos = erroresDeCampos(error, [
      'clubId',
      'temporadaId',
      'coloresUniformes',
      'categoriaIds',
    ])

    if (error.codigo === 'REFERENCIA_INVALIDA' && Array.isArray(error.problema.extensiones?.categoriasInvalidas)) {
      campos.categoriaIds = 'Solo se inscriben categorías que participan en la temporada elegida.'
    }

    setErrores(campos)
    setErrorGeneral(error.problema.detail)
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados: Record<string, string> = {}
    const textoColores = colores.trim()

    if (!clubId && !afiliacion) {
      encontrados.clubId = 'Elige el club que se afilia.'
    }
    if (!temporadaId) {
      encontrados.temporadaId = 'Elige la temporada.'
    }
    if (!textoColores) {
      encontrados.coloresUniformes = 'Describe los colores del uniforme.'
    }
    if (categorias.length === 0) {
      encontrados.categoriaIds = 'Selecciona al menos una categoría.'
    }

    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    const peticion = { coloresUniformes: textoColores, categoriaIds: categorias }

    if (afiliacion) {
      actualizar.mutate(peticion, { onSuccess: onGuardada, onError: alFallar })
      return
    }

    crear.mutate(
      { clubId, temporadaId, ...peticion },
      { onSuccess: onGuardada, onError: alFallar },
    )
  }

  const idCategorias = `${id}-categorias`
  const idErrorCategorias = `${id}-categorias-error`
  const idColores = `${id}-colores`
  const idErrorColores = `${id}-colores-error`
  const idAyudaColores = `${id}-colores-ayuda`

  if (!editando && (clubes.length === 0 || abiertas.length === 0)) {
    return (
      <EstadoVacio
        icono={FileCheck2}
        titulo={clubes.length === 0 ? 'No hay clubes activos para afiliar' : 'No hay temporadas abiertas'}
        descripcion={
          clubes.length === 0
            ? 'Registra un club activo en Organización antes de capturar su tarjetón.'
            : 'La temporada debe estar en configuración o activa para recibir tarjetones.'
        }
        accion={
          <Button variante="secundario" onClick={onCancelar}>
            Volver a la bandeja
          </Button>
        }
      />
    )
  }

  return (
    <form
      onSubmit={alEnviar}
      noValidate
      className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-border bg-surface"
    >
      <header className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-start sm:justify-between sm:p-6">
        <div>
          <h2 className="text-balance text-headline-lg text-text-main">
            {editando ? 'Editar tarjetón' : 'Nuevo tarjetón'}
          </h2>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            {editando
              ? 'El tarjetón se puede corregir mientras siga SOLICITADA. Al guardar, la Presidencia verá los cambios.'
              : 'Registra los colores y las categorías del club. El folio se asigna al guardar y el tarjetón queda a revisión de la Presidencia.'}
          </p>
        </div>
        {afiliacion ? (
          <Insignia
            variante={etiquetaEstadoAfiliacion[afiliacion.estado].variante}
            icono={etiquetaEstadoAfiliacion[afiliacion.estado].icono}
          >
            {etiquetaEstadoAfiliacion[afiliacion.estado].texto}
          </Insignia>
        ) : (
          <p className="flex shrink-0 items-center gap-2 rounded-md bg-background px-3 py-2 text-body-sm text-text-muted">
            <FileCheck2 aria-hidden="true" className="size-4" />
            Folio automático
          </p>
        )}
      </header>

      <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-[minmax(0,1fr)_18rem] xl:items-start">
        <div className="flex min-w-0 flex-col gap-8">
          <section aria-labelledby={`${id}-datos`} className="flex flex-col gap-3">
            <div>
              <h3 id={`${id}-datos`} className="flex items-center gap-2 text-headline-sm text-text-main">
                <FileCheck2 aria-hidden="true" className="size-5 text-primary" />
                Club y temporada
              </h3>
              <p className="text-body-sm text-text-muted">
                La identidad del club y su directiva se consultan en el expediente del club.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              {editando || clubes.length === 1 ? (
                <div className="flex min-w-0 flex-col gap-1.5">
                  <span className="text-label-md text-text-main">Club</span>
                  <p className="flex min-h-11 items-center rounded-md border border-border bg-background px-3 text-body-md text-text-main">
                    {clubSeleccionado?.nombre ?? 'Club del tarjetón'}
                  </p>
                </div>
              ) : (
                <CampoSeleccion
                  id={`${id}-club`}
                  etiqueta="Club"
                  required
                  value={clubId}
                  error={errores.clubId}
                  disabled={guardando}
                  onChange={(evento) => {
                    setClubId(evento.target.value)
                    limpiarError('clubId')
                  }}
                >
                  <option value="">Selecciona…</option>
                  {clubes.map((club) => (
                    <option key={club.id} value={club.id}>
                      {club.nombre}
                    </option>
                  ))}
                </CampoSeleccion>
              )}

              {editando ? (
                <div className="flex min-w-0 flex-col gap-1.5">
                  <span className="text-label-md text-text-main">Temporada</span>
                  <p className="flex min-h-11 items-center gap-2 rounded-md border border-border bg-background px-3 text-body-md text-text-main">
                    {temporadaSeleccionada?.nombre ?? 'Temporada del tarjetón'}
                    {temporadaSeleccionada && (
                      <span className="text-body-sm text-text-muted">
                        · {etiquetaEstadoTemporada[temporadaSeleccionada.estado].texto}
                      </span>
                    )}
                  </p>
                </div>
              ) : (
                <CampoSeleccion
                  id={`${id}-temporada`}
                  etiqueta="Temporada"
                  required
                  value={temporadaId}
                  error={errores.temporadaId}
                  disabled={guardando}
                  ayuda={
                    sinCategorias && temporadaId
                      ? 'Esta temporada no tiene categorías asignadas.'
                      : undefined
                  }
                  onChange={(evento) => cambiarTemporada(evento.target.value)}
                >
                  <option value="">Selecciona…</option>
                  {abiertas.map((temporada) => (
                    <option key={temporada.id} value={temporada.id}>
                      {temporada.nombre} · {etiquetaEstadoTemporada[temporada.estado].texto}
                    </option>
                  ))}
                </CampoSeleccion>
              )}
            </div>
          </section>

          <section aria-labelledby={`${id}-categorias-titulo`} className="flex flex-col gap-3">
            <div>
              <h3
                id={`${id}-categorias-titulo`}
                className="flex items-center gap-2 text-headline-sm text-text-main"
              >
                <ListChecks aria-hidden="true" className="size-5 text-primary" />
                Categorías inscritas
              </h3>
              <p className="text-body-sm text-text-muted">
                Elige todas las categorías de la temporada en las que el club inscribe equipos.
              </p>
            </div>

            <fieldset
              id={idCategorias}
              aria-describedby={errores.categoriaIds ? idErrorCategorias : undefined}
              aria-invalid={errores.categoriaIds ? true : undefined}
              className="flex flex-col gap-2"
            >
              <legend className="sr-only">Categorías de la temporada</legend>
              {sinCategorias ? (
                <p className="rounded-md bg-background p-3 text-body-md text-text-muted">
                  La temporada elegida no tiene categorías asignadas. Configúralas en Temporadas
                  antes de capturar el tarjetón.
                </p>
              ) : (
                categoriasDisponibles.map((categoria) =>
                  categoriaDisponible(
                    categoria,
                    categorias.includes(categoria.id),
                    guardando,
                    alternarCategoria,
                  ),
                )
              )}
            </fieldset>

            {errores.categoriaIds && (
              <p id={idErrorCategorias} role="alert" className="text-body-sm text-error-strong">
                {errores.categoriaIds}
              </p>
            )}
          </section>

          <section aria-labelledby={`${id}-colores-titulo`} className="flex flex-col gap-3">
            <div>
              <h3
                id={`${id}-colores-titulo`}
                className="flex items-center gap-2 text-headline-sm text-text-main"
              >
                <Shirt aria-hidden="true" className="size-5 text-primary" />
                Colores del uniforme
              </h3>
              <p className="text-body-sm text-text-muted">
                Describe el uniforme titular y, si aplica, el alternativo. Se usan para cotejar la
                cédula arbitral.
              </p>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor={idColores} className="text-label-md text-text-main">
                Colores del uniforme
                <span aria-hidden="true" className="ml-0.5 text-error">
                  *
                </span>
              </label>
              <textarea
                id={idColores}
                value={colores}
                required
                rows={3}
                maxLength={MAXIMO_COLORES}
                disabled={guardando}
                aria-invalid={errores.coloresUniformes ? true : undefined}
                aria-describedby={
                  errores.coloresUniformes ? `${idErrorColores} ${idAyudaColores}` : idAyudaColores
                }
                onChange={(evento) => {
                  setColores(evento.target.value)
                  limpiarError('coloresUniformes')
                }}
                className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
                placeholder="Titular: verde y blanco. Alternativo: azul marino."
              />
              <p id={idAyudaColores} className="flex justify-between gap-2 text-body-sm text-text-muted">
                <span>Por ejemplo, colores de camiseta, short y calcetas.</span>
                <span className="font-mono">
                  {colores.trim().length}/{MAXIMO_COLORES}
                </span>
              </p>
              {errores.coloresUniformes && (
                <p id={idErrorColores} role="alert" className="text-body-sm text-error-strong">
                  {errores.coloresUniformes}
                </p>
              )}
            </div>
          </section>
        </div>

        <aside className="flex flex-col gap-4 rounded-md bg-background p-4">
          <div className="flex flex-col gap-1">
            <h3 className="text-headline-sm text-text-main">
              {editando ? 'Guardar cambios' : 'Enviar a aprobación'}
            </h3>
            <p className="text-body-sm text-text-muted">
              {editando
                ? 'Los cambios se ven de inmediato en el expediente de la Presidencia.'
                : 'El tarjetón nace SOLICITADA. La Presidencia lo aprueba o lo rechaza con observaciones.'}
            </p>
          </div>

          {temporadaCerrada && (
            <p role="alert" className="rounded-md bg-warning/14 p-3 text-body-sm text-warning-strong">
              La temporada está cerrada: el tarjetón ya no admite cambios.
            </p>
          )}

          <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-2 text-body-sm">
            <dt className="text-text-muted">Folio</dt>
            <dd className="break-all font-mono text-text-main">
              {afiliacion?.folio ?? 'Se asigna al guardar'}
            </dd>
            <dt className="text-text-muted">Estado</dt>
            <dd className="text-text-main">
              {afiliacion
                ? etiquetaEstadoAfiliacion[afiliacion.estado].texto
                : 'Solicitada al guardar'}
            </dd>
          </dl>

          {errorGeneral && (
            <p
              role="alert"
              className="flex items-start gap-2 rounded-md bg-error/8 p-3 text-body-md text-error-strong"
            >
              <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              {errorGeneral}
            </p>
          )}

          <div className="flex flex-col gap-2">
            <Button
              type="submit"
              icono={Send}
              cargando={guardando}
              disabled={!puedeEnviar}
              className="w-full"
            >
              {editando ? 'Guardar cambios' : 'Enviar a aprobación'}
            </Button>
            <Button
              variante="secundario"
              onClick={onCancelar}
              disabled={guardando}
              className="w-full"
            >
              Cancelar
            </Button>
          </div>
        </aside>
      </div>
    </form>
  )
}

/** Opción de categoría ya normalizada para la lista de casillas. */
type CategoriaOpcion = {
  id: string
  nombre: string
  detalle: string
}

function aOpcion(categoriaTemporada: CategoriaContextoAfiliacion): CategoriaOpcion {
  const { id, nombre, grupos } = categoriaTemporada

  return {
    id,
    nombre,
    detalle:
      grupos.length === 0
        ? 'Sin grupos'
        : grupos
            .map(({ nombre, equipos }) => `${nombre} (${equipos} ${equipos === 1 ? 'equipo' : 'equipos'})`)
            .join(' · '),
  }
}

/** Casilla de una categoría con sus grupos; se mantiene accesible sin `SeleccionMultiple`. */
function categoriaDisponible(
  categoria: CategoriaOpcion,
  marcada: boolean,
  deshabilitada: boolean,
  alternar: (categoriaId: string, marcada: boolean) => void,
) {
  return (
    <label
      key={categoria.id}
      className={[
        'flex min-h-11 cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors',
        marcada ? 'border-primary bg-primary/6' : 'border-border bg-surface hover:bg-background',
        deshabilitada ? 'cursor-not-allowed opacity-60' : '',
      ].join(' ')}
    >
      <input
        type="checkbox"
        checked={marcada}
        disabled={deshabilitada}
        onChange={(evento) => alternar(categoria.id, evento.target.checked)}
        className="mt-0.5 size-[1.125rem] shrink-0 accent-primary"
      />
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-label-md font-semibold text-text-main">{categoria.nombre}</span>
        <span className="text-body-sm text-text-muted">{categoria.detalle}</span>
      </span>
    </label>
  )
}
