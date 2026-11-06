import { useId, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useCrearEquipo } from '@/features/organizacion/api'
import { detalleDeFallo, erroresDeCampos, MAXIMO_NOMBRE } from '@/features/organizacion/erroresFormulario'
import { SelectorCategoriaGrupo } from '@/features/organizacion/SelectorCategoriaGrupo'
import type { Club, Equipo } from '@/features/organizacion/tipos'
import { clavesEstructura, useTemporadas } from '@/features/temporadas/api'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  club: Club
  onCerrar: () => void
  onRegistrado: (equipo: Equipo) => void
}

type Valores = {
  temporadaId: string
  categoriaId: string
  grupoId: string
  nombre: string
}

/**
 * Alta de un equipo del club en un grupo (F-15, RF-009, RN-007). Solo se ofrecen temporadas en
 * configuración o activas; la categoría y el grupo se cargan al elegir la temporada. Errores del
 * servidor por código: `DUPLICADO` en el nombre, `REFERENCIA_INVALIDA` en el grupo; `RN-041` y
 * `RF-003` explican por qué no procede.
 */
export function ModalRegistrarEquipo({ club, onCerrar, onRegistrado }: Props) {
  const dialogoRef = useDialogoModal()
  const clienteConsultas = useQueryClient()
  const temporadas = useTemporadas(0, 100)
  const crear = useCrearEquipo()
  const abiertas = (temporadas.data?.contenido ?? []).filter(({ estado }) => estado !== 'CERRADA')
  const activa = abiertas.find(({ estado }) => estado === 'ACTIVA')
  const [valores, setValores] = useState<Valores>({ temporadaId: '', categoriaId: '', grupoId: '', nombre: '' })
  const [tocoTemporada, setTocoTemporada] = useState(false)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  // La activa se preselecciona mientras la persona no elija otra temporada.
  const temporadaId = tocoTemporada ? valores.temporadaId : (activa?.id ?? '')

  const cambiar = (cambios: Partial<Valores>) => {
    setValores((actuales) => ({ ...actuales, ...cambios }))
    setErrores((actuales) => {
      const resto = { ...actuales }
      for (const campo of Object.keys(cambios)) {
        delete resto[campo]
      }
      return resto
    })
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral(detalleDeFallo(error, 'No se pudo registrar el equipo. Intenta de nuevo.'))
      return
    }

    if (error.codigo === 'DUPLICADO') {
      setErrores({ nombre: error.problema.detail })
      return
    }

    if (error.codigo === 'REFERENCIA_INVALIDA') {
      setErrores({ grupoId: error.problema.detail })
      void clienteConsultas.invalidateQueries({ queryKey: clavesEstructura.categoriasTemporada(temporadaId) })
      return
    }

    setErrores(erroresDeCampos(error, ['nombre', 'grupoId']))
    setErrorGeneral(error.problema.detail)
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const nombre = valores.nombre.trim()
    const encontrados: Record<string, string> = {}

    if (!temporadaId) {
      encontrados.temporadaId = 'Elige la temporada.'
    } else if (!valores.categoriaId) {
      encontrados.categoriaId = 'Elige la categoría.'
    } else if (!valores.grupoId) {
      encontrados.grupoId = 'Elige el grupo.'
    }
    if (!nombre) {
      encontrados.nombre = 'Escribe el nombre del equipo.'
    }

    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    crear.mutate({ clubId: club.id, grupoId: valores.grupoId, nombre }, { onSuccess: onRegistrado, onError: alFallar })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!crear.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div className="flex flex-col gap-1">
          <h2 id={idTitulo} className="text-balance text-headline-md">
            Registrar equipo
          </h2>
          <p className="break-words text-body-md text-text-muted">
            Equipo de {club.nombre}. Nace activo en el grupo que elijas.
          </p>
        </div>

        <CampoSeleccion
          id={`${id}-temporada`}
          etiqueta="Temporada"
          required
          disabled={temporadas.isPending || crear.isPending}
          error={errores.temporadaId}
          ayuda={temporadas.isSuccess && abiertas.length === 0 ? 'No hay temporadas en configuración ni activas.' : undefined}
          value={temporadaId}
          onChange={(evento) => {
            setTocoTemporada(true)
            cambiar({ temporadaId: evento.target.value, categoriaId: '', grupoId: '' })
          }}
        >
          <option value="">{temporadas.isPending ? 'Cargando temporadas…' : 'Selecciona…'}</option>
          {abiertas.map((temporada) => (
            <option key={temporada.id} value={temporada.id}>
              {temporada.nombre} · {etiquetaEstadoTemporada[temporada.estado].texto}
            </option>
          ))}
        </CampoSeleccion>
        {temporadas.isError && (
          <p role="alert" className="text-body-sm text-error-strong">
            {detalleDeFallo(temporadas.error, 'No se pudieron cargar las temporadas.')}
          </p>
        )}

        {temporadaId && (
          <SelectorCategoriaGrupo
            key={temporadaId}
            temporadaId={temporadaId}
            idBase={id}
            categoriaId={valores.categoriaId}
            grupoId={valores.grupoId}
            errorCategoria={errores.categoriaId}
            errorGrupo={errores.grupoId}
            deshabilitado={crear.isPending}
            onCambiarCategoria={(categoriaId) => cambiar({ categoriaId, grupoId: '' })}
            onCambiarGrupo={(grupoId) => cambiar({ grupoId })}
          />
        )}

        <CampoTexto
          id={`${id}-nombre`}
          etiqueta="Nombre del equipo"
          required
          maxLength={MAXIMO_NOMBRE}
          error={errores.nombre}
          value={valores.nombre}
          onChange={(evento) => cambiar({ nombre: evento.target.value })}
        />

        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={crear.isPending}>
            Cancelar
          </Button>
          <Button type="submit" cargando={crear.isPending}>
            Registrar
          </Button>
        </div>
      </form>
    </dialog>
  )
}
