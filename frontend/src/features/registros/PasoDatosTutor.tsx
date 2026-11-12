import { useId, type Dispatch, type FormEvent } from 'react'
import { ArrowLeft, ArrowRight, Baby } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { AvisoBloqueo } from '@/features/registros/AvisoBloqueo'
import { FormularioPersona } from '@/features/registros/FormularioPersona'
import { SeccionTutor } from '@/features/registros/SeccionTutor'
import { SelectorEquipo } from '@/features/registros/SelectorEquipo'
import { TarjetaPersona } from '@/features/registros/TarjetaPersona'
import { TituloPaso } from '@/features/registros/TituloPaso'
import {
  DATOS_PERSONA_VACIOS,
  jugadorEsMenor,
  normalizarCurp,
  type AccionAsistente,
  type DatosPersona,
  type EstadoAsistente,
  type GrupoEquipos,
} from '@/features/registros/asistenteAlta'

type Props = {
  estado: EstadoAsistente
  despachar: Dispatch<AccionAsistente>
  enfocar: boolean
  hoy: string
  grupos: readonly GrupoEquipos[]
  equiposCargando: boolean
  equiposError: unknown
  onReintentarEquipos: () => void
  procesando: boolean
  buscandoTutor: boolean
  /** Sin escrituras se puede volver a cambiar la CURP del paso 1 (DEC-103). */
  puedeVolver: boolean
  onBuscarTutor: () => void
  onAnterior: () => void
  onContinuar: () => void
}

/**
 * Paso 2 (F-18): datos del jugador nuevo o su identidad de solo lectura (DEC-105), equipo y, si es
 * menor, tutor y parentesco. «Continuar» hace las escrituras de DEC-103.
 */
