import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  etiqueta: string
  valorInicial?: string
  etiquetaGuardar: string
  guardando: boolean
  /** Guarda el nombre ya recortado; devuelve el error de la mutación, si lo hubo. */
  onGuardar: (nombre: string, alFallar: (error: unknown) => void) => void
  onCancelar: () => void
}

const MAXIMO_NOMBRE = 60

/**
 * Formulario en línea del nombre de un grupo, para agregarlo o renombrarlo (F-11, RF-005). El
 * nombre mide de 1 a 60 caracteres; el `400` y el `409 DUPLICADO` se muestran en el mismo campo,
 * y los demás bloqueos (`RF-003`, `RF-005`) con su `detail` (D9).
 */
export function FormularioNombreGrupo({
  etiqueta,
  valorInicial = '',
  etiquetaGuardar,
  guardando,
  onGuardar,
  onCancelar,
}: Props) {
  const id = useId()
  const [nombre, setNombre] = useState(valorInicial)
  const [error, setError] = useState<string | undefined>()
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)

  const alFallar = (fallo: unknown) => {
    if (!(fallo instanceof ApiError)) {
      setErrorGeneral('No se pudo guardar el grupo. Intenta de nuevo.')
      return
    }

    const { codigo, status, detail } = fallo.problema

    if (codigo === 'DUPLICADO' || status === 400) {
      setError(fallo.mensajeDe('nombre') ?? detail)
      return
    }

    setErrorGeneral(detail)
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)
    const limpio = nombre.trim()

    if (!limpio) {
      setError('Escribe el nombre del grupo.')
      return
    }

    onGuardar(limpio, alFallar)
  }

  return (
    <form
      onSubmit={alEnviar}
      noValidate
      onKeyDown={(evento) => {
        if (evento.key === 'Escape' && !guardando) {
          evento.preventDefault()
          onCancelar()
        }
      }}
      className="flex flex-col gap-2 rounded-md border border-border bg-background p-3 sm:flex-row sm:items-start"
    >
      <div className="min-w-0 flex-1">
        <CampoTexto
          id={`${id}-nombre`}
          etiqueta={etiqueta}
          required
          autoFocus
          maxLength={MAXIMO_NOMBRE}
          value={nombre}
          error={error}
          onChange={(evento) => {
            setNombre(evento.target.value)
            setError(undefined)
          }}
        />
        {errorGeneral && (
          <p role="alert" className="mt-2 text-body-sm text-error-strong">
            {errorGeneral}
          </p>
        )}
      </div>
      <div className="flex gap-2 sm:mt-[1.625rem]">
        <Button type="submit" cargando={guardando}>
          {etiquetaGuardar}
        </Button>
        <Button variante="secundario" disabled={guardando} onClick={onCancelar}>
          Cancelar
        </Button>
      </div>
    </form>
  )
}
