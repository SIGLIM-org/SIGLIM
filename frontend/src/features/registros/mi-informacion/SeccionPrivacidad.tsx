import { useId } from 'react'
import { Link } from 'react-router'

/**
 * Privacidad con texto fijo y sin datos: aún no hay consulta de consentimientos y la solicitud ARCO
 * exige un permiso que el titular no tiene, así que no se ofrece «Solicitar revocación de difusión»
 * (DEC-149; RF-097 y RF-098 quedan en el backlog).
 *
 * Refs: RF-144, RF-097, RF-098, DEC-67, DEC-149
 */
export function SeccionPrivacidad() {
  const id = useId()

  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      <h2 id={id} className="text-headline-md text-text-main">
        Privacidad
      </h2>
      <div className="flex flex-col gap-2 rounded-lg border border-border bg-surface px-4 py-3">
        <p className="text-body-md text-text-main">
          Tus datos se tratan conforme al aviso de privacidad de la Liga. Para revocar la difusión de
          tu imagen o ejercer tus derechos de acceso, rectificación, cancelación u oposición,
          solicítalo a la Secretaría de la Liga.
        </p>
        <Link
          to="/aviso-de-privacidad"
          className="inline-flex min-h-11 items-center self-start text-label-md text-primary underline underline-offset-2 hover:text-primary-hover"
        >
          Consultar el aviso de privacidad
        </Link>
      </div>
    </section>
  )
}
