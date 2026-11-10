import { useId, useState, type FormEvent } from 'react'
import { Save } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { useActualizarArticulo, useCrearArticulo } from '@/features/disciplina/api'
import { CampoCasilla } from '@/features/disciplina/CampoCasilla'
import {
  etiquetaEfecto,
  etiquetaSujeto,
  etiquetaTipoSancion,
} from '@/features/disciplina/etiquetas'
import { PanelEdicion } from '@/features/disciplina/PanelEdicion'
import type {
  ActualizarArticuloPeticion,
  Articulo,
  CrearArticuloPeticion,
  EfectoSancion,
  SujetoSancion,
  TipoSancion,
} from '@/features/disciplina/tipos'
import { ApiError } from '@/lib/api/ApiError'

const CAMPOS_FORMULARIO = new Set([
  'numero',
  'descripcion',
  'tipoSancion',
  'sujeto',
  'efecto',
  'aplicacionAutomatica',
  'partidosSuspension',
  'puntos',
  'montoMulta',
  'otorgaDefaultRestantes',
  'activo',
])
const MAXIMO_NUMERO = 20
const MAXIMO_PARTIDOS = 32767
const MAXIMO_PUNTOS = 32767
const MAXIMO_MONTO = 9999999999.99

const TIPOS_SANCION = Object.keys(etiquetaTipoSancion) as TipoSancion[]
const SUJETOS = Object.keys(etiquetaSujeto) as SujetoSancion[]
const EFECTOS = Object.keys(etiquetaEfecto) as EfectoSancion[]

type Props = {
  /** Artículo a editar; sin él, el formulario crea uno nuevo. */
  articulo?: Articulo
  onCerrar: () => void
  onGuardado: (articulo: Articulo, creado: boolean) => void
}

/**
 * Alta y edición de un artículo del reglamento (F-32, RF-129). Solo se captura
 * el valor numérico que corresponde al tipo de sanción (DEC-30); un artículo que
 * deja de aplicarse se desactiva y nunca se borra (RN-005).
 */
