import { useId, useState, type ChangeEvent, type FormEvent } from 'react'
import { Check, FileText, Info, ReceiptText, Upload } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { Insignia } from '@/components/ui/Insignia'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { clavesTesoreria, useRegistrarPago } from '@/features/tesoreria/api'
import { estadoCobro, etiquetaMedioPago } from '@/features/tesoreria/etiquetas'
import { formatearMonto, hoyEnZonaNegocio } from '@/features/tesoreria/fechas'
import type { Cobro, MedioPago, Pago } from '@/features/tesoreria/tipos'
import { ApiError } from '@/lib/api/ApiError'

/*
 * Límites del comprobante (convenciones de API §6.13, «Pagos externos»). El backend vuelve a
 * validarlos por firma binaria; aquí se revisan antes de enviar porque, con un archivo mucho mayor,
 * el servidor corta la conexión antes de que el cliente pueda leer el 413.
 */
const TAMANO_MAXIMO_COMPROBANTE = 5 * 1024 * 1024
const TIPOS_COMPROBANTE = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
const EXTENSIONES_COMPROBANTE = ['.jpg', '.jpeg', '.png', '.webp', '.pdf']
const ACEPTA_COMPROBANTE = [...TIPOS_COMPROBANTE, ...EXTENSIONES_COMPROBANTE].join(',')
const LONGITUD_MAXIMA_REFERENCIA = 60

const MEDIOS: readonly MedioPago[] = ['EFECTIVO', 'TRANSFERENCIA', 'OTRO']

const CAMPOS_FORMULARIO = new Set(['medio', 'fechaPago', 'referencia', 'archivo'])

const ERROR_GENERICO = 'No se pudo registrar el pago. Intenta de nuevo.'

type Props = {
  cobro: Cobro
  onCerrar: () => void
  onRegistrado: (pago: Pago) => void
}

type Valores = {
  medio: MedioPago | ''
  fechaPago: string
  referencia: string
}

const VALORES_INICIALES: Valores = { medio: '', fechaPago: '', referencia: '' }

const formatoTamano = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 1 })

