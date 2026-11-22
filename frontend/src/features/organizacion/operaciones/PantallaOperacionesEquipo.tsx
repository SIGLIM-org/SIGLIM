import { useState } from 'react'
import { FileCheck2, Info, Plus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { BandejaOperaciones } from '@/features/organizacion/operaciones/BandejaOperaciones'
import { FormularioOperacion } from '@/features/organizacion/operaciones/FormularioOperacion'
import { ModalDictamenOperacion } from '@/features/organizacion/operaciones/ModalDictamenOperacion'
import {
  TAMANO_OPERACIONES,
  useOperacionesEquipo,
} from '@/features/organizacion/operaciones/api'
import {
  APROBAR_OPERACION,
  SOLICITAR_OPERACION,
  usePermisosOperaciones,
} from '@/features/organizacion/operaciones/permisos'
import type {
  DecisionOperacionEquipo,
  FiltroEstadoOperacionEquipo,
  OperacionEquipo,
} from '@/features/organizacion/operaciones/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

export const RUTA_OPERACIONES_EQUIPO = '/app/organizacion/operaciones-equipo'

/**
 * Página de la ruta de operaciones de equipo (B-09, UI-034). `RutaConPermiso` solo mira el módulo
 * M-03; aquí se exige solicitar o aprobar antes de montar la bandeja y pedir sus datos.
 */
export function PantallaOperacionesEquipo() {
  return (
    <ConPermiso
      permiso={[SOLICITAR_OPERACION, APROBAR_OPERACION]}
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <PaginaOperacionesEquipo />
    </ConPermiso>
  )
}

type Dictamen = {
  operacion: OperacionEquipo
  decision: DecisionOperacionEquipo
}

/**
 * Venta de equipo y conservación de plaza. La Directiva registra la operación y la deja SOLICITADA;
 * la Presidencia la autoriza o la rechaza con observaciones. El cambio de club se aplica al cerrar
 * la temporada y el historial de propietarios se conserva (RN-040).
 */
function PaginaOperacionesEquipo() {
  const { solicitar, aprobar } = usePermisosOperaciones()
  const [filtro, setFiltro] = useState<FiltroEstadoOperacionEquipo>(() =>
    aprobar && !solicitar ? 'SOLICITADA' : 'TODOS',
  )
  const [pagina, setPagina] = useState(0)
  const [creando, setCreando] = useState(false)
  const [dictamen, setDictamen] = useState<Dictamen | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const lista = useOperacionesEquipo({
    estado: filtro === 'TODOS' ? undefined : filtro,
    pagina,
    tamano: TAMANO_OPERACIONES,
  })
  const vista = solicitar && aprobar ? 'Club y Presidencia' : solicitar ? 'Club' : 'Presidencia'

  if (esSinPermiso(lista.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const cambiarFiltro = (nuevo: FiltroEstadoOperacionEquipo) => {
    setFiltro(nuevo)
    setPagina(0)
    setMensaje(null)
  }

  const nueva = () => {
    setMensaje(null)
    setCreando(true)
    setDictamen(null)
  }

  const alCrear = (operacion: OperacionEquipo) => {
    setCreando(false)
    setFiltro('TODOS')
    setPagina(0)
    setMensaje(
      `La operación de «${operacion.equipo.nombre}» quedó SOLICITADA. La Presidencia ya puede revisarla.`,
    )
  }

  const alResolver = (operacion: OperacionEquipo) => {
    const rechazada = operacion.estado === 'RECHAZADA'

    setDictamen(null)
    setMensaje(
      rechazada
        ? `La operación de «${operacion.equipo.nombre}» se rechazó con observaciones.`
        : `La operación de «${operacion.equipo.nombre}» quedó AUTORIZADA. Se aplicará al cerrar la temporada.`,
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-balance text-display-lg-mobile text-text-main sm:text-display-lg">
            Venta de equipo y conservación de plaza
          </h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            {vista === 'Presidencia'
              ? 'Revisa las solicitudes de los clubes, autorízalas o recházalas con observaciones.'
              : vista === 'Club y Presidencia'
                ? 'Registra operaciones de tu club y resuelve las solicitudes de los demás clubes.'
                : 'Registra la venta de un equipo o la conservación de su plaza; la Presidencia la autoriza.'}
          </p>
        </div>
        {solicitar && !creando && (
          <Button icono={Plus} onClick={nueva} className="shrink-0">
            Registrar operación
          </Button>
        )}
      </header>

      <section
        aria-label="Cómo funciona una operación de equipo"
        className="flex items-start gap-3 rounded-lg border border-border bg-surface p-4"
      >
        <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-info/12 text-info-strong">
          <Info aria-hidden="true" className="size-5" />
        </span>
        <div>
          <h2 className="text-headline-sm text-text-main">Solicitada → Autorizada → Aplicada</h2>
          <p className="mt-0.5 max-w-3xl text-body-md text-text-muted">
            La Directiva registra la venta o la conservación de plaza. La Presidencia la autoriza o
            la rechaza con observaciones. La venta cambia el club del equipo al cerrar la temporada;
            la conservación mantiene la plaza en su club. El historial de propietarios se conserva.
          </p>
        </div>
      </section>

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <FileCheck2 aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      <BandejaOperaciones
        filtro={filtro}
        pagina={pagina}
        datos={lista.data}
        cargando={lista.isPending}
        error={lista.error}
        conAcciones={aprobar}
        onFiltro={cambiarFiltro}
        onPagina={(nuevaPagina) => {
          setPagina(nuevaPagina)
          setMensaje(null)
        }}
        onAutorizar={(operacion) => setDictamen({ operacion, decision: 'AUTORIZADA' })}
        onRechazar={(operacion) => setDictamen({ operacion, decision: 'RECHAZADA' })}
        onReintentar={() => void lista.refetch()}
        onNueva={solicitar ? nueva : undefined}
      />

      {creando && <FormularioOperacion onCreada={alCrear} onCerrar={() => setCreando(false)} />}

      {dictamen && (
        <ModalDictamenOperacion
          operacion={dictamen.operacion}
          decision={dictamen.decision}
          onCerrar={() => setDictamen(null)}
          onResuelta={alResolver}
        />
      )}
    </div>
  )
}
