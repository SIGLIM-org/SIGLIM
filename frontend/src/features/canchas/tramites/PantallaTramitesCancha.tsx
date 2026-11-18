import { useId, useState, type KeyboardEvent } from 'react'
import { FileCheck2, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { BandejaTramites } from '@/features/canchas/tramites/BandejaTramites'
import { FormularioTramite } from '@/features/canchas/tramites/FormularioTramite'
import { ModalCartaOficio } from '@/features/canchas/tramites/ModalCartaOficio'
import { ModalDictamenTramite } from '@/features/canchas/tramites/ModalDictamenTramite'
import { TAMANO_TRAMITES, useSolicitudesCancha } from '@/features/canchas/tramites/api'
import { etiquetaEstadoTramite, formatos } from '@/features/canchas/tramites/etiquetas'
import { PERMISOS_TRAMITES, usePermisosTramites } from '@/features/canchas/tramites/permisos'
import type {
  DecisionSolicitudCancha,
  FiltroEstadoTramite,
  FormatoTramite,
  SolicitudCancha,
} from '@/features/canchas/tramites/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

export const RUTA_TRAMITES_CANCHA = '/app/programacion/tramites-cancha'

/**
 * Página de la ruta de trámites de cancha (B-07, UI-028). `RutaConPermiso` solo mira el módulo
 * M-06; aquí se exige capturar, dictaminar o generar oficios antes de pedir datos.
 */
export function PantallaTramitesCancha() {
  return (
    <ConPermiso permiso={[...PERMISOS_TRAMITES]} alternativa={<PaginaSinPermiso rutaInicio="/app" />}>
      <PaginaTramitesCancha />
    </ConPermiso>
  )
}

type Dictamen = {
  solicitud: SolicitudCancha
  decision: DecisionSolicitudCancha
}

const FORMATOS: readonly FormatoTramite[] = [1, 2]

/**
 * Préstamo y comodato (Formato 1) y ofertas de cancha (Formato 2). La Directiva o el Secretario
 * capturan el trámite con su número de oficio; la Presidencia lo dictamina; quien genera oficios
 * descarga la carta (RF-116, RF-117, RF-118).
 */
function PaginaTramitesCancha() {
  const { capturar, dictaminar, generarOficio } = usePermisosTramites()
  const id = useId()
  const [formato, setFormato] = useState<FormatoTramite>(1)
  const [filtro, setFiltro] = useState<FiltroEstadoTramite>('TODOS')
  const [pagina, setPagina] = useState(0)
  const [creando, setCreando] = useState(false)
  const [dictamen, setDictamen] = useState<Dictamen | null>(null)
  const [carta, setCarta] = useState<SolicitudCancha | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const lista = useSolicitudesCancha({
    formato,
    estado: filtro === 'TODOS' ? undefined : filtro,
    pagina,
    tamano: TAMANO_TRAMITES,
  })

  if (esSinPermiso(lista.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const cambiarFormato = (nuevo: FormatoTramite) => {
    setFormato(nuevo)
    setFiltro('TODOS')
    setPagina(0)
    setMensaje(null)
  }

  const navegarFormatos = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }
    evento.preventDefault()
    const siguiente: FormatoTramite = formato === 1 ? 2 : 1
    cambiarFormato(siguiente)
    document.getElementById(`${id}-formato-${siguiente}`)?.focus()
  }

  const alCrear = (solicitud: SolicitudCancha) => {
    setCreando(false)
    setFiltro('TODOS')
    setPagina(0)
    setMensaje(
      `Se registró el oficio ${solicitud.numeroOficio}. Quedó ${etiquetaEstadoTramite[
        solicitud.estado
      ].texto.toLowerCase()} hasta el dictamen de la Presidencia.`,
    )
  }

  const alResolver = (solicitud: SolicitudCancha) => {
    setDictamen(null)
    setMensaje(
      `El oficio ${solicitud.numeroOficio} quedó ${etiquetaEstadoTramite[solicitud.estado].texto.toLowerCase()}.`,
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-balance text-display-lg-mobile text-text-main sm:text-display-lg">
            Trámites y solicitudes de canchas
          </h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Solicitudes de préstamo o comodato y ofertas de cancha con los formatos de la Liga. Cada
            trámite lleva su número de oficio y la Presidencia lo dictamina.
          </p>
        </div>
        {capturar && !creando && (
          <Button icono={Plus} onClick={() => setCreando(true)} className="shrink-0">
            Nueva solicitud
          </Button>
        )}
      </header>

      <div
        role="tablist"
        aria-label="Formato del trámite"
        onKeyDown={navegarFormatos}
        className="flex flex-col gap-1 border-b border-border sm:flex-row"
      >
        {FORMATOS.map((opcion) => (
          <button
            key={opcion}
            type="button"
            role="tab"
            id={`${id}-formato-${opcion}`}
            aria-selected={formato === opcion}
            aria-controls={`${id}-panel`}
            tabIndex={formato === opcion ? 0 : -1}
            onClick={() => cambiarFormato(opcion)}
            className={[
              '-mb-px min-h-11 border-b-2 px-4 text-left text-label-md transition-colors',
              formato === opcion
                ? 'border-primary text-primary'
                : 'border-transparent text-text-muted hover:text-text-main',
            ].join(' ')}
          >
            {formatos[opcion].titulo}
          </button>
        ))}
      </div>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <FileCheck2 aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      <div role="tabpanel" id={`${id}-panel`} aria-labelledby={`${id}-formato-${formato}`}>
        <BandejaTramites
          formato={formato}
          filtro={filtro}
          pagina={pagina}
          datos={lista.data}
          cargando={lista.isPending}
          error={lista.error}
          dictaminar={dictaminar}
          generarOficio={generarOficio}
          onFiltro={(nuevo) => {
            setFiltro(nuevo)
            setPagina(0)
            setMensaje(null)
          }}
          onPagina={setPagina}
          onDecidir={(solicitud, decision) => setDictamen({ solicitud, decision })}
          onCarta={setCarta}
          onReintentar={() => void lista.refetch()}
          onNueva={capturar ? () => setCreando(true) : undefined}
        />
      </div>

      {creando && <FormularioTramite formato={formato} onCreada={alCrear} onCerrar={() => setCreando(false)} />}

      {dictamen && (
        <ModalDictamenTramite
          solicitud={dictamen.solicitud}
          decision={dictamen.decision}
          onCerrar={() => setDictamen(null)}
          onResuelta={alResolver}
        />
      )}

      {carta && <ModalCartaOficio solicitud={carta} onCerrar={() => setCarta(null)} />}
    </div>
  )
}
