import { useEffect, useMemo, useState, type KeyboardEvent } from 'react'
import {
  CircleCheck,
  Inbox,
  LockKeyhole,
  Save,
  Send,
  ShieldCheck,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tieneAlgunPermiso } from '@/features/seguridad/permisos'
import {
  useCatalogoPermisos,
  useRol,
  useRoles,
  useSolicitudes,
} from '@/features/roles/api'
import { ListaRoles } from '@/features/roles/ListaRoles'
import { MatrizPermisos } from '@/features/roles/MatrizPermisos'
import { ModalDictamenPermisos } from '@/features/roles/ModalDictamenPermisos'
import { ModalGuardarPermisos } from '@/features/roles/ModalGuardarPermisos'
import {
  agruparPorModulo,
  codigosDeRol,
  hayCambios,
  paresIncompatiblesNuevos,
} from '@/features/roles/reglas'
import { TarjetaSolicitud } from '@/features/roles/TarjetaSolicitud'
import type {
  DecisionSolicitud,
  ResultadoGuardado,
  SolicitudCambioPermisos,
} from '@/features/roles/tipos'
import { esSinPermiso, reintentarConsulta } from '@/lib/api/errores'

type Pestana = 'matriz' | 'solicitudes'

type Mensaje = {
  tono: 'exito' | 'aviso'
  texto: string
}

type Dictamen = {
  solicitud: SolicitudCambioPermisos
  decision: DecisionSolicitud
}

const APROBAR_INCOMPATIBLE = 'seguridad.permisos:APROBAR_INCOMPATIBLE'
const EDITAR_ROL = 'seguridad.rol:EDITAR'
const formatoEntero = new Intl.NumberFormat('es-MX')

function Encabezado() {
  return (
    <header>
      <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">
        Roles y permisos
      </h1>
      <p className="mt-1 max-w-3xl text-body-md text-text-muted">
        Ajusta la matriz de cada rol. Un cambio que combine permisos incompatibles queda pendiente
        de la aprobación del Presidente del Comité Ejecutivo.
      </p>
    </header>
  )
}

function Aviso({ mensaje }: { mensaje: Mensaje }) {
  const Icono = mensaje.tono === 'exito' ? CircleCheck : TriangleAlert

  return (
    <p
      role="status"
      className={[
        'flex items-start gap-2 rounded-lg border p-3 text-body-md',
        mensaje.tono === 'exito'
          ? 'border-success/30 bg-success/8 text-success-strong'
          : 'border-warning/40 bg-warning/10 text-warning-strong',
      ].join(' ')}
    >
      <Icono aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
      {mensaje.texto}
    </p>
  )
}

/**
 * Pantalla F-13 (H-20.2, RF-095, RF-096): pestaña de matriz con los permisos del rol por módulo y
 * pestaña de solicitudes pendientes para el dictamen del Presidente. El backend decide los
 * permisos (VAL-015), los pares incompatibles (VAL-023) y el límite del propio rol (VAL-022); la
 * interfaz los anticipa y explica el resultado.
 */
