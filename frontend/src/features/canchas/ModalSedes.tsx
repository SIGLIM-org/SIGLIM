import { useId, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Building2, CheckCircle2, Pencil } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { clavesSedes, useActualizarSede, useCrearSede, useSedes } from '@/features/canchas/api'
import type { ActualizarSedePeticion, CrearSedePeticion, Sede } from '@/features/canchas/tipos'
import { ApiError } from '@/lib/api/ApiError'
import { esNoEncontrado } from '@/lib/api/errores'

type Props = {
  onCerrar: () => void
}

type Valores = { nombre: string; direccion: string }

const CAMPOS_FORMULARIO = new Set<string>(['nombre', 'direccion'])
const VACIOS: Valores = { nombre: '', direccion: '' }

/** Solo lo que cambió: un campo ausente conserva su valor y la dirección en blanco se vacía. */
function cambiosDe(sede: Sede, valores: Valores): ActualizarSedePeticion {
  const cambios: ActualizarSedePeticion = {}
  const nombre = valores.nombre.trim()
  const direccion = valores.direccion.trim()

  if (nombre !== sede.nombre) {
    cambios.nombre = nombre
  }
  if (direccion !== (sede.direccion ?? '')) {
    cambios.direccion = direccion
  }

  return cambios
}

/**
 * Alta y renombre de sedes (RF-028). [PROPUESTA] D6: un modal con la lista de sedes y un único
 * formulario que alterna entre «Nueva sede» y «Editar sede»; las sedes no se borran (D3).
 * [PROPUESTA] D9: un 409 `DUPLICADO` se marca en el nombre sin cerrar el modal y un 404 al
 * editar se explica arriba del formulario y recarga la lista.
 */
