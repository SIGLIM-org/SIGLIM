import { useId, useState } from 'react'
import {
  Ban,
  CalendarDays,
  CircleAlert,
  CircleCheck,
  Info,
  Plus,
  ShieldCheck,
  UserRound,
  X,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { Insignia } from '@/components/ui/Insignia'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { useActualizarUsuario, useUsuario } from '@/features/usuarios/api'
import {
  describirAsignacion,
  etiquetaAmbito,
  formatearFechaAlta,
  iniciales,
} from '@/features/usuarios/etiquetas'
import { ModalAgregarAsignacion } from '@/features/usuarios/ModalAgregarAsignacion'
import { ModalDesactivarCuenta } from '@/features/usuarios/ModalDesactivarCuenta'
import { ModalRevocarAsignacion } from '@/features/usuarios/ModalRevocarAsignacion'
import type {
  AsignacionUsuario,
  ClubResumen,
  RolCatalogo,
} from '@/features/usuarios/tipos'
import { ApiError } from '@/lib/api/ApiError'
import { reintentarConsulta } from '@/lib/api/errores'

type Props = {
  usuarioId: string
  roles: readonly RolCatalogo[]
  clubes: readonly ClubResumen[]
  onCerrar: () => void
}

/**
 * Panel lateral del usuario de F-12 (H-03.2, RF-094): datos de la cuenta, sus asignaciones con
 * vigencia, el alta o revocación de roles y la activación o desactivación con motivo. Usa `<dialog>`
 * con `showModal()` para la trampa de foco y Esc nativos (RNF-008).
 *
 * En la cuenta propia (separación de funciones, VAL-022, DEC-53; DEF-04) el panel es de solo
 * consulta: oculta «Agregar asignación», «Revocar» y el interruptor «Cuenta activa» y explica por
 * qué. Se compara `perfiles.id` con la sesión, nunca el rol; el backend responde 403 `VAL-022`.
 */
export function PanelUsuario({ usuarioId, roles, clubes, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const { yo } = useSesionActual()
  const consulta = useUsuario(usuarioId)
  const actualizar = useActualizarUsuario(usuarioId)
  const [revocando, setRevocando] = useState<AsignacionUsuario | null>(null)
  const [agregando, setAgregando] = useState(false)
  const [desactivando, setDesactivando] = useState(false)
  const [errorActivacion, setErrorActivacion] = useState<string | null>(null)
  const [mensajePanel, setMensajePanel] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const idAsignaciones = `${id}-asignaciones`

  const usuario = consulta.data
  const ocupado = actualizar.isPending
  const esCuentaPropia = usuario?.id === yo.perfil.id

  const activar = () => {
    if (!usuario) {
      return
    }

    setErrorActivacion(null)
    setMensajePanel(null)
    actualizar.mutate(
      { activo: true },
      {
        onSuccess: (actualizado) => {
          setMensajePanel(`Cuenta «${actualizado.nombreMostrado}» activada.`)
        },
        onError: (fallo) => {
          setErrorActivacion(
            fallo instanceof ApiError
              ? fallo.problema.detail
              : 'No se pudo activar la cuenta. Intenta de nuevo.',
          )
        },
      },
    )
  }

  const alRevocar = () => {
    const descripcion = revocando ? describirAsignacion(revocando) : 'La asignación'
    const mensaje = `Asignación «${descripcion}» revocada; la cuenta conserva el historial.`
    setRevocando(null)
    setMensajePanel(mensaje)
  }

  const alAgregar = (asignacion: AsignacionUsuario) => {
    const mensaje = `Asignación «${describirAsignacion(asignacion)}» agregada.`
    setAgregando(false)
    setMensajePanel(mensaje)
  }

  const alDesactivar = () => {
    const mensaje = `Cuenta «${usuario?.nombreMostrado ?? ''}» desactivada.`
    setDesactivando(false)
    setMensajePanel(mensaje)
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!ocupado) {
          onCerrar()
        }
      }}
      onClick={(evento) => {
        if (evento.target === evento.currentTarget && !ocupado) {
          onCerrar()
        }
      }}
      className="m-0 ml-auto h-dvh max-h-none w-full max-w-xl overflow-hidden rounded-none border-l border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <div className="flex h-full flex-col">
        <header className="flex items-start justify-between gap-3 border-b border-border p-5">
          <div className="flex min-w-0 items-start gap-3">
            <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
              <UserRound aria-hidden="true" className="size-5" />
            </span>
            <div className="min-w-0">
              <h2 id={idTitulo} className="text-headline-md text-text-main">
                Detalle de usuario
              </h2>
              <p className="mt-1 text-body-sm text-text-muted">
                Cuenta, roles y ámbito de acceso.
              </p>
            </div>
          </div>
          <button
            type="button"
            aria-label="Cerrar panel"
            onClick={onCerrar}
            disabled={ocupado}
            className="inline-flex size-11 shrink-0 items-center justify-center rounded-md border border-border text-text-main transition-colors hover:bg-background disabled:cursor-not-allowed disabled:opacity-50"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </header>

        {mensajePanel && (
          <p
            role="status"
            className="flex items-start gap-2 border-b border-success/30 bg-success/8 px-5 py-3 text-body-md text-success-strong"
          >
            <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            {mensajePanel}
          </p>
        )}

        <div className="flex-1 overflow-y-auto p-5">
          {consulta.isPending && (
            <EstadoCarga etiqueta="Cargando la cuenta…" filas={4} columnas={3} />
          )}

          {consulta.isError && (
            <ErrorConsulta
              error={consulta.error}
              onReintentar={
                reintentarConsulta(0, consulta.error)
                  ? () => void consulta.refetch()
                  : undefined
              }
            />
          )}

          {usuario && !consulta.isError && (
            <div className="flex flex-col gap-6">
              {esCuentaPropia && (
                <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-sm text-info-strong">
                  <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                  Esta es tu cuenta. Por separación de funciones (VAL-022) no puedes cambiar tus
                  asignaciones ni el estado de tu cuenta.
                </p>
              )}

              <section aria-label="Datos de la cuenta" className="flex flex-col gap-3">
                <div className="flex min-w-0 items-center gap-3">
                  <span
                    aria-hidden="true"
                    className="grid size-12 shrink-0 place-items-center rounded-md border border-border bg-background text-headline-sm font-semibold text-text-main"
                  >
                    {iniciales(usuario.nombreMostrado)}
                  </span>
                  <div className="min-w-0">
                    <h3 className="break-words text-headline-md text-text-main">
                      {usuario.nombreMostrado}
                    </h3>
                    <p className="break-all font-mono text-mono-data text-text-muted">
                      {usuario.correo ?? 'Sin correo'}
                    </p>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
                  <Insignia variante={usuario.activo ? 'exito' : 'neutra'}>
                    {usuario.activo ? 'Activo' : 'Inactivo'}
                  </Insignia>
                  <span className="inline-flex items-center gap-1.5 text-body-sm text-text-muted">
                    <CalendarDays aria-hidden="true" className="size-4" />
                    Alta: {formatearFechaAlta(usuario.creadoEn)}
                  </span>
                </div>
              </section>

              <section
                aria-label="Estado de la cuenta"
                className="flex items-start justify-between gap-4 rounded-lg border border-border p-4"
              >
                <div className="min-w-0">
                  <h3 className="text-headline-sm text-text-main">Cuenta activa</h3>
                  <p className="mt-1 text-body-md text-text-muted">
                    {usuario.activo
                      ? 'Puede iniciar sesión y operar con los permisos de sus roles.'
                      : 'No puede iniciar sesión mientras esté desactivada.'}
                  </p>
                  {errorActivacion && (
                    <p role="alert" className="mt-2 flex items-start gap-1.5 text-body-sm text-error-strong">
                      <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                      {errorActivacion}
                    </p>
                  )}
                </div>
                {esCuentaPropia ? (
                  <span className="text-body-sm text-text-muted">Tu cuenta</span>
                ) : (
                  <ConPermiso
                    permiso="seguridad.usuario:EDITAR"
                    alternativa={
                      <span className="text-body-sm text-text-muted">Solo consulta</span>
                    }
                  >
                    <button
                      type="button"
                      role="switch"
                      aria-checked={usuario.activo}
                      aria-label="Cuenta activa"
                      disabled={ocupado}
                      onClick={() => {
                        if (usuario.activo) {
                          setMensajePanel(null)
                          setDesactivando(true)
                        } else {
                          activar()
                        }
                      }}
                      className={[
                        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-50',
                        usuario.activo ? 'bg-primary' : 'bg-border',
                      ].join(' ')}
                    >
                      <span
                        aria-hidden="true"
                        className={[
                          'inline-block size-5 rounded-full bg-surface shadow-sm transition-transform',
                          usuario.activo ? 'translate-x-5' : 'translate-x-0.5',
                        ].join(' ')}
                      />
                    </button>
                  </ConPermiso>
                )}
              </section>

              <section aria-labelledby={idAsignaciones} className="flex flex-col gap-3">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <h3 id={idAsignaciones} className="text-headline-sm text-text-main">
                      Asignaciones de rol
                    </h3>
                    <p className="mt-1 text-body-sm text-text-muted">
                      Cada asignación define el ámbito con el que la cuenta puede operar.
                    </p>
                  </div>
                  {!esCuentaPropia && (
                    <ConPermiso permiso="seguridad.usuario:EDITAR">
                      <Button
                        variante="secundario"
                        icono={Plus}
                        className="shrink-0"
                        onClick={() => {
                          setMensajePanel(null)
                          setAgregando(true)
                        }}
                      >
                        Agregar asignación
                      </Button>
                    </ConPermiso>
                  )}
                </div>

                {usuario.asignaciones.length === 0 ? (
                  <p className="rounded-lg border border-border bg-background p-4 text-body-md text-text-muted">
                    Esta cuenta no tiene roles asignados.
                  </p>
                ) : (
                  <ul className="divide-y divide-border rounded-lg border border-border">
                    {usuario.asignaciones.map((asignacion) => (
                      <li key={asignacion.id} className="flex flex-col gap-2 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-2">
                          <div className="min-w-0">
                            <p className="text-headline-sm text-text-main">
                              {asignacion.rol.nombre}
                            </p>
                            <p className="text-body-sm text-text-muted">
                              {etiquetaAmbito[asignacion.ambito]}
                              {asignacion.club ? ` · ${asignacion.club.nombre}` : ''}
                            </p>
                          </div>
                          <div className="flex flex-wrap items-center gap-2">
                            <Insignia variante={asignacion.activa ? 'exito' : 'neutra'}>
                              {asignacion.activa ? 'Vigente' : 'Revocada'}
                            </Insignia>
                            {asignacion.activa && !esCuentaPropia && (
                              <ConPermiso permiso="seguridad.usuario:EDITAR">
                                <Button
                                  variante="destructivo"
                                  icono={Ban}
                                  onClick={() => {
                                    setMensajePanel(null)
                                    setRevocando(asignacion)
                                  }}
                                >
                                  Revocar
                                </Button>
                              </ConPermiso>
                            )}
                          </div>
                        </div>
                        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body-sm text-text-muted">
                          <span className="inline-flex items-center gap-1.5">
                            <ShieldCheck aria-hidden="true" className="size-4" />
                            Asignada el {formatearFechaAlta(asignacion.creadoEn)}
                          </span>
                          {asignacion.revocadaEn && (
                            <span>Revocada el {formatearFechaAlta(asignacion.revocadaEn)}</span>
                          )}
                        </p>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </div>
          )}
        </div>
      </div>

      {revocando && (
        <ModalRevocarAsignacion
          asignacion={revocando}
          onCerrar={() => setRevocando(null)}
          onRevocada={alRevocar}
        />
      )}

      {agregando && usuario && (
        <ModalAgregarAsignacion
          usuarioId={usuario.id}
          nombreUsuario={usuario.nombreMostrado}
          roles={roles}
          clubes={clubes}
          onCerrar={() => setAgregando(false)}
          onAgregada={alAgregar}
        />
      )}

      {desactivando && usuario && (
        <ModalDesactivarCuenta
          usuario={usuario}
          onCerrar={() => setDesactivando(false)}
          onDesactivada={alDesactivar}
        />
      )}
    </dialog>
  )
}
