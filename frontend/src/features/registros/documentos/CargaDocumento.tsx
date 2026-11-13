import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react'
import { FileText, Upload, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import {
  ACEPTA_DOCUMENTO,
  AYUDA_ARCHIVO,
  esArchivoImagen,
  tamanoLegible,
  validarArchivoDocumento,
} from '@/features/registros/documentos/archivoDocumento'

type Props = {
  /** Nombre del tipo de documento, p. ej. «Fotografía». */
  nombre: string
  modo: 'cargar' | 'reemplazar'
  enviando: boolean
  /** 413 o 415 del servidor: manda sobre la revisión del navegador (DEC-112). */
  errorServidor?: string
  onSubir: (archivo: File) => void
  onCancelar: () => void
  /** Se eligió otro archivo: la página descarta el error anterior del servidor. */
  onElegir?: () => void
}

/**
 * Carga con vista previa (DEC-112): elegir archivo → vista previa local → «Subir», «Cambiar
 * archivo» o «Cancelar». La imagen se previsualiza con `URL.createObjectURL`, que se revoca al
 * cambiar de archivo, al cancelar y al desmontar (tras subir, la página cierra este panel). Un PDF
 * se muestra con ícono, nombre y tamaño. El tamaño y el tipo se revisan antes de enviar.
 *
 * Refs: RF-108, RF-109, DEC-112
 */
export function CargaDocumento({
  nombre,
  modo,
  enviando,
  errorServidor,
  onSubir,
  onCancelar,
  onElegir,
}: Props) {
  const id = useId()
  const idEntrada = `${id}-archivo`
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const [archivo, setArchivo] = useState<File | null>(null)
  const [vistaPrevia, setVistaPrevia] = useState<string | null>(null)
  const [errorLocal, setErrorLocal] = useState<string | undefined>()
  const urlActual = useRef<string | null>(null)
  const entrada = useRef<HTMLInputElement>(null)

  const liberarVistaPrevia = () => {
    if (urlActual.current) {
      URL.revokeObjectURL(urlActual.current)
      urlActual.current = null
    }
    setVistaPrevia(null)
  }

  // Al desmontar (subida terminada o panel cerrado) se libera la URL local.
  useEffect(
    () => () => {
      if (urlActual.current) {
        URL.revokeObjectURL(urlActual.current)
      }
    },
    [],
  )

  // Abrir el panel equivale a querer elegir el archivo: el foco va al selector.
  useEffect(() => {
    entrada.current?.focus()
  }, [])

  const elegir = (evento: ChangeEvent<HTMLInputElement>) => {
    const elegido = evento.target.files?.[0] ?? null
    // Se limpia el valor para que elegir el mismo archivo otra vez dispare `change`.
    evento.target.value = ''
    if (!elegido) {
      return
    }

    liberarVistaPrevia()
    onElegir?.()
    const problema = validarArchivoDocumento(elegido)
    setArchivo(elegido)
    setErrorLocal(problema)

    if (!problema && esArchivoImagen(elegido)) {
      const url = URL.createObjectURL(elegido)
      urlActual.current = url
      setVistaPrevia(url)
    }
  }

  const cancelar = () => {
    liberarVistaPrevia()
    setArchivo(null)
    onCancelar()
  }

  const error = errorLocal ?? errorServidor
  const valido = archivo !== null && !errorLocal
  const etiqueta = modo === 'cargar' ? `Archivo de ${nombre}` : `Archivo nuevo de ${nombre}`

  return (
    <div className="flex flex-col gap-3 rounded-md border border-primary/40 bg-primary/4 p-3">
      <div className="flex flex-col gap-1.5">
        <label htmlFor={idEntrada} className="text-label-md text-text-main">
          {etiqueta}
        </label>
        <label
          className={[
            'flex min-h-11 cursor-pointer flex-wrap items-center gap-3 rounded-md border border-dashed bg-surface px-3 py-2.5 transition-colors hover:border-primary',
            'has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary has-disabled:cursor-not-allowed has-disabled:opacity-60',
            error ? 'border-error' : 'border-border',
          ].join(' ')}
        >
          <input
            ref={entrada}
            id={idEntrada}
            type="file"
            accept={ACEPTA_DOCUMENTO}
            disabled={enviando}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${idError} ${idAyuda}` : idAyuda}
            onChange={elegir}
            className="sr-only"
          />
          <span
            aria-hidden="true"
            className="inline-flex min-h-9 shrink-0 items-center gap-2 rounded-md border border-border bg-surface px-3 text-label-md text-text-main"
          >
            <Upload className="size-4" />
            {archivo ? 'Cambiar archivo' : 'Elegir archivo'}
          </span>
          <span aria-hidden="true" className="text-body-sm text-text-muted">
            {archivo ? 'Toca para elegir otro' : 'Foto o PDF desde tu dispositivo'}
          </span>
        </label>
        <p id={idAyuda} className="text-body-sm text-text-muted">
          {AYUDA_ARCHIVO}
        </p>
        {error && (
          <p id={idError} role="alert" className="text-body-sm text-error-strong">
            {error}
          </p>
        )}
      </div>

      {archivo && !errorLocal && (
        <figure className="flex flex-col gap-2">
          {vistaPrevia ? (
            <img
              src={vistaPrevia}
              alt={`Vista previa de ${nombre}`}
              className="max-h-64 w-full rounded-md border border-border bg-background object-contain"
            />
          ) : (
            <span className="flex items-center gap-3 rounded-md border border-border bg-surface p-3">
              <FileText aria-hidden="true" className="size-8 shrink-0 text-text-muted" />
              <span className="min-w-0 break-all text-body-md text-text-main">{archivo.name}</span>
            </span>
          )}
          <figcaption className="flex flex-wrap items-baseline gap-x-2 text-body-sm text-text-muted">
            {vistaPrevia && <span className="min-w-0 break-all">{archivo.name}</span>}
            <span className="font-mono tabular-nums">{tamanoLegible(archivo.size)}</span>
          </figcaption>
        </figure>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variante="secundario" icono={X} onClick={cancelar} disabled={enviando}>
          Cancelar
        </Button>
        {valido && (
          <Button icono={Upload} cargando={enviando} onClick={() => onSubir(archivo)}>
            Subir
          </Button>
        )}
      </div>
    </div>
  )
}
