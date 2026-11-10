import { Link } from 'react-router'
import {
  ArrowRight,
  CalendarDays,
  ClipboardCheck,
  EyeOff,
  Goal,
  LogIn,
  ShieldCheck,
  Trophy,
} from 'lucide-react'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { AvisoPublicacion } from '@/features/portal/AvisoPublicacion'
import { EncabezadoPortal } from '@/features/portal/EncabezadoPortal'

/**
 * Inicio del portal público (ruta `/`, F-01).
 *
 * Presenta el portal y reserva la sección `#programacion`, que H-19.2 sustituirá
 * por la programación real de `GET /api/v1/publico/programacion` con sus filtros.
 * No se usan datos de ejemplo: el portal solo muestra información publicada
 * (RF-056, VAL-014).
 */
export function PortalProgramacion() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <EncabezadoPortal
        seccion="Programación"
        titulo="Programación de la semana"
        descripcion="Consulta la programación aprobada por la Liga, los resultados oficiales y las tablas de posiciones. No necesitas una cuenta y el portal es de solo lectura."
        acciones={
          <>
            <Link to="/resultados" className={clasesBoton('primario')}>
              <Goal aria-hidden="true" className="size-4" />
              Consultar resultados
            </Link>
            <Link to="/tabla" className={clasesBoton('secundario')}>
              <Trophy aria-hidden="true" className="size-4" />
              Ver tabla de posiciones
            </Link>
          </>
        }
      />

      <section
        id="programacion"
        aria-labelledby="titulo-programacion"
        className="mt-8 scroll-mt-20"
      >
        <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-label-sm uppercase text-primary">Jornada</p>
            <h2
              id="titulo-programacion"
              className="text-headline-lg text-text-main"
            >
              Programación publicada
            </h2>
            <p className="mt-1 max-w-2xl text-body-md text-text-muted">
              Aquí aparecerán los partidos de la jornada agrupados por día y
              cancha, con sus filtros por categoría, grupo y equipo.
            </p>
          </div>
          <Insignia variante="neutra" icono={CalendarDays}>
            Sin jornada publicada
          </Insignia>
        </div>

        <EstadoVacio
          titulo="Aún no hay programación publicada"
          descripcion="La programación se muestra cuando la jornada está aprobada y publicada. Mientras tanto, puedes consultar los resultados oficiales y la tabla de posiciones."
          icono={CalendarDays}
          accion={
            <Link to="/resultados" className={clasesBoton('secundario')}>
              Consultar resultados
              <ArrowRight aria-hidden="true" className="size-4" />
            </Link>
          }
        />
      </section>

      <AvisoPublicacion>
        <span className="font-medium">Solo se publica información aprobada.</span>{' '}
        La programación requiere la aprobación del Presidente y los resultados se
        muestran con la cédula arbitral aprobada.
      </AvisoPublicacion>

      <section aria-labelledby="titulo-accesos" className="mt-8">
        <h2 id="titulo-accesos" className="text-headline-lg text-text-main">
          Explora el portal
        </h2>
        <p className="mt-1 text-body-md text-text-muted">
          Tres secciones de consulta, sin iniciar sesión.
        </p>

        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <a
            href="#programacion"
            className="group flex flex-col gap-3 rounded-lg border border-border bg-surface p-5 transition-colors hover:border-primary hover:bg-background"
          >
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <CalendarDays aria-hidden="true" className="size-5" />
            </span>
            <span className="text-headline-sm text-text-main">Programación</span>
            <span className="text-body-md text-text-muted">
              Partidos de la jornada por día, cancha, categoría y grupo.
            </span>
            <span className="mt-auto inline-flex items-center gap-1 text-label-md text-primary">
              Ver programación
              <ArrowRight
                aria-hidden="true"
                className="size-4 transition-transform group-hover:translate-x-0.5"
              />
            </span>
          </a>

          <Link
            to="/resultados"
            className="group flex flex-col gap-3 rounded-lg border border-border bg-surface p-5 transition-colors hover:border-primary hover:bg-background"
          >
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <Goal aria-hidden="true" className="size-5" />
            </span>
            <span className="text-headline-sm text-text-main">
              Resultados oficiales
            </span>
            <span className="text-body-md text-text-muted">
              Marcadores de los partidos con cédula arbitral aprobada.
            </span>
            <span className="mt-auto inline-flex items-center gap-1 text-label-md text-primary">
              Consultar resultados
              <ArrowRight
                aria-hidden="true"
                className="size-4 transition-transform group-hover:translate-x-0.5"
              />
            </span>
          </Link>

          <Link
            to="/tabla"
            className="group flex flex-col gap-3 rounded-lg border border-border bg-surface p-5 transition-colors hover:border-primary hover:bg-background"
          >
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <Trophy aria-hidden="true" className="size-5" />
            </span>
            <span className="text-headline-sm text-text-main">
              Tabla de posiciones
            </span>
            <span className="text-body-md text-text-muted">
              Posiciones calculadas por grupo a partir de los resultados
              oficiales.
            </span>
            <span className="mt-auto inline-flex items-center gap-1 text-label-md text-primary">
              Ver tabla de posiciones
              <ArrowRight
                aria-hidden="true"
                className="size-4 transition-transform group-hover:translate-x-0.5"
              />
            </span>
          </Link>
        </div>
      </section>

      <section aria-labelledby="titulo-publicacion" className="mt-8">
        <h2 id="titulo-publicacion" className="text-headline-lg text-text-main">
          Información oficial, paso a paso
        </h2>
        <p className="mt-1 max-w-3xl text-body-md text-text-muted">
          El portal solo muestra información que la Liga ya validó. Este es el
          recorrido de cada dato hasta su publicación.
        </p>

        <ol className="mt-4 grid gap-4 sm:grid-cols-3">
          <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <ClipboardCheck aria-hidden="true" className="size-5" />
            </span>
            <span className="text-label-sm uppercase text-text-muted">Paso 1</span>
            <span className="text-headline-sm text-text-main">
              Jornada aprobada
            </span>
            <span className="text-body-md text-text-muted">
              La programación se publica cuando el Presidente aprueba la
              jornada.
            </span>
          </li>

          <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <ShieldCheck aria-hidden="true" className="size-5" />
            </span>
            <span className="text-label-sm uppercase text-text-muted">Paso 2</span>
            <span className="text-headline-sm text-text-main">
              Cédula validada
            </span>
            <span className="text-body-md text-text-muted">
              Los resultados aparecen al aprobarse la cédula arbitral del
              partido.
            </span>
          </li>

          <li className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <Trophy aria-hidden="true" className="size-5" />
            </span>
            <span className="text-label-sm uppercase text-text-muted">Paso 3</span>
            <span className="text-headline-sm text-text-main">Tabla calculada</span>
            <span className="text-body-md text-text-muted">
              Las posiciones se calculan a partir de los resultados oficiales,
              nunca se capturan a mano.
            </span>
          </li>
        </ol>
      </section>

      <section className="mt-8 grid gap-4 lg:grid-cols-2">
        <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-5">
          <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
            <LogIn aria-hidden="true" className="size-5" />
          </span>
          <h2 className="text-headline-md text-text-main">Acceso para la Liga</h2>
          <p className="text-body-md text-text-muted">
            Autoridades, clubes, árbitros y jugadores registrados acceden a la
            aplicación interna con su cuenta.
          </p>
          <Link
            to="/login"
            className={`${clasesBoton('primario')} mt-auto self-start`}
          >
            Iniciar sesión
          </Link>
        </div>

        <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-5">
          <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
            <EyeOff aria-hidden="true" className="size-5" />
          </span>
          <h2 className="text-headline-md text-text-main">Privacidad</h2>
          <p className="text-body-md text-text-muted">
            El portal no publica datos personales, salvo los que su titular
            autorizó para difusión.
          </p>
          <Link
            to="/aviso-de-privacidad"
            className={`${clasesBoton('secundario')} mt-auto self-start`}
          >
            Consultar aviso de privacidad
          </Link>
        </div>
      </section>
    </div>
  )
}
