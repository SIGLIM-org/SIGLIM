import {
  elegibilidadDeRegistro,
  type VarianteElegibilidad,
} from '@/features/registros/plantilla/elegibilidad'
import type { EstadoRegistro } from '@/features/registros/tipos'

const COLOR: Record<VarianteElegibilidad, string> = {
  exito: 'text-success-strong',
  advertencia: 'text-warning-strong',
  error: 'text-error-strong',
}

type Props = {
  estado: EstadoRegistro
}

/**
 * Elegibilidad calculada (DEC-117). Va como ícono y texto, sin el fondo de una insignia, para que no
 * se confunda con el estado oficial del registro (ERS 3.1.1); nunca depende solo del color (RNF-008).
 */
export function IndicadorElegibilidad({ estado }: Props) {
  const { texto, variante, icono: Icono } = elegibilidadDeRegistro(estado)

  return (
    <span className={`inline-flex items-center gap-1.5 text-label-md ${COLOR[variante]}`}>
      <Icono aria-hidden="true" className="size-4 shrink-0" />
      {texto}
    </span>
  )
}
