import { forwardRef, type ReactNode } from 'react'
import { fechaLarga, horaCorta } from '@/features/canchas/tramites/etiquetas'
import type { CartaOficio, EstadoSolicitudCancha } from '@/features/canchas/tramites/tipos'

type Props = {
  carta: CartaOficio
}

function Dato({ etiqueta, valor }: { etiqueta: string; valor: ReactNode }) {
  return (
    <li>
      <strong>{etiqueta}:</strong> {valor}.
    </li>
  )
}

function Casilla({ marcada, texto }: { marcada: boolean; texto: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span aria-hidden="true" className="font-mono">
        {marcada ? '☒' : '☐'}
      </span>
      {texto}
    </span>
  )
}

function Firma({ nombre, lineas }: { nombre: string; lineas: (string | undefined)[] }) {
  return (
    <div className="mt-10 flex flex-col items-center text-center">
      <p className="font-bold">ATENTAMENTE</p>
      <span aria-hidden="true" className="mt-12 block w-64 border-t border-text-main" />
      <p className="mt-1 font-bold uppercase">{nombre}</p>
      {lineas.filter(Boolean).map((linea) => (
        <p key={linea}>{linea}</p>
      ))}
    </div>
  )
}

const ESTADOS_F1: readonly [EstadoSolicitudCancha, string][] = [
  ['APROBADA', 'Aprobada'],
  ['NO_APROBADA', 'No aprobada'],
  ['PENDIENTE', 'Pendiente'],
]
const ESTADOS_F2: readonly [EstadoSolicitudCancha, string][] = [
  ['ACEPTADA', 'Aceptada'],
  ['RECHAZADA', 'Rechazada'],
  ['PENDIENTE_NEGOCIACION', 'Pendiente de negociación'],
]

/**
 * Carta de los Formatos 1 y 2 del cliente (R8) con número de oficio y datos del trámite (RF-118).
 * Reproduce la estructura de la plantilla: encabezado, destinatario, cuerpo, datos, firma y el
 * espacio de autorización (Formato 1) o el acuse de recepción (Formato 2) con el estado vigente.
 */
