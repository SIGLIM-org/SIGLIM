import { useState } from 'react'
import { Link } from 'react-router'
import { CircleCheck, Info, Layers } from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useCategoriasTemporada } from '@/features/temporadas/api'
import { SeccionGruposCategoria } from '@/features/temporadas/SeccionGruposCategoria'
import type { Temporada } from '@/features/temporadas/tipos'
import { reintentarConsulta } from '@/lib/api/errores'

type Props = {
  temporada: Temporada
  /** `temporadas.temporada:EDITAR` y la temporada no está cerrada (D8). */
  puedeEditar: boolean
}

/**
 * Pestaña Grupos de F-11 (H-05.3, RF-005): una sección por categoría asignada, en el orden del
 * catálogo. Los grupos se leen anidados en `GET /temporadas/{id}/categorias`. En una temporada
 * activa solo se renombran; agregar grupos es de la configuración (RF-005, D8).
 */
export function PestanaGrupos({ temporada, puedeEditar }: Props) {
  const consulta = useCategoriasTemporada(temporada.id)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const cerrada = temporada.estado === 'CERRADA'
  const activa = temporada.estado === 'ACTIVA'
  const puedeAgregar = puedeEditar && temporada.estado === 'CONFIGURACION'

  return (
    <section aria-labelledby="titulo-grupos" className="flex min-w-0 flex-col gap-4">
      <div>
        <h2 id="titulo-grupos" className="text-headline-md text-text-main">
          Grupos por categoría
        </h2>
        <p className="mt-1 max-w-3xl text-body-md text-text-muted">
          Cada categoría asignada a {temporada.nombre} se divide en grupos de equipos.
        </p>
        {!puedeEditar && !cerrada && (
          <p className="mt-1 text-body-sm text-text-muted">Solo consulta</p>
        )}
      </div>

      {puedeEditar && activa && (
        <p className="flex items-start gap-2 rounded-lg border border-border bg-surface p-3 text-body-md text-text-main">
          <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-info-strong" />
          La temporada está activa: ya no se pueden agregar grupos (RF-005). Puedes renombrar los
          existentes.
        </p>
      )}

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      {consulta.isPending && <EstadoCarga etiqueta="Cargando grupos…" filas={4} columnas={2} />}

      {consulta.isError && (
        <ErrorConsulta
          error={consulta.error}
          onReintentar={
            reintentarConsulta(0, consulta.error) ? () => void consulta.refetch() : undefined
          }
        />
      )}

      {consulta.data?.length === 0 && (
        <EstadoVacio
          icono={Layers}
          titulo="La temporada aún no tiene categorías"
          descripcion="Los grupos se crean dentro de una categoría asignada a la temporada."
          accion={
            <Link
              to={{ search: '?pestana=categorias' }}
              replace
              className={clasesBoton('secundario')}
            >
              Ir a Categorías
            </Link>
          }
        />
      )}

      {consulta.data?.map((categoriaTemporada) => (
        <SeccionGruposCategoria
          key={categoriaTemporada.id}
          categoriaTemporada={categoriaTemporada}
          puedeRenombrar={puedeEditar}
          puedeAgregar={puedeAgregar}
          onMensaje={setMensaje}
        />
      ))}
    </section>
  )
}
