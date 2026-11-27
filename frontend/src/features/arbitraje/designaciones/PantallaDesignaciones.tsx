import { Link, useSearchParams } from 'react-router'
import { ClipboardCheck, ShieldOff } from 'lucide-react'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { JornadaDesignaciones } from '@/features/arbitraje/designaciones/JornadaDesignaciones'
import { ListaJornadasSemana } from '@/features/arbitraje/designaciones/ListaJornadasSemana'
import { NavegadorSemana } from '@/features/arbitraje/designaciones/NavegadorSemana'
import { usePermisosDesignacion } from '@/features/arbitraje/designaciones/permisos'
import { RUTA_MIS_DESIGNACIONES } from '@/features/arbitraje/designaciones/rutas'
import { semanaActual, semanaDeParametro } from '@/features/canchas/fechas'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermisoPropio } from '@/layouts/navegacion'

export { RUTA_DESIGNACIONES } from '@/features/arbitraje/designaciones/rutas'

/**
 * Designación de árbitros F-24 (H-14.2, UI-025, DEC-209 a DEC-211). La Comisión de Arbitraje elige
 * la semana (`?semana=`, el lunes) y abre una jornada (`?jornadaId=`) para designar al central y a
 * los asistentes de cada partido. Una jornada llegada por URL se muestra aunque falte la semana.
 *
 * Refs: RF-023, RF-115, RF-039, RF-022, VAL-019, RN-021, DEC-201 a DEC-204
 */
export function PantallaDesignaciones() {
  const { consultar, editar } = usePermisosDesignacion()
  const { yo } = useSesionActual()
  const [parametros, setParametros] = useSearchParams()
  const jornadaId = parametros.get('jornadaId')
  const semanaParametro = parametros.get('semana')
  const semana = semanaDeParametro(semanaParametro)

  const encabezado = (
    <header className="flex flex-col gap-1">
      <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
        Designación de árbitros
      </h1>
      <p className="max-w-2xl text-body-md text-text-muted">
        Elige la jornada y designa al árbitro central y a los asistentes de cada partido. Los
        árbitros que no pueden designarse muestran la causa.
      </p>
    </header>
  )

  if (!consultar) {
    return (
      <div className="flex flex-col gap-6">
        {encabezado}
        <EstadoVacio
          icono={ShieldOff}
          titulo="Esta pantalla es de la Comisión de Arbitraje y de la Programación"
          descripcion="Aquí se designan los árbitros de cada partido de la liga. Tu cuenta no consulta las designaciones de la liga."
          accion={
            tienePermisoPropio(yo.permisos, 'arbitraje.designacion:CONSULTAR') ? (
              <Link to={RUTA_MIS_DESIGNACIONES} className={clasesBoton('secundario')}>
                <ClipboardCheck aria-hidden="true" className="size-4 shrink-0" />
                Ver mis designaciones
              </Link>
            ) : undefined
          }
        />
      </div>
    )
  }

  const cambiarSemana = (lunes: string | null) =>
    setParametros(lunes ? { semana: lunes } : {})

  return (
    <div className="flex flex-col gap-6">
      {encabezado}

      {jornadaId ? (
        <JornadaDesignaciones
          key={jornadaId}
          jornadaId={jornadaId}
          semana={semanaParametro ? semana : null}
          editar={editar}
        />
      ) : (
        <section aria-label="Jornadas de la semana" className="flex flex-col gap-4">
          <NavegadorSemana
            semana={semana}
            esSemanaActual={semana === semanaActual()}
            onCambiar={cambiarSemana}
          />
          <ListaJornadasSemana semana={semana} />
        </section>
      )}
    </div>
  )
}
