import { useMemo, useState } from 'react'
import { CircleCheck, Clock, EyeOff, History, UserRoundPlus, UsersRound } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useDirectiva } from '@/features/organizacion/api'
import {
  contarHistorial,
  contarPendientes,
  filasDeDirectiva,
} from '@/features/organizacion/directiva'
import { etiquetaCargoDirectiva } from '@/features/organizacion/etiquetas'
import { ModalAutorizarCargoDirectiva } from '@/features/organizacion/ModalAutorizarCargoDirectiva'
import { ModalRegistrarCargoDirectiva } from '@/features/organizacion/ModalRegistrarCargoDirectiva'
import { usePermisosClubes } from '@/features/organizacion/permisosClubes'
import { TablaDirectiva } from '@/features/organizacion/TablaDirectiva'
import type { CargoDirectiva, Club, TipoCargoDirectiva } from '@/features/organizacion/tipos'

type Props = {
  club: Club
}

type Modal =
  | { tipo: 'registrar'; cargo?: TipoCargoDirectiva }
  | { tipo: 'autorizar'; cargo: CargoDirectiva }
  | null

/**
 * Pestaña de directiva del detalle del club (F-15, RF-102): los cuatro cargos con su titular y
 * vigencia, los cambios pendientes y el historial. Registrar un cambio exige
 * `organizacion.directiva:EDITAR`; autorizarlo, `:APROBAR`. La interfaz solo oculta acciones: el
 * backend decide el ámbito y la validez de cada transición (VAL-015, ADR-0007).
 */
export function PestanaDirectiva({ club }: Props) {
  const consulta = useDirectiva(club.id)
  const permisos = usePermisosClubes()
  const [mostrarHistorial, setMostrarHistorial] = useState(false)
  const [modal, setModal] = useState<Modal>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const cargos = useMemo(() => consulta.data ?? [], [consulta.data])
  const filas = useMemo(
    () => filasDeDirectiva(cargos, mostrarHistorial),
    [cargos, mostrarHistorial],
  )
  const historial = contarHistorial(cargos)
  const pendientes = contarPendientes(cargos)

  const abrir = (siguiente: Modal) => {
    setAviso(null)
    setModal(siguiente)
  }

  const terminar = (mensaje: string) => {
    setModal(null)
    setAviso(mensaje)
  }

  const botonRegistrar = permisos.registrarDirectiva ? (
    <Button
      icono={UserRoundPlus}
      onClick={() => abrir({ tipo: 'registrar' })}
      className="w-full sm:w-auto"
    >
      Registrar cambio
    </Button>
  ) : undefined

  return (
    <section aria-label="Directiva del club" className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-start sm:justify-between">
        <div className="flex min-w-0 flex-col gap-1">
          <h3 className="text-headline-sm text-text-main">Mesa directiva</h3>
          <p className="max-w-prose text-body-md text-text-muted">
            Representantes del club ante la Liga. Un cambio queda pendiente hasta que lo autorice
            la Presidencia.
          </p>
        </div>
        {botonRegistrar && cargos.length > 0 && (
          <div className="flex shrink-0">{botonRegistrar}</div>
        )}
      </div>

      {aviso && (
        <p role="status" className="flex items-center gap-2 text-label-md text-success-strong">
          <CircleCheck aria-hidden="true" className="size-4 shrink-0" />
          {aviso}
        </p>
      )}

      {pendientes > 0 && !permisos.autorizarDirectiva && (
        <p className="flex items-start gap-2 rounded-md bg-info/12 p-3 text-body-md text-info-strong">
          <Clock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          {pendientes === 1
            ? 'Hay un cambio de directiva pendiente de autorización por la Presidencia.'
            : `Hay ${pendientes} cambios de directiva pendientes de autorización por la Presidencia.`}
        </p>
      )}

      {consulta.isPending && <EstadoCarga etiqueta="Cargando la directiva…" filas={4} columnas={5} />}
      {consulta.isError && (
        <ErrorConsulta error={consulta.error} onReintentar={() => void consulta.refetch()} />
      )}

      {consulta.isSuccess && cargos.length === 0 && (
        <EstadoVacio
          icono={UsersRound}
          titulo="Sin directiva registrada"
          descripcion={
            permisos.registrarDirectiva
              ? 'Registra el primer cargo para que la Liga sepa quién representa al club.'
              : 'Cuando la Liga registre los cargos, aparecerán aquí.'
          }
          accion={botonRegistrar}
        />
      )}

      {consulta.isSuccess && cargos.length > 0 && (
        <>
          <TablaDirectiva
            filas={filas}
            puedeRegistrar={permisos.registrarDirectiva}
            puedeAutorizar={permisos.autorizarDirectiva}
            actualizando={consulta.isFetching}
            onRegistrar={(cargo) => abrir({ tipo: 'registrar', cargo })}
            onAutorizar={(cargo) => abrir({ tipo: 'autorizar', cargo })}
          />

          {historial > 0 && (
            <div>
              <Button
                variante="secundario"
                icono={mostrarHistorial ? EyeOff : History}
                aria-expanded={mostrarHistorial}
                onClick={() => setMostrarHistorial((actual) => !actual)}
              >
                {mostrarHistorial ? 'Ocultar historial' : `Ver historial (${historial})`}
              </Button>
            </div>
          )}
        </>
      )}

      {modal?.tipo === 'registrar' && (
        <ModalRegistrarCargoDirectiva
          club={club}
          cargos={cargos}
          cargoInicial={modal.cargo}
          onCerrar={() => setModal(null)}
          onRegistrado={(cargo) =>
            terminar(
              `Cambio de ${etiquetaCargoDirectiva[cargo.cargo].texto} registrado. Queda pendiente de autorización.`,
            )
          }
        />
      )}

      {modal?.tipo === 'autorizar' && (
        <ModalAutorizarCargoDirectiva
          cargo={modal.cargo}
          vigente={cargos.find(
            (cargo) =>
              cargo.cargo === modal.cargo.cargo &&
              cargo.estado === 'AUTORIZADO' &&
              !cargo.hasta,
          )}
          onCerrar={() => setModal(null)}
          onAutorizado={(cargo) =>
            terminar(`Cambio de ${etiquetaCargoDirectiva[cargo.cargo].texto} autorizado.`)
          }
        />
      )}
    </section>
  )
}
