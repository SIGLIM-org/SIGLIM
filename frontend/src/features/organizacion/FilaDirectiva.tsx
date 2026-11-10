import { CircleDot, Pencil, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import type { FilaDirectiva as Fila } from '@/features/organizacion/directiva'
import { diaAnterior } from '@/features/organizacion/directiva'
import { etiquetaCargoDirectiva, estadoVisibleDeCargo } from '@/features/organizacion/etiquetas'
import type { CargoDirectiva, TipoCargoDirectiva } from '@/features/organizacion/tipos'
import { formatearFecha, formatearPeriodo } from '@/features/temporadas/fechas'

type Props = {
  fila: Fila
  /** Hay al menos una acción posible en la tabla; sin ella no se pinta la columna. */
  conAcciones: boolean
  puedeRegistrar: boolean
  puedeAutorizar: boolean
  onRegistrar: (cargo: TipoCargoDirectiva) => void
  onAutorizar: (cargo: CargoDirectiva) => void
}

const CLASE_CELDA = 'block py-1 lg:table-cell lg:px-4 lg:py-3 lg:align-top'
const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

/**
 * Fila de la directiva (F-15, RF-102): el cargo, su titular y la vigencia. Un cambio pendiente
 * conserva a la vista al titular vigente y explica a quién termina la autorización. Bajo `lg` la
 * fila se apila como tarjeta y conserva los roles de tabla (RNF-007, RNF-008).
 */
export function FilaDirectiva({
  fila,
  conAcciones,
  puedeRegistrar,
  puedeAutorizar,
  onRegistrar,
  onAutorizar,
}: Props) {
  const tipo = fila.tipo === 'vacia' ? fila.cargo : fila.cargo.cargo
  const etiquetaCargo = etiquetaCargoDirectiva[tipo]
  const IconoCargo = etiquetaCargo.icono
  const vacia = fila.tipo === 'vacia'
  const cargo = vacia ? undefined : fila.cargo
  const estado = cargo ? estadoVisibleDeCargo(cargo) : { texto: 'Sin registro', variante: 'neutra' as const, icono: CircleDot }
  const titular = cargo?.nombrePersona ?? 'Sin titular'
  const vigencia = vacia
    ? 'Sin vigencia'
    : fila.tipo === 'pendiente'
      ? `Desde ${formatearFecha(fila.cargo.desde)}`
      : formatearPeriodo(fila.cargo.desde, fila.cargo.hasta)
  const notaPendiente =
    fila.tipo === 'pendiente' && fila.vigente
      ? `Al autorizar, ${fila.vigente.nombrePersona} termina el ${formatearFecha(diaAnterior(fila.cargo.desde))}.`
      : null

  return (
    <tr
      role="row"
      className={[
        'block border-b border-border p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:p-0',
        vacia ? 'bg-background/40' : '',
      ].join(' ')}
    >
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Cargo</span>
        <span className="flex items-center gap-2 text-headline-sm text-text-main">
          <IconoCargo aria-hidden="true" className="size-4 shrink-0 text-primary" />
          {etiquetaCargo.texto}
        </span>
      </td>
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Titular</span>
        <span className={['block break-words text-body-md', vacia ? 'text-text-muted' : 'text-text-main'].join(' ')}>
          {titular}
        </span>
        {notaPendiente && (
          <span className="mt-1 block max-w-prose text-body-sm text-text-muted">{notaPendiente}</span>
        )}
      </td>
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Vigencia</span>
        <span className="block text-body-md text-text-main">{vigencia}</span>
      </td>
      <td role="cell" className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Estado</span>
        <Insignia variante={estado.variante} icono={estado.icono}>
          {estado.texto}
        </Insignia>
      </td>
      {conAcciones && (
        <td role="cell" className={`${CLASE_CELDA} pt-3 lg:text-right`}>
          {fila.tipo === 'pendiente' && puedeAutorizar && (
            <Button
              variante="secundario"
              icono={ShieldCheck}
              aria-label={`Autorizar cambio de ${etiquetaCargo.texto}`}
              onClick={() => onAutorizar(fila.cargo)}
            >
              Autorizar
            </Button>
          )}
          {(fila.tipo === 'vigente' || fila.tipo === 'vacia') && puedeRegistrar && (
            <Button
              variante="secundario"
              icono={Pencil}
              aria-label={`Registrar cambio de ${etiquetaCargo.texto}`}
              onClick={() => onRegistrar(tipo)}
            >
              {vacia ? 'Registrar' : 'Cambiar'}
            </Button>
          )}
        </td>
      )}
    </tr>
  )
}
