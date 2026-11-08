import { useId, useMemo, useState } from 'react'
import { CircleCheck, Info, Plus, Users } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { TAMANO_EQUIPOS, useEquipos } from '@/features/organizacion/api'
import { filtrarPorTemporada, temporadaPorOmision, temporadasDeEquipos } from '@/features/organizacion/equipos'
import { ModalBajaEquipo } from '@/features/organizacion/ModalBajaEquipo'
import { ModalEditarEquipo } from '@/features/organizacion/ModalEditarEquipo'
import { ModalRegistrarEquipo } from '@/features/organizacion/ModalRegistrarEquipo'
import { usePermisosClubes } from '@/features/organizacion/permisosClubes'
import { TablaEquipos } from '@/features/organizacion/TablaEquipos'
import type { Club, Equipo, EstadoEquipo } from '@/features/organizacion/tipos'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'

type Props = {
  club: Club
}

type Modal = { tipo: 'registrar' } | { tipo: 'editar' | 'baja'; equipo: Equipo } | null

/**
 * Equipos del club por temporada (F-15, RF-009, RN-005). Las temporadas del filtro salen de los
 * propios equipos (DEC-54); por omisión, la activa. Se muestran en el orden del servidor
 * (categoría por su orden configurado → grupo → nombre, DEC-16). Registrar un equipo pide elegir su grupo, así
 * que solo se ofrece con `temporadas.temporada:CONSULTAR` y con el club activo (RN-041). Un club
 * cerrado es de solo lectura (RN-041): tampoco se editan ni se dan de baja sus equipos. Es la
 * guarda del cliente, aunque DEC-61 ya impide cerrar un club con equipos activos en temporadas abiertas.
 */
export function PestanaEquipos({ club }: Props) {
  const id = useId()
  const permisos = usePermisosClubes()
  const [estado, setEstado] = useState<EstadoEquipo | undefined>('ACTIVO')
  /** `undefined` sigue la temporada por omisión; `''` son todas. */
  const [temporadaElegida, setTemporadaElegida] = useState<string | undefined>(undefined)
  const [modal, setModal] = useState<Modal>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const consulta = useEquipos({ clubId: club.id, estado })
  const contenido = consulta.data?.contenido
  const temporadas = useMemo(() => temporadasDeEquipos(contenido ?? []), [contenido])
  const temporadaId =
    temporadaElegida === '' || temporadas.some(({ id: actual }) => actual === temporadaElegida)
      ? (temporadaElegida as string)
      : temporadaPorOmision(temporadas)
  const equipos = useMemo(() => filtrarPorTemporada(contenido ?? [], temporadaId), [contenido, temporadaId])
  const puedeRegistrar = permisos.elegirGrupo && club.estado === 'ACTIVO'
  const abrir = (siguiente: Modal) => {
    setAviso(null)
    setModal(siguiente)
  }
  const abrirRegistro = () => abrir({ tipo: 'registrar' })
  const terminar = (mensaje: string) => {
    setModal(null)
    setAviso(mensaje)
  }

  const botonRegistrar = puedeRegistrar && (
    <Button icono={Plus} onClick={abrirRegistro}>
      Registrar equipo
    </Button>
  )

  return (
    <section aria-label="Equipos del club" className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between">
        <div className="grid grid-cols-2 gap-3 sm:w-auto sm:min-w-96">
          <CampoSeleccion
            id={`${id}-estado`}
            etiqueta="Estado"
            value={estado ?? ''}
            onChange={(evento) => {
              setAviso(null)
              setEstado((evento.target.value || undefined) as EstadoEquipo | undefined)
            }}
          >
            <option value="ACTIVO">Activos</option>
            <option value="">Todos</option>
          </CampoSeleccion>
          <CampoSeleccion
            id={`${id}-temporada`}
            etiqueta="Temporada"
            disabled={temporadas.length === 0}
            value={temporadaId}
            onChange={(evento) => setTemporadaElegida(evento.target.value)}
          >
            <option value="">Todas</option>
            {temporadas.map((temporada) => (
              <option key={temporada.id} value={temporada.id}>
                {temporada.nombre} · {etiquetaEstadoTemporada[temporada.estado].texto}
              </option>
            ))}
          </CampoSeleccion>
        </div>
        {botonRegistrar && <div className="flex shrink-0 [&>button]:w-full sm:[&>button]:w-auto">{botonRegistrar}</div>}
      </div>

      {aviso && (
        <p role="status" className="flex items-center gap-2 text-label-md text-success-strong">
          <CircleCheck aria-hidden="true" className="size-4 shrink-0" />
          {aviso}
        </p>
      )}

      {consulta.isPending && <EstadoCarga etiqueta="Cargando equipos…" filas={4} columnas={4} />}
      {consulta.isError && (
        <ErrorConsulta error={consulta.error} onReintentar={() => void consulta.refetch()} />
      )}
      {consulta.isSuccess && consulta.data.contenido.length === 0 && (
        <EstadoVacio
          icono={Users}
          titulo={estado === 'ACTIVO' ? 'Sin equipos activos' : 'Este club aún no tiene equipos'}
          descripcion={
            estado === 'ACTIVO' ? 'Los equipos dados de baja aparecen al elegir «Todos» en Estado.' : undefined
          }
          accion={botonRegistrar}
        />
      )}
      {consulta.isSuccess && consulta.data.contenido.length > 0 && (
        <>
          {consulta.data.totalElementos > TAMANO_EQUIPOS && (
            <p className="flex items-start gap-2 rounded-md bg-info/12 p-3 text-body-md text-info-strong">
              <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              Se muestran los primeros {TAMANO_EQUIPOS} de {consulta.data.totalElementos} equipos.
            </p>
          )}
          <TablaEquipos
            equipos={equipos}
            mostrarTemporada={!temporadaId}
            conAcciones={permisos.editarEquipo && club.estado === 'ACTIVO'}
            actualizando={consulta.isFetching}
            onEditar={(equipo) => abrir({ tipo: 'editar', equipo })}
            onDarDeBaja={(equipo) => abrir({ tipo: 'baja', equipo })}
          />
        </>
      )}

      {modal?.tipo === 'registrar' && (
        <ModalRegistrarEquipo
          club={club}
          onCerrar={() => setModal(null)}
          onRegistrado={(equipo) => {
            setTemporadaElegida(equipo.temporada.id)
            terminar(`Equipo ${equipo.nombre} registrado.`)
          }}
        />
      )}
      {modal?.tipo === 'editar' && (
        <ModalEditarEquipo
          equipo={modal.equipo}
          puedeCambiarGrupo={permisos.elegirGrupo}
          onCerrar={() => setModal(null)}
          onGuardado={(equipo) => terminar(`Equipo ${equipo.nombre} actualizado.`)}
        />
      )}
      {modal?.tipo === 'baja' && (
        <ModalBajaEquipo
          equipo={modal.equipo}
          onCerrar={() => setModal(null)}
          onDadoDeBaja={(equipo) => terminar(`Equipo ${equipo.nombre} dado de baja. Su historial se conserva.`)}
        />
      )}
    </section>
  )
}
