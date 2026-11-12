import { useId } from 'react'
import { Link } from 'react-router'
import { CircleCheck, Info, UserRoundPlus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { Insignia } from '@/components/ui/Insignia'
import { TituloPaso } from '@/features/registros/TituloPaso'
import type { RegistroJugador } from '@/features/registros/tipos'

type Props = {
  enfocar: boolean
  /** H-10.3 sustituye este componente y carga los documentos sobre este registro. */
  registroId: string
  registro: RegistroJugador
  rutaSalida: string
  onOtroJugador: () => void
}

const formatoFecha = new Intl.DateTimeFormat('es-MX', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

/**
 * Paso 4 provisional (DEC-108): el resumen del registro recién creado, en `PENDIENTE_VALIDACION`,
 * y el aviso de que la carga de documentos todavía no está disponible. H-10.3 lo reemplaza.
 */
export function PasoDocumentosPendiente({
  enfocar,
  registroId,
  registro,
  rutaSalida,
  onOtroJugador,
}: Props) {
  const id = useId()
  const datos: { termino: string; valor: string }[] = [
    { termino: 'Jugador', valor: registro.persona.nombreCompleto },
    { termino: 'Equipo', valor: registro.equipo.nombre },
    { termino: 'Club', valor: registro.club.nombre },
    { termino: 'Categoría', valor: registro.categoria.nombre },
    { termino: 'Temporada', valor: registro.temporada.nombre },
    {
      termino: 'Fecha de alta',
      valor: formatoFecha.format(new Date(`${registro.altaEn}T00:00:00Z`)),
    },
  ]

  return (
    <section aria-labelledby={`${id}-titulo`} data-registro-id={registroId} className="flex flex-col gap-6">
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-md bg-success/12 text-success-strong">
          <CircleCheck aria-hidden="true" className="size-5" />
        </span>
        <div className="flex min-w-0 flex-col gap-2">
          <TituloPaso id={`${id}-titulo`} enfocar={enfocar}>
            Registro enviado a validación
          </TituloPaso>
          <div>
            <Insignia variante="advertencia">Pendiente de validación</Insignia>
          </div>
        </div>
      </div>

      <dl className="grid gap-px overflow-hidden rounded-lg border border-border bg-border sm:grid-cols-2 lg:grid-cols-3">
        {datos.map(({ termino, valor }) => (
          <div key={termino} className="flex flex-col gap-1 bg-surface p-4">
            <dt className="text-body-sm text-text-muted">{termino}</dt>
            <dd className="break-words text-headline-sm text-text-main">{valor}</dd>
          </div>
        ))}
      </dl>

      <p className="flex max-w-prose items-start gap-2 text-body-md text-text-muted">
        <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        La carga de documentos aún no está disponible en esta pantalla. El registro queda pendiente
        de validación hasta que la liga revise sus documentos.
      </p>

      <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:justify-end">
        <Link to={rutaSalida} className={clasesBoton('secundario')}>
          Volver a Organización
        </Link>
        <Button icono={UserRoundPlus} onClick={onOtroJugador}>
          Dar de alta a otro jugador
        </Button>
      </div>
    </section>
  )
}
