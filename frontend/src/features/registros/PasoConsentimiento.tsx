import { useId, useState, type FormEvent } from 'react'
import { ArrowLeft, CircleCheck, FileText, Pencil, Send, ShieldAlert } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import { AvisoBloqueo } from '@/features/registros/AvisoBloqueo'
import { TarjetaPersona } from '@/features/registros/TarjetaPersona'
import { TituloPaso } from '@/features/registros/TituloPaso'
import {
  etiquetaFinalidad,
  nombreCompleto,
  type AccionError,
} from '@/features/registros/asistenteAlta'
import type { Equipo } from '@/features/organizacion/tipos'
import type { AvisoPrivacidad, PersonaConMinoria } from '@/features/registros/tipos'
import { esNoEncontrado } from '@/lib/api/errores'

type Props = {
  enfocar: boolean
  aviso: AvisoPrivacidad | undefined
  avisoCargando: boolean
  avisoError: unknown
  onReintentarAviso: () => void
  jugador: PersonaConMinoria
  /** Solo en un menor: quien otorga el consentimiento (DEC-76). */
  tutor: PersonaConMinoria | null
  parentesco?: string
  equipo: Equipo | undefined
  /** El consentimiento ya se registró en un intento anterior y no se repite (DEC-103). */
  consentimientoRegistrado: boolean
  bloqueo: AccionError | null
  enviando: boolean
  onAnterior: () => void
  onCambiarEquipo: () => void
  onEnviar: (finalidades: string[]) => void
}

