import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  Check,
  CircleCheck,
  CircleX,
  CreditCard,
  Lock,
  RotateCw,
  ShieldCheck,
  X,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import {
  clavesTesoreria,
  useConfirmarPagoSimulado,
  useIniciarPagoSimulado,
} from '@/features/tesoreria/api'
import { estadoCobro, etiquetaTipoCobro } from '@/features/tesoreria/etiquetas'
import {
  formatearFecha,
  formatearFechaHora,
  formatearMonto,
} from '@/features/tesoreria/fechas'
import type {
  Cobro,
  IntentoPago,
  ResultadoPagoSimulado,
} from '@/features/tesoreria/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  cobro: Cobro
  onCerrar: () => void
}

type Paso = 1 | 2 | 3

const PASOS = [
  { numero: 1, titulo: 'Cobro' },
  { numero: 2, titulo: 'Simulación' },
  { numero: 3, titulo: 'Comprobante' },
] as const

function detalleDe(error: unknown, respaldo: string): string {
  return error instanceof ApiError ? error.problema.detail : respaldo
}

/** Código del problema RFC 9457 o `null` si el fallo no vino de la API. */
function codigoDe(error: unknown): string | null {
  return error instanceof ApiError ? error.problema.codigo : null
}

function FilaDato({
  etiqueta,
  valor,
  mono = false,
}: {
  etiqueta: string
  valor: ReactNode
  mono?: boolean
}) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="shrink-0 text-body-sm text-text-muted">{etiqueta}</dt>
      <dd
        className={[
          'min-w-0 break-words text-right text-body-md text-text-main',
          mono ? 'font-mono tabular-nums' : '',
        ].join(' ')}
      >
        {valor}
      </dd>
    </div>
  )
}

function MensajeError({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
      {children}
    </p>
  )
}

function AvisoSimulado() {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-primary/20 bg-primary/8 p-3">
      <ShieldCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-primary" />
      <div>
        <p className="text-headline-sm text-primary">
          Pago simulado: no se realiza ningún cargo
        </p>
        <p className="mt-1 text-body-sm text-text-muted">
          Entorno demostrativo de la Liga. No se piden datos bancarios ni se afecta ninguna
          cuenta real.
        </p>
      </div>
    </div>
  )
}

/**
 * Flujo móvil de pago simulado de F-34 (H-30.2, RF-138, DEC-37): resumen del cobro,
 * simulación con referencia ficticia y comprobante. Solo usa los datos que expone la API;
 * no muestra folios, descargas ni cargos que el backend no genere. Si el cobro ya no admite el
 * pago en línea (`VAL-028`, p. ej. porque venció, DEC-60) o lo resolvió otra sesión
 * (`CONCURRENCIA`), refresca los cobros y adeudos. Tras un `VAL-028` el modal ya no ofrece
 * ninguna acción de pago en ningún paso: sin iniciar ni reintentar en el cobro y sin aprobar ni
 * rechazar en la simulación; solo muestra el `detail` y deja cancelar, volver o cerrar (B-31.2).
 */
