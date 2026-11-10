import { Link } from 'react-router'
import {
  ArrowRight,
  CalendarDays,
  ClipboardCheck,
  EyeOff,
  Goal,
  ShieldCheck,
  Trophy,
} from 'lucide-react'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { AvisoPublicacion } from '@/features/portal/AvisoPublicacion'
import { EncabezadoPortal } from '@/features/portal/EncabezadoPortal'

/**
 * Resultados oficiales del portal (ruta `/resultados`, F-03).
 *
 * Reserva la sección `#resultados`, que H-19.2 sustituirá por los datos de
 * `GET /api/v1/publico/resultados`. Sin datos de ejemplo: solo se publican
 * resultados de cédulas aprobadas (RF-056, VAL-014).
 */
export function PortalResultados() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <EncabezadoPortal
        seccion="Resultados"
        titulo="Resultados oficiales"
        descripcion="Consulta los marcadores de los partidos con cédula arbitral aprobada. Los resultados se publican al validarse la cédula y no incluyen datos personales."
        acciones={
          <>
            <Link to="/" className={clasesBoton('secundario')}>
              <CalendarDays aria-hidden="true" className="size-4" />
              Ver programación
            </Link>
            <Link to="/tabla" className={clasesBoton('primario')}>
              <Trophy aria-hidden="true" className="size-4" />
              Consultar tabla
            </Link>
          </>
        }
      />

      <section
        id="resultados"
        aria-labelledby="titulo-resultados"
        className="mt-8 scroll-mt-20"
      >
        <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-label-sm uppercase text-primary">Marcadores</p>
            <h2
              id="titulo-resultados"
              className="text-headline-lg text-text-main"
            >
              Resultados publicados
            </h2>
            <p className="mt-1 max-w-2xl text-body-md text-text-muted">
              Aquí aparecerán los partidos con su marcador oficial, agrupados
              por día y cancha, con filtros por categoría, grupo y equipo.
            </p>
          </div>
          <Insignia variante="neutra" icono={Goal}>
            Sin resultados publicados
          </Insignia>
        </div>

        <EstadoVacio
          titulo="Aún no hay resultados publicados"
          descripcion="Los marcadores se muestran cuando la cédula arbitral del partido está aprobada. Mientras tanto, consulta la programación o la tabla de posiciones."
          icono={Goal}
          accion={
            <Link to="/tabla" className={clasesBoton('secundario')}>
              Ver tabla de posiciones
              <ArrowRight aria-hidden="true" className="size-4" />
            </Link>
          }
        />
      </section>

      <AvisoPublicacion>
        <span className="font-medium">
          Solo se publican resultados oficiales.
        </span>{' '}
        Un marcador aparece cuando la cédula arbitral está aprobada; el portal no
        expone datos personales.
      </AvisoPublicacion>

      <section aria-labelledby="titulo-lectura" className="mt-8">
        <h2 id="titulo-lectura" className="text-headline-lg text-text-main">
          Qué encontrarás en los resultados
        </h2>
        <p className="mt-1 max-w-3xl text-body-md text-text-muted">
          Cada marcador conserva su carácter oficial y se distingue de los
          indicadores calculados, como la tabla de posiciones.
        </p>

        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <ClipboardCheck aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">Marcador oficial</h3>
            <p className="text-body-md text-text-muted">
              El resultado se publica cuando la cédula arbitral del partido está
              aprobada.
            </p>
          </article>

          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <ShieldCheck aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">
              Resultado por default
            </h3>
            <p className="text-body-md text-text-muted">
              Cuando el reglamento lo determine, el marcador se identifica como
              «Por default».
            </p>
          </article>

          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <EyeOff aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">Sin datos personales</h3>
            <p className="text-body-md text-text-muted">
              Se muestran equipos, cancha y marcador; no se exponen datos de
              jugadores.
            </p>
          </article>
        </div>
      </section>
    </div>
  )
}