export function PasoDatosTutor({
  estado,
  despachar,
  enfocar,
  hoy,
  grupos,
  equiposCargando,
  equiposError,
  onReintentarEquipos,
  procesando,
  buscandoTutor,
  puedeVolver,
  onBuscarTutor,
  onAnterior,
  onContinuar,
}: Props) {
  const id = useId()
  const { errores, jugador, bloqueo } = estado
  const menor = jugadorEsMenor(estado, hoy)

  const actualizar = (cambios: Partial<EstadoAsistente>) =>
    despachar({ tipo: 'actualizar', cambios: { ...cambios, bloqueo: null } })

  const cambiarDatoJugador = (campo: keyof DatosPersona, valor: string) =>
    actualizar({
      datosJugador: { ...estado.datosJugador, [campo]: valor },
      errores: { ...errores, jugador: { ...errores.jugador, [campo]: undefined } },
    })

  const cambiarDatoTutor = (campo: keyof DatosPersona, valor: string) =>
    actualizar({
      datosTutor: { ...estado.datosTutor, [campo]: valor },
      errores: { ...errores, tutor: { ...errores.tutor, [campo]: undefined }, tutorFaltante: undefined },
    })

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    onContinuar()
  }

  const bloqueoTutor =
    errores.tutorFaltante ?? (bloqueo?.tipo === 'tutor' ? bloqueo.mensaje : undefined)

  return (
    <form onSubmit={alEnviar} noValidate aria-labelledby={`${id}-titulo`} className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <TituloPaso id={`${id}-titulo`} enfocar={enfocar}>
          Datos y equipo
        </TituloPaso>
        <p className="max-w-prose text-body-md text-text-muted">
          Confirma la identidad, elige el equipo y, si es menor de edad, registra a su tutor.
        </p>
      </div>

      <section aria-labelledby={`${id}-jugador`} className="flex flex-col gap-4">
        <h3 id={`${id}-jugador`} className="text-headline-md text-text-main">
          Jugador
        </h3>
        {jugador ? (
          <TarjetaPersona rol="Identidad registrada" persona={jugador} />
        ) : (
          <>
            <p className="text-body-md text-text-muted">
              CURP <span className="font-mono text-mono-data text-text-main">{estado.curp}</span>
            </p>
            <FormularioPersona
              idBase={`${id}-jugador-datos`}
              datos={estado.datosJugador}
              errores={errores.jugador}
              hoy={hoy}
              deshabilitado={procesando}
              onCambiar={cambiarDatoJugador}
            />
            {menor && (
              <p className="flex flex-wrap items-center gap-2 text-body-md text-text-muted">
                <Insignia variante="informacion" icono={Baby}>
                  Menor de edad
                </Insignia>
                Por su fecha de nacimiento necesita tutor y consentimiento.
              </p>
            )}
          </>
        )}
      </section>

      <section aria-labelledby={`${id}-equipo`} className="flex flex-col gap-4">
        <h3 id={`${id}-equipo`} className="text-headline-md text-text-main">
          Equipo
        </h3>
        {equiposError ? (
          <ErrorConsulta error={equiposError} onReintentar={onReintentarEquipos} />
        ) : equiposCargando ? (
          <p role="status" className="text-body-md text-text-muted">
            Cargando equipos…
          </p>
        ) : grupos.length === 0 ? (
          <EstadoVacio
            titulo="No hay equipos disponibles"
            descripcion="No hay equipos activos de tu club en una temporada abierta. Registra el equipo en Clubes antes de dar de alta jugadores."
          />
        ) : (
          <div className="sm:max-w-xl">
            <SelectorEquipo
              id={`${id}-equipo-campo`}
              grupos={grupos}
              valor={estado.equipoId}
              error={errores.equipo}
              deshabilitado={procesando}
              onCambiar={(equipoId) =>
                actualizar({ equipoId, errores: { ...errores, equipo: undefined } })
              }
            />
          </div>
        )}
      </section>

      {menor && (
        <SeccionTutor
          idBase={`${id}-tutor`}
          hoy={hoy}
          deshabilitado={procesando}
          curp={estado.tutorCurp}
          buscando={buscandoTutor}
          tutor={estado.tutor}
          tutorNuevo={estado.tutorNuevo}
          datos={estado.datosTutor}
          errores={errores.tutor}
          parentesco={estado.parentesco}
          parentescoOtro={estado.parentescoOtro}
          errorParentesco={errores.parentesco}
          bloqueo={bloqueoTutor}
          puedeCambiarTutor={!estado.tutelaRegistrada}
          onCambiarCurp={(valor) =>
            actualizar({
              tutorCurp: normalizarCurp(valor),
              tutorNuevo: false,
              errores: { ...errores, tutor: {}, tutorFaltante: undefined },
            })
          }
          onBuscar={onBuscarTutor}
          onCambiarTutor={() =>
            actualizar({
              tutor: null,
              tutorNuevo: false,
              tutorCurp: '',
              datosTutor: DATOS_PERSONA_VACIOS,
              errores: { ...errores, tutor: {}, tutorFaltante: undefined },
            })
          }
          onCambiarDato={cambiarDatoTutor}
          onCambiarParentesco={(parentesco) =>
            actualizar({ parentesco, errores: { ...errores, parentesco: undefined } })
          }
          onCambiarParentescoOtro={(parentescoOtro) =>
            actualizar({ parentescoOtro, errores: { ...errores, parentesco: undefined } })
          }
        />
      )}

      {bloqueo?.tipo === 'general' && (
        <AvisoBloqueo titulo="No se pudo guardar" mensaje={bloqueo.mensaje} />
      )}

      <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:justify-between">
        {puedeVolver ? (
          <Button variante="secundario" icono={ArrowLeft} disabled={procesando} onClick={onAnterior}>
            Anterior: identidad
          </Button>
        ) : (
          <span />
        )}
        <Button type="submit" icono={ArrowRight} cargando={procesando}>
          Continuar
        </Button>
      </div>
    </form>
  )
}
