import { useId, useState } from 'react'
import { Save } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useGuardarTarifa } from '@/features/tesoreria/api'
import { formatearMonto } from '@/features/tesoreria/fechas'
import { CONCEPTOS_TARIFA, leerMonto, textoDeMonto } from '@/features/tesoreria/tarifas'
import type { ConceptoTarifa, ReferenciaCobro, Tarifa } from '@/features/tesoreria/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  temporadaId: string
  categoria: ReferenciaCobro
  tarifas: Partial<Record<ConceptoTarifa, Tarifa>>
  soloLectura: boolean
  onGuardada: (mensaje: string) => void
  /** La temporada se cerró mientras se editaba (`409 RF-003`): hay que releer el contexto. */
  onTemporadaCerrada: () => void
}

type PorConcepto<T> = Record<ConceptoTarifa, T>

const SIN_ERRORES: PorConcepto<string | undefined> = { ARBITRAJE: undefined, CANCHA: undefined }

function textosIniciales(tarifas: Partial<Record<ConceptoTarifa, Tarifa>>): PorConcepto<string> {
  return { ARBITRAJE: textoDeMonto(tarifas.ARBITRAJE), CANCHA: textoDeMonto(tarifas.CANCHA) }
}

/**
 * Fila de una categoría en la tabla de tarifas (B-29.2, RF-135): un monto por concepto y un botón
 * que guarda solo lo que cambió. Una tarifa existente no se puede dejar vacía porque la API no las
 * borra; en una temporada cerrada la fila es de solo lectura (RF-003).
 */
