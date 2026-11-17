import { useEffect, useId, useState, type FormEvent } from 'react'
import { CircleAlert, Send } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useContextoTramites, useCrearSolicitudCancha } from '@/features/canchas/tramites/api'
import {
  aOferta,
  aPrestamo,
  camposDe,
  validarCampos,
  type CampoFormato,
  type Valores,
} from '@/features/canchas/tramites/campos'
import { etiquetaTipoTramite, formatos } from '@/features/canchas/tramites/etiquetas'
import type {
  DestinatarioPrestamo,
  FormatoTramite,
  SolicitudCancha,
  TipoSolicitudCancha,
} from '@/features/canchas/tramites/tipos'
import { detalleDeFallo } from '@/features/organizacion/erroresFormulario'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  formato: FormatoTramite
  onCreada: (solicitud: SolicitudCancha) => void
  onCerrar: () => void
}

type Oferente = 'CLUB' | 'EXTERNO'

const MAXIMO_EXTERNO = 150

/**
 * Captura de un trámite con los campos del formato del cliente (B-07, RF-116, RF-117, R8). El
 * número de oficio lo asigna el sistema al guardar. Una oferta de externo solo la ofrece a quien
 * captura con ámbito de liga (el Secretario).
 */
export function FormularioTramite({ formato, onCreada, onCerrar }: Props) {
  const dialogoRef = useDialogoModal()
  const id = useId()
  const contexto = useContextoTramites()
  const crear = useCrearSolicitudCancha()
  const [tipo, setTipo] = useState<TipoSolicitudCancha>(formato === 1 ? 'PRESTAMO' : 'OFERTA')
  const [oferente, setOferente] = useState<Oferente>('CLUB')
  const [clubId, setClubId] = useState('')
  const [externo, setExterno] = useState('')
  const [canchaId, setCanchaId] = useState('')
  const [dirigidaA, setDirigidaA] = useState<DestinatarioPrestamo>('LIGA')
  const [valores, setValores] = useState<Valores>({})
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const clubes = contexto.data?.clubes ?? []
  const canchas = contexto.data?.canchas ?? []
  const capturaExternos = formato === 2 && (contexto.data?.capturaExternos ?? false)
  const conClub = formato === 1 || oferente === 'CLUB'
  const sinOrigen = conClub && clubes.length === 0
  const primerClubId = clubes[0]?.id

  useEffect(() => {
    if (!clubId && primerClubId) {
      setClubId(primerClubId)
    }
  }, [clubId, primerClubId])

  const cambiar = (campo: string, valor: string) => {
    setValores((actuales) => ({ ...actuales, [campo]: valor }))
    setErrores((actuales) => {
      if (!(campo in actuales)) {
        return actuales
      }
      const resto = { ...actuales }
      delete resto[campo]
      return resto
    })
  }

  const alFallar = (error: unknown) => {
    setErrorGeneral(detalleDeFallo(error, 'No se pudo guardar el trámite. Intenta de nuevo.'))
    if (error instanceof ApiError) {
      setErrores(
        Object.fromEntries(
          error.errores.map(({ campo, mensaje }) => [campo.replace(/^(prestamo|oferta)\./, ''), mensaje]),
        ),
      )
    }
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados = validarCampos(tipo, valores)
    const nombreExterno = externo.trim()

    if (conClub && !clubId) {
      encontrados.clubId = 'Elige el club.'
    }
    if (!conClub && !nombreExterno) {
      encontrados.solicitanteExterno = 'Escribe el nombre del oferente externo.'
    }
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      setErrorGeneral('Revisa los campos marcados.')
      return
    }

    crear.mutate(
      {
        tipo,
        ...(conClub ? { clubId } : { solicitanteExterno: nombreExterno }),
        ...(canchaId ? { canchaId } : {}),
        ...(tipo === 'OFERTA' ? { oferta: aOferta(valores) } : { prestamo: aPrestamo(valores, dirigidaA) }),
      },
      { onSuccess: onCreada, onError: alFallar },
    )
  }

  const campo = (definicion: CampoFormato) => {
    const idCampo = `${id}-${definicion.nombre}`
    const valor = valores[definicion.nombre] ?? ''
    const error = errores[definicion.nombre]

    if (definicion.multilinea) {
      return (
        <div key={definicion.nombre} className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor={idCampo} className="text-label-md text-text-main">
            {definicion.etiqueta}
            {definicion.obligatorio && (
              <span aria-hidden="true" className="ml-0.5 text-error">
                *
              </span>
            )}
          </label>
          <textarea
            id={idCampo}
            value={valor}
            rows={3}
            required={definicion.obligatorio}
            maxLength={definicion.maximo}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${idCampo}-error` : undefined}
            onChange={(evento) => cambiar(definicion.nombre, evento.target.value)}
            className="min-h-11 rounded-md border border-border bg-surface px-3 py-2 text-body-md text-text-main aria-invalid:border-error"
          />
          {error && (
            <p id={`${idCampo}-error`} role="alert" className="text-body-sm text-error-strong">
              {error}
            </p>
          )}
        </div>
      )
    }

    return (
      <div key={definicion.nombre} className={definicion.ancho ? 'sm:col-span-2' : undefined}>
        <CampoTexto
          id={idCampo}
          etiqueta={definicion.etiqueta}
          type={definicion.tipo ?? 'text'}
          inputMode={definicion.tipo === 'number' ? 'numeric' : undefined}
          min={definicion.tipo === 'number' ? 0 : undefined}
          required={definicion.obligatorio}
          maxLength={definicion.tipo === 'number' ? undefined : definicion.maximo}
          value={valor}
          ayuda={definicion.ayuda}
          error={error}
          onChange={(evento) => cambiar(definicion.nombre, evento.target.value)}
        />
      </div>
    )
  }

  const cargando = contexto.isPending

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={`${id}-titulo`}
      aria-describedby={`${id}-descripcion`}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!crear.isPending) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate className="flex flex-col gap-5 p-6">
        <div>
          <h2 id={`${id}-titulo`} className="text-balance text-headline-md">
            Nueva solicitud · {formatos[formato].titulo}
          </h2>
          <p id={`${id}-descripcion`} className="mt-1 text-body-md text-text-muted">
            {formato === 1
              ? 'Captura los datos de la carta de solicitud. El sistema le asigna el número de oficio y la deja Pendiente hasta el dictamen de la Presidencia.'
              : 'Captura los datos de la carta de oferta. El sistema le asigna el número de oficio y la deja Pendiente de negociación hasta que la Presidencia la acepte o la rechace.'}
          </p>
        </div>

        {cargando && <EstadoCarga etiqueta="Cargando clubes y canchas…" filas={3} columnas={2} />}
        {contexto.error != null && (
          <ErrorConsulta error={contexto.error} onReintentar={() => void contexto.refetch()} />
        )}

        {!cargando && contexto.error == null && sinOrigen && !capturaExternos && (
          <EstadoVacio
            icono={CircleAlert}
            titulo="No hay clubes disponibles"
            descripcion="Tu cuenta no puede capturar trámites para ningún club activo. Pide a la Liga que revise tu asignación."
            accion={
              <Button variante="secundario" onClick={onCerrar}>
                Cerrar
              </Button>
            }
          />
        )}

        {!cargando && contexto.error == null && !(sinOrigen && !capturaExternos) && (
          <>
            {formato === 1 && (
              <fieldset className="flex flex-col gap-2">
                <legend className="text-label-md text-text-main">
                  Tipo de trámite <span className="text-error" aria-hidden="true">*</span>
                </legend>
                <div className="grid gap-2 sm:grid-cols-2">
                  {(['PRESTAMO', 'COMODATO'] as const).map((opcion) => {
                    const etiqueta = etiquetaTipoTramite[opcion]
                    const Icono = etiqueta.icono
                    const seleccionado = tipo === opcion

                    return (
                      <label
                        key={opcion}
                        className={[
                          'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors focus-within:ring-2 focus-within:ring-primary focus-within:ring-offset-2',
                          seleccionado ? 'border-primary bg-primary/8' : 'border-border bg-surface hover:bg-background',
                        ].join(' ')}
                      >
                        <input
                          type="radio"
                          name={`${id}-tipo`}
                          value={opcion}
                          checked={seleccionado}
                          onChange={() => setTipo(opcion)}
                          className="mt-1 size-4 accent-primary"
                        />
                        <span className="min-w-0">
                          <span className="flex items-center gap-1.5 text-label-md text-text-main">
                            <Icono aria-hidden="true" className="size-4 shrink-0 text-primary" />
                            {etiqueta.texto}
                          </span>
                          <span className="mt-0.5 block text-body-sm text-text-muted">{etiqueta.descripcion}</span>
                        </span>
                      </label>
                    )
                  })}
                </div>
              </fieldset>
            )}

            {capturaExternos && (
              <fieldset className="flex flex-col gap-2">
                <legend className="text-label-md text-text-main">
                  ¿Quién ofrece la cancha? <span className="text-error" aria-hidden="true">*</span>
                </legend>
                <div className="flex flex-wrap gap-4">
                  {(
                    [
                      ['CLUB', 'Un club de la liga'],
                      ['EXTERNO', 'Un oferente externo sin cuenta'],
                    ] as const
                  ).map(([valor, texto]) => (
                    <label key={valor} className="flex min-h-11 items-center gap-2 text-body-md">
                      <input
                        type="radio"
                        name={`${id}-oferente`}
                        value={valor}
                        checked={oferente === valor}
                        onChange={() => setOferente(valor)}
                        className="size-4 accent-primary"
                      />
                      {texto}
                    </label>
                  ))}
                </div>
              </fieldset>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              {conClub ? (
                <CampoSeleccion
                  id={`${id}-club`}
                  etiqueta={formato === 1 ? 'Club solicitante' : 'Club oferente'}
                  required
                  value={clubId}
                  error={errores.clubId}
                  disabled={clubes.length === 0}
                  onChange={(evento) => setClubId(evento.target.value)}
                >
                  {clubes.length === 0 ? (
                    <option value="">Sin clubes disponibles</option>
                  ) : (
                    clubes.map((club) => (
                      <option key={club.id} value={club.id}>
                        {club.nombre}
                      </option>
                    ))
                  )}
                </CampoSeleccion>
              ) : (
                <CampoTexto
                  id={`${id}-externo`}
                  etiqueta="Oferente externo"
                  required
                  maxLength={MAXIMO_EXTERNO}
                  value={externo}
                  error={errores.solicitanteExterno}
                  ayuda="Persona o institución dueña de la cancha."
                  onChange={(evento) => {
                    setExterno(evento.target.value)
                    setErrores(({ solicitanteExterno: _omitido, ...resto }) => resto)
                  }}
                />
              )}

              <CampoSeleccion
                id={`${id}-cancha`}
                etiqueta="Cancha del catálogo (opcional)"
                value={canchaId}
                ayuda="Elígela si la cancha ya está registrada en la Liga."
                onChange={(evento) => setCanchaId(evento.target.value)}
              >
                <option value="">No está en el catálogo</option>
                {canchas.map((cancha) => (
                  <option key={cancha.id} value={cancha.id}>
                    {cancha.nombre}
                  </option>
                ))}
              </CampoSeleccion>

              {formato === 1 && (
                <CampoSeleccion
                  id={`${id}-dirigida`}
                  etiqueta="Carta dirigida a"
                  required
                  value={dirigidaA}
                  onChange={(evento) => setDirigidaA(evento.target.value as DestinatarioPrestamo)}
                >
                  <option value="LIGA">La Liga</option>
                  <option value="DUENO_EXTERNO">Un dueño externo</option>
                </CampoSeleccion>
              )}
            </div>

            <fieldset className="flex flex-col gap-3 border-t border-border pt-4">
              <legend className="sr-only">Datos de la carta</legend>
              <h3 className="text-headline-sm text-text-main">Datos de la carta</h3>
              <div className="grid gap-4 sm:grid-cols-2">{camposDe(tipo).map(campo)}</div>
            </fieldset>
          </>
        )}

        {errorGeneral && (
          <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
            {errorGeneral}
          </p>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variante="secundario" onClick={onCerrar} disabled={crear.isPending}>
            Cancelar
          </Button>
          <Button
            type="submit"
            icono={Send}
            cargando={crear.isPending}
            disabled={crear.isPending || cargando || contexto.error != null || (sinOrigen && !capturaExternos)}
          >
            Guardar y asignar oficio
          </Button>
        </div>
      </form>
    </dialog>
  )
}
