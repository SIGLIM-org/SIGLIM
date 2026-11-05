import { useState } from 'react'
import { Pencil, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useCrearGrupo, useRenombrarGrupo } from '@/features/temporadas/api'
import { FormularioNombreGrupo } from '@/features/temporadas/FormularioNombreGrupo'
import type { CategoriaTemporada } from '@/features/temporadas/tipos'

type Props = {
  categoriaTemporada: CategoriaTemporada
  /** EDITAR y temporada en CONFIGURACION o ACTIVA (RF-005, D8). */
  puedeRenombrar: boolean
  /** EDITAR y temporada en CONFIGURACION (RF-005, D8). */
  puedeAgregar: boolean
  onMensaje: (mensaje: string) => void
}

function cantidad(total: number, singular: string, plural: string): string {
  return `${total} ${total === 1 ? singular : plural}`
}

/**
 * Grupos de una categoría de la temporada (F-11, H-05.3, RF-005): nombre y conteo de equipos, con
 * renombrar y agregar en línea según el estado de la temporada.
 */
export function SeccionGruposCategoria({
  categoriaTemporada,
  puedeRenombrar,
  puedeAgregar,
  onMensaje,
}: Props) {
  const { categoria, grupos } = categoriaTemporada
  const crear = useCrearGrupo()
  const renombrar = useRenombrarGrupo()
  const [agregando, setAgregando] = useState(false)
  const [renombrandoId, setRenombrandoId] = useState<string | null>(null)
  const equipos = grupos.reduce((total, grupo) => total + grupo.equipos, 0)
  const idTitulo = `grupos-${categoriaTemporada.id}`

  return (
    <section
      aria-labelledby={idTitulo}
      className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-4"
    >
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <h3 id={idTitulo} className="text-headline-sm text-text-main">
          {categoria.nombre}{' '}
          <span className="font-normal text-text-muted">
            ·{' '}
            {cantidad(grupos.length, 'grupo', 'grupos')} · {cantidad(equipos, 'equipo', 'equipos')}
          </span>
        </h3>
        {puedeAgregar && !agregando && (
          <Button
            variante="secundario"
            icono={Plus}
            aria-label={`Agregar grupo a ${categoria.nombre}`}
            onClick={() => setAgregando(true)}
            className="shrink-0"
          >
            Agregar grupo
          </Button>
        )}
      </div>

      {grupos.length === 0 ? (
        <p className="text-body-md text-text-muted">Esta categoría aún no tiene grupos.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-border rounded-md border border-border">
          {grupos.map((grupo) => (
            <li key={grupo.id} className="px-3 py-2">
              {renombrandoId === grupo.id ? (
                <FormularioNombreGrupo
                  etiqueta={`Nuevo nombre del grupo ${grupo.nombre}`}
                  valorInicial={grupo.nombre}
                  etiquetaGuardar="Guardar"
                  guardando={renombrar.isPending}
                  onCancelar={() => setRenombrandoId(null)}
                  onGuardar={(nombre, alFallar) => {
                    if (nombre === grupo.nombre) {
                      setRenombrandoId(null)
                      return
                    }

                    renombrar.mutate(
                      { id: grupo.id, nombre },
                      {
                        onSuccess: () => {
                          setRenombrandoId(null)
                          onMensaje(`El grupo «${grupo.nombre}» ahora se llama «${nombre}».`)
                        },
                        onError: alFallar,
                      },
                    )
                  }}
                />
              ) : (
                <div className="flex min-h-11 flex-wrap items-center justify-between gap-2">
                  <span className="min-w-0 break-words text-label-md text-text-main">
                    {grupo.nombre}
                  </span>
                  <span className="flex items-center gap-3">
                    <span className="font-mono text-mono-data text-text-muted">
                      {cantidad(grupo.equipos, 'equipo', 'equipos')}
                    </span>
                    {puedeRenombrar && (
                      <Button
                        variante="secundario"
                        icono={Pencil}
                        aria-label={`Renombrar el grupo ${grupo.nombre}`}
                        disabled={renombrandoId !== null}
                        onClick={() => setRenombrandoId(grupo.id)}
                      >
                        Renombrar
                      </Button>
                    )}
                  </span>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {agregando && (
        <FormularioNombreGrupo
          etiqueta={`Nombre del grupo nuevo de ${categoria.nombre}`}
          etiquetaGuardar="Agregar"
          guardando={crear.isPending}
          onCancelar={() => setAgregando(false)}
          onGuardar={(nombre, alFallar) =>
            crear.mutate(
              { categoriaTemporadaId: categoriaTemporada.id, nombre },
              {
                onSuccess: () => {
                  setAgregando(false)
                  onMensaje(`Se agregó el grupo «${nombre}» a ${categoria.nombre}.`)
                },
                onError: alFallar,
              },
            )
          }
        />
      )}
    </section>
  )
}
