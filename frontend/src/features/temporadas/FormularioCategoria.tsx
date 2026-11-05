import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useActualizarCategoria, useCrearCategoria } from '@/features/temporadas/api'
import { etiquetaTipoCategoria } from '@/features/temporadas/etiquetas'
import type {
  ActualizarCategoriaPeticion,
  Categoria,
  CrearCategoriaPeticion,
  TipoCategoria,
} from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  /** Categoría a editar; sin ella, el formulario crea una nueva. */
  categoria?: Categoria
  /** Catálogo completo, para elegir la categoría superior. */
  catalogo: readonly Categoria[]
  onCerrar: () => void
  onGuardada: (categoria: Categoria, creada: boolean) => void
}

type Valores = {
  nombre: string
  tipo: TipoCategoria | ''
  /** `''` = «Ninguna (tope de su rama)». */
  superiorId: string
}

const MAXIMO_NOMBRE = 80
const CAMPOS = new Set(['nombre', 'tipo', 'categoriaSuperiorId'])

/** Solo lo que cambió (RF-099). Pasar a «Ninguna» desde una superior es `quitarSuperior`. */
function cambiosDe(categoria: Categoria, valores: Valores): ActualizarCategoriaPeticion {
  const cambios: ActualizarCategoriaPeticion = {}
  const nombre = valores.nombre.trim()
  const superiorActual = categoria.categoriaSuperior?.id ?? ''

  if (nombre !== categoria.nombre) {
    cambios.nombre = nombre
  }
  if (valores.tipo && valores.tipo !== categoria.tipo) {
    cambios.tipo = valores.tipo
  }
  if (valores.superiorId !== superiorActual) {
    if (valores.superiorId === '') {
      cambios.quitarSuperior = true
    } else {
      cambios.categoriaSuperiorId = valores.superiorId
    }
  }

  return cambios
}

/**
 * Alta y edición de una categoría del catálogo de la liga (F-11, H-05.3, RF-099). La superior se
 * elige entre las activas, sin la propia; el servidor rechaza los ciclos (`409 RF-099`, con la
 * cadena en el mensaje) y el cambio de jerarquía en una temporada activa (`409 RF-110`).
 */
export function FormularioCategoria({ categoria, catalogo, onCerrar, onGuardada }: Props) {
  const dialogoRef = useDialogoModal()
  const crear = useCrearCategoria()
  const actualizar = useActualizarCategoria()
  const [valores, setValores] = useState<Valores>(() => ({
    nombre: categoria?.nombre ?? '',
    tipo: categoria?.tipo ?? '',
    superiorId: categoria?.categoriaSuperior?.id ?? '',
  }))
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const guardando = crear.isPending || actualizar.isPending

  const superiores = catalogo.filter(
    (otra) =>
      otra.id !== categoria?.id && (otra.activa || otra.id === categoria?.categoriaSuperior?.id),
  )

  const cambiar = (campo: keyof Valores, valor: string) => {
    setValores((actuales) => ({ ...actuales, [campo]: valor }))
    setErrores(({ [campo === 'superiorId' ? 'categoriaSuperiorId' : campo]: _quitado, ...resto }) => resto)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo guardar la categoría. Intenta de nuevo.')
      return
    }

    const { codigo, status, detail } = error.problema

    if (codigo === 'DUPLICADO') {
      setErrores({ nombre: detail })
      setErrorGeneral(null)
      return
    }

    if (status === 422) {
      setErrores({ categoriaSuperiorId: detail })
      setErrorGeneral(null)
      return
    }

    // 400 VALIDACION marca sus campos; RF-099 y RF-110 explican el bloqueo con su detail.
    setErrores(
      Object.fromEntries(
        error.errores.filter(({ campo }) => CAMPOS.has(campo)).map(({ campo, mensaje }) => [campo, mensaje]),
      ),
    )
    setErrorGeneral(detail)
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados: Record<string, string> = {}
    if (!valores.nombre.trim()) {
      encontrados.nombre = 'Escribe el nombre de la categoría.'
    }
    if (!valores.tipo) {
      encontrados.tipo = 'Elige el tipo de la categoría.'
    }
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0 || !valores.tipo) {
      return
    }

    if (categoria) {
      const cambios = cambiosDe(categoria, valores)

      if (Object.keys(cambios).length === 0) {
        onCerrar()
        return
      }

      actualizar.mutate(
        { id: categoria.id, ...cambios },
        { onSuccess: (guardada) => onGuardada(guardada, false), onError: alFallar },
      )
      return
    }

    const peticion: CrearCategoriaPeticion = {
      nombre: valores.nombre.trim(),
      tipo: valores.tipo,
      ...(valores.superiorId ? { categoriaSuperiorId: valores.superiorId } : {}),
    }

    crear.mutate(peticion, {
      onSuccess: (guardada) => onGuardada(guardada, true),
      onError: alFallar,
    })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!guardando) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-4 p-6">
        <div>
          <h2 id={idTitulo} className="text-headline-md">
            {categoria ? 'Editar categoría' : 'Nueva categoría'}
          </h2>
          <p className="mt-1 text-body-sm text-text-muted">
            El catálogo es de la liga y lo comparten todas las temporadas.
            {!categoria && ' La categoría nace activa y al final del orden.'}
          </p>
        </div>

        <CampoTexto
          id={`${id}-nombre`}
          etiqueta="Nombre"
          required
          maxLength={MAXIMO_NOMBRE}
          error={errores.nombre}
          value={valores.nombre}
          onChange={(evento) => cambiar('nombre', evento.target.value)}
        />
        <CampoSeleccion
          id={`${id}-tipo`}
          etiqueta="Tipo"
          required
          error={errores.tipo}
          value={valores.tipo}
          onChange={(evento) => cambiar('tipo', evento.target.value)}
        >
          <option value="" disabled>
            Elige un tipo
          </option>
          {(Object.keys(etiquetaTipoCategoria) as TipoCategoria[]).map((tipo) => (
            <option key={tipo} value={tipo}>
              {etiquetaTipoCategoria[tipo]}
            </option>
          ))}
        </CampoSeleccion>
        <CampoSeleccion
          id={`${id}-superior`}
          etiqueta="Categoría superior"
          ayuda="Elige «Ninguna» si es el tope de su rama. Solo se ofrecen categorías activas."
          error={errores.categoriaSuperiorId}
          value={valores.superiorId}
          onChange={(evento) => cambiar('superiorId', evento.target.value)}
        >
          <option value="">Ninguna (tope de su rama)</option>
          {superiores.map((otra) => (
            <option key={otra.id} value={otra.id}>
              {otra.nombre}
              {otra.activa ? '' : ' (inactiva)'}
            </option>
          ))}
        </CampoSeleccion>

        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </Button>
          <Button type="submit" cargando={guardando}>
            Guardar
          </Button>
        </div>
      </form>
    </dialog>
  )
}
