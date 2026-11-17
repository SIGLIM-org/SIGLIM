import { CheckCircle2, FileText, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import {
  canchaDe,
  decisionesDeFormato,
  esPendiente,
  etiquetaEstadoTramite,
  etiquetaTipoTramite,
  fechaDeTramite,
  fechaLarga,
  horaCorta,
  siguientePaso,
  solicitanteDe,
  textoDecision,
} from '@/features/canchas/tramites/etiquetas'
import type { DecisionSolicitudCancha, SolicitudCancha } from '@/features/canchas/tramites/tipos'

type Props = {
  solicitud: SolicitudCancha
  dictaminar: boolean
  generarOficio: boolean
  onDecidir: (solicitud: SolicitudCancha, decision: DecisionSolicitudCancha) => void
  onCarta: (solicitud: SolicitudCancha) => void
}

const CLASE_CELDA = 'block py-1.5 lg:table-cell lg:px-3 lg:py-3 lg:align-top'
const CLASE_ETIQUETA = 'block text-label-sm text-text-muted lg:hidden'

/**
 * Fila de la bandeja de trámites de cancha (B-07, UI-028). En móvil se lee como tarjeta. Solo un
 * trámite pendiente ofrece el dictamen; la carta se ofrece con `canchas.oficio:GENERAR`.
 */
export function FilaTramite({ solicitud, dictaminar, generarOficio, onDecidir, onCarta }: Props) {
  const estado = etiquetaEstadoTramite[solicitud.estado]
  const tipo = etiquetaTipoTramite[solicitud.tipo]
  const IconoTipo = tipo.icono
  const [favorable, desfavorable] = decisionesDeFormato(solicitud.formato)
  const resoluble = dictaminar && esPendiente(solicitud.estado)
  const solicitante = solicitanteDe(solicitud)

  return (
    <tr className="block border-b border-border p-3 transition-colors last:border-b-0 hover:bg-background lg:table-row lg:p-0">
      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Oficio</span>
        <span className="block font-mono text-label-md text-text-main tabular-nums">
          {solicitud.numeroOficio}
        </span>
        <span className="mt-0.5 flex items-center gap-1.5 text-body-sm text-text-muted">
          <IconoTipo aria-hidden="true" className="size-3.5 shrink-0 text-primary" />
          {tipo.texto}
        </span>
      </td>

      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>{solicitud.formato === 1 ? 'Club solicitante' : 'Oferente'}</span>
        <span className="block break-words text-body-md text-text-main">{solicitante}</span>
        {!solicitud.club && (
          <span className="block text-body-sm text-text-muted">Oferente externo</span>
        )}
      </td>

      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Cancha</span>
        <span className="block break-words text-body-md text-text-main">{canchaDe(solicitud)}</span>
        {solicitud.prestamo && (
          <span className="block text-body-sm text-text-muted">
            {fechaLarga(solicitud.prestamo.fechaUtilizacion)} · {horaCorta(solicitud.prestamo.horaInicio)} a{' '}
            {horaCorta(solicitud.prestamo.horaTermino)}
          </span>
        )}
        {solicitud.oferta && (
          <span className="block break-words text-body-sm text-text-muted">
            {solicitud.oferta.disponibilidad}
          </span>
        )}
      </td>

      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Captura</span>
        <span className="block text-body-md text-text-main">{fechaDeTramite(solicitud.capturadaEn)}</span>
        <span className="block break-words text-body-sm text-text-muted">
          {solicitud.capturadaPor.nombreMostrado ?? 'Perfil sin nombre visible'}
        </span>
      </td>

      <td className={CLASE_CELDA}>
        <span className={CLASE_ETIQUETA}>Estado</span>
        <Insignia variante={estado.variante} icono={estado.icono}>
          {estado.texto}
        </Insignia>
        <span className="mt-1 block max-w-prose break-words text-body-sm text-text-muted">
          {siguientePaso(solicitud)}
        </span>
        {solicitud.resueltaEn && (
          <span className="mt-0.5 block text-body-sm text-text-muted">
            Dictaminado el {fechaDeTramite(solicitud.resueltaEn)}
          </span>
        )}
      </td>

      {(dictaminar || generarOficio) && (
        <td className={`${CLASE_CELDA} pt-3 lg:text-right`}>
          <span className={CLASE_ETIQUETA}>Acciones</span>
          <div className="flex flex-col items-start gap-2 lg:items-end">
            {resoluble && (
              <div className="flex flex-wrap gap-2 lg:justify-end">
                <Button
                  variante="secundario"
                  icono={CheckCircle2}
                  aria-label={`${textoDecision[favorable]} el oficio ${solicitud.numeroOficio} de ${solicitante}`}
                  onClick={() => onDecidir(solicitud, favorable)}
                >
                  {textoDecision[favorable]}
                </Button>
                <Button
                  variante="secundario"
                  icono={XCircle}
                  aria-label={`${textoDecision[desfavorable]} el oficio ${solicitud.numeroOficio} de ${solicitante}`}
                  onClick={() => onDecidir(solicitud, desfavorable)}
                >
                  {textoDecision[desfavorable]}
                </Button>
              </div>
            )}
            {generarOficio && (
              <Button
                variante="secundario"
                icono={FileText}
                aria-label={`Generar la carta del oficio ${solicitud.numeroOficio}`}
                onClick={() => onCarta(solicitud)}
              >
                Generar carta
              </Button>
            )}
            {!resoluble && !generarOficio && (
              <span className="text-body-sm text-text-muted">Sin acciones</span>
            )}
          </div>
        </td>
      )}
    </tr>
  )
}