export function GestionRolesPermisos() {
  const { yo } = useSesionActual()
  const roles = useRoles()
  const catalogo = useCatalogoPermisos()
  const [pestana, setPestana] = useState<Pestana>('matriz')
  const [rolSeleccionado, setRolSeleccionado] = useState<string | null>(null)
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set())
  const [mensaje, setMensaje] = useState<Mensaje | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [dictamen, setDictamen] = useState<Dictamen | null>(null)
  const [paginaSolicitudes, setPaginaSolicitudes] = useState(0)
  const rolId = rolSeleccionado ?? roles.data?.[0]?.id ?? null
  const detalle = useRol(rolId)
  const puedeAprobar = tieneAlgunPermiso(yo.permisos, [APROBAR_INCOMPATIBLE])
  const puedeEditar = tieneAlgunPermiso(yo.permisos, [EDITAR_ROL])
  const solicitudes = useSolicitudes(paginaSolicitudes, puedeAprobar)

  const permisosVigentes = useMemo(
    () => (detalle.data ? codigosDeRol(detalle.data.permisos) : new Set<string>()),
    [detalle.data],
  )
  const grupos = useMemo(
    () => agruparPorModulo(catalogo.data?.permisos ?? []),
    [catalogo.data],
  )
  const descripciones = useMemo(
    () =>
      new Map(
        (catalogo.data?.permisos ?? []).map(({ codigo, descripcion }) => [codigo, descripcion]),
      ),
    [catalogo.data],
  )
  const paresNuevos = useMemo(
    () =>
      paresIncompatiblesNuevos(
        seleccion,
        permisosVigentes,
        catalogo.data?.paresIncompatibles ?? [],
      ),
    [catalogo.data, permisosVigentes, seleccion],
  )

  useEffect(() => {
    setSeleccion(new Set(permisosVigentes))
  }, [permisosVigentes])

  const esRolPropio =
    detalle.data !== undefined &&
    yo.asignaciones.some(({ rol }) => rol.codigo === detalle.data?.codigo)
  const cambios = hayCambios(permisosVigentes, seleccion)
  const matrizBloqueada = !puedeEditar || esRolPropio
  const totalSeleccionados = seleccion.size
  const totalPermisos = catalogo.data?.permisos.length ?? 0

  const pestanas: readonly {
    valor: Pestana
    texto: string
    icono: LucideIcon
    cantidad?: number
  }[] = puedeAprobar
    ? [
        { valor: 'matriz', texto: 'Matriz de permisos', icono: ShieldCheck },
        {
          valor: 'solicitudes',
          texto: 'Solicitudes pendientes',
          icono: Inbox,
          cantidad: solicitudes.data?.totalElementos,
        },
      ]
    : [{ valor: 'matriz', texto: 'Matriz de permisos', icono: ShieldCheck }]

  const cambiarPestana = (nueva: Pestana) => {
    setPestana(nueva)
    setMensaje(null)
  }

  const navegarPestanas = (evento: KeyboardEvent<HTMLDivElement>) => {
    const actual = pestanas.findIndex(({ valor }) => valor === pestana)
    let siguiente: number | null = null

    if (evento.key === 'ArrowRight') {
      siguiente = (actual + 1) % pestanas.length
    } else if (evento.key === 'ArrowLeft') {
      siguiente = (actual - 1 + pestanas.length) % pestanas.length
    } else if (evento.key === 'Home') {
      siguiente = 0
    } else if (evento.key === 'End') {
      siguiente = pestanas.length - 1
    }

    if (siguiente === null) {
      return
    }

    evento.preventDefault()
    const valor = pestanas[siguiente].valor
    cambiarPestana(valor)
    document.getElementById(`pestana-roles-${valor}`)?.focus()
  }

  const elegirRol = (id: string) => {
    setRolSeleccionado(id)
    setMensaje(null)
  }

  const alternarPermiso = (codigo: string, marcado: boolean) => {
    setSeleccion((actual) => {
      const siguiente = new Set(actual)

      if (marcado) {
        siguiente.add(codigo)
      } else {
        siguiente.delete(codigo)
      }

      return siguiente
    })
    setMensaje(null)
  }

  const abrirGuardado = () => {
    setMensaje(null)
    setGuardando(true)
  }

  const alGuardar = (resultado: ResultadoGuardado) => {
    setGuardando(false)

    if (resultado.tipo === 'aplicado') {
      setSeleccion(codigosDeRol(resultado.rol.permisos))
      setMensaje({
        tono: 'exito',
        texto: 'Cambios aplicados a la matriz del rol y registrados en la auditoría.',
      })
      return
    }

    setSeleccion(new Set(permisosVigentes))
    setMensaje({
      tono: 'aviso',
      texto:
        'El cambio quedó pendiente: la matriz del rol no se modificó y el Presidente debe aprobarlo o rechazarlo.',
    })
    void detalle.refetch()
  }

  const alDictaminar = (solicitud: SolicitudCambioPermisos, decision: DecisionSolicitud) => {
    setMensaje(null)
    setDictamen({ solicitud, decision })
  }

  const alResolver = (solicitud: SolicitudCambioPermisos) => {
    const decision = dictamen?.decision
    setDictamen(null)

    if (solicitudes.data?.contenido.length === 1 && paginaSolicitudes > 0) {
      setPaginaSolicitudes((pagina) => Math.max(0, pagina - 1))
    }

    setMensaje({
      tono: 'exito',
      texto:
        decision === 'APROBADO'
          ? `Solicitud aprobada: la matriz de «${solicitud.rol.nombre}» se actualizó y el dictamen quedó en la auditoría.`
          : `Solicitud rechazada: la matriz de «${solicitud.rol.nombre}» no cambió y el dictamen quedó en la auditoría.`,
    })
    void detalle.refetch()
  }

  const consultaFallida = roles.error ?? catalogo.error

  if (esSinPermiso(consultaFallida)) {
    return (
      <div className="flex flex-col gap-6">
        <Encabezado />
        <ErrorConsulta error={consultaFallida} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <Encabezado />

      <div
        role="tablist"
        aria-label="Roles y permisos"
        onKeyDown={navegarPestanas}
        className="flex min-w-0 overflow-x-auto border-b-2 border-border"
      >
        {pestanas.map(({ valor, texto, icono: Icono, cantidad }) => {
          const activa = pestana === valor

          return (
            <button
              key={valor}
              type="button"
              role="tab"
              id={`pestana-roles-${valor}`}
              aria-selected={activa}
              aria-controls={`panel-roles-${valor}`}
              tabIndex={activa ? 0 : -1}
              onClick={() => cambiarPestana(valor)}
              className={[
                '-mb-0.5 flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-3 text-label-md transition-colors sm:px-4',
                activa
                  ? 'border-primary text-primary'
                  : 'border-transparent text-text-muted hover:text-text-main',
              ].join(' ')}
            >
              <Icono aria-hidden="true" className="size-4 shrink-0" />
              {texto}
              {cantidad !== undefined && cantidad > 0 && (
                <span
                  className={[
                    'rounded-full px-2 py-0.5 font-mono text-mono-data',
                    activa ? 'bg-primary/10 text-primary' : 'bg-border/40 text-text-muted',
                  ].join(' ')}
                >
                  {formatoEntero.format(cantidad)}
                </span>
              )}
            </button>
          )
        })}
      </div>

      {mensaje && <Aviso mensaje={mensaje} />}

      {pestana === 'matriz' && (
        <div
          role="tabpanel"
          id="panel-roles-matriz"
          aria-labelledby="pestana-roles-matriz"
          className="flex flex-col gap-6"
        >
          {(roles.isPending || catalogo.isPending) && (
            <EstadoCarga etiqueta="Cargando roles y permisos…" filas={6} columnas={4} />
          )}

          {consultaFallida && (
            <ErrorConsulta
              error={consultaFallida}
              onReintentar={
                reintentarConsulta(0, consultaFallida)
                  ? () => {
                      if (roles.error) {
                        void roles.refetch()
                      }
                      if (catalogo.error) {
                        void catalogo.refetch()
                      }
                    }
                  : undefined
              }
            />
          )}

          {!roles.isPending && !catalogo.isPending && !consultaFallida && roles.data?.length === 0 && (
            <EstadoVacio
              titulo="No hay roles configurados"
              descripcion="La liga debe tener al menos los roles de la matriz inicial."
            />
          )}

          {!consultaFallida && roles.data && roles.data.length > 0 && (
            <div className="grid min-w-0 gap-6 lg:grid-cols-[minmax(14rem,18rem)_minmax(0,1fr)]">
              <ListaRoles
                roles={roles.data}
                seleccionado={rolId}
                onSeleccionar={elegirRol}
              />

              <section aria-label="Matriz de permisos del rol" className="flex min-w-0 flex-col gap-4">
                {detalle.isPending && (
                  <EstadoCarga etiqueta="Cargando la matriz del rol…" filas={8} columnas={3} />
                )}

                {detalle.isError && (
                  <ErrorConsulta
                    error={detalle.error}
                    onReintentar={
                      reintentarConsulta(0, detalle.error)
                        ? () => void detalle.refetch()
                        : undefined
                    }
                  />
                )}

                {detalle.data && (
                  <>
                    <div className="flex flex-col gap-3 border-b border-border pb-4 sm:flex-row sm:items-start sm:justify-between">
                      <div className="min-w-0">
                        <h2 className="text-headline-lg text-text-main">{detalle.data.nombre}</h2>
                        <p className="break-all font-mono text-body-sm text-text-muted">
                          {detalle.data.codigo}
                        </p>
                        {detalle.data.descripcion && (
                          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
                            {detalle.data.descripcion}
                          </p>
                        )}
                        <p className="mt-1 text-body-sm text-text-muted">
                          {formatoEntero.format(totalSeleccionados)} de{' '}
                          {formatoEntero.format(totalPermisos)} permisos seleccionados
                        </p>
                      </div>

                      {puedeEditar && !esRolPropio && (
                        <Button
                          icono={paresNuevos.length > 0 ? Send : Save}
                          onClick={abrirGuardado}
                          disabled={!cambios}
                          className="shrink-0"
                        >
                          {paresNuevos.length > 0 ? 'Solicitar aprobación' : 'Guardar cambios'}
                        </Button>
                      )}
                    </div>

                    {esRolPropio && (
                      <div className="flex gap-3 rounded-lg border border-warning/40 bg-warning/10 p-3 text-text-main">
                        <LockKeyhole
                          aria-hidden="true"
                          className="mt-0.5 size-5 shrink-0 text-warning-strong"
                        />
                        <p className="text-body-md">
                          <strong className="text-warning-strong">
                            No puedes cambiar los permisos de tu propio rol.
                          </strong>{' '}
                          La edición se rechaza con el código VAL-022; puedes consultar la matriz y
                          pedir a otra cuenta autorizada que la modifique.
                        </p>
                      </div>
                    )}

                    {puedeEditar && !esRolPropio && paresNuevos.length > 0 && (
                      <div className="flex gap-3 rounded-lg border border-warning/40 bg-warning/10 p-3 text-text-main">
                        <TriangleAlert
                          aria-hidden="true"
                          className="mt-0.5 size-5 shrink-0 text-warning-strong"
                        />
                        <div className="flex min-w-0 flex-col gap-1">
                          <p className="text-label-md text-warning-strong">
                            Este cambio combina permisos incompatibles
                          </p>
                          <p className="text-body-md">
                            Quedará pendiente hasta que lo apruebe el Presidente. La matriz actual
                            no se modifica.
                          </p>
                          <ul className="flex list-disc flex-col gap-1 pl-4 text-body-md">
                            {paresNuevos.map(({ permisoA, permisoB, motivo }) => (
                              <li key={`${permisoA}-${permisoB}`}>
                                {descripciones.get(permisoA) ?? permisoA} con{' '}
                                {descripciones.get(permisoB) ?? permisoB}: {motivo}.
                              </li>
                            ))}
                          </ul>
                        </div>
                      </div>
                    )}

                    {!puedeEditar && (
                      <p className="text-body-sm text-text-muted">
                        Tienes acceso de solo lectura a esta matriz.
                      </p>
                    )}

                    <MatrizPermisos
                      grupos={grupos}
                      seleccion={seleccion}
                      deshabilitada={matrizBloqueada}
                      onAlternar={alternarPermiso}
                    />
                  </>
                )}
              </section>
            </div>
          )}
        </div>
      )}

      {pestana === 'solicitudes' && puedeAprobar && (
        <div
          role="tabpanel"
          id="panel-roles-solicitudes"
          aria-labelledby="pestana-roles-solicitudes"
          className="flex flex-col gap-4"
        >
          {solicitudes.isPending && (
            <EstadoCarga etiqueta="Cargando solicitudes pendientes…" filas={4} columnas={3} />
          )}

          {solicitudes.isError && (
            <ErrorConsulta
              error={solicitudes.error}
              onReintentar={
                reintentarConsulta(0, solicitudes.error)
                  ? () => void solicitudes.refetch()
                  : undefined
              }
            />
          )}

          {solicitudes.data?.totalElementos === 0 && (
            <EstadoVacio
              icono={Inbox}
              titulo="No hay solicitudes pendientes"
              descripcion="Los cambios que combinen permisos incompatibles aparecerán aquí para su dictamen."
            />
          )}

          {solicitudes.data && solicitudes.data.contenido.length > 0 && (
            <section aria-label="Solicitudes pendientes" className="flex flex-col gap-3">
              <p className="text-label-md text-text-main">
                {solicitudes.data.totalElementos === 1
                  ? '1 solicitud pendiente'
                  : `${formatoEntero.format(solicitudes.data.totalElementos)} solicitudes pendientes`}
              </p>

              {solicitudes.data.contenido.map((solicitud) => (
                <TarjetaSolicitud
                  key={solicitud.id}
                  solicitud={solicitud}
                  descripciones={descripciones}
                  esSolicitante={solicitud.solicitadoPor.id === yo.perfil.id}
                  esRolPropio={yo.asignaciones.some(
                    ({ rol }) => rol.codigo === solicitud.rol.codigo,
                  )}
                  onDictaminar={(decision) => alDictaminar(solicitud, decision)}
                />
              ))}

              {solicitudes.data.totalPaginas > 1 && (
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-body-sm text-text-muted">
                    Página {paginaSolicitudes + 1} de {solicitudes.data.totalPaginas}
                  </span>
                  <div className="flex gap-2">
                    <Button
                      variante="secundario"
                      disabled={paginaSolicitudes === 0}
                      onClick={() => setPaginaSolicitudes((pagina) => Math.max(0, pagina - 1))}
                    >
                      Anterior
                    </Button>
                    <Button
                      variante="secundario"
                      disabled={paginaSolicitudes + 1 >= solicitudes.data.totalPaginas}
                      onClick={() => setPaginaSolicitudes((pagina) => pagina + 1)}
                    >
                      Siguiente
                    </Button>
                  </div>
                </div>
              )}
            </section>
          )}

          {solicitudes.data &&
            solicitudes.data.contenido.length === 0 &&
            solicitudes.data.totalElementos > 0 && (
              <EstadoVacio
                icono={Inbox}
                titulo="Esta página ya no tiene solicitudes"
                accion={
                  <Button variante="secundario" onClick={() => setPaginaSolicitudes(0)}>
                    Ir a la primera página
                  </Button>
                }
              />
            )}
        </div>
      )}

      {guardando && detalle.data && (
        <ModalGuardarPermisos
          rol={detalle.data}
          permisos={[...seleccion]}
          paresNuevos={paresNuevos}
          descripciones={descripciones}
          onCerrar={() => setGuardando(false)}
          onGuardado={alGuardar}
        />
      )}

      {dictamen && (
        <ModalDictamenPermisos
          solicitud={dictamen.solicitud}
          decision={dictamen.decision}
          onCerrar={() => setDictamen(null)}
          onResuelto={alResolver}
        />
      )}
    </div>
  )
}