export const CartaOficioDocumento = forwardRef<HTMLElement, Props>(function CartaOficioDocumento(
  { carta },
  ref,
) {
  const { prestamo, oferta } = carta
  const lugarFecha = `${prestamo?.lugar ?? oferta?.lugar ?? ''}, a ${fechaLarga(carta.fechaEmision)}.`
  const estados = carta.formato === 1 ? ESTADOS_F1 : ESTADOS_F2

  return (
    <article
      ref={ref}
      aria-label={`Carta del oficio ${carta.numeroOficio}`}
      className="mx-auto flex max-w-[70ch] flex-col gap-4 bg-surface p-8 text-body-md leading-relaxed text-text-main sm:p-12"
    >
      <header className="flex flex-col gap-0.5">
        <p className="font-bold uppercase">
          {carta.formato === 1 ? carta.liga : oferta?.institucion || oferta?.oferenteNombre}
        </p>
        <p>
          <strong>OFICIO N.º:</strong> <span className="font-mono tabular-nums">{carta.numeroOficio}</span>
        </p>
        <p>
          <strong>ASUNTO:</strong> {carta.asunto}
        </p>
      </header>

      <p className="text-right">{lugarFecha}</p>

      {prestamo && (
        <>
          <div>
            <p className="font-bold uppercase">C. {prestamo.destinatarioNombre}</p>
            <p>{prestamo.destinatarioCargo}</p>
            <p className="font-bold">PRESENTE.</p>
          </div>
          <p className="text-justify">
            Por medio de la presente, quien suscribe, <strong>{prestamo.solicitanteNombre}</strong>, en
            calidad de <strong>{prestamo.solicitanteCargo}</strong> de <strong>{carta.solicitante}</strong>,
            solicita atentamente {carta.tipo === 'COMODATO' ? 'en comodato' : 'el préstamo de'} las
            instalaciones deportivas ubicadas en <strong>{prestamo.instalacion}</strong>, con la finalidad
            de llevar a cabo <strong>{prestamo.finalidad}</strong>.
          </p>
          <p>Para tal efecto, se presenta la siguiente información:</p>
          <ul className="list-disc pl-6">
            <Dato etiqueta="Instalación solicitada" valor={prestamo.instalacion} />
            <Dato etiqueta="Fecha de utilización" valor={fechaLarga(prestamo.fechaUtilizacion)} />
            <Dato
              etiqueta="Horario solicitado"
              valor={`${horaCorta(prestamo.horaInicio)} a ${horaCorta(prestamo.horaTermino)}`}
            />
            <Dato etiqueta="Actividad deportiva" valor={prestamo.actividad} />
            {prestamo.equiposParticipantes && (
              <Dato etiqueta="Equipos participantes" valor={prestamo.equiposParticipantes} />
            )}
            {prestamo.asistentesAproximados != null && (
              <Dato etiqueta="Número aproximado de asistentes" valor={prestamo.asistentesAproximados} />
            )}
            <Dato etiqueta="Responsable de la actividad" valor={prestamo.responsableActividad} />
            <Dato etiqueta="Teléfono de contacto" valor={prestamo.telefonoContacto} />
          </ul>
          <p className="text-justify">
            Asimismo, manifestamos nuestra disposición para respetar las disposiciones de uso de las
            instalaciones, cuidar el espacio deportivo y atender las condiciones de acceso, limpieza,
            seguridad y conservación que establezca la administración.
          </p>
          <p className="text-justify">
            Agradecemos de antemano la atención brindada a la presente solicitud y quedamos en espera de
            su respuesta y de la confirmación de disponibilidad de las instalaciones.
          </p>
          <Firma
            nombre={prestamo.solicitanteNombre}
            lineas={[
              prestamo.solicitanteCargo,
              carta.solicitante,
              `Teléfono: ${prestamo.telefonoContacto}`,
              prestamo.correoContacto && `Correo electrónico: ${prestamo.correoContacto}`,
            ]}
          />
        </>
      )}

      {oferta && (
        <>
          <div>
            <p className="font-bold uppercase">A la directiva de {carta.liga}</p>
            <p className="font-bold">PRESENTE.</p>
          </div>
          <p className="text-justify">
            Por medio de la presente, yo, <strong>{oferta.oferenteNombre}</strong>, en calidad de{' '}
            <strong>{oferta.oferenteCaracter}</strong> de la instalación deportiva denominada{' '}
            <strong>{oferta.nombreCancha}</strong>, ubicada en <strong>{oferta.ubicacion}</strong>,
            manifiesto mi interés en poner a disposición de esa liga las instalaciones mencionadas para la
            celebración de encuentros deportivos correspondientes a sus competiciones.
          </p>
          <p>Las características y condiciones generales de la oferta son las siguientes:</p>
          <ul className="list-disc pl-6">
            <Dato etiqueta="Nombre de la cancha" valor={oferta.nombreCancha} />
            <Dato etiqueta="Ubicación" valor={oferta.ubicacion} />
            <Dato etiqueta="Tipo de superficie" valor={oferta.tipoSuperficie} />
            <Dato etiqueta="Disponibilidad" valor={oferta.disponibilidad} />
            {oferta.capacidadAproximada != null && (
              <Dato etiqueta="Capacidad aproximada" valor={oferta.capacidadAproximada} />
            )}
            {oferta.serviciosDisponibles && (
              <Dato etiqueta="Servicios disponibles" valor={oferta.serviciosDisponibles} />
            )}
            <Dato etiqueta="Costo por uso" valor={oferta.costoUso} />
            {oferta.condicionesPago && <Dato etiqueta="Condiciones de pago" valor={oferta.condicionesPago} />}
            <Dato etiqueta="Persona responsable" valor={oferta.personaResponsable} />
            <Dato etiqueta="Teléfono de contacto" valor={oferta.telefonoContacto} />
          </ul>
          <p className="text-justify">
            La presente oferta queda sujeta a la disponibilidad de las instalaciones y a la aceptación de
            las condiciones de uso que acuerden ambas partes. La programación definitiva de partidos se
            realizará previa confirmación de fechas y horarios.
          </p>
          <p className="text-justify">
            Asimismo, se establece que cualquier condición relativa al mantenimiento, limpieza, daños,
            cancelaciones y responsabilidades durante el uso de las instalaciones deberá acordarse con la
            directiva de la liga antes de su utilización.
          </p>
          <p className="text-justify">
            Sin más por el momento, quedo a disposición para proporcionar información adicional y
            formalizar los acuerdos correspondientes.
          </p>
          <Firma
            nombre={oferta.oferenteNombre}
            lineas={[
              oferta.oferenteCaracter,
              oferta.institucion,
              `Teléfono: ${oferta.telefonoContacto}`,
              oferta.correoContacto && `Correo electrónico: ${oferta.correoContacto}`,
            ]}
          />
        </>
      )}

      <section className="mt-6 flex flex-col gap-2 border-t border-dashed border-text-muted pt-4">
        <h3 className="font-bold uppercase">
          {carta.formato === 1 ? 'Espacio para la autorización' : 'Acuse de recepción de la liga'}
        </h3>
        <p className="flex flex-wrap gap-x-6 gap-y-1">
          <strong>{carta.formato === 1 ? 'Solicitud:' : 'Resultado de la revisión:'}</strong>
          {estados.map(([estado, texto]) => (
            <Casilla key={estado} marcada={carta.estado === estado} texto={texto} />
          ))}
        </p>
        <p>
          <strong>Observaciones{carta.formato === 1 ? ' y condiciones' : ''}:</strong>{' '}
          {carta.observaciones ?? '—'}
        </p>
        <p>
          <strong>{carta.formato === 1 ? 'Responsable de la autorización' : 'Recibido por'}:</strong>{' '}
          {carta.resueltaPor ?? '______________________________'}
        </p>
        <p>
          <strong>Fecha:</strong>{' '}
          {carta.resueltaEn ? fechaLarga(carta.resueltaEn.slice(0, 10)) : '______________'}
          <span className="ml-8">
            <strong>Firma:</strong> ______________________
          </span>
        </p>
      </section>
    </article>
  )
})
