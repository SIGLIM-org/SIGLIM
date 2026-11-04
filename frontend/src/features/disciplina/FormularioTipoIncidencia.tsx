import { useId, useState, type FormEvent } from 'react'
import { Save } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { useActualizarTipoIncidencia, useCrearTipoIncidencia } from '@/features/disciplina/api'
import { CampoCasilla } from '@/features/disciplina/CampoCasilla'
import { PanelEdicion } from '@/features/disciplina/PanelEdicion'
import type {
  ActualizarTipoIncidenciaPeticion,
  CrearTipoIncidenciaPeticion,
  TipoIncidencia,
} from '@/features/disciplina/tipos'
import { ApiError } from '@/lib/api/ApiError'

const CAMPOS_FORMULARIO = new Set(['codigo', 'nombre', 'abreExpediente', 'activo'])
const MAXIMO_CODIGO = 40
const MAXIMO_NOMBRE = 100

type Props = {
  /** Tipo a editar; sin él, el formulario crea uno nuevo. */
  tipo?: TipoIncidencia
  onCerrar: () => void
  onGuardado: (tipo: TipoIncidencia, creado: boolean) => void
}

/**
 * Alta y edición de un tipo de incidencia (F-32, RF-128). El tipo nace activo y
 * se desactiva con la casilla «Activo»; nunca se borra (RN-005).
 */
export function FormularioTipoIncidencia({ tipo, onCerrar, onGuardado }: Props) {
  const crear = useCrearTipoIncidencia()
  const actualizar = useActualizarTipoIncidencia(tipo?.id ?? 0)
  const [codigo, setCodigo] = useState(tipo?.codigo ?? '')
  const [nombre, setNombre] = useState(tipo?.nombre ?? '')
  const [abreExpediente, setAbreExpediente] = useState(tipo?.abreExpediente ?? false)
  const [activo, setActivo] = useState(tipo?.activo ?? true)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const guardando = crear.isPending || actualizar.isPending
  const esNuevo = !tipo

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
      setErrorGeneral('No se pudo guardar el tipo de incidencia. Inténtalo de nuevo.')
      return
    }

    const deCampos = Object.fromEntries(
      (error.problema.errores ?? [])
        .filter(({ campo }) => CAMPOS_FORMULARIO.has(campo))
        .map(({ campo, mensaje }) => [campo, mensaje]),
    )

    if (error.problema.codigo === 'DUPLICADO') {
      setErrores({ codigo: error.problema.detail })
      setErrorGeneral(null)
      return
    }

    setErrores(deCampos)
    setErrorGeneral(error.problema.detail)
  }

  const validar = (): Record<string, string> => {
    const encontrados: Record<string, string> = {}
    const codigoLimpio = codigo.trim()
    const nombreLimpio = nombre.trim()

    if (!codigoLimpio) {
      encontrados.codigo = 'Escribe el código.'
    } else if (codigoLimpio.length > MAXIMO_CODIGO) {
      encontrados.codigo = `El código admite hasta ${MAXIMO_CODIGO} caracteres.`
    }

    if (!nombreLimpio) {
      encontrados.nombre = 'Escribe el nombre.'
    } else if (nombreLimpio.length > MAXIMO_NOMBRE) {
      encontrados.nombre = `El nombre admite hasta ${MAXIMO_NOMBRE} caracteres.`
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

    const datos: CrearTipoIncidenciaPeticion = {
      codigo: codigo.trim(),
      nombre: nombre.trim(),
      abreExpediente,
    }

    if (tipo) {
      const cambios: ActualizarTipoIncidenciaPeticion = { ...datos, activo }
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
      titulo={esNuevo ? 'Nuevo tipo de incidencia' : `Editar ${tipo.codigo}`}
      descripcion={
        esNuevo
          ? 'Define cómo se identifica la incidencia al capturar una cédula.'
          : 'Los cambios quedan registrados en la auditoría.'
      }
      insignia={tipo?.esDemo ? <InsigniaNoOficial /> : undefined}
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
          id={`${id}-codigo`}
          etiqueta="Código"
          required
          maxLength={MAXIMO_CODIGO}
          value={codigo}
          ayuda="Identificador legible; por ejemplo, INC-01."
          error={errores.codigo}
          onChange={(evento) => {
            setCodigo(evento.target.value)
            limpiarError('codigo')
          }}
        />

        <CampoTexto
          id={`${id}-nombre`}
          etiqueta="Nombre"
          required
          maxLength={MAXIMO_NOMBRE}
          value={nombre}
          error={errores.nombre}
          onChange={(evento) => {
            setNombre(evento.target.value)
            limpiarError('nombre')
          }}
        />

        <CampoCasilla
          id={`${id}-abre-expediente`}
          etiqueta="Abre expediente"
          descripcion="Al aprobar una cédula con esta incidencia se abrirá un expediente disciplinario (DEC-22)."
          checked={abreExpediente}
          onChange={(marcada) => {
            setAbreExpediente(marcada)
            limpiarError('abreExpediente')
          }}
          error={errores.abreExpediente}
        />

        {tipo && (
          <CampoCasilla
            id={`${id}-activo`}
            etiqueta="Activo"
            descripcion="Al desactivarlo deja de ofrecerse al capturar una cédula, pero conserva su historial."
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
            {esNuevo ? 'Crear tipo' : 'Guardar cambios'}
          </Button>
        </div>
      </form>
    </PanelEdicion>
  )
}
