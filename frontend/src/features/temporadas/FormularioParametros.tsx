import { useId, useRef, useState, type FormEvent } from 'react'
import { CircleAlert, Save } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { ModalMotivo } from '@/components/ui/ModalMotivo'
import { useRegistrarParametros } from '@/features/temporadas/api'
import { CampoParametro } from '@/features/temporadas/CampoParametro'
import { CasillaParametro } from '@/features/temporadas/CasillaParametro'
import { EditorDesempates } from '@/features/temporadas/EditorDesempates'
import {
  CAMPOS_DEMO,
  etiquetaCampoParametro,
  etiquetaFormatoLiguilla,
} from '@/features/temporadas/etiquetas'
import {
  aPeticion,
  categoriasInvalidasDe,
  erroresDeServidor,
  hayCambios,
  RANGOS,
  validarFormulario,
  type CampoNumerico,
  type FilaCategoriaFormulario,
  type FormularioParametros as Borrador,
} from '@/features/temporadas/parametros'
import { SeccionParametros } from '@/features/temporadas/SeccionParametros'
import { SeleccionParametro } from '@/features/temporadas/SeleccionParametro'
import { TablaParametrosCategoria } from '@/features/temporadas/TablaParametrosCategoria'
import type {
  CategoriaTemporada,
  ClasificadosLiguilla,
  FormatoLiguilla,
  RegistrarParametrosPeticion,
  VersionParametros,
} from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  temporadaId: string
  vigente: VersionParametros | null
  /** Borrador inicial: la vigente o, en la primera versión, los predeterminados (D6). */
  inicial: Borrador
  categorias: CategoriaTemporada[]
  onGuardada: (version: VersionParametros) => void
}

const CLASIFICADOS: readonly ClasificadosLiguilla[] = [2, 4, 8, 16, 32]

const AVISO_NO_RETROACTIVO =
  'No cambian los bloques de los partidos ya programados, los plazos de las cédulas ya enviadas ni las fases y propuestas ya registradas.'
const AVISO_OFICIAL = 'La Liga confirmó estos valores (PEN-021).'

/**
 * Formulario de una versión nueva de parámetros (F-11, RF-006, RF-050, RF-100, DEC-24 a DEC-29).
 * Lleva el juego completo: nada se hereda de la versión anterior. Valida antes de abrir el modal
 * del motivo (RN-021) y el servidor vuelve a validar (D9). Los campos [DEMO] llevan «No oficial»
 * mientras el interruptor de valores oficiales esté apagado (D4).
 */
