import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useReemplazarConvocatoria } from '@/features/temporadas/api'
import { formatearFecha } from '@/features/temporadas/fechas'
import type {
  Convocatoria,
  ReemplazarConvocatoriaPeticion,
  Temporada,
} from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  temporada: Temporada
  onCerrar: () => void
  onGuardada: (convocatoria: Convocatoria) => void
}

type Valores = {
  referencia: string
  version: string
  documentoUrl: string
}

const CAMPOS_FORMULARIO = new Set<string>(['referencia', 'version', 'documentoUrl'])
const LONGITUD_MAXIMA_VERSION = 30
const ENLACE_VALIDO = /^https?:\/\/\S+$/i

function valoresDe(temporada: Temporada): Valores {
  return {
    referencia: temporada.convocatoria?.referencia ?? '',
    version: temporada.convocatoria?.version ?? '',
    documentoUrl: temporada.convocatoria?.documentoUrl ?? '',
  }
}

/**
 * Asocia o reemplaza la convocatoria de una temporada (F-10, B-02.2, RF-002).
 *
 * <p>La convocatoria es una referencia, no un archivo: se guardan la referencia, su versión y, si
 * se conoce, el enlace. El `PUT` reemplaza la referencia completa, así que dejar un campo opcional
 * vacío lo borra. Una temporada `CERRADA` no ofrece esta acción porque es de solo lectura
 * (RF-003, RN-005).
 */
export function FormularioConvocatoria({ temporada, onCerrar, onGuardada }: Props) {
  const dialogoRef = useDialogoModal()
  const reemplazar = useReemplazarConvocatoria(temporada.id)
  const [valores, setValores] = useState(() => valoresDe(temporada))
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const existente = temporada.convocatoria
  const guardando = reemplazar.isPending

  const cambiar = (campo: keyof Valores, valor: string) => {
    setValores((actuales) => ({ ...actuales, [campo]: valor }))
    setErrores(({ [campo]: _quitado, ...resto }) => resto)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo guardar la convocatoria. Intenta de nuevo.')
      return
    }

    setErrores(
      Object.fromEntries(
        (error.problema.errores ?? [])
          .filter(({ campo }) => CAMPOS_FORMULARIO.has(campo))
          .map(({ campo, mensaje }) => [campo, mensaje]),
      ),
    )
    setErrorGeneral(error.problema.detail)
  }

  const validar = (): Record<string, string> => {
    const encontrados: Record<string, string> = {}
    const referencia = valores.referencia.trim()
    const version = valores.version.trim()
    const documentoUrl = valores.documentoUrl.trim()

    if (!referencia) {
      encontrados.referencia = 'Escribe la referencia de la convocatoria.'
    }
    if (version.length > LONGITUD_MAXIMA_VERSION) {
      encontrados.version = `La versión admite hasta ${LONGITUD_MAXIMA_VERSION} caracteres.`
    }
    if (documentoUrl && !ENLACE_VALIDO.test(documentoUrl)) {
      encontrados.documentoUrl = 'El enlace debe empezar por http:// o https://.'
    }

    return encontrados
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    const referencia = valores.referencia.trim()
    const version = valores.version.trim()
    const documentoUrl = valores.documentoUrl.trim()
    const peticion: ReemplazarConvocatoriaPeticion = {
      referencia,
      ...(version ? { version } : {}),
      ...(documentoUrl ? { documentoUrl } : {}),
    }

    reemplazar.mutate(peticion, { onSuccess: onGuardada, onError: alFallar })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
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
          <h2 id={idTitulo} className="text-balance break-words text-headline-md">
            Convocatoria de «{temporada.nombre}»
          </h2>
          <p id={idDescripcion} className="mt-1 text-body-sm text-text-muted">
            {existente
              ? 'Al guardar se reemplaza la referencia anterior; la que había queda en la auditoría.'
              : 'Asocia la referencia normativa de la temporada. Se guardan el título, la versión y el enlace, no el archivo.'}
          </p>
        </div>

        {existente && (
          <p className="rounded-md bg-background p-3 font-mono text-mono-data text-text-muted">
            Asociada el {formatearFecha(existente.creadoEn.slice(0, 10))}
          </p>
        )}

        <CampoTexto
          id={`${id}-referencia`}
          etiqueta="Referencia"
          required
          ayuda="Título o referencia de la convocatoria."
          error={errores.referencia}
          value={valores.referencia}
          onChange={(evento) => cambiar('referencia', evento.target.value)}
        />
        <CampoTexto
          id={`${id}-version`}
          etiqueta="Versión"
          maxLength={30}
          ayuda={
            existente?.version
              ? 'Déjala vacía para borrarla. Hasta 30 caracteres.'
              : 'Opcional. Hasta 30 caracteres.'
          }
          error={errores.version}
          value={valores.version}
          onChange={(evento) => cambiar('version', evento.target.value)}
        />
        <CampoTexto
          id={`${id}-documento`}
          etiqueta="Enlace al documento"
          type="url"
          inputMode="url"
          ayuda={
            existente?.documentoUrl
              ? 'Déjalo vacío para borrarlo. Debe empezar por http:// o https://.'
              : 'Opcional. Debe empezar por http:// o https://.'
          }
          error={errores.documentoUrl}
          value={valores.documentoUrl}
          onChange={(evento) => cambiar('documentoUrl', evento.target.value)}
        />

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
            Guardar convocatoria
          </Button>
        </div>
      </form>
    </dialog>
  )
}