const formatoFecha = new Intl.DateTimeFormat('es-MX', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

function fechaLegible(dia: string): string {
  return formatoFecha.format(new Date(`${dia}T00:00:00Z`))
}

/**
 * Paso 3 (F-18) con la disposición de F-06 (UI-033): aviso vigente, finalidades sin marcar y la
 * declaración expresa del titular o del tutor (RF-097, DEC-106). «Enviar a validación» registra el
 * consentimiento y después el alta (DEC-103).
 */
export function PasoConsentimiento({
  enfocar,
  aviso,
  avisoCargando,
  avisoError,
  onReintentarAviso,
  jugador,
  tutor,
  parentesco,
  equipo,
  consentimientoRegistrado,
  bloqueo,
  enviando,
  onAnterior,
  onCambiarEquipo,
  onEnviar,
}: Props) {
  const id = useId()
  const [finalidades, setFinalidades] = useState<string[]>([])
  const [acepta, setAcepta] = useState(false)
  const menor = jugador.esMenor
  const otorgante = menor && tutor ? nombreCompleto(tutor) : nombreCompleto(jugador)
  const listo = consentimientoRegistrado || (finalidades.length > 0 && acepta)
  const sinAviso = !consentimientoRegistrado && esNoEncontrado(avisoError)
  const puedeEnviar = listo && !enviando && (consentimientoRegistrado || Boolean(aviso))

  const alternar = (codigo: string, marcada: boolean) =>
    setFinalidades((actuales) =>
      marcada ? [...actuales, codigo] : actuales.filter((actual) => actual !== codigo),
    )

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    if (puedeEnviar) {
      onEnviar(finalidades)
    }
  }

  return (
    <form onSubmit={alEnviar} noValidate aria-labelledby={`${id}-titulo`} className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <TituloPaso id={`${id}-titulo`} enfocar={enfocar}>
          Consentimiento para el tratamiento de datos personales
        </TituloPaso>
        {aviso && (
          <p className="flex flex-wrap items-center gap-2 text-body-md text-text-muted">
            <span>
              Aviso de privacidad <span className="font-mono text-mono-data">{aviso.version}</span>{' '}
              · vigente desde {fechaLegible(aviso.vigenteDesde)}
            </span>
            {aviso.esDemo && <InsigniaNoOficial />}
          </p>
        )}
      </div>

      {menor && (
        <div className="flex gap-3 rounded-lg border border-warning/40 bg-warning/8 p-4">
          <ShieldAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning-strong" />
          <div className="flex flex-col gap-1">
            <p className="text-headline-sm text-warning-strong">
              Sin el consentimiento del tutor no se puede completar el registro del menor
            </p>
            <p className="max-w-prose text-body-md text-text-main">
              El tutor debe leer el aviso y otorgar su consentimiento expreso. Es un bloqueo del
              sistema: sin él, el registro no se envía a validación.
            </p>
          </div>
        </div>
      )}

      <div className={['grid gap-3', menor && tutor ? 'lg:grid-cols-2' : ''].join(' ')}>
        <TarjetaPersona
          rol="Titular"
          persona={jugador}
          detalle={
            equipo && (
              <span className="text-body-sm text-text-muted">
                {equipo.nombre} · {equipo.categoria.nombre}
              </span>
            )
          }
        />
        {menor && tutor && (
          <TarjetaPersona
            rol="Tutor"
            persona={tutor}
            detalle={parentesco && <span className="text-body-sm text-text-muted">{parentesco}</span>}
          />
        )}
      </div>

      {consentimientoRegistrado ? (
        <p className="flex items-start gap-2 rounded-lg border border-success/40 bg-success/8 p-4 text-body-md text-text-main">
          <CircleCheck aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-success-strong" />
          El consentimiento ya quedó registrado. Solo falta enviar el registro a validación.
        </p>
      ) : sinAviso ? (
        <AvisoBloqueo
          titulo="No hay un aviso de privacidad vigente"
          mensaje="Sin aviso vigente no se puede registrar el consentimiento ni completar el alta. Contacta a la liga para que publique el aviso de privacidad."
        />
      ) : avisoError ? (
        <ErrorConsulta error={avisoError} onReintentar={onReintentarAviso} />
      ) : avisoCargando || !aviso ? (
        <p role="status" className="text-body-md text-text-muted">
          Cargando el aviso de privacidad…
        </p>
      ) : (
        <>
          <section aria-labelledby={`${id}-aviso`} className="flex flex-col gap-3">
            <h3 id={`${id}-aviso`} className="flex items-center gap-2 text-headline-md text-text-main">
              <FileText aria-hidden="true" className="size-5 text-text-muted" />
              Texto del aviso
            </h3>
            <div
              tabIndex={0}
              role="region"
              aria-label="Texto del aviso de privacidad"
              className="max-h-72 overflow-y-auto rounded-lg border border-border bg-background p-4 text-body-md whitespace-pre-line text-text-main focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <p className="max-w-[75ch]">{aviso.contenido}</p>
            </div>
          </section>

          <fieldset className="flex flex-col gap-3">
            <legend className="text-headline-md text-text-main">Finalidades</legend>
            <p className="-mt-1 text-body-md text-text-muted">
              Marca las finalidades que {menor ? 'el tutor autoriza' : 'el titular autoriza'}. Se
              necesita al menos una.
            </p>
            <ul className="flex flex-col gap-2">
              {aviso.finalidades.map((codigo) => {
                const idCasilla = `${id}-finalidad-${codigo}`

                return (
                  <li key={codigo}>
                    <label
                      htmlFor={idCasilla}
                      className="flex min-h-11 cursor-pointer items-center gap-3 rounded-lg border border-border bg-surface px-4 py-3 text-body-md text-text-main hover:bg-background has-[:checked]:border-primary has-[:checked]:bg-primary/6"
                    >
                      <input
                        id={idCasilla}
                        type="checkbox"
                        checked={finalidades.includes(codigo)}
                        disabled={enviando}
                        onChange={(evento) => alternar(codigo, evento.target.checked)}
                        className="size-4.5 shrink-0 accent-primary"
                      />
                      {etiquetaFinalidad(codigo)}
                    </label>
                  </li>
                )
              })}
            </ul>
          </fieldset>

          <label
            htmlFor={`${id}-acepta`}
            className="flex cursor-pointer items-start gap-3 rounded-lg border border-border bg-surface p-4 has-[:checked]:border-primary has-[:checked]:bg-primary/6"
          >
            <input
              id={`${id}-acepta`}
              type="checkbox"
              checked={acepta}
              disabled={enviando}
              onChange={(evento) => setAcepta(evento.target.checked)}
              className="mt-0.5 size-4.5 shrink-0 accent-primary"
            />
            <span className="flex flex-col gap-1">
              <span className="text-headline-sm text-text-main">
                {otorgante} leyó el aviso y otorga su consentimiento expreso
              </span>
              <span className="text-body-md text-text-muted">
                {menor
                  ? `Como tutor, autoriza el tratamiento de los datos de ${nombreCompleto(jugador)} para las finalidades marcadas.`
                  : 'Autoriza el tratamiento de sus datos para las finalidades marcadas.'}
              </span>
            </span>
          </label>
        </>
      )}

      {bloqueo?.tipo === 'cambiarEquipo' && (
        <AvisoBloqueo
          titulo="No se puede registrar en este equipo"
          mensaje={bloqueo.mensaje}
          accion={
            <Button variante="secundario" icono={Pencil} onClick={onCambiarEquipo}>
              Cambiar equipo
            </Button>
          }
        >
          {(bloqueo.club || bloqueo.equipo) && (
            <dl className="grid gap-x-3 gap-y-1 text-body-md sm:grid-cols-[auto_1fr]">
              {bloqueo.club && (
                <>
                  <dt className="text-label-md">Club donde ya está</dt>
                  <dd className="font-semibold">{bloqueo.club}</dd>
                </>
              )}
              {bloqueo.equipo && (
                <>
                  <dt className="text-label-md">Equipo</dt>
                  <dd className="font-semibold">{bloqueo.equipo}</dd>
                </>
              )}
            </dl>
          )}
        </AvisoBloqueo>
      )}
      {(bloqueo?.tipo === 'consentimiento' ||
        bloqueo?.tipo === 'recargarAviso' ||
        bloqueo?.tipo === 'general') && (
        <AvisoBloqueo
          titulo={
            bloqueo.tipo === 'general'
              ? 'No se pudo enviar el registro'
              : 'Vuelve a otorgar el consentimiento'
          }
          mensaje={bloqueo.mensaje}
        />
      )}

      <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:justify-between">
        <Button variante="secundario" icono={ArrowLeft} disabled={enviando} onClick={onAnterior}>
          Anterior: datos y tutor
        </Button>
        <Button type="submit" icono={Send} cargando={enviando} disabled={!puedeEnviar}>
          Enviar a validación
        </Button>
      </div>
    </form>
  )
}
