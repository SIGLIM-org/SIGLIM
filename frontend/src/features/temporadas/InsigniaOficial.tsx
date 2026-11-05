import { BadgeCheck } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'

type Props = {
  esDemo: boolean
}

/** «Oficial» si la Liga confirmó la versión de parámetros, «No oficial» si es demo (PEN-021). */
export function InsigniaOficial({ esDemo }: Props) {
  return esDemo ? (
    <InsigniaNoOficial />
  ) : (
    <Insignia variante="exito" icono={BadgeCheck}>
      Oficial
    </Insignia>
  )
}