export function ModalSedes({ onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const clienteConsultas = useQueryClient()
  const sedes = useSedes()
  const [editando, setEditando] = useState<Sede | null>(null)
  const crear = useCrearSede()
  const actualizar = useActualizarSede(editando?.id ?? '')
  const [valores, setValores] = useState<Valores>(VACIOS)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idTituloFormulario = `${id}-titulo-formulario`
  const idNombre = `${id}-nombre`
  const guardando = crear.isPending || actualizar.isPending

  const reiniciar = (sede: Sede | null) => {
    setEditando(sede)
    setValores(sede ? { nombre: sede.nombre, direccion: sede.direccion ?? '' } : VACIOS)
    setErrores({})
    setErrorGeneral(null)
  }

  /** El foco va al nombre para que el teclado no se pierda al cambiar de modo (RNF-008). */
  const cambiarModo = (sede: Sede | null) => {
    reiniciar(sede)
    setAviso(null)
    document.getElementById(idNombre)?.focus()
  }

  const cambiar = (campo: keyof Valores, valor: string) => {
    setValores((actuales) => ({ ...actuales, [campo]: valor }))
    setErrores(({ [campo]: _quitado, ...resto }) => resto)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      return
    }

    if (error.problema.codigo === 'DUPLICADO') {
      setErrores({ nombre: error.problema.detail })
      setErrorGeneral(null)
      return
    }

    if (esNoEncontrado(error)) {
      void clienteConsultas.invalidateQueries({ queryKey: clavesSedes.todas })
    }

    setErrores(
      Object.fromEntries(
        (error.problema.errores ?? [])
          .filter(({ campo }) => CAMPOS_FORMULARIO.has(campo))
          .map(({ campo, mensaje }) => [campo, mensaje]),
      ),
    )
    setErrorGeneral(error.problema.detail)
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)
    setAviso(null)

    const nombre = valores.nombre.trim()

    if (!nombre) {
      setErrores({ nombre: 'Escribe el nombre de la sede.' })
      return
    }

    if (editando) {
      const cambios = cambiosDe(editando, valores)

      if (Object.keys(cambios).length === 0) {
        reiniciar(null)
        return
      }

      actualizar.mutate(cambios, {
        onSuccess: (sede) => {
          reiniciar(null)
          setAviso(`Sede «${sede.nombre}» actualizada.`)
        },
        onError: alFallar,
      })
      return
    }

    const direccion = valores.direccion.trim()
    const peticion: CrearSedePeticion = { nombre, ...(direccion ? { direccion } : {}) }

    crear.mutate(peticion, {
      onSuccess: (sede) => {
        reiniciar(null)
        setAviso(`Sede «${sede.nombre}» registrada.`)
      },
      onError: alFallar,
    })
  }

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
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex flex-col gap-5 p-6">
        <div className="flex flex-col gap-1">
          <h2 id={idTitulo} className="text-balance text-headline-md">
            Sedes
          </h2>
          <p className="text-body-md text-text-muted">
            Agrupan las canchas que están en un mismo lugar. Las sedes no se borran.
          </p>
        </div>

        <section aria-label="Sedes registradas" className="flex flex-col gap-3">
          {sedes.isPending && <EstadoCarga etiqueta="Cargando sedes…" filas={3} columnas={1} />}
          {sedes.isError && (
            <ErrorConsulta error={sedes.error} onReintentar={() => void sedes.refetch()} />
          )}
          {sedes.isSuccess && sedes.data.length === 0 && (
            <EstadoVacio
              icono={Building2}
              titulo="Aún no hay sedes"
              descripcion="Registra la primera con el formulario de abajo."
            />
          )}
          {sedes.isSuccess && sedes.data.length > 0 && (
            <ul className="flex flex-col overflow-hidden rounded-lg border border-border">
              {sedes.data.map((sede) => (
                <li
                  key={sede.id}
                  aria-current={editando?.id === sede.id ? 'true' : undefined}
                  className={[
                    'flex items-center justify-between gap-3 border-b border-border p-3 last:border-b-0',
                    editando?.id === sede.id ? 'bg-primary/8' : '',
                  ].join(' ')}
                >
                  <span className="flex min-w-0 flex-col">
                    <span className="break-words text-label-md text-text-main">{sede.nombre}</span>
                    {sede.direccion && (
                      <span className="break-words text-body-sm text-text-muted">{sede.direccion}</span>
                    )}
                  </span>
                  <Button
                    variante="secundario"
                    icono={Pencil}
                    onClick={() => cambiarModo(sede)}
                    disabled={guardando}
                    aria-label={`Editar ${sede.nombre}`}
                    className="shrink-0"
                  >
                    Editar
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {aviso && (
          <p role="status" className="flex items-center gap-2 text-label-md text-success-strong">
            <CheckCircle2 aria-hidden="true" className="size-4 shrink-0" />
            {aviso}
          </p>
        )}

        <form
          onSubmit={alEnviar}
          noValidate
          aria-labelledby={idTituloFormulario}
          className="flex flex-col gap-4 border-t border-border pt-5"
        >
          <h3 id={idTituloFormulario} className="text-balance break-words text-headline-sm">
            {editando ? `Editar sede — ${editando.nombre}` : 'Nueva sede'}
          </h3>
          <CampoTexto
            id={idNombre}
            etiqueta="Nombre"
            required
            maxLength={120}
            error={errores.nombre}
            value={valores.nombre}
            onChange={(evento) => cambiar('nombre', evento.target.value)}
          />
          <CampoTexto
            id={`${id}-direccion`}
            etiqueta="Dirección"
            ayuda={editando ? 'Déjalo vacío para borrarla.' : 'Opcional.'}
            error={errores.direccion}
            value={valores.direccion}
            onChange={(evento) => cambiar('direccion', evento.target.value)}
          />

          {errorGeneral && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorGeneral}
            </p>
          )}

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            {editando && (
              <Button variante="secundario" onClick={() => cambiarModo(null)} disabled={guardando}>
                Cancelar edición
              </Button>
            )}
            <Button type="submit" cargando={guardando}>
              {editando ? 'Guardar cambios' : 'Registrar sede'}
            </Button>
          </div>
        </form>

        <div className="flex justify-end border-t border-border pt-4">
          <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
            Cerrar
          </Button>
        </div>
      </div>
    </dialog>
  )
}
