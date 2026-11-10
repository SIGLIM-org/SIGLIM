import { useId, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Eye, PenLine, Save, Send } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { useActualizarComunicado, useCrearComunicado } from '@/features/publicacion/api'
import { etiquetaEstadoPublicacion } from '@/features/publicacion/etiquetas'
import { ModalEnvioRevision } from '@/features/publicacion/ModalEnvioRevision'
import { VistaPreviaComunicado } from '@/features/publicacion/VistaPreviaComunicado'
import type {
  ActualizarComunicadoPeticion,
  Comunicado,
} from '@/features/publicacion/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  /** Comunicado en borrador que se edita; sin él, el editor crea uno nuevo. */
  comunicado?: Comunicado
  onCreado: (comunicado: Comunicado) => void
  onActualizado: (comunicado: Comunicado) => void
  onEnviado: (comunicado: Comunicado) => void
  /** Solo para el alta; el borrador existente se descarta con sus propios controles. */
  onCancelar?: () => void
}

type Valores = {
  titulo: string
  cuerpo: string
}

const CAMPOS_FORMULARIO = new Set<string>(['titulo', 'cuerpo'])
const LONGITUD_MAXIMA_TITULO = 200

function valoresDe(comunicado?: Comunicado): Valores {
  return {
    titulo: comunicado?.titulo ?? '',
    cuerpo: comunicado?.cuerpo ?? '',
  }
}

/** Solo lo que cambió: un campo ausente conserva su valor (RF-090). */
function cambiosDe(comunicado: Comunicado, valores: Valores): ActualizarComunicadoPeticion {
  const cambios: ActualizarComunicadoPeticion = {}
  const titulo = valores.titulo.trim()
  const cuerpo = valores.cuerpo.trim()

  if (titulo !== comunicado.titulo) {
    cambios.titulo = titulo
  }
  if (cuerpo !== comunicado.cuerpo) {
    cambios.cuerpo = cuerpo
  }

  return cambios
}

/**
 * Redacción de un comunicado (UI-031, RF-090). Se guarda como borrador y se
 * envía a revisión con una acción aparte, para que la Secretaría pueda
 * corregirlo antes de que lo dictamine la Presidencia. El cuerpo es texto
 * plano: el API no admite formato enriquecido.
 */
