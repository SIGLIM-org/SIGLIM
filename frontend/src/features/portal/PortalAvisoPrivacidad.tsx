import { Link } from 'react-router'
import {
  ArrowLeft,
  CalendarDays,
  EyeOff,
  FileText,
  Goal,
  LockKeyhole,
  UserCheck,
} from 'lucide-react'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { AvisoPublicacion } from '@/features/portal/AvisoPublicacion'
import { EncabezadoPortal } from '@/features/portal/EncabezadoPortal'

/**
 * Aviso de privacidad del portal (ruta `/aviso-de-privacidad`).
 *
 * El texto legal lo define la Liga (PEN-039, DEC-40) y se cargará desde
 * `avisos_privacidad`; por eso la página no inventa el contenido y lo marca como
 * pendiente de publicación. Sí explica las reglas ya decididas sobre datos
 * personales (RNF-012, RF-145).
 */
export function PortalAvisoPrivacidad() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <EncabezadoPortal
        seccion="Aviso de privacidad"
        titulo="Aviso de privacidad"
        descripcion="Consulta cómo SIGLIM protege los datos personales de jugadores, tutores y usuarios. El texto legal del aviso lo define la Liga."
        acciones={
          <>
            <Link to="/" className={clasesBoton('primario')}>
              <CalendarDays aria-hidden="true" className="size-4" />
              Ir a la programación
            </Link>
            <Link to="/resultados" className={clasesBoton('secundario')}>
              <Goal aria-hidden="true" className="size-4" />
              Ver resultados
            </Link>
          </>
        }
      />

      <section
        id="aviso"
        aria-labelledby="titulo-aviso"
        className="mt-8 scroll-mt-20"
      >
        <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-label-sm uppercase text-primary">
              Información legal
            </p>
            <h2 id="titulo-aviso" className="text-headline-lg text-text-main">
              Contenido del aviso
            </h2>
            <p className="mt-1 max-w-2xl text-body-md text-text-muted">
              El texto completo se mostrará aquí cuando la Liga apruebe el aviso
              que corresponde a esta instalación.
            </p>
          </div>
          <Insignia variante="neutra" icono={FileText}>
            Pendiente de publicación
          </Insignia>
        </div>

        <EstadoVacio
          titulo="El aviso aún no está disponible"
          descripcion="El contenido legal del aviso de privacidad lo define la Liga. Cuando esté aprobado, se publicará en esta página."
          icono={FileText}
          accion={
            <Link to="/" className={clasesBoton('secundario')}>
              <ArrowLeft aria-hidden="true" className="size-4" />
              Volver al portal
            </Link>
          }
        />
      </section>

      <AvisoPublicacion>
        <span className="font-medium">
          El portal no publica datos personales.
        </span>{' '}
        Solo se muestran los datos que su titular autorizó para difusión, conforme
        a la protección de datos del sistema.
      </AvisoPublicacion>

      <section aria-labelledby="titulo-proteccion" className="mt-8">
        <h2 id="titulo-proteccion" className="text-headline-lg text-text-main">
          Cómo se protegen los datos
        </h2>
        <p className="mt-1 max-w-3xl text-body-md text-text-muted">
          Estas reglas ya rigen el funcionamiento del portal mientras el texto
          legal está en definición.
        </p>

        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <EyeOff aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">
              Datos personales fuera del portal
            </h3>
            <p className="text-body-md text-text-muted">
              El portal muestra equipos, canchas y marcadores; no expone datos de
              jugadores.
            </p>
          </article>

          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <UserCheck aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">
              Difusión con consentimiento
            </h3>
            <p className="text-body-md text-text-muted">
              Un jugador solo aparece con nombre si autorizó su difusión; si no,
              se muestra «Jugador (dato reservado)».
            </p>
          </article>

          <article className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-5">
            <span className="grid size-10 place-items-center rounded-md bg-primary/10 text-primary">
              <LockKeyhole aria-hidden="true" className="size-5" />
            </span>
            <h3 className="text-headline-sm text-text-main">Acceso por ámbito</h3>
            <p className="text-body-md text-text-muted">
              Los datos personales solo se consultan dentro del ámbito autorizado
              para cada usuario.
            </p>
          </article>
        </div>
      </section>

    </div>
  )
}
