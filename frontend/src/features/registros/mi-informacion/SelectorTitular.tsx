import { useId } from 'react'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import type { Titular } from '@/features/registros/mi-informacion/tipos'

type Props = {
  titulares: readonly Titular[]
  seleccionado: string
  onCambiar: (personaId: string) => void
}

function textoOpcion({ nombre, esPropia, esMenor }: Titular): string {
  if (esPropia) {
    return `${nombre} (tú)`
  }
  return esMenor ? `${nombre} (menor)` : nombre
}

/**
 * Lista nativa para elegir de quién ver la información cuando la cuenta tiene dos o más
 * titulares; con uno solo no se muestra (DEC-148).
 *
 * Refs: RF-144, DEC-148
 */
export function SelectorTitular({ titulares, seleccionado, onCambiar }: Props) {
  const id = useId()

  return (
    <CampoSeleccion
      id={id}
      etiqueta="Ver la información de"
      value={seleccionado}
      onChange={(evento) => onCambiar(evento.target.value)}
    >
      {titulares.map((titular) => (
        <option key={titular.personaId} value={titular.personaId}>
          {textoOpcion(titular)}
        </option>
      ))}
    </CampoSeleccion>
  )
}