export function FilaTarifas({
  temporadaId,
  categoria,
  tarifas,
  soloLectura,
  onGuardada,
  onTemporadaCerrada,
}: Props) {
  const id = useId()
  const guardar = useGuardarTarifa()
  const [textos, setTextos] = useState<PorConcepto<string>>(() => textosIniciales(tarifas))
  const [errores, setErrores] = useState<PorConcepto<string | undefined>>(SIN_ERRORES)
  const [errorFila, setErrorFila] = useState<string | null>(null)

  const cambiado = (concepto: ConceptoTarifa): boolean => {
    const vigente = tarifas[concepto]
    const leido = leerMonto(textos[concepto])

    if (leido.tipo === 'vacio') {
      return vigente !== undefined
    }
    return leido.tipo === 'invalido' || vigente?.monto !== leido.monto
  }

  const pendientes = CONCEPTOS_TARIFA.filter(({ valor }) => cambiado(valor))

  const alGuardar = async () => {
    const nuevosErrores: PorConcepto<string | undefined> = { ...SIN_ERRORES }
    const montos: { concepto: ConceptoTarifa; texto: string; monto: number }[] = []

    for (const { valor, texto } of pendientes) {
      const leido = leerMonto(textos[valor])

      if (leido.tipo === 'invalido') {
        nuevosErrores[valor] = leido.mensaje
      } else if (leido.tipo === 'vacio') {
        nuevosErrores[valor] = `La tarifa de ${texto.toLowerCase()} ya existe: escribe su nuevo monto.`
      } else {
        montos.push({ concepto: valor, texto, monto: leido.monto })
      }
    }

    setErrores(nuevosErrores)
    setErrorFila(null)
    const primero = CONCEPTOS_TARIFA.find(({ valor }) => nuevosErrores[valor])
    if (primero) {
      document.getElementById(`${id}-${primero.valor}`)?.focus()
      return
    }

    const guardados: string[] = []
    for (const { concepto, texto, monto } of montos) {
      try {
        await guardar.mutateAsync({
          temporadaId,
          categoriaId: categoria.id,
          concepto,
          monto,
          tarifaId: tarifas[concepto]?.id,
        })
        guardados.push(`${texto.toLowerCase()} ${formatearMonto(monto)}`)
        setTextos((actuales) => ({ ...actuales, [concepto]: monto.toFixed(2) }))
      } catch (error) {
        if (error instanceof ApiError) {
          const deCampo = error.mensajeDe('monto')
          if (deCampo) {
            setErrores((actuales) => ({ ...actuales, [concepto]: deCampo }))
          } else {
            setErrorFila(error.problema.detail ?? error.message)
          }
          if (error.codigo === 'RF-003') {
            onTemporadaCerrada()
          }
        } else {
          setErrorFila('No se pudo guardar la tarifa. Revisa tu conexión e inténtalo de nuevo.')
        }
        return
      }
    }

    if (guardados.length > 0) {
      onGuardada(`Tarifas de «${categoria.nombre}» guardadas: ${guardados.join(' y ')}.`)
    }
  }

  return (
    <tr className="border-b border-border last:border-b-0 max-sm:grid max-sm:grid-cols-2 max-sm:gap-3 max-sm:p-4 sm:hover:bg-background">
      <th
        scope="row"
        className="text-left align-top text-headline-sm text-text-main max-sm:col-span-2 sm:px-4 sm:py-3 sm:pt-6 sm:text-label-md"
      >
        {categoria.nombre}
        {errorFila && (
          <p role="alert" className="mt-1 max-w-sm text-body-sm font-normal text-error-strong">
            {errorFila}
          </p>
        )}
      </th>

      {CONCEPTOS_TARIFA.map(({ valor, texto }) => {
        const idCampo = `${id}-${valor}`
        const error = errores[valor]

        return (
          <td key={valor} className="align-top sm:w-52 sm:px-4 sm:py-3">
            {soloLectura ? (
              <p className="flex flex-col gap-1 sm:min-h-11 sm:flex-row sm:items-center sm:justify-end">
                <span className="text-label-md text-text-muted sm:sr-only">{texto}</span>
                {tarifas[valor] ? (
                  <span className="font-mono text-mono-data tabular-nums text-text-main">
                    {formatearMonto(tarifas[valor].monto)}
                  </span>
                ) : (
                  <span className="text-body-md text-text-muted">Sin tarifa</span>
                )}
              </p>
            ) : (
              <div className="flex flex-col gap-1.5 sm:items-end">
                <label htmlFor={idCampo} className="text-label-md text-text-main sm:sr-only">
                  {texto}
                  <span className="sr-only"> de {categoria.nombre}</span>
                </label>
                <div className="relative flex w-full items-center sm:w-44">
                  <span
                    aria-hidden="true"
                    className="pointer-events-none absolute left-3 font-mono text-mono-data text-text-muted"
                  >
                    $
                  </span>
                  <input
                    id={idCampo}
                    type="text"
                    inputMode="decimal"
                    autoComplete="off"
                    placeholder="Sin tarifa"
                    value={textos[valor]}
                    onChange={(evento) => {
                      const nuevo = evento.target.value
                      setTextos((actuales) => ({ ...actuales, [valor]: nuevo }))
                      setErrores((actuales) => ({ ...actuales, [valor]: undefined }))
                    }}
                    aria-invalid={error ? true : undefined}
                    aria-describedby={error ? `${idCampo}-error` : undefined}
                    className={[
                      'min-h-11 w-full rounded-md border bg-surface pl-7 pr-3 text-right font-mono text-mono-data tabular-nums text-text-main placeholder:font-sans placeholder:text-body-md placeholder:text-text-muted/70',
                      error ? 'border-error' : cambiado(valor) ? 'border-primary' : 'border-border',
                    ].join(' ')}
                  />
                </div>
                {error && (
                  <p id={`${idCampo}-error`} role="alert" className="text-body-sm text-error-strong sm:w-44 sm:text-right">
                    {error}
                  </p>
                )}
              </div>
            )}
          </td>
        )
      })}

      {!soloLectura && (
        <td className="align-top max-sm:col-span-2 sm:w-px sm:whitespace-nowrap sm:px-4 sm:py-3">
          <div className="flex sm:justify-end">
            <Button
              variante="secundario"
              icono={Save}
              cargando={guardar.isPending}
              disabled={pendientes.length === 0}
              onClick={() => void alGuardar()}
              aria-label={`Guardar tarifas de ${categoria.nombre}`}
              className="max-sm:w-full"
            >
              Guardar
            </Button>
          </div>
        </td>
      )}
    </tr>
  )
}
