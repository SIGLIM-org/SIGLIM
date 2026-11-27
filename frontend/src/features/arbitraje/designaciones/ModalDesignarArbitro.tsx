import { useId, useMemo, useState, type FormEvent } from 'react'
import { Search, UserCheck } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { MAXIMO_MOTIVO, MINIMO_MOTIVO } from '@/components/ui/ModalMotivo'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useCandidatos, useDesignar } from '@/features/arbitraje/designaciones/api'
import { OpcionCandidato } from '@/features/arbitraje/designaciones/OpcionCandidato'
import {
  coincideBusqueda,
  nombreArbitro,
  nombrePartido,
  ordenarCandidatos,
  textoFuncion,
} from '@/features/arbitraje/designaciones/etiquetas'
import type {
  Designacion,
  FuncionArbitral,
  PartidoDesignable,
} from '@/features/arbitraje/designaciones/tipos'
import { formatearFechaCorta, formatearHora } from '@/features/programacion/fechas'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  partido: PartidoDesignable
  funcion: FuncionArbitral
  /** Central vigente que se sustituye; exige motivo (DEC-202). */
  sustituye: Designacion | null
  onCerrar: () => void
  onDesignado: (nombre: string) => void
}

/**
 * Diálogo de designación de F-24 (H-14.2, DEC-210). Lista los candidatos de DEC-204: los elegibles
 * primero, por carga y nombre; los no elegibles con el radio deshabilitado y su causa escrita
 * debajo (VAL-019, RF-039, ya designado), y la advertencia RF-022 en ámbar, que no impide elegir.
 * Al sustituir al central pide el motivo del cambio (RN-021). Un 409 o 422 del backend se muestra
 * aquí y la lista se vuelve a pedir.
 */
