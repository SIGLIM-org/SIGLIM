import { useId } from 'react'
import { ArrowRightLeft, History, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import type { OpcionMovimiento, TipoMovimiento } from '@/features/registros/tipos'

type Props = {
  /** Opción con `tipo`: la persona jugó en una temporada anterior y no tiene registro en esta. */
  opcion: OpcionMovimiento & { tipo: TipoMovimiento }
  onElegir: (tipo: TipoMovimiento) => void
}

/**
 * Oferta del paso 1 de F-18 (B-06.2, DEC-147): si la persona jugó en una temporada anterior, el
 * registro se crea como renovación (mismo club, RF-015) o como transferencia (otro club, RF-016).
 * Una transferencia que la temporada no admite (RF-111) queda deshabilitada con la explicación, no
 * se oculta. Los textos no nombran al otro club (DEC-88, DEC-90, PEN-016).
 */
export function OfertaMovimiento({ opcion, onElegir }: Props) {
  const id = useId()
  const temporada = opcion.temporadaAnterior
    ? `en la temporada «${opcion.temporadaAnterior}»`
    : 'en una temporada anterior'
  const renovacion = opcion.tipo === 'RENOVACION'
  const bloqueada = !opcion.permitido
  const idMotivo = `${id}-motivo`

  return (
    <section
      aria-labelledby={`${id}-titulo`}
      className="flex gap-3 rounded-lg border border-border bg-background p-4"
    >
      <History aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-primary" />
      <div className="flex min-w-0 flex-col gap-2">
        <h3 id={`${id}-titulo`} className="text-headline-sm text-text-main">
          {renovacion ? `Jugó en tu club ${temporada}` : `Jugó en otro club ${temporada}`}
        </h3>
        <p className="max-w-prose text-body-md text-text-muted">
          {renovacion
            ? 'Renueva su registro para esta temporada: queda enlazado a su historial y sus documentos aprobados siguen contando. La liga valida el registro nuevo.'
            : 'Regístralo en tu club como transferencia: su historial queda enlazado y cargas sus documentos para el registro nuevo. La liga valida el registro.'}
        </p>
        {bloqueada && (
          <p id={idMotivo} className="max-w-prose text-body-md text-text-main">
            {opcion.motivo ??
              'La liga no permite cambios de club entre temporadas en esta temporada. Consulta a la liga si debe habilitarse.'}
          </p>
        )}
        <div className="pt-1">
          <Button
            icono={renovacion ? RefreshCw : ArrowRightLeft}
            disabled={bloqueada}
            aria-describedby={bloqueada ? idMotivo : undefined}
            onClick={() => onElegir(opcion.tipo)}
            className="w-full sm:w-auto"
          >
            {renovacion ? 'Renovar en mi club' : 'Registrar en este equipo'}
          </Button>
        </div>
      </div>
    </section>
  )
}