/** «820 KB» o «2.4 MB». */
function tamanoLegible(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${formatoTamano.format(Math.max(1, Math.round(bytes / 1024)))} KB`
  }

  return `${formatoTamano.format(bytes / (1024 * 1024))} MB`
}

/** Revisa el comprobante en el navegador; devuelve el mensaje de error o `undefined`. */
function validarComprobante(archivo: File | null): string | undefined {
  if (!archivo) {
    return 'Adjunta el comprobante del pago.'
  }
  if (archivo.size > TAMANO_MAXIMO_COMPROBANTE) {
    return 'El comprobante pesa más de 5 MB; adjunta uno más ligero.'
  }

  const nombre = archivo.name.toLowerCase()
  const permitido = archivo.type
    ? TIPOS_COMPROBANTE.includes(archivo.type)
    : EXTENSIONES_COMPROBANTE.some((extension) => nombre.endsWith(extension))

  return permitido ? undefined : 'El comprobante debe ser JPEG, PNG, WebP o PDF.'
}

/**
 * Modal para registrar un pago recibido fuera del sistema (F-33, H-31.2, RF-139). Solo captura los
 * campos del contrato: medio, fecha opcional, referencia opcional y comprobante. El monto no se
 * edita: se envía el del cobro, porque no hay pagos parciales. Un cobro que ya no admite pago
 * responde 409 (VAL-028) y la tabla se refresca para mostrar su estado real.
 */
export function ModalRegistrarPago({ cobro, onCerrar, onRegistrado }: Props) {
  const dialogoRef = useDialogoModal()
  const clienteConsultas = useQueryClient()
  const registrar = useRegistrarPago()
  const [valores, setValores] = useState<Valores>(VALORES_INICIALES)
  const [archivo, setArchivo] = useState<File | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idMedioError = `${id}-medio-error`
  const idArchivo = `${id}-archivo`
  const idArchivoEtiqueta = `${id}-archivo-etiqueta`
  const idArchivoAyuda = `${id}-archivo-ayuda`
  const idArchivoError = `${id}-archivo-error`
  const idArchivoElegido = `${id}-archivo-elegido`
  const hoy = hoyEnZonaNegocio()
  const guardando = registrar.isPending
  const estado = estadoCobro(cobro.estado)

  const cambiar = (cambios: Partial<Valores>) => {
    setValores((actuales) => ({ ...actuales, ...cambios }))
    setErrores((actuales) => {
      const siguiente = { ...actuales }
      for (const campo of Object.keys(cambios)) {
        delete siguiente[campo]
      }
      return siguiente
    })
    setErrorGeneral(null)
  }

  const elegirArchivo = (evento: ChangeEvent<HTMLInputElement>) => {
    const elegido = evento.target.files?.[0] ?? null
    const problema = elegido ? validarComprobante(elegido) : undefined

    setArchivo(elegido)
    setErrores((actuales) => {
      const { archivo: _anterior, ...resto } = actuales
      return problema ? { ...resto, archivo: problema } : resto
    })
    setErrorGeneral(null)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral(ERROR_GENERICO)
      return
    }

    const { codigo, detail, errores: deApi = [] } = error.problema

    switch (codigo) {
      case 'VALIDACION': {
        const deCampos = deApi.filter(({ campo }) => CAMPOS_FORMULARIO.has(campo))
        const ajenos = deApi
          .filter(({ campo }) => !CAMPOS_FORMULARIO.has(campo))
          .map(({ mensaje }) => mensaje)

        setErrores(Object.fromEntries(deCampos.map(({ campo, mensaje }) => [campo, mensaje])))
        setErrorGeneral([detail, ...ajenos].join(' '))
        return
      }
      case 'ARCHIVO_DEMASIADO_GRANDE':
      case 'TIPO_ARCHIVO_NO_PERMITIDO':
        setErrores({ archivo: detail })
        return
      case 'VAL-028':
      case 'RF-139':
        // El cobro cambió o su monto no coincide: se refresca la tabla para ver el estado real.
        setErrorGeneral(detail)
        void clienteConsultas.invalidateQueries({ queryKey: clavesTesoreria.todas })
        return
      default:
        setErrorGeneral(detail || ERROR_GENERICO)
    }
  }

  const validar = (): Record<string, string> => {
    const encontrados: Record<string, string> = {}

    if (!valores.medio) {
      encontrados.medio = 'Elige el medio de pago.'
    }
    if (valores.fechaPago && valores.fechaPago > hoy) {
      encontrados.fechaPago = 'La fecha del pago no puede ser futura.'
    }
    if (valores.referencia.trim().length > LONGITUD_MAXIMA_REFERENCIA) {
      encontrados.referencia = 'La referencia admite hasta 60 caracteres.'
    }

    const problemaArchivo = validarComprobante(archivo)

    if (problemaArchivo) {
      encontrados.archivo = problemaArchivo
    }

    return encontrados
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0 || !valores.medio || !archivo) {
      return
    }

    const referencia = valores.referencia.trim()

    registrar.mutate(
      {
        cobroId: cobro.id,
        medio: valores.medio,
        monto: cobro.monto,
        archivo,
        ...(valores.fechaPago ? { fechaPago: valores.fechaPago } : {}),
        ...(referencia ? { referencia } : {}),
      },
      { onSuccess: onRegistrado, onError: alFallar },
    )
  }

  const descritoArchivo =
    [errores.archivo ? idArchivoError : idArchivoAyuda, archivo ? idArchivoElegido : undefined]
      .filter(Boolean)
      .join(' ')

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
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate>
        <div className="flex items-start gap-3 border-b border-border p-5">
          <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
            <ReceiptText aria-hidden="true" className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 id={idTitulo} className="text-headline-md">
              Registrar pago
            </h2>
            <p className="mt-1 text-body-sm text-text-muted">
              Pago recibido fuera del sistema, en efectivo, transferencia u otro medio.
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-5 p-5">
          <section
            aria-label="Cobro a liquidar"
            className="grid gap-4 rounded-md border border-border bg-background p-4 sm:grid-cols-[1fr_auto] sm:items-start"
          >
            <dl className="flex min-w-0 flex-col gap-3">
              <div>
                <dt className="text-label-sm text-text-muted">Concepto</dt>
                <dd className="mt-0.5 break-words text-headline-sm text-text-main">
                  {cobro.concepto}
                </dd>
              </div>
              <div>
                <dt className="text-label-sm text-text-muted">Destino</dt>
                <dd className="mt-0.5 break-words text-body-md text-text-main">
                  {cobro.equipo?.nombre ?? cobro.club.nombre}
                  <span className="block text-body-sm text-text-muted">
                    {cobro.equipo ? cobro.club.nombre : 'Cargo al club'}
                  </span>
                </dd>
              </div>
              <div>
                <dt className="sr-only">Estado</dt>
                <dd>
                  <Insignia variante={estado.variante}>{estado.texto}</Insignia>
                </dd>
              </div>
            </dl>
            <dl className="border-t border-border pt-3 sm:border-t-0 sm:border-l sm:pt-0 sm:pl-4 sm:text-right">
              <dt className="text-label-sm text-text-muted">Importe a liquidar</dt>
              <dd className="mt-1 font-mono text-headline-md tabular-nums text-text-main">
                {formatearMonto(cobro.monto)}
              </dd>
              <dd className="text-body-sm text-text-muted">MXN</dd>
            </dl>
          </section>

          <fieldset
            aria-describedby={errores.medio ? idMedioError : undefined}
            className="flex flex-col gap-1.5"
          >
            <legend className="mb-1.5 text-label-md text-text-main">
              Medio de pago
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            </legend>
            <div className="grid gap-2 sm:grid-cols-3">
              {MEDIOS.map((medio) => (
                <label
                  key={medio}
                  className={[
                    'flex min-h-11 cursor-pointer items-center gap-2.5 rounded-md border bg-surface px-3 text-body-md text-text-main transition-colors',
                    'hover:bg-background has-checked:border-primary has-checked:bg-primary/8 has-disabled:cursor-not-allowed has-disabled:opacity-60',
                    errores.medio ? 'border-error' : 'border-border',
                  ].join(' ')}
                >
                  <input
                    type="radio"
                    name={`${id}-medio`}
                    value={medio}
                    required
                    checked={valores.medio === medio}
                    disabled={guardando}
                    aria-invalid={errores.medio ? true : undefined}
                    onChange={() => cambiar({ medio })}
                    className="size-[18px] shrink-0 accent-primary"
                  />
                  {etiquetaMedioPago[medio]}
                </label>
              ))}
            </div>
            {errores.medio && (
              <p id={idMedioError} role="alert" className="text-body-sm text-error-strong">
                {errores.medio}
              </p>
            )}
          </fieldset>

          <div className="grid gap-3 sm:grid-cols-2">
            <CampoTexto
              id={`${id}-fecha`}
              etiqueta="Fecha del pago"
              type="date"
              max={hoy}
              disabled={guardando}
              error={errores.fechaPago}
              ayuda="Opcional; si la dejas vacía, se usa el momento del registro."
              value={valores.fechaPago}
              onChange={(evento) => cambiar({ fechaPago: evento.target.value })}
            />
            <CampoTexto
              id={`${id}-referencia`}
              etiqueta="Referencia"
              maxLength={LONGITUD_MAXIMA_REFERENCIA}
              autoComplete="off"
              disabled={guardando}
              placeholder="Ej. SPEI-98432170 o REC-1184"
              error={errores.referencia}
              value={valores.referencia}
              onChange={(evento) => cambiar({ referencia: evento.target.value })}
            />
          </div>

          <div className="flex flex-col gap-1.5">
              <label id={idArchivoEtiqueta} htmlFor={idArchivo} className="text-label-md text-text-main">
              Comprobante
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            </label>
            <label
              className={[
                'flex min-h-11 cursor-pointer flex-wrap items-center gap-3 rounded-md border border-dashed bg-background px-3 py-2.5 transition-colors hover:border-primary',
                'has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary has-disabled:cursor-not-allowed has-disabled:opacity-60',
                errores.archivo ? 'border-error' : 'border-border',
              ].join(' ')}
            >
              <input
                id={idArchivo}
                aria-labelledby={idArchivoEtiqueta}
                type="file"
                required
                accept={ACEPTA_COMPROBANTE}
                disabled={guardando}
                aria-invalid={errores.archivo ? true : undefined}
                aria-describedby={descritoArchivo}
                onChange={elegirArchivo}
                className="sr-only"
              />
              <span
                aria-hidden="true"
                className="inline-flex min-h-9 shrink-0 items-center gap-2 rounded-md border border-border bg-surface px-3 text-label-md text-text-main"
              >
                <Upload className="size-4" />
                {archivo ? 'Cambiar archivo' : 'Elegir archivo'}
              </span>
              {archivo ? (
                <span
                  id={idArchivoElegido}
                  className="flex min-w-0 flex-1 items-center gap-2 text-body-md text-text-main"
                >
                  <FileText aria-hidden="true" className="size-4 shrink-0 text-text-muted" />
                  <span className="min-w-0 truncate">{archivo.name}</span>
                  <span className="shrink-0 font-mono text-mono-data tabular-nums text-text-muted">
                    {tamanoLegible(archivo.size)}
                  </span>
                </span>
              ) : (
                <span aria-hidden="true" className="text-body-sm text-text-muted">
                  Ningún archivo elegido
                </span>
              )}
            </label>
            {errores.archivo ? (
              <p id={idArchivoError} role="alert" className="text-body-sm text-error-strong">
                {errores.archivo}
              </p>
            ) : (
              <p id={idArchivoAyuda} className="text-body-sm text-text-muted">
                JPEG, PNG, WebP o PDF de hasta 5 MB.
              </p>
            )}
          </div>

          <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-sm text-info-strong">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            Se registra el pago total; el cobro quedará PAGADO y no admitirá otro pago.
          </p>

          {errorGeneral && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorGeneral}
            </p>
          )}

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
              Cancelar
            </Button>
            <Button type="submit" icono={Check} cargando={guardando}>
              Registrar pago
            </Button>
          </div>
        </div>
      </form>
    </dialog>
  )
}
