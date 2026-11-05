import { useState } from 'react'
import {
  CircleCheck,
  RotateCw,
  SearchX,
  UserPlus,
  Users,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { FormularioFiltrosUsuarios } from '@/features/usuarios/FormularioFiltrosUsuarios'
import { ModalInvitarUsuario } from '@/features/usuarios/ModalInvitarUsuario'
import { PaginacionUsuarios } from '@/features/usuarios/PaginacionUsuarios'
import { PanelUsuario } from '@/features/usuarios/PanelUsuario'
import { TablaUsuarios } from '@/features/usuarios/TablaUsuarios'
import { useClubes, useRoles, useUsuarios } from '@/features/usuarios/api'
import type { FiltrosUsuarios, Usuario } from '@/features/usuarios/tipos'
import { esSinPermiso, reintentarConsulta } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

const TAMANO = 20
const FILTROS_INICIALES: FiltrosUsuarios = { pagina: 0, tamano: TAMANO }
const formatoEntero = new Intl.NumberFormat('es-MX')

function hayFiltros(filtros: FiltrosUsuarios): boolean {
  return Boolean(
    filtros.q ||
      filtros.rolId ||
      filtros.ambito ||
      filtros.clubId ||
      filtros.activo !== undefined,
  )
}

/**
 * Pantalla de usuarios de F-12 (H-03.2, RF-094): tabla con filtros, invitación y panel lateral con
 * asignaciones, revocación con motivo y activación o desactivación. Las acciones de escritura se
 * ocultan sin permiso; el backend vuelve a autorizar cada una (VAL-015).
 */
export function GestionUsuarios() {
  const [filtros, setFiltros] = useState<FiltrosUsuarios>(FILTROS_INICIALES)
  const [invitando, setInvitando] = useState(false)
  const [seleccionado, setSeleccionado] = useState<string | null>(null)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const consulta = useUsuarios(filtros)
  const roles = useRoles()
  const clubes = useClubes()

  if (esSinPermiso(consulta.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const datos = consulta.data
  const filtrosAplicados = hayFiltros(filtros)

  const aplicarFiltros = (nuevos: FiltrosUsuarios) => {
    setFiltros({ ...nuevos, pagina: 0, tamano: TAMANO })
    setMensaje(null)
  }

  const limpiarFiltros = () => {
    setFiltros(FILTROS_INICIALES)
    setMensaje(null)
  }

  const abrirInvitacion = () => {
    setMensaje(null)
    setInvitando(true)
  }

  const alInvitar = (usuario: Usuario) => {
    setInvitando(false)
    setMensaje(`Invitación enviada a ${usuario.correo ?? usuario.nombreMostrado}.`)
  }

  const abrirPanel = (usuario: Usuario) => {
    setMensaje(null)
    setSeleccionado(usuario.id)
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Usuarios</h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Cuentas internas de la Liga, sus roles y el ámbito con el que pueden operar.
          </p>
        </div>
        <ConPermiso permiso="seguridad.usuario:CREAR">
          <Button icono={UserPlus} onClick={abrirInvitacion} className="shrink-0">
            Invitar usuario
          </Button>
        </ConPermiso>
      </header>

      <FormularioFiltrosUsuarios
        aplicados={filtros}
        roles={roles.data ?? []}
        clubes={clubes.data ?? []}
        onAplicar={aplicarFiltros}
      />

      {(roles.isError || clubes.isError) && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-surface px-4 py-2"
        >
          <p className="text-body-md text-text-main">
            No se pudieron cargar todas las opciones de los filtros; la lista sigue disponible.
          </p>
          <Button
            variante="secundario"
            icono={RotateCw}
            onClick={() => {
              if (roles.isError) {
                void roles.refetch()
              }
              if (clubes.isError) {
                void clubes.refetch()
              }
            }}
          >
            Reintentar
          </Button>
        </div>
      )}

      {mensaje && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-success/30 bg-success/8 p-3 text-body-md text-success-strong"
        >
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          {mensaje}
        </p>
      )}

      {consulta.isPending && (
        <EstadoCarga etiqueta="Cargando usuarios…" filas={6} columnas={5} />
      )}

      {consulta.isError && (
        <ErrorConsulta
          error={consulta.error}
          onReintentar={
            reintentarConsulta(0, consulta.error) ? () => void consulta.refetch() : undefined
          }
        />
      )}

      {!consulta.isError && datos?.totalElementos === 0 && (
        filtrosAplicados ? (
          <EstadoVacio
            icono={SearchX}
            titulo="Ninguna cuenta coincide con los filtros"
            descripcion="Prueba con otro nombre, rol, ámbito, club o estado."
            accion={
              <Button variante="secundario" onClick={limpiarFiltros}>
                Limpiar filtros
              </Button>
            }
          />
        ) : (
          <EstadoVacio
            icono={Users}
            titulo="Aún no hay cuentas registradas"
            descripcion="Invita la primera cuenta para que pueda trabajar en la Liga."
            accion={
              <ConPermiso permiso="seguridad.usuario:CREAR">
                <Button icono={UserPlus} onClick={abrirInvitacion}>
                  Invitar usuario
                </Button>
              </ConPermiso>
            }
          />
        )
      )}

      {!consulta.isError && datos && datos.contenido.length === 0 && datos.totalElementos > 0 && (
        <EstadoVacio
          titulo="Esta página ya no tiene cuentas"
          accion={
            <Button variante="secundario" onClick={() => setFiltros(FILTROS_INICIALES)}>
              Ir a la primera página
            </Button>
          }
        />
      )}

      {!consulta.isError && datos && datos.contenido.length > 0 && (
        <section aria-label="Usuarios registrados" className="flex flex-col gap-3">
          <p aria-live="polite" className="text-label-md text-text-main">
            {datos.totalElementos === 1
              ? '1 cuenta'
              : `${formatoEntero.format(datos.totalElementos)} cuentas`}
          </p>
          <TablaUsuarios
            usuarios={datos.contenido}
            actualizando={consulta.isPlaceholderData}
            onGestionar={abrirPanel}
          />
          {datos.totalPaginas > 1 && (
            <PaginacionUsuarios
              pagina={datos.pagina}
              totalPaginas={datos.totalPaginas}
              onCambiarPagina={(pagina) =>
                setFiltros((actuales) => ({ ...actuales, pagina }))
              }
            />
          )}
        </section>
      )}

      {invitando && (
        <ModalInvitarUsuario
          roles={roles.data ?? []}
          clubes={clubes.data ?? []}
          onCerrar={() => setInvitando(false)}
          onInvitado={alInvitar}
        />
      )}

      {seleccionado && (
        <PanelUsuario
          usuarioId={seleccionado}
          roles={roles.data ?? []}
          clubes={clubes.data ?? []}
          onCerrar={() => setSeleccionado(null)}
        />
      )}
    </div>
  )
}