export function FormularioArticulo({ articulo, onCerrar, onGuardado }: Props) {
  const crear = useCrearArticulo()
  const actualizar = useActualizarArticulo(articulo?.id ?? '')
  const [numero, setNumero] = useState(articulo?.numero ?? '')
  const [descripcion, setDescripcion] = useState(articulo?.descripcion ?? '')
  const [tipoSancion, setTipoSancion] = useState<TipoSancion | ''>(articulo?.tipoSancion ?? '')
  const [sujeto, setSujeto] = useState<SujetoSancion | ''>(articulo?.sujeto ?? '')
  const [efecto, setEfecto] = useState<EfectoSancion>(articulo?.efecto ?? 'HACIA_ADELANTE')
  const [aplicacionAutomatica, setAplicacionAutomatica] = useState(
    articulo?.aplicacionAutomatica ?? false,
  )
  const [partidosSuspension, setPartidosSuspension] = useState(
    articulo?.partidosSuspension?.toString() ?? '',
  )
  const [puntos, setPuntos] = useState(articulo?.puntos?.toString() ?? '')
  const [montoMulta, setMontoMulta] = useState(articulo?.montoMulta?.toString() ?? '')
  const [otorgaDefaultRestantes, setOtorgaDefaultRestantes] = useState(
    articulo?.otorgaDefaultRestantes ?? true,
  )
  const [activo, setActivo] = useState(articulo?.activo ?? true)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const guardando = crear.isPending || actualizar.isPending
  const esNuevo = !articulo

  const limpiarError = (campo: string) => {
    setErrores((actuales) => {
      if (!(campo in actuales)) {
        return actuales
      }

      return Object.fromEntries(
        Object.entries(actuales).filter(([clave]) => clave !== campo),
      )
    })
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo guardar el artículo. Inténtalo de nuevo.')
      return
    }

    const deCampos = Object.fromEntries(
      (error.problema.errores ?? [])
        .filter(({ campo }) => CAMPOS_FORMULARIO.has(campo))
        .map(({ campo, mensaje }) => [campo, mensaje]),
    )

    if (error.problema.codigo === 'DUPLICADO') {
      setErrores({ numero: error.problema.detail })
      setErrorGeneral(null)
      return
    }

    setErrores(deCampos)
    setErrorGeneral(error.problema.detail)
  }

  const validar = (): Record<string, string> => {
    const encontrados: Record<string, string> = {}
    const numeroLimpio = numero.trim()

    if (!numeroLimpio) {
      encontrados.numero = 'Escribe el número del artículo.'
    } else if (numeroLimpio.length > MAXIMO_NUMERO) {
      encontrados.numero = `El número admite hasta ${MAXIMO_NUMERO} caracteres.`
    }

    if (!descripcion.trim()) {
      encontrados.descripcion = 'Escribe la descripción.'
    }

    if (!tipoSancion) {
      encontrados.tipoSancion = 'Elige el tipo de sanción.'
    }

    if (!sujeto) {
      encontrados.sujeto = 'Elige a quién aplica la sanción.'
    }

    if (tipoSancion === 'SUSPENSION_PARTIDOS') {
      const partidos = Number(partidosSuspension)

      if (
        !partidosSuspension.trim() ||
        !Number.isInteger(partidos) ||
        partidos < 1 ||
        partidos > MAXIMO_PARTIDOS
      ) {
        encontrados.partidosSuspension = `Indica un número entero de partidos entre 1 y ${MAXIMO_PARTIDOS}.`
      }
    }

    if (tipoSancion === 'PERDIDA_PUNTOS') {
      const puntosPerdidos = Number(puntos)

      if (
        !puntos.trim() ||
        !Number.isInteger(puntosPerdidos) ||
        puntosPerdidos < 1 ||
        puntosPerdidos > MAXIMO_PUNTOS
      ) {
        encontrados.puntos = `Indica un número entero de puntos entre 1 y ${MAXIMO_PUNTOS}.`
      }
    }

    if (tipoSancion === 'MULTA') {
      const monto = Number(montoMulta)

      if (
        !montoMulta.trim() ||
        !Number.isFinite(monto) ||
        monto < 0.01 ||
        monto > MAXIMO_MONTO
      ) {
        encontrados.montoMulta = 'Indica un monto mayor a cero y hasta 9,999,999,999.99 MXN.'
      }
    }

    return encontrados
  }

  const enviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    const datos: CrearArticuloPeticion = {
      numero: numero.trim(),
      descripcion: descripcion.trim(),
      tipoSancion: tipoSancion as TipoSancion,
      sujeto: sujeto as SujetoSancion,
      efecto,
      aplicacionAutomatica,
    }

    if (tipoSancion === 'SUSPENSION_PARTIDOS') {
      datos.partidosSuspension = Number(partidosSuspension)
    }

    if (tipoSancion === 'PERDIDA_PUNTOS') {
      datos.puntos = Number(puntos)
    }

    if (tipoSancion === 'MULTA') {
      datos.montoMulta = Number(montoMulta)
    }

    if (tipoSancion === 'BAJA') {
      datos.otorgaDefaultRestantes = otorgaDefaultRestantes
    }

    if (articulo) {
      const cambios: ActualizarArticuloPeticion = { ...datos, activo }
      actualizar.mutate(cambios, {
        onSuccess: (guardado) => onGuardado(guardado, false),
        onError: alFallar,
      })
      return
    }

    crear.mutate(datos, {
      onSuccess: (guardado) => onGuardado(guardado, true),
      onError: alFallar,
    })
  }

  return (
    <PanelEdicion
      titulo={esNuevo ? 'Nuevo artículo' : `Editar ${articulo.numero}`}
      descripcion={
        esNuevo
          ? 'Registra la sanción, el sujeto, el efecto y la aplicación automática del artículo.'
          : 'Los cambios quedan registrados en la auditoría.'
      }
      insignia={articulo?.esDemo ? <InsigniaNoOficial /> : undefined}
      onCerrar={onCerrar}
    >
      <form onSubmit={enviar} className="flex flex-col gap-4 p-4" noValidate>
        {errorGeneral && (
          <p
            role="alert"
            className="rounded-md border border-error/30 bg-error/8 p-3 text-body-md text-error-strong"
          >
            {errorGeneral}
          </p>
        )}

        <CampoTexto
          id={`${id}-numero`}
          etiqueta="Número"
          required
          maxLength={MAXIMO_NUMERO}
          value={numero}
          ayuda="Como aparece en el reglamento; por ejemplo, Art. 42."
          error={errores.numero}
          onChange={(evento) => {
            setNumero(evento.target.value)
            limpiarError('numero')
          }}
        />

        <div className="flex flex-col gap-1.5">
          <label htmlFor={`${id}-descripcion`} className="text-label-md text-text-main">
            Descripción
            <span aria-hidden="true" className="ml-0.5 text-error">
              *
            </span>
          </label>
          <textarea
            id={`${id}-descripcion`}
            required
            rows={4}
            value={descripcion}
            placeholder="Conducta tipificada en el reglamento."
            aria-invalid={errores.descripcion ? true : undefined}
            aria-describedby={errores.descripcion ? `${id}-descripcion-error` : undefined}
            onChange={(evento) => {
              setDescripcion(evento.target.value)
              limpiarError('descripcion')
            }}
            className={[
              'min-h-28 w-full resize-y rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main placeholder:text-text-muted/70',
              errores.descripcion ? 'border-error' : '',
            ]
              .filter(Boolean)
              .join(' ')}
          />
          {errores.descripcion && (
            <p
              id={`${id}-descripcion-error`}
              role="alert"
              className="text-body-sm text-error-strong"
            >
              {errores.descripcion}
            </p>
          )}
        </div>

        <CampoSeleccion
          id={`${id}-tipo-sancion`}
          etiqueta="Tipo de sanción"
          required
          value={tipoSancion}
          error={errores.tipoSancion}
          onChange={(evento) => {
            setTipoSancion(evento.target.value as TipoSancion | '')
            limpiarError('tipoSancion')
          }}
        >
          <option value="">Elige una opción.</option>
          {TIPOS_SANCION.map((valor) => (
            <option key={valor} value={valor}>
              {etiquetaTipoSancion[valor]}
            </option>
          ))}
        </CampoSeleccion>

        <div className="grid gap-4 sm:grid-cols-2">
          <CampoSeleccion
            id={`${id}-sujeto`}
            etiqueta="Sujeto"
            required
            value={sujeto}
            error={errores.sujeto}
            onChange={(evento) => {
              setSujeto(evento.target.value as SujetoSancion | '')
              limpiarError('sujeto')
            }}
          >
            <option value="">Elige una opción.</option>
            {SUJETOS.map((valor) => (
              <option key={valor} value={valor}>
                {etiquetaSujeto[valor]}
              </option>
            ))}
          </CampoSeleccion>

          <CampoSeleccion
            id={`${id}-efecto`}
            etiqueta="Efecto"
            required
            value={efecto}
            error={errores.efecto}
            onChange={(evento) => {
              setEfecto(evento.target.value as EfectoSancion)
              limpiarError('efecto')
            }}
          >
            {EFECTOS.map((valor) => (
              <option key={valor} value={valor}>
                {etiquetaEfecto[valor]}
              </option>
            ))}
          </CampoSeleccion>
        </div>

        {tipoSancion === 'SUSPENSION_PARTIDOS' && (
          <CampoTexto
            id={`${id}-partidos`}
            etiqueta="Partidos de suspensión"
            type="number"
            inputMode="numeric"
            required
            min={1}
            max={MAXIMO_PARTIDOS}
            step={1}
            value={partidosSuspension}
            ayuda={`Entre 1 y ${MAXIMO_PARTIDOS} partidos.`}
            error={errores.partidosSuspension}
            onChange={(evento) => {
              setPartidosSuspension(evento.target.value)
              limpiarError('partidosSuspension')
            }}
          />
        )}

        {tipoSancion === 'PERDIDA_PUNTOS' && (
          <CampoTexto
            id={`${id}-puntos`}
            etiqueta="Puntos"
            type="number"
            inputMode="numeric"
            required
            min={1}
            max={MAXIMO_PUNTOS}
            step={1}
            value={puntos}
            ayuda={`Entre 1 y ${MAXIMO_PUNTOS} puntos.`}
            error={errores.puntos}
            onChange={(evento) => {
              setPuntos(evento.target.value)
              limpiarError('puntos')
            }}
          />
        )}

        {tipoSancion === 'MULTA' && (
          <CampoTexto
            id={`${id}-monto`}
            etiqueta="Monto de la multa"
            type="number"
            inputMode="decimal"
            required
            min={0.01}
            max={MAXIMO_MONTO}
            step={0.01}
            value={montoMulta}
            ayuda="Entre 0.01 y 9,999,999,999.99 MXN."
            error={errores.montoMulta}
            onChange={(evento) => {
              setMontoMulta(evento.target.value)
              limpiarError('montoMulta')
            }}
          />
        )}

        {tipoSancion === 'PERDIDA_PARTIDO' && (
          <p className="rounded-md border border-border bg-background p-3 text-body-sm text-text-muted">
            La pérdida de partido no lleva partidos de suspensión, puntos ni monto.
          </p>
        )}

        <CampoCasilla
          id={`${id}-aplicacion-automatica`}
          etiqueta="Aplicación automática"
          descripcion="El sistema aplica la sanción al validar la cédula, sin esperar una resolución de la Comisión (DEC-31)."
          checked={aplicacionAutomatica}
          onChange={(marcada) => {
            setAplicacionAutomatica(marcada)
            limpiarError('aplicacionAutomatica')
          }}
          error={errores.aplicacionAutomatica}
        />

        {tipoSancion === 'BAJA' && (
          <CampoCasilla
            id={`${id}-default-restantes`}
            etiqueta="Otorga los partidos restantes al rival"
            descripcion="En una baja, el rival recibe por defecto los partidos pendientes (DEC-32)."
            checked={otorgaDefaultRestantes}
            onChange={(marcada) => {
              setOtorgaDefaultRestantes(marcada)
              limpiarError('otorgaDefaultRestantes')
            }}
            error={errores.otorgaDefaultRestantes}
          />
        )}

        {articulo && (
          <CampoCasilla
            id={`${id}-activo`}
            etiqueta="Activo"
            descripcion="Al desactivarlo deja de aplicarse en nuevas resoluciones, pero conserva su historial."
            checked={activo}
            onChange={(marcada) => {
              setActivo(marcada)
              limpiarError('activo')
            }}
            error={errores.activo}
          />
        )}

        <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
            Cancelar
          </Button>
          <Button type="submit" icono={Save} cargando={guardando}>
            {esNuevo ? 'Crear artículo' : 'Guardar cambios'}
          </Button>
        </div>
      </form>
    </PanelEdicion>
  )
}