export function ModalPagoSimulado({ cobro, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const clienteConsultas = useQueryClient()
  const iniciar = useIniciarPagoSimulado()
  const confirmar = useConfirmarPagoSimulado()
  const [paso, setPaso] = useState<Paso>(1)
  const [intento, setIntento] = useState<IntentoPago | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [resultadoEnCurso, setResultadoEnCurso] =
    useState<ResultadoPagoSimulado | null>(null)
  const [cobroNoPagable, setCobroNoPagable] = useState(false)
  const refTituloPaso = useRef<HTMLHeadingElement>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idPaso = `${id}-paso`
  const estado = estadoCobro(cobro.estado)
  const procesando = iniciar.isPending || confirmar.isPending
  const aprobado = intento?.estado === 'APROBADO'
  const resueltoEn = intento?.resueltoEn ?? intento?.iniciadoEn

  useEffect(() => {
    refTituloPaso.current?.focus()
  }, [paso])

  const iniciarIntento = () => {
    setError(null)

    // Si ya se inició y sigue pendiente, se reutiliza el mismo intento.
    if (intento?.estado === 'PENDIENTE') {
      setPaso(2)
      return
    }

    iniciar.mutate(cobro.id, {
      onSuccess: (nuevo) => {
        setIntento(nuevo)
        setPaso(2)
      },
      onError: (fallo) =>
        alFallar(fallo, 'No se pudo iniciar el pago simulado. Intenta de nuevo.'),
    })
  }

  const confirmarResultado = (resultado: ResultadoPagoSimulado) => {
    if (!intento) {
      return
    }

    setError(null)
    setResultadoEnCurso(resultado)
    confirmar.mutate(
      { intentoId: intento.id, resultado },
      {
        onSuccess: (resuelto) => {
          setIntento(resuelto)
          setPaso(3)
        },
        onError: (fallo) =>
          alFallar(fallo, 'No se pudo confirmar el pago simulado. Intenta de nuevo.'),
        onSettled: () => setResultadoEnCurso(null),
      },
    )
  }

  /**
   * Trata el fallo de iniciar o confirmar por su `codigo` (nunca por el texto) y muestra siempre
   * el `detail` de la API.
   */
  const alFallar = (fallo: unknown, respaldo: string) => {
    switch (codigoDe(fallo)) {
      case 'VAL-028':
        // El cobro cambió de estado (pagado o vencido): ya no admite el pago simulado.
        setCobroNoPagable(true)
        void clienteConsultas.invalidateQueries({ queryKey: clavesTesoreria.todas })
        break
      case 'CONCURRENCIA':
        // Otra sesión resolvió el intento: se refresca la tabla para ver el estado real.
        void clienteConsultas.invalidateQueries({ queryKey: clavesTesoreria.todas })
        break
    }
    setError(detalleDe(fallo, respaldo))
  }

  const volverAlCobro = () => {
    // Tras un VAL-028 se conserva el detalle: el paso 1 ya no ofrece pagar y lo explica.
    if (!cobroNoPagable) {
      setError(null)
    }
    setPaso(1)
  }

  const destino = (
    <>
      <span className="block">{cobro.equipo?.nombre ?? cobro.club.nombre}</span>
      <span className="block text-body-sm text-text-muted">
        {cobro.equipo ? cobro.club.nombre : 'Cargo al club'}
      </span>
    </>
  )

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!procesando) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex items-start justify-between gap-3 border-b border-border p-5">
        <div className="flex min-w-0 items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
            <CreditCard aria-hidden="true" className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 id={idTitulo} className="text-headline-md text-text-main">
              Pago simulado
            </h2>
            <p className="mt-1 break-words text-body-sm text-text-muted">
              {cobro.concepto}
            </p>
          </div>
        </div>
        <button
          type="button"
          aria-label="Cerrar pago simulado"
          onClick={onCerrar}
          disabled={procesando}
          className="inline-flex size-11 shrink-0 items-center justify-center rounded-md border border-border text-text-main transition-colors hover:bg-background disabled:cursor-not-allowed disabled:opacity-50"
        >
          <X aria-hidden="true" className="size-5" />
        </button>
      </div>

      <div className="flex flex-col gap-4 p-5">
        <ol
          aria-label="Pasos del pago simulado"
          className="grid grid-cols-3 gap-1 rounded-lg bg-background p-1.5"
        >
          {PASOS.map(({ numero, titulo }) => {
            const activo = paso === numero
            const completado = paso > numero

            return (
              <li
                key={numero}
                aria-current={activo ? 'step' : undefined}
                className={[
                  'flex min-h-11 min-w-0 flex-col items-center justify-center gap-1 rounded-md px-0.5 text-center transition-colors',
                  activo
                    ? 'bg-primary text-on-primary'
                    : completado
                      ? 'text-primary'
                      : 'text-text-muted',
                ].join(' ')}
              >
                <span
                  aria-hidden="true"
                  className={[
                    'grid size-4 shrink-0 place-items-center rounded-full text-label-sm',
                    activo
                      ? 'bg-on-primary text-primary'
                      : completado
                        ? 'bg-primary/10 text-primary'
                        : 'bg-border text-text-muted',
                  ].join(' ')}
                >
                  {completado ? <Check className="size-3" /> : numero}
                </span>
                <span className="max-w-full text-label-sm font-semibold tracking-normal">
                  <span className="sr-only">Paso {numero} de 3: </span>
                  {titulo}
                </span>
              </li>
            )
          })}
        </ol>

        <AvisoSimulado />

        {paso === 1 && (
          <>
            <section aria-labelledby={`${idPaso}-cobro`} className="flex flex-col gap-3">
              <div className="flex items-center justify-between gap-2">
                <h3
                  id={`${idPaso}-cobro`}
                  ref={refTituloPaso}
                  tabIndex={-1}
                  className="text-headline-sm text-text-main focus:outline-none"
                >
                  Cobro a pagar
                </h3>
                <Insignia variante={estado.variante}>{estado.texto}</Insignia>
              </div>
              <dl className="flex flex-col gap-2 rounded-lg border border-border bg-background p-4">
                <FilaDato etiqueta="Tipo" valor={etiquetaTipoCobro[cobro.tipo]} />
                <FilaDato etiqueta="Concepto" valor={cobro.concepto} />
                <FilaDato etiqueta="Destino" valor={destino} />
                <FilaDato
                  etiqueta="Monto"
                  valor={`${formatearMonto(cobro.monto)} MXN`}
                  mono
                />
                <FilaDato
                  etiqueta="Fecha límite"
                  valor={formatearFecha(cobro.fechaLimite)}
                  mono
                />
              </dl>
            </section>

            {error && <MensajeError>{error}</MensajeError>}

            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button variante="secundario" onClick={onCerrar} disabled={procesando}>
                Cancelar
              </Button>
              {!cobroNoPagable && (
                <Button
                  icono={CreditCard}
                  onClick={iniciarIntento}
                  cargando={iniciar.isPending}
                >
                  {cobro.estado === 'RECHAZADO'
                    ? 'Reintentar pago simulado'
                    : 'Iniciar pago simulado'}
                </Button>
              )}
            </div>
          </>
        )}

        {paso === 2 && intento && (
          <>
            <section
              aria-labelledby={`${idPaso}-simulacion`}
              className="flex flex-col gap-3"
            >
              <h3
                id={`${idPaso}-simulacion`}
                ref={refTituloPaso}
                tabIndex={-1}
                className="text-headline-sm text-text-main focus:outline-none"
              >
                Simulación de pago
              </h3>

              <div className="rounded-lg border border-border bg-background p-4">
                <p className="text-label-sm uppercase text-text-muted">
                  Referencia de simulación
                </p>
                <p className="mt-1 break-all font-mono text-headline-sm text-text-main">
                  {intento.referencia}
                </p>
              </div>

              <dl className="flex flex-col gap-2 rounded-lg border border-border bg-background p-4">
                <FilaDato etiqueta="Concepto" valor={cobro.concepto} />
                <FilaDato etiqueta="Destino" valor={destino} />
                <FilaDato
                  etiqueta="Monto"
                  valor={`${formatearMonto(cobro.monto)} MXN`}
                  mono
                />
              </dl>

              <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-sm text-info-strong">
                <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                No se requieren datos bancarios ni claves de seguridad. La referencia la
                genera la Liga y solo identifica esta simulación.
              </p>
            </section>

            {error && <MensajeError>{error}</MensajeError>}

            <div className="flex flex-col gap-2">
              {!cobroNoPagable && (
                <>
                  <Button
                    icono={CircleCheck}
                    onClick={() => confirmarResultado('APROBADO')}
                    cargando={resultadoEnCurso === 'APROBADO'}
                    disabled={confirmar.isPending}
                  >
                    Aprobar pago simulado
                  </Button>
                  <Button
                    variante="secundario"
                    icono={CircleX}
                    onClick={() => confirmarResultado('RECHAZADO')}
                    cargando={resultadoEnCurso === 'RECHAZADO'}
                    disabled={confirmar.isPending}
                  >
                    Rechazar pago simulado
                  </Button>
                </>
              )}
              <Button
                variante="secundario"
                icono={ArrowLeft}
                onClick={volverAlCobro}
                disabled={procesando}
              >
                Volver al cobro
              </Button>
            </div>
          </>
        )}

        {paso === 3 && intento && (
          <>
            <section
              aria-labelledby={`${idPaso}-resultado`}
              className="flex flex-col items-center gap-4 text-center"
            >
              <span
                className={[
                  'grid size-16 place-items-center rounded-full',
                  aprobado
                    ? 'bg-success/12 text-success-strong'
                    : 'bg-error/12 text-error-strong',
                ].join(' ')}
              >
                {aprobado ? (
                  <CircleCheck aria-hidden="true" className="size-9" />
                ) : (
                  <CircleX aria-hidden="true" className="size-9" />
                )}
              </span>
              <div>
                <h3
                  id={`${idPaso}-resultado`}
                  ref={refTituloPaso}
                  tabIndex={-1}
                  className="text-headline-lg text-text-main focus:outline-none"
                >
                  {aprobado ? 'Pago registrado' : 'El pago fue rechazado'}
                </h3>
                <p className="mt-1 text-body-md text-text-muted">
                  {aprobado
                    ? 'El cobro quedó pagado y el movimiento se registró como pago en línea simulado.'
                    : 'La simulación no generó ningún cargo. El cobro quedó rechazado y puede reintentarse.'}
                </p>
              </div>
            </section>

            <section
              aria-labelledby={`${idPaso}-comprobante`}
              className="rounded-lg border border-border bg-background p-4 text-left"
            >
              <div className="flex items-center justify-between gap-2 border-b border-border pb-2">
                <h4
                  id={`${idPaso}-comprobante`}
                  className="text-label-md font-semibold text-text-main"
                >
                  Comprobante de pago simulado
                </h4>
                <Insignia variante={aprobado ? 'exito' : 'error'}>
                  {aprobado ? 'Aprobado' : 'Rechazado'}
                </Insignia>
              </div>
              <dl className="mt-3 flex flex-col gap-2">
                <FilaDato etiqueta="Referencia" valor={intento.referencia} mono />
                <FilaDato etiqueta="Concepto" valor={cobro.concepto} />
                <FilaDato etiqueta="Destino" valor={destino} />
                <FilaDato
                  etiqueta="Monto"
                  valor={`${formatearMonto(cobro.monto)} MXN`}
                  mono
                />
                <FilaDato
                  etiqueta="Fecha de resolución"
                  valor={resueltoEn ? formatearFechaHora(resueltoEn) : '—'}
                  mono
                />
              </dl>
            </section>

            {error && <MensajeError>{error}</MensajeError>}

            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button variante="secundario" onClick={onCerrar} disabled={procesando}>
                Cerrar
              </Button>
              {!aprobado && (
                <Button
                  icono={RotateCw}
                  onClick={iniciarIntento}
                  cargando={iniciar.isPending}
                >
                  Reintentar pago
                </Button>
              )}
            </div>
          </>
        )}
      </div>
    </dialog>
  )
}