export function FormularioParametros({
  temporadaId,
  vigente,
  inicial,
  categorias,
  onGuardada,
}: Props) {
  const id = useId()
  const formularioRef = useRef<HTMLFormElement>(null)
  const registrar = useRegistrarParametros(temporadaId)
  const [borrador, setBorrador] = useState<Borrador>(inicial)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [invalidas, setInvalidas] = useState<string[]>([])
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const [confirmando, setConfirmando] = useState(false)
  const [errorModal, setErrorModal] = useState<string | null>(null)

  const primera = vigente === null
  const conCambios = hayCambios(borrador, vigente)
  const noOficial = (campo: string) => !borrador.oficial && CAMPOS_DEMO.includes(campo)

  const quitarError = (campo: string) =>
    setErrores(({ [campo]: _quitado, ...resto }) => resto)

  const cambiarNumero = (campo: CampoNumerico, valor: string) => {
    setBorrador((actual) => ({ ...actual, [campo]: valor }))
    quitarError(campo)
  }

  const cambiarFila = (
    categoriaId: string,
    campo: keyof FilaCategoriaFormulario,
    valor: string,
  ) => {
    setBorrador((actual) => {
      const fila = actual.categorias[categoriaId] ?? { duracionJuegoMin: '', bloqueCanchaMin: '' }
      return {
        ...actual,
        categorias: { ...actual.categorias, [categoriaId]: { ...fila, [campo]: valor } },
      }
    })
    setErrores(({ [`categorias.${categoriaId}.duracionJuegoMin`]: _d, [`categorias.${categoriaId}.bloqueCanchaMin`]: _b, ...resto }) => resto)
    setInvalidas((actuales) => actuales.filter((otra) => otra !== categoriaId))
  }

  /** Lleva el foco al primer campo marcado, en el orden del formulario. */
  const enfocarPrimerError = () => {
    window.requestAnimationFrame(() => {
      formularioRef.current?.querySelector<HTMLElement>('[aria-invalid="true"], [data-error-foco]')?.focus()
    })
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)
    setInvalidas([])

    const encontrados = validarFormulario(borrador)
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      setErrorGeneral('Revisa los campos marcados antes de guardar.')
      enfocarPrimerError()
      return
    }

    setErrorModal(null)
    setConfirmando(true)
  }

  const alFallar = (error: unknown, peticion: RegistrarParametrosPeticion) => {
    if (!(error instanceof ApiError)) {
      setErrorModal('No se pudo guardar la versión. Intenta de nuevo.')
      return
    }

    const { codigo, status, detail } = error.problema

    if (codigo === 'VALIDACION' && status === 400) {
      const deCampos = erroresDeServidor(error, peticion)

      if (deCampos.motivo) {
        setErrorModal(deCampos.motivo)
        return
      }

      setConfirmando(false)
      setErrores(deCampos)
      setErrorGeneral(detail)
      enfocarPrimerError()
      return
    }

    if (status === 422) {
      setConfirmando(false)
      setInvalidas(categoriasInvalidasDe(error))
      setErrorGeneral(detail)
      return
    }

    // RN-021 (motivo), RF-003 (temporada cerrada) y cualquier otro: en el modal donde ocurrió.
    setErrorModal(detail)
  }

  const confirmar = (motivo: string) => {
    setErrorModal(null)
    const peticion = aPeticion(borrador, motivo)

    registrar.mutate(peticion, {
      onSuccess: (version) => {
        setConfirmando(false)
        onGuardada(version)
      },
      onError: (error) => alFallar(error, peticion),
    })
  }

  const campo = (nombre: CampoNumerico, etiqueta: string, ayuda?: string) => (
    <CampoParametro
      id={`${id}-${nombre}`}
      etiqueta={etiqueta}
      valor={borrador[nombre]}
      onCambiar={(valor) => cambiarNumero(nombre, valor)}
      noOficial={noOficial(nombre)}
      min={RANGOS[nombre].minimo}
      max={RANGOS[nombre].maximo}
      ayuda={ayuda}
      error={errores[nombre]}
    />
  )

  const titulo = primera ? 'Guardar la versión 1' : `Guardar como versión ${vigente.version + 1}`
  const descripcion = primera
    ? 'Con estos parámetros la temporada cumple el requisito RF-100 para activarse.'
    : `La versión ${vigente.version} se conserva en el historial. La tabla de posiciones de toda la temporada se recalculará con los puntos y criterios de la versión nueva.`
  const textosAviso = [
    ...(primera ? [] : [AVISO_NO_RETROACTIVO]),
    ...(borrador.oficial ? [AVISO_OFICIAL] : []),
  ]
  const idTituloDesempates = `${id}-titulo-desempates`

  return (
    <>
      <form ref={formularioRef} onSubmit={alEnviar} noValidate className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-col gap-1 rounded-lg border border-border bg-surface p-4">
          <label
            htmlFor={`${id}-oficial`}
            className="flex min-h-11 cursor-pointer items-center gap-3 text-label-md text-text-main"
          >
            <input
              id={`${id}-oficial`}
              type="checkbox"
              role="switch"
              checked={borrador.oficial}
              aria-describedby={`${id}-oficial-ayuda`}
              onChange={(evento) =>
                setBorrador((actual) => ({ ...actual, oficial: evento.target.checked }))
              }
              className="size-[18px] shrink-0 accent-primary"
            />
            Valores confirmados por la Liga (oficiales)
          </label>
          <p id={`${id}-oficial-ayuda`} className="text-body-sm text-text-muted">
            Márcalo solo cuando la Liga haya aprobado estos valores. Mientras tanto, los campos sin
            aprobar se rotulan «No oficial» (PEN-021).
          </p>
        </div>

        <SeccionParametros titulo="Puntuación">
          <div className="grid gap-4 sm:grid-cols-3">
            {campo('puntosVictoria', etiquetaCampoParametro.puntosVictoria)}
            {campo('puntosEmpate', etiquetaCampoParametro.puntosEmpate)}
            {campo('puntosDerrota', etiquetaCampoParametro.puntosDerrota)}
          </div>
        </SeccionParametros>

        <SeccionParametros titulo="Marcador por default y cédula">
          <div className="grid gap-4 sm:grid-cols-3">
            {campo('golesDefaultGanador', etiquetaCampoParametro.golesDefaultGanador)}
            {campo('golesDefaultPerdedor', etiquetaCampoParametro.golesDefaultPerdedor)}
            {campo(
              'plazoValidacionCedulaHoras',
              'Plazo de validación de la cédula (horas)',
            )}
          </div>
        </SeccionParametros>

        <SeccionParametros
          titulo="Criterios de desempate"
          idTitulo={idTituloDesempates}
          insignia={noOficial('criteriosDesempate') && <InsigniaNoOficial />}
          descripcion="Orden en que se decide la posición de equipos empatados. Usa Subir y Bajar, o arrastra las filas con el mouse."
        >
          <EditorDesempates
            criterios={borrador.criteriosDesempate}
            idTitulo={idTituloDesempates}
            onCambiar={(criterios) => {
              setBorrador((actual) => ({ ...actual, criteriosDesempate: criterios }))
              quitarError('criteriosDesempate')
            }}
          />
          {errores.criteriosDesempate && (
            <p role="alert" data-error-foco tabIndex={-1} className="text-body-sm text-error-strong">
              {errores.criteriosDesempate}
            </p>
          )}
        </SeccionParametros>

        <SeccionParametros
          titulo="Duración del juego y bloque de cancha"
          descripcion="El bloque es el tiempo que la cancha queda ocupada por un partido; no puede ser menor que la duración."
        >
          <div className="grid gap-4 sm:grid-cols-2">
            {campo('duracionJuegoMin', 'Duración del juego (min)')}
            {campo('bloqueCanchaMin', 'Bloque de cancha (min)')}
          </div>
          <TablaParametrosCategoria
            categorias={categorias}
            filas={borrador.categorias}
            duracionTemporada={borrador.duracionJuegoMin}
            bloqueTemporada={borrador.bloqueCanchaMin}
            errores={errores}
            invalidas={invalidas}
            onCambiar={cambiarFila}
          />
        </SeccionParametros>

        <SeccionParametros titulo="Liguilla">
          <div className="grid gap-4 sm:grid-cols-2">
            <SeleccionParametro
              id={`${id}-clasificados`}
              etiqueta={etiquetaCampoParametro.clasificadosLiguilla}
              valor={String(borrador.clasificadosLiguilla)}
              noOficial={noOficial('clasificadosLiguilla')}
              error={errores.clasificadosLiguilla}
              onCambiar={(valor) => {
                setBorrador((actual) => ({
                  ...actual,
                  clasificadosLiguilla: Number(valor) as ClasificadosLiguilla,
                }))
                quitarError('clasificadosLiguilla')
              }}
            >
              {CLASIFICADOS.map((cantidad) => (
                <option key={cantidad} value={cantidad}>
                  {cantidad} equipos
                </option>
              ))}
            </SeleccionParametro>
            <SeleccionParametro
              id={`${id}-formato`}
              etiqueta={etiquetaCampoParametro.formatoLiguilla}
              valor={borrador.formatoLiguilla}
              noOficial={noOficial('formatoLiguilla')}
              error={errores.formatoLiguilla}
              onCambiar={(valor) => {
                setBorrador((actual) => ({ ...actual, formatoLiguilla: valor as FormatoLiguilla }))
                quitarError('formatoLiguilla')
              }}
            >
              {(Object.keys(etiquetaFormatoLiguilla) as FormatoLiguilla[]).map((formato) => (
                <option key={formato} value={formato}>
                  {etiquetaFormatoLiguilla[formato]}
                </option>
              ))}
            </SeleccionParametro>
          </div>
          <CasillaParametro
            id={`${id}-penales`}
            etiqueta={etiquetaCampoParametro.empateLiguillaPorPenales}
            marcada={borrador.empateLiguillaPorPenales}
            noOficial={noOficial('empateLiguillaPorPenales')}
            error={errores.empateLiguillaPorPenales}
            onCambiar={(marcada) =>
              setBorrador((actual) => ({ ...actual, empateLiguillaPorPenales: marcada }))
            }
          />
        </SeccionParametros>

        <SeccionParametros titulo="Ascensos, descensos y registros">
          <div className="grid gap-4 sm:grid-cols-2">
            {campo('equiposAscienden', etiquetaCampoParametro.equiposAscienden)}
            {campo('equiposDescienden', etiquetaCampoParametro.equiposDescienden)}
          </div>
          <CasillaParametro
            id={`${id}-cambio-club`}
            etiqueta={etiquetaCampoParametro.permiteCambioClub}
            marcada={borrador.permiteCambioClub}
            error={errores.permiteCambioClub}
            onCambiar={(marcada) =>
              setBorrador((actual) => ({ ...actual, permiteCambioClub: marcada }))
            }
          />
        </SeccionParametros>

        {errorGeneral && (
          <p
            role="alert"
            className="flex items-start gap-2 rounded-lg border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
          >
            <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            {errorGeneral}
          </p>
        )}

        <div className="sticky bottom-0 flex flex-col gap-2 border-t border-border bg-background py-3 sm:flex-row sm:items-center sm:justify-end">
          {!primera && !conCambios && (
            <p className="text-body-sm text-text-muted sm:mr-auto">
              Sin cambios respecto a la versión {vigente.version}.
            </p>
          )}
          <Button type="submit" icono={Save} disabled={!primera && !conCambios}>
            {primera ? 'Guardar la versión 1' : 'Guardar como nueva versión'}
          </Button>
        </div>
      </form>

      <ModalMotivo
        abierto={confirmando}
        titulo={titulo}
        descripcion={descripcion}
        aviso={
          (textosAviso.length > 0 || errorModal) && (
            <div className="flex flex-col gap-2">
              {textosAviso.length > 0 && (
                <p className="rounded-md bg-info/8 p-3 text-body-md text-info-strong">
                  {textosAviso.join(' ')}
                </p>
              )}
              {errorModal && (
                <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
                  {errorModal}
                </p>
              )}
            </div>
          )
        }
        etiquetaConfirmar="Guardar versión"
        procesando={registrar.isPending}
        onConfirmar={confirmar}
        onCancelar={() => setConfirmando(false)}
      />
    </>
  )
}
