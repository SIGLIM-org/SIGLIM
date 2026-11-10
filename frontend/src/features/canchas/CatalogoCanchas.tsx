import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { ArrowLeft, Building2, MapPinned, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useCanchas } from '@/features/canchas/api'
import { DetalleCancha } from '@/features/canchas/DetalleCancha'
import { semanaDeParametro } from '@/features/canchas/fechas'
import { FormularioCancha } from '@/features/canchas/FormularioCancha'
import { ListaCanchas } from '@/features/canchas/ListaCanchas'
import { ModalSedes } from '@/features/canchas/ModalSedes'
import type { FiltrosCanchas } from '@/features/canchas/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

const RUTA = '/app/programacion/canchas'
const FILTROS_INICIALES: FiltrosCanchas = { pagina: 0, tamano: 20 }

/**
 * Lista y detalle de canchas (F-21). Desde `lg`, la lista y los datos de la cancha van lado a
 * lado, y el calendario debajo a todo el ancho para que quepan los 7 días (`lg:contents` deja
 * que los hijos del detalle ocupen esta rejilla). En móvil, la lista o, con una cancha elegida,
 * solo su detalle. La lista sigue montada para conservar los filtros.
 */
export function CatalogoCanchas() {
  const { canchaId } = useParams()
  const [parametros, setParametros] = useSearchParams()
  const navegar = useNavigate()
  const [filtros, setFiltros] = useState<FiltrosCanchas>(FILTROS_INICIALES)
  const [creando, setCreando] = useState(false)
  const [gestionandoSedes, setGestionandoSedes] = useState(false)
  const consulta = useCanchas(filtros)
  const abrirNueva = useCallback(() => setCreando(true), [])

  const valorSemana = parametros.get('semana')
  const semana = semanaDeParametro(valorSemana)
  const sufijoSemana = valorSemana ? `?semana=${semana}` : ''

  if (esSinPermiso(consulta.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Canchas</h1>
          <p className="mt-1 text-body-md text-text-muted">
            Catálogo de canchas, su disponibilidad y sus bloqueos.
          </p>
        </div>
        {/* [PROPUESTA] D6: las sedes se gestionan en un modal junto a «Nueva cancha» (RF-028). */}
        <ConPermiso permiso="canchas.cancha:EDITAR">
          <div className="flex shrink-0 flex-col-reverse gap-2 sm:flex-row">
            <Button variante="secundario" icono={Building2} onClick={() => setGestionandoSedes(true)}>
              Sedes
            </Button>
            <Button icono={Plus} onClick={abrirNueva}>
              Nueva cancha
            </Button>
          </div>
        </ConPermiso>
      </header>

      <div className="grid gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]">
        <div className={canchaId ? 'hidden min-w-0 lg:block' : 'min-w-0'}>
          <ListaCanchas
            filtros={filtros}
            datos={consulta.data}
            cargando={consulta.isPending}
            error={consulta.error}
            canchaId={canchaId}
            sufijoSemana={sufijoSemana}
            onCambiarFiltros={setFiltros}
            onReintentar={() => void consulta.refetch()}
            onNueva={abrirNueva}
          />
        </div>

        <div className={canchaId ? 'flex min-w-0 flex-col gap-4 lg:contents' : 'hidden min-w-0 lg:block'}>
          {canchaId ? (
            <>
              <Link
                to={`${RUTA}${sufijoSemana}`}
                className="inline-flex min-h-11 items-center gap-2 self-start text-label-md text-primary underline-offset-2 hover:underline lg:hidden"
              >
                <ArrowLeft aria-hidden="true" className="size-4" />
                Volver al catálogo
              </Link>
              <DetalleCancha
                key={canchaId}
                canchaId={canchaId}
                semana={semana}
                rutaCatalogo={`${RUTA}${sufijoSemana}`}
                onCambiarSemana={(lunes) =>
                  setParametros((actuales) => {
                    actuales.set('semana', lunes)
                    return actuales
                  })
                }
              />
            </>
          ) : (
            <EstadoVacio
              icono={MapPinned}
              titulo="Elige una cancha"
              descripcion="Aquí verás sus datos y su calendario semanal."
            />
          )}
        </div>
      </div>

      {gestionandoSedes && <ModalSedes onCerrar={() => setGestionandoSedes(false)} />}
      {creando && (
        <FormularioCancha
          onCerrar={() => setCreando(false)}
          onGuardada={(cancha) => {
            setCreando(false)
            void navegar(`${RUTA}/${cancha.id}${sufijoSemana}`)
          }}
        />
      )}
    </div>
  )
}
