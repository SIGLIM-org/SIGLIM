import { Link } from 'react-router'
import {
  ArrowRight,
  CalendarDays,
  ClipboardCheck,
  Goal,
  ShieldCheck,
  Trophy,
} from 'lucide-react'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { AvisoPublicacion } from '@/features/portal/AvisoPublicacion'
import { EncabezadoPortal } from '@/features/portal/EncabezadoPortal'

/**
 * Tabla de posiciones del portal (ruta `/tabla`, F-04).
 *
 * Reserva la sección `#tabla`, que H-19.2 sustituirá por la respuesta de
 * `GET /api/v1/publico/grupos/{id}/tabla`. La tabla es un indicador calculado
 * (RN-022) y se marca como no oficial mientras use parámetros [DEMO].
 */
export function PortalTabla() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <EncabezadoPortal
        seccion="Tabla de posiciones"
        titulo="Tabla de posiciones"
        descripcion="Consulta las posiciones por categoría y grupo, calculadas a partir de los resultados oficiales. La tabla no se captura a mano."
        acciones={
          <>
            <Link to="/resultados" className={clasesBoton('primario')}>
              <Goal aria-hidden="true" className="size-4" />
              Ver resultados
            </Link>
            <Link to="/" className={clasesBoton('secundario')}>
              <CalendarDays aria-hidden="true" className="size-4" />
              Ver programación
            </Link>
          </>
        }
      />

      <section
        id="tabla"
        aria-labelledby="titulo-tabla"
        className="mt-8 scroll-mt-20"
      >
        <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-label-sm uppercase text-primary">Posiciones</p>
            <h2 id="titulo-tabla" className="text-headline-lg text-text-main">
              Tabla publicada
            </h2>
            <p className="mt-1 max-w-2xl text-body-md text-text-muted">
              Aquí aparecerá la tabla de cada grupo con los puntos y criterios de
              desempate configurados para la temporada.
            </p>
          </div>
          <Insignia variante="neutra" icono={Trophy}>
            Sin posiciones publicadas
          </Insignia>
        </div>

        <EstadoVacio
          titulo="Aún no hay tabla de posiciones"
          descripcion="La tabla se calcula cuando el grupo tiene resultados oficiales. No se captura a mano: se deriva de los resultados publicados."
          icono={Trophy}
          accion={
            <Link to="/resultados" className={clasesBoton('secundario')}>
              Consultar resultados
              <ArrowRight aria-hidden="true" className="size-4" />
            </Link>
          }
        />
      </section>

      <AvisoPublicacion>
        <span className="font-medium">La tabla es un indicador calculado.</span>{' '}
        El resultado del partido es el dato oficial; las posiciones se derivan de
        los resultados publicados.
      </AvisoPublicacion>

      <section aria-labelledby="titulo-contenido-tabla" className="mt-8">
        <h2
          id="titulo-contenido-tabla"
          className="text-headline-lg text-text-main"
        >
          Qué incluye la tabla
        </h2>
        <p className="mt-1 max-w-3xl text-body-md text-text-muted">
          La consulta muestra el rendimiento de cada equipo y distingue los
          parámetros que la Liga aún no ha aprobado.
        </p>

        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <ClipboardCheck aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">Columnas de juego</h3>
            <p className="text-body-md text-text-muted">
              Partidos jugados, ganados, empatados y perdidos; goles a favor y en
              contra, diferencia y puntos.
            </p>
          </article>

          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <ShieldCheck aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">
              Criterios de desempate
            </h3>
            <p className="text-body-md text-text-muted">
              El orden sigue los criterios configurados para la temporada, nunca
              el nombre del equipo.
            </p>
          </article>

          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <Trophy aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">
              Parámetros no oficiales
            </h3>
            <p className="text-body-md text-text-muted">
              Si los puntos o desempates son valores de demostración, la tabla se
              identifica como no oficial.
            </p>
            <InsigniaNoOficial />
          </article>
        </div>
      </section>
    </div>
  )
}