export function ModalDesignarArbitro({ partido, funcion, sustituye, onCerrar, onDesignado }: Props) {
  const dialogoRef = useDialogoModal()
  const candidatos = useCandidatos(partido.id, true)
  const designar = useDesignar(partido.id)
  const [busqueda, setBusqueda] = useState('')
  const [seleccion, setSeleccion] = useState<string | null>(null)
  const [motivo, setMotivo] = useState('')
  const [errorSeleccion, setErrorSeleccion] = useState(false)
  const [errorMotivo, setErrorMotivo] = useState<string | null>(null)
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idDescripcion = `${id}-descripcion`
  const idMotivo = `${id}-motivo`
  const idAyudaMotivo = `${id}-motivo-ayuda`
  const idErrorMotivo = `${id}-motivo-error`
  const idErrorSeleccion = `${id}-seleccion-error`
  const partidoTexto = nombrePartido(partido)
  const pideMotivo = funcion === 'CENTRAL' && sustituye !== null
  const longitudMotivo = motivo.trim().length

  const ordenados = useMemo(() => ordenarCandidatos(candidatos.data ?? []), [candidatos.data])
  const visibles = ordenados.filter((candidato) => coincideBusqueda(candidato, busqueda))
  // Tras refrescar la lista, un árbitro que ya no es elegible deja de contar como elegido.
  const elegido = ordenados.find(
    (candidato) => candidato.arbitro.id === seleccion && candidato.elegible,
  )

  const alConfirmar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    if (!elegido) {
      setErrorSeleccion(true)
      return
    }
    if (pideMotivo && (longitudMotivo < MINIMO_MOTIVO || longitudMotivo > MAXIMO_MOTIVO)) {
      setErrorMotivo(
        `Escribe un motivo de ${MINIMO_MOTIVO} a ${MAXIMO_MOTIVO} caracteres para cambiar al árbitro central.`,
      )
      return
    }

    designar.mutate(
      {
        arbitroId: elegido.arbitro.id,
        funcion,
        ...(pideMotivo ? { motivo: motivo.trim() } : {}),
      },
      {
        onSuccess: () => onDesignado(nombreArbitro(elegido)),
        onError: (fallo) => {
          const detalle =
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo designar al árbitro. Intenta de nuevo.'

          if (pideMotivo && fallo instanceof ApiError && fallo.problema.codigo === 'RN-021') {
            setErrorMotivo(detalle)
            return
          }
          setErrorGeneral(detalle)
        },
      },
    )
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      aria-describedby={idDescripcion}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!designar.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-xl overflow-hidden rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form
        onSubmit={alConfirmar}
        noValidate
        className="flex max-h-[calc(100dvh-2rem)] flex-col"
      >
        <div className="flex flex-col gap-1 border-b border-border p-4 sm:p-6 sm:pb-4">
          <h2 id={idTitulo} className="text-balance text-headline-md">
            Designar {textoFuncion[funcion]} · {partidoTexto}
          </h2>
          <p id={idDescripcion} className="text-body-md text-text-muted">
            {formatearFechaCorta(partido.inicio)},{' '}
            <span className="font-mono tabular-nums">{formatearHora(partido.inicio)}</span>, en{' '}
            {partido.cancha.nombre}.{' '}
            {sustituye
              ? `Sustituye a ${nombreArbitro(sustituye)}; su designación queda en el historial con el motivo.`
              : 'Los árbitros que no pueden designarse muestran la causa.'}
          </p>
        </div>

        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4 sm:px-6">
          <CampoTexto
            id={`${id}-busqueda`}
            etiqueta="Buscar árbitro"
            type="search"
            placeholder="Nombre o club"
            autoComplete="off"
            value={busqueda}
            onChange={(evento) => setBusqueda(evento.target.value)}
            accion={<Search aria-hidden="true" className="mr-2 size-4 text-text-muted" />}
          />

          {candidatos.isPending && (
            <EstadoCarga etiqueta="Cargando árbitros validados…" filas={4} columnas={2} />
          )}

          {candidatos.isError && (
            <ErrorConsulta
              error={candidatos.error}
              onReintentar={() => void candidatos.refetch()}
            />
          )}

          {candidatos.data && (
            <fieldset
              aria-describedby={errorSeleccion && !elegido ? idErrorSeleccion : undefined}
              className="flex min-w-0 flex-col gap-2"
            >
              <legend className="mb-2 text-label-md text-text-main">
                Árbitros validados{' '}
                <span className="font-normal text-text-muted">
                  ({visibles.length} de {ordenados.length})
                </span>
              </legend>

              {errorSeleccion && !elegido && (
                <p id={idErrorSeleccion} role="alert" className="text-body-sm text-error-strong">
                  Elige un árbitro elegible.
                </p>
              )}

              {ordenados.length === 0 ? (
                <p className="text-body-md text-text-muted">
                  La liga no tiene árbitros validados. La Comisión los valida en «Árbitros».
                </p>
              ) : visibles.length === 0 ? (
                <p className="text-body-md text-text-muted">
                  Ningún árbitro coincide con «{busqueda.trim()}».
                </p>
              ) : (
                <ul className="flex flex-col gap-2">
                  {visibles.map((candidato) => (
                    <OpcionCandidato
                      key={candidato.arbitro.id}
                      candidato={candidato}
                      grupo={`${id}-arbitro`}
                      elegido={elegido?.arbitro.id === candidato.arbitro.id}
                      onElegir={() => {
                        setSeleccion(candidato.arbitro.id)
                        setErrorSeleccion(false)
                        setErrorGeneral(null)
                      }}
                    />
                  ))}
                </ul>
              )}
            </fieldset>
          )}

          {pideMotivo && (
            <div className="flex flex-col gap-1">
              <label htmlFor={idMotivo} className="text-label-md text-text-main">
                Motivo del cambio
                <span aria-hidden="true" className="ml-0.5 text-error">
                  *
                </span>
              </label>
              <textarea
                id={idMotivo}
                value={motivo}
                required
                rows={3}
                maxLength={MAXIMO_MOTIVO}
                aria-invalid={errorMotivo ? true : undefined}
                aria-describedby={errorMotivo ? `${idErrorMotivo} ${idAyudaMotivo}` : idAyudaMotivo}
                onChange={(evento) => {
                  setMotivo(evento.target.value)
                  setErrorMotivo(null)
                }}
                className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
              />
              <p
                id={idAyudaMotivo}
                className="flex justify-between gap-2 text-body-sm text-text-muted"
              >
                <span>
                  De {MINIMO_MOTIVO} a {MAXIMO_MOTIVO} caracteres. Quedará en el historial.
                </span>
                <span className="font-mono tabular-nums">
                  {longitudMotivo}/{MAXIMO_MOTIVO}
                </span>
              </p>
              {errorMotivo && (
                <p id={idErrorMotivo} role="alert" className="text-body-sm text-error-strong">
                  {errorMotivo}
                </p>
              )}
            </div>
          )}

          {errorGeneral && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorGeneral}
            </p>
          )}
        </div>

        <div className="flex flex-col-reverse gap-2 border-t border-border p-4 sm:flex-row sm:justify-end sm:px-6">
          <Button variante="secundario" onClick={onCerrar} disabled={designar.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            icono={UserCheck}
            cargando={designar.isPending}
            disabled={!candidatos.data}
          >
            Designar
          </Button>
        </div>
      </form>
    </dialog>
  )
}