export function EditorComunicado({
  comunicado,
  onCreado,
  onActualizado,
  onEnviado,
  onCancelar,
}: Props) {
  const crear = useCrearComunicado()
  const actualizar = useActualizarComunicado(comunicado?.id ?? '')
  const [valores, setValores] = useState(() => valoresDe(comunicado))
  const [vista, setVista] = useState<'editor' | 'vista'>('editor')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const [confirmandoEnvio, setConfirmandoEnvio] = useState(false)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idCuerpo = `${id}-cuerpo`
  const idTabEditor = `${id}-tab-editor`
  const idTabVista = `${id}-tab-vista`
  const idEditor = `${id}-editor`
  const idVista = `${id}-vista`
  const esNuevo = !comunicado
  const guardando = crear.isPending || actualizar.isPending
  const cambios = comunicado ? cambiosDe(comunicado, valores) : {}
  const hayCambios = Object.keys(cambios).length > 0
  const etiqueta = comunicado ? etiquetaEstadoPublicacion[comunicado.estadoPublicacion] : null

  const cambiar = (campo: keyof Valores, valor: string) => {
    setValores((actuales) => ({ ...actuales, [campo]: valor }))
    setErrores(({ [campo]: _quitado, ...resto }) => resto)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo guardar el comunicado. Intenta de nuevo.')
      return
    }

    const deCampos = Object.fromEntries(
      (error.problema.errores ?? [])
        .filter(({ campo }) => CAMPOS_FORMULARIO.has(campo))
        .map(({ campo, mensaje }) => [campo, mensaje]),
    )

    setErrores(deCampos)
    setErrorGeneral(error.problema.detail)
  }

  const validar = (): Record<string, string> => {
    const encontrados: Record<string, string> = {}
    const titulo = valores.titulo.trim()
    const cuerpo = valores.cuerpo.trim()

    if (!titulo) {
      encontrados.titulo = 'Escribe el título del comunicado.'
    } else if (titulo.length > LONGITUD_MAXIMA_TITULO) {
      encontrados.titulo = `El título admite hasta ${LONGITUD_MAXIMA_TITULO} caracteres.`
    }
    if (!cuerpo) {
      encontrados.cuerpo = 'Escribe el cuerpo del comunicado.'
    }

    return encontrados
  }

  const alGuardar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    const peticion = {
      titulo: valores.titulo.trim(),
      cuerpo: valores.cuerpo.trim(),
    }

    if (!comunicado) {
      crear.mutate(peticion, { onSuccess: onCreado, onError: alFallar })
      return
    }

    if (!hayCambios) {
      return
    }

    actualizar.mutate(cambios, {
      onSuccess: (actualizado) => {
        setValores(valoresDe(actualizado))
        onActualizado(actualizado)
      },
      onError: alFallar,
    })
  }

  const descartar = () => {
    if (!comunicado) {
      return
    }

    setValores(valoresDe(comunicado))
    setErrores({})
    setErrorGeneral(null)
  }

  const navegarVistas = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const siguiente = vista === 'editor' ? 'vista' : 'editor'

    setVista(siguiente)
    document.getElementById(siguiente === 'editor' ? idTabEditor : idTabVista)?.focus()
  }

  return (
    <section
      aria-label={esNuevo ? 'Redactar comunicado' : 'Editar comunicado'}
      className="overflow-hidden rounded-lg border border-border bg-surface"
    >
      <header className="flex flex-col gap-3 border-b border-border p-5 sm:flex-row sm:items-start sm:justify-between sm:p-6">
        <div>
          <h2 className="text-headline-lg text-text-main">
            {esNuevo ? 'Redactar comunicado' : 'Editar comunicado'}
          </h2>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Escribe el título y el cuerpo. Guarda el borrador y envíalo a revisión cuando esté
            listo.
          </p>
        </div>
        {etiqueta && (
          <Insignia variante={etiqueta.variante} icono={etiqueta.icono}>
            {etiqueta.texto}
          </Insignia>
        )}
      </header>

      {comunicado?.observaciones && (
        <section
          aria-label="Observaciones de la Presidencia"
          className="mx-5 mt-4 rounded-md border border-warning/40 bg-warning/8 p-4 text-warning-strong sm:mx-6"
        >
          <h3 className="text-headline-sm">Observaciones de la Presidencia</h3>
          <p className="mt-1 whitespace-pre-wrap text-body-md">{comunicado.observaciones}</p>
        </section>
      )}

      <div
        role="tablist"
        aria-label="Editor y vista previa"
        onKeyDown={navegarVistas}
        className="mt-4 flex gap-1 border-b border-border px-5 sm:px-6"
      >
        <button
          type="button"
          role="tab"
          id={idTabEditor}
          aria-selected={vista === 'editor'}
          aria-controls={idEditor}
          tabIndex={vista === 'editor' ? 0 : -1}
          onClick={() => setVista('editor')}
          className={[
            'inline-flex min-h-11 items-center gap-2 border-b-2 px-3 text-label-md transition-colors',
            vista === 'editor'
              ? 'border-primary text-primary'
              : 'border-transparent text-text-muted hover:text-text-main',
          ].join(' ')}
        >
          <PenLine aria-hidden="true" className="size-4" />
          Redactar
        </button>
        <button
          type="button"
          role="tab"
          id={idTabVista}
          aria-selected={vista === 'vista'}
          aria-controls={idVista}
          tabIndex={vista === 'vista' ? 0 : -1}
          onClick={() => setVista('vista')}
          className={[
            'inline-flex min-h-11 items-center gap-2 border-b-2 px-3 text-label-md transition-colors',
            vista === 'vista'
              ? 'border-primary text-primary'
              : 'border-transparent text-text-muted hover:text-text-main',
          ].join(' ')}
        >
          <Eye aria-hidden="true" className="size-4" />
          Vista previa
        </button>
      </div>

      <div role="tabpanel" id={idEditor} aria-labelledby={idTabEditor} hidden={vista !== 'editor'}>
        <form onSubmit={alGuardar} noValidate className="flex flex-col gap-5 p-5 sm:p-6">
          <div className="flex flex-col gap-1.5">
            <div className="flex items-baseline justify-between gap-2">
              <label htmlFor={idTitulo} className="text-label-md text-text-main">
                Título
                <span aria-hidden="true" className="ml-0.5 text-error">
                  *
                </span>
              </label>
              <span className="font-mono text-body-sm text-text-muted">
                {valores.titulo.length}/{LONGITUD_MAXIMA_TITULO}
              </span>
            </div>
            <input
              id={idTitulo}
              value={valores.titulo}
              required
              maxLength={LONGITUD_MAXIMA_TITULO}
              aria-invalid={errores.titulo ? true : undefined}
              aria-describedby={errores.titulo ? `${idTitulo}-error` : `${idTitulo}-ayuda`}
              onChange={(evento) => cambiar('titulo', evento.target.value)}
              className={[
                'min-h-11 w-full rounded-md border border-border bg-surface px-3 text-body-md text-text-main placeholder:text-text-muted/70',
                errores.titulo ? 'border-error' : '',
              ]
                .filter(Boolean)
                .join(' ')}
            />
            {errores.titulo ? (
              <p id={`${idTitulo}-error`} role="alert" className="text-body-sm text-error-strong">
                {errores.titulo}
              </p>
            ) : (
              <p id={`${idTitulo}-ayuda`} className="text-body-sm text-text-muted">
                Hasta {LONGITUD_MAXIMA_TITULO} caracteres. Es el título que verá el público.
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor={idCuerpo} className="text-label-md text-text-main">
              Cuerpo del comunicado
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            </label>
            <textarea
              id={idCuerpo}
              value={valores.cuerpo}
              required
              rows={14}
              aria-invalid={errores.cuerpo ? true : undefined}
              aria-describedby={errores.cuerpo ? `${idCuerpo}-error` : `${idCuerpo}-ayuda`}
              onChange={(evento) => cambiar('cuerpo', evento.target.value)}
              className={[
                'min-h-64 w-full rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main placeholder:text-text-muted/70',
                errores.cuerpo ? 'border-error' : '',
              ]
                .filter(Boolean)
                .join(' ')}
            />
            {errores.cuerpo ? (
              <p id={`${idCuerpo}-error`} role="alert" className="text-body-sm text-error-strong">
                {errores.cuerpo}
              </p>
            ) : (
              <p id={`${idCuerpo}-ayuda`} className="text-body-sm text-text-muted">
                Texto plano; los saltos de línea se conservan en la vista pública.
              </p>
            )}
          </div>

          {errorGeneral && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorGeneral}
            </p>
          )}

          <div className="flex flex-col gap-3 border-t border-border pt-4">
            {comunicado && hayCambios && (
              <p role="status" className="text-body-sm text-warning-strong">
                Hay cambios sin guardar. Guárdalos antes de enviar el comunicado a revisión.
              </p>
            )}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                {esNuevo
                  ? onCancelar && (
                      <Button variante="secundario" onClick={onCancelar} disabled={guardando}>
                        Cancelar
                      </Button>
                    )
                  : hayCambios && (
                      <Button variante="secundario" onClick={descartar} disabled={guardando}>
                        Descartar cambios
                      </Button>
                    )}
              </div>
              <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
                <Button
                  type="submit"
                  icono={Save}
                  cargando={guardando}
                  disabled={!esNuevo && !hayCambios}
                >
                  {esNuevo ? 'Guardar borrador' : 'Guardar cambios'}
                </Button>
                {comunicado && (
                  <Button
                    variante="secundario"
                    icono={Send}
                    onClick={() => setConfirmandoEnvio(true)}
                    disabled={hayCambios || guardando}
                    title={
                      hayCambios ? 'Guarda los cambios antes de enviar el comunicado.' : undefined
                    }
                  >
                    Enviar a revisión
                  </Button>
                )}
              </div>
            </div>
          </div>
        </form>
      </div>

      <div role="tabpanel" id={idVista} aria-labelledby={idTabVista} hidden={vista !== 'vista'}>
        <div className="p-5 sm:p-6">
          <VistaPreviaComunicado
            titulo={valores.titulo}
            cuerpo={valores.cuerpo}
            estado={comunicado?.estadoPublicacion ?? 'BORRADOR'}
            publicadoEn={comunicado?.publicadoEn}
          />
        </div>
      </div>

      {confirmandoEnvio && comunicado && (
        <ModalEnvioRevision
          comunicado={comunicado}
          onCerrar={() => setConfirmandoEnvio(false)}
          onEnviado={(enviado) => {
            setConfirmandoEnvio(false)
            onEnviado(enviado)
          }}
        />
      )}
    </section>
  )
}
