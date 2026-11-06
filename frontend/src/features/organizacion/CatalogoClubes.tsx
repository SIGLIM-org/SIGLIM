import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ArrowLeft, Plus, Shield } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useClubes } from '@/features/organizacion/api'
import { DetalleClub } from '@/features/organizacion/DetalleClub'
import { ListaClubes } from '@/features/organizacion/ListaClubes'
import { ModalRegistrarClub } from '@/features/organizacion/ModalRegistrarClub'
import { usePermisosClubes } from '@/features/organizacion/permisosClubes'
import type { FiltrosClubes } from '@/features/organizacion/tipos'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

export const RUTA_CLUBES = '/app/organizacion/clubes'

const FILTROS_INICIALES: FiltrosClubes = { estado: 'ACTIVO', pagina: 0, tamano: 20 }
/** Sin filtro de estado: la sesión de ámbito CLUB llega a su club aunque esté cerrado (RN-005). */
const FILTROS_SOLO_CLUB: FiltrosClubes = { pagina: 0, tamano: 20 }

/**
 * Clubes y sus equipos (F-15, H-06.2). Desde `lg`, la lista a la izquierda y el detalle a la
 * derecha; en móvil, una vista a la vez. Una sesión que solo alcanza su club (todos sus permisos
 * de consulta con ámbito CLUB) ve directo el detalle, sin lista ni «Volver», para no caer en el
 * ciclo volver → redirigir. Esa sesión parte sin filtro de estado para alcanzar su club aunque
 * esté cerrado (RN-005); el ámbito LIGA parte de «Activos».
 */
export function CatalogoClubes() {
  const { clubId } = useParams()
  const navegar = useNavigate()
  const permisos = usePermisosClubes()
  const [filtros, setFiltros] = useState<FiltrosClubes>(permisos.soloClub ? FILTROS_SOLO_CLUB : FILTROS_INICIALES)
  const [registrando, setRegistrando] = useState(false)
  const consulta = useClubes(filtros)
  const abrirRegistro = useCallback(() => setRegistrando(true), [])
  const clubUnico =
    permisos.soloClub && consulta.data?.totalElementos === 1 ? consulta.data.contenido[0] : undefined
  const ocultarLista = permisos.soloClub && (consulta.isPending || clubUnico !== undefined)

  useEffect(() => {
    if (clubUnico && !clubId) {
      void navegar(`${RUTA_CLUBES}/${clubUnico.id}`, { replace: true })
    }
  }, [clubUnico, clubId, navegar])

  if (esSinPermiso(consulta.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Clubes</h1>
          <p className="mt-1 text-body-md text-text-muted">
            {ocultarLista ? 'Datos de tu club y sus equipos.' : 'Clubes de la liga y sus equipos.'}
          </p>
        </div>
        {permisos.registrarClub && !ocultarLista && (
          <Button icono={Plus} onClick={abrirRegistro} className="shrink-0">
            Registrar club
          </Button>
        )}
      </header>

      <div className={ocultarLista ? 'min-w-0' : 'grid gap-6 lg:grid-cols-[18rem_minmax(0,1fr)]'}>
        {!ocultarLista && (
          <div className={clubId ? 'hidden min-w-0 lg:block' : 'min-w-0'}>
            <ListaClubes
              filtros={filtros}
              datos={consulta.data}
              cargando={consulta.isPending}
              error={consulta.error}
              clubId={clubId}
              onCambiarFiltros={setFiltros}
              onReintentar={() => void consulta.refetch()}
              onRegistrar={abrirRegistro}
            />
          </div>
        )}

        <div className={clubId ? 'flex min-w-0 flex-col gap-4' : 'hidden min-w-0 lg:block'}>
          {clubId ? (
            <>
              {!ocultarLista && (
                <Link
                  to={RUTA_CLUBES}
                  className="inline-flex min-h-11 items-center gap-2 self-start text-label-md text-primary underline-offset-2 hover:underline lg:hidden"
                >
                  <ArrowLeft aria-hidden="true" className="size-4" />
                  Volver a clubes
                </Link>
              )}
              <DetalleClub key={clubId} clubId={clubId} mostrarVolver={!ocultarLista} />
            </>
          ) : (
            !ocultarLista && (
              <EstadoVacio
                icono={Shield}
                titulo="Elige un club"
                descripcion="Aquí verás sus datos y sus equipos."
              />
            )
          )}
        </div>
      </div>

      {registrando && (
        <ModalRegistrarClub
          onCerrar={() => setRegistrando(false)}
          onRegistrado={(club) => {
            setRegistrando(false)
            void navegar(`${RUTA_CLUBES}/${club.id}`)
          }}
        />
      )}
    </div>
  )
}
