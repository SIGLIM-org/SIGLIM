import { useMemo, useReducer, useState } from 'react'
import { useNavigate } from 'react-router'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useBuscarPersona, useCrearPersona, useEquipos } from '@/features/organizacion/api'
import { hoyEnZonaNegocio } from '@/features/organizacion/directiva'
import type { Persona } from '@/features/organizacion/tipos'
import { IndicadorPasos } from '@/features/registros/IndicadorPasos'
import { ModalSalirAlta } from '@/features/registros/ModalSalirAlta'
import { PasoConsentimiento } from '@/features/registros/PasoConsentimiento'
import { PasoDatosTutor } from '@/features/registros/PasoDatosTutor'
import { PasoDocumentosPendiente } from '@/features/registros/PasoDocumentosPendiente'
import { PasoIdentidad } from '@/features/registros/PasoIdentidad'
import {
  useAvisoVigente,
  useRegistrarAltaJugador,
  useRegistrarConsentimiento,
  useRegistrarTutela,
} from '@/features/registros/api'
import {
  ESTADO_INICIAL,
  PASOS,
  SIN_ERRORES,
  accionDeError,
  agruparEquipos,
  curpCompleta,
  esMenorEnFecha,
  esTutelaDuplicada,
  huboEscrituras,
  jugadorEsMenor,
  normalizarCurp,
  parentescoFinal,
  peticionPersona,
  personaCreada,
  personaExistenteDe,
  reducirAsistente,
  validarDatosPersona,
  type AccionError,
  type ErroresAsistente,
  type ErroresPersona,
} from '@/features/registros/asistenteAlta'
import type { PersonaConMinoria } from '@/features/registros/tipos'
import { ApiError } from '@/lib/api/ApiError'
import { esNoEncontrado } from '@/lib/api/errores'

export const RUTA_SALIDA_ALTA = '/app/organizacion'

const MENSAJE_CURP = 'La CURP debe tener 18 caracteres y el formato oficial.'

/** Identidad de `GET /personas?curp=` con `esMenor` obligatorio (DEC-104). */
function conMinoria(persona: Persona): PersonaConMinoria {
  return { ...persona, esMenor: persona.esMenor === true }
}

/**
 * Campos que cada contexto dibuja en el paso 2. El jugador nuevo solo captura nombre, apellidos y
 * fecha (su CURP es de solo lectura y no lleva contacto); el tutor dibuja la CURP y el contacto.
 */
const CAMPOS_VISIBLES: Record<'persona' | 'tutor', readonly string[]> = {
  persona: ['nombre', 'apellidoPaterno', 'apellidoMaterno', 'fechaNacimiento'],
  tutor: ['curp', 'nombre', 'apellidoPaterno', 'apellidoMaterno', 'fechaNacimiento', 'correo', 'telefono'],
}

/** Errores de `errores` que el formulario de ese contexto puede marcar; el resto no se vería. */
function erroresPersona(contexto: 'persona' | 'tutor', errores: Record<string, string>): ErroresPersona {
  const campos = CAMPOS_VISIBLES[contexto]

  return Object.fromEntries(Object.entries(errores).filter(([campo]) => campos.includes(campo)))
}

/**
 * Asistente de alta de jugador (F-18, H-09.3, CU-004) en el orden de DEC-103: identidad → datos y
 * tutor → consentimiento (F-06) → documentos. Conserva los ids ya escritos para no repetir
 * escrituras al volver o reintentar; la interfaz decide por `codigo` (DEC-63, DEC-107).
 *
 * Refs: RF-010, RF-011, RF-013, RF-097, RF-106, RF-107, VAL-017, VAL-025, UI-006, UI-033
 */
export function AsistenteAltaJugador() {
  const navigate = useNavigate()
  const [estado, despachar] = useReducer(reducirAsistente, ESTADO_INICIAL)
  const [procesando, setProcesando] = useState(false)
  const [buscandoTutor, setBuscandoTutor] = useState(false)
  const [errorBusqueda, setErrorBusqueda] = useState<string | undefined>()
  const [confirmarSalida, setConfirmarSalida] = useState(false)
  /** Cambia para descartar las casillas del paso 3 cuando el aviso cambió (RF-097). */
  const [versionConsentimiento, setVersionConsentimiento] = useState(0)

  const buscarPersona = useBuscarPersona()
  const crearPersona = useCrearPersona()
  const registrarTutela = useRegistrarTutela()
  const registrarConsentimiento = useRegistrarConsentimiento()
  const registrarAlta = useRegistrarAltaJugador()
  const equipos = useEquipos({ estado: 'ACTIVO' })
  const aviso = useAvisoVigente({ habilitada: estado.paso >= 3 })

  const hoy = hoyEnZonaNegocio()
  const grupos = useMemo(() => agruparEquipos(equipos.data?.contenido ?? []), [equipos.data])
  const equipoSeleccionado = equipos.data?.contenido.find(({ id }) => id === estado.equipoId)
  const escribio = huboEscrituras(estado)

  const actualizar = (cambios: Partial<typeof estado>) => despachar({ tipo: 'actualizar', cambios })
  const marcarErrores = (errores: ErroresAsistente) => actualizar({ errores })

  // ── Paso 1 ────────────────────────────────────────────────────────────────────────────────

  const buscarJugador = async () => {
    const curp = normalizarCurp(estado.curp)

    if (!curpCompleta(curp)) {
      marcarErrores({ ...estado.errores, jugador: { curp: MENSAJE_CURP } })
      return
    }

    setErrorBusqueda(undefined)
    try {
      const encontrada = await buscarPersona.mutateAsync(curp)
      actualizar({ curp, jugador: conMinoria(encontrada), jugadorNuevo: false })
    } catch (error) {
      if (esNoEncontrado(error)) {
        actualizar({ curp, jugador: null, jugadorNuevo: true })
      } else if (error instanceof ApiError && error.estado === 400) {
        marcarErrores({ ...estado.errores, jugador: { curp: error.problema.detail } })
      } else {
        setErrorBusqueda(accionDeError('persona', error).mensaje)
      }
    }
  }

  // ── Paso 2 ────────────────────────────────────────────────────────────────────────────────

  const buscarTutor = async () => {
    const curp = normalizarCurp(estado.tutorCurp)
    const sinFaltante = { ...estado.errores, tutorFaltante: undefined }

    if (!curpCompleta(curp)) {
      marcarErrores({ ...sinFaltante, tutor: { curp: MENSAJE_CURP } })
      return
    }
    if (curp === normalizarCurp(estado.curp)) {
      marcarErrores({ ...sinFaltante, tutor: { curp: 'El tutor debe ser otra persona, no el jugador.' } })
      return
    }

    setBuscandoTutor(true)
    try {
      const encontrada = conMinoria(await buscarPersona.mutateAsync(curp))
      actualizar({
        tutorCurp: curp,
        tutor: encontrada.esMenor ? null : encontrada,
        tutorNuevo: false,
        bloqueo: null,
        errores: encontrada.esMenor
          ? { ...sinFaltante, tutor: {}, tutorFaltante: 'El tutor debe ser mayor de edad. Busca a otra persona.' }
          : { ...sinFaltante, tutor: {} },
      })
    } catch (error) {
      if (esNoEncontrado(error)) {
        actualizar({ tutorCurp: curp, tutor: null, tutorNuevo: true, errores: { ...sinFaltante, tutor: {} } })
      } else {
        marcarErrores({
          ...sinFaltante,
          tutor: error instanceof ApiError && error.estado === 400 ? { curp: error.problema.detail } : {},
          tutorFaltante:
            error instanceof ApiError && error.estado === 400 ? undefined : accionDeError('tutor', error).mensaje,
        })
      }
    } finally {
      setBuscandoTutor(false)
    }
  }

  /** Validaciones del paso 2 antes de escribir; devuelve si se puede seguir. */
  const validarPaso2 = (menor: boolean): boolean => {
    const errores: ErroresAsistente = { jugador: {}, tutor: {} }

    if (!estado.equipoId) {
      errores.equipo = 'Elige el equipo del registro.'
    }
    if (!estado.jugador) {
      errores.jugador = validarDatosPersona(estado.curp, estado.datosJugador, hoy)
    }
    if (menor && !estado.tutelaRegistrada) {
      if (!estado.tutor && !estado.tutorNuevo) {
        errores.tutorFaltante =
          'Busca al tutor por su CURP o regístralo para continuar. Sin tutor no se puede registrar a un menor.'
      } else if (!estado.tutor) {
        errores.tutor = validarDatosPersona(estado.tutorCurp, estado.datosTutor, hoy)
        if (!errores.tutor.fechaNacimiento && esMenorEnFecha(estado.datosTutor.fechaNacimiento, hoy)) {
          errores.tutor.fechaNacimiento = 'El tutor debe ser mayor de edad.'
          errores.tutorFaltante = 'El tutor debe ser mayor de edad. Indica a otra persona como tutor.'
        }
      }
      if (!parentescoFinal(estado.parentesco, estado.parentescoOtro)) {
        errores.parentesco =
          estado.parentesco === 'Otro' ? 'Escribe el parentesco.' : 'Elige el parentesco con el menor.'
      }
    }

    const hayErrores =
      Boolean(errores.equipo || errores.parentesco || errores.tutorFaltante) ||
      Object.keys(errores.jugador).length > 0 ||
      Object.keys(errores.tutor).length > 0

    actualizar({ errores, bloqueo: null })
    return !hayErrores
  }

  /** Muestra en el paso 2 el error de una escritura (persona, tutor o tutela). */
  const mostrarErrorPaso2 = (accion: AccionError) => {
    if (accion.tipo === 'campos') {
      // Si ningún campo señalado está a la vista, se muestra el `detail` como bloqueo general.
      const campos = erroresPersona(accion.contexto === 'tutor' ? 'tutor' : 'persona', accion.errores)
      const errores =
        accion.contexto === 'tutor' ? { ...SIN_ERRORES, tutor: campos } : { ...SIN_ERRORES, jugador: campos }

      actualizar({
        errores,
        bloqueo: Object.keys(campos).length > 0 ? null : { tipo: 'general', contexto: accion.contexto, mensaje: accion.mensaje },
      })
      return
    }
    actualizar({ bloqueo: accion })
  }

  /** Persona del jugador o del tutor: crea si es nueva y reutiliza la existente con `409 RF-010`. */
  const asegurarPersona = async (
    contexto: 'persona' | 'tutor',
  ): Promise<{ persona: PersonaConMinoria; creada: boolean } | null> => {
    const curp = contexto === 'persona' ? estado.curp : estado.tutorCurp
    const datos = contexto === 'persona' ? estado.datosJugador : estado.datosTutor
    const menorPorFecha = esMenorEnFecha(datos.fechaNacimiento, hoy)

    try {
      const detalle = await crearPersona.mutateAsync(peticionPersona(curp, datos))
      if (contexto === 'tutor') {
        return { persona: personaCreada(detalle, false), creada: true }
      }
      // Se confirma la minoría con el backend (DEC-104); sin respuesta, vale la de la fecha.
      try {
        return { persona: conMinoria(await buscarPersona.mutateAsync(detalle.curp)), creada: true }
      } catch {
        return { persona: personaCreada(detalle, menorPorFecha), creada: true }
      }
    } catch (error) {
      const existente = personaExistenteDe(error)

      if (!existente) {
        mostrarErrorPaso2(accionDeError(contexto, error))
        return null
      }
      try {
        const persona = conMinoria(await buscarPersona.mutateAsync(normalizarCurp(curp)))
        return { persona: { ...persona, id: existente }, creada: false }
      } catch (errorBusquedaExistente) {
        mostrarErrorPaso2(accionDeError(contexto, errorBusquedaExistente))
        return null
      }
    }
  }

  const continuarDatos = async () => {
    if (!validarPaso2(jugadorEsMenor(estado, hoy))) {
      return
    }

    setProcesando(true)
    try {
      let jugador = estado.jugador
      if (!jugador) {
        const resultado = await asegurarPersona('persona')
        if (!resultado) {
          return
        }
        jugador = resultado.persona
        actualizar({ jugador, personaCreada: estado.personaCreada || resultado.creada })
      }

      if (!jugador.esMenor) {
        despachar({ tipo: 'ir', paso: 3 })
        return
      }

      // El backend confirmó que es menor aunque la fecha capturada no lo indicara.
      if (!estado.tutor && !estado.tutorNuevo) {
        actualizar({
          errores: {
            ...SIN_ERRORES,
            tutorFaltante: 'Esta persona es menor de edad. Busca o registra a su tutor para continuar.',
          },
        })
        return
      }

      let tutor = estado.tutor
      if (!tutor) {
        const resultado = await asegurarPersona('tutor')
        if (!resultado) {
          return
        }
        if (resultado.persona.esMenor) {
          actualizar({
            errores: { ...SIN_ERRORES, tutorFaltante: 'El tutor debe ser mayor de edad. Busca a otra persona.' },
          })
          return
        }
        tutor = resultado.persona
        actualizar({ tutor, tutorCreado: estado.tutorCreado || resultado.creada })
      }

      if (!estado.tutelaRegistrada) {
        try {
          await registrarTutela.mutateAsync({
            menorId: jugador.id,
            tutorId: tutor.id,
            parentesco: parentescoFinal(estado.parentesco, estado.parentescoOtro) ?? '',
          })
        } catch (error) {
          if (!esTutelaDuplicada(error)) {
            mostrarErrorPaso2(accionDeError('tutela', error))
            return
          }
        }
        actualizar({ tutelaRegistrada: true })
      }

      despachar({ tipo: 'ir', paso: 3 })
    } finally {
      setProcesando(false)
    }
  }

  // ── Paso 3 ────────────────────────────────────────────────────────────────────────────────

  const enviar = async (finalidades: string[]) => {
    const jugador = estado.jugador
    if (!jugador || !estado.equipoId) {
      return
    }

    setProcesando(true)
    try {
      if (!estado.consentimientoId) {
        const avisoVigente = aviso.data
        if (!avisoVigente) {
          return
        }
        try {
          const consentimiento = await registrarConsentimiento.mutateAsync({
            personaId: jugador.id,
            avisoId: avisoVigente.id,
            finalidades,
            ...(jugador.esMenor && estado.tutor ? { otorgadoPorId: estado.tutor.id } : {}),
          })
          actualizar({ consentimientoId: consentimiento.id })
        } catch (error) {
          const accion = accionDeError('consentimiento', error)
          if (accion.tipo === 'recargarAviso') {
            setVersionConsentimiento((version) => version + 1)
            void aviso.refetch()
            actualizar({
              bloqueo: {
                ...accion,
                mensaje: `${accion.mensaje} Recargamos el aviso vigente: léelo y marca de nuevo las finalidades.`,
              },
            })
          } else if (accion.tipo === 'tutor') {
            despachar({ tipo: 'ir', paso: 2, bloqueo: accion })
            actualizar({ tutelaRegistrada: false })
          } else {
            actualizar({ bloqueo: accion })
          }
          return
        }
      }

      try {
        const registro = await registrarAlta.mutateAsync({ personaId: jugador.id, equipoId: estado.equipoId })
        actualizar({ registro })
        despachar({ tipo: 'ir', paso: 4 })
      } catch (error) {
        const accion = accionDeError('alta', error)
        if (accion.tipo === 'tutor') {
          // VAL-025 TUTOR: la tutela ya no está vigente; se vuelve a registrar.
          despachar({ tipo: 'ir', paso: 2, bloqueo: accion })
          actualizar({ tutelaRegistrada: false })
        } else if (accion.tipo === 'consentimiento') {
          setVersionConsentimiento((version) => version + 1)
          actualizar({ consentimientoId: null, bloqueo: accion })
        } else {
          actualizar({ bloqueo: accion.tipo === 'campos' ? { tipo: 'general', contexto: 'alta', mensaje: accion.mensaje } : accion })
        }
      }
    } finally {
      setProcesando(false)
    }
  }

  // ── Salida ────────────────────────────────────────────────────────────────────────────────

  const cancelar = () => {
    if (escribio && !estado.registro) {
      setConfirmarSalida(true)
      return
    }
    void navigate(RUTA_SALIDA_ALTA)
  }

  const enfocar = estado.navego

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
      <header className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Alta de jugador</h1>
          <p className="max-w-prose text-body-md text-text-muted">
            Registra a un jugador por su CURP. El registro queda pendiente hasta que la liga lo
            valide.
          </p>
        </div>
        {!estado.registro && (
          <Button variante="secundario" icono={X} disabled={procesando} onClick={cancelar} className="shrink-0">
            Cancelar
          </Button>
        )}
      </header>

      <IndicadorPasos pasos={PASOS} actual={estado.paso} />

      <div className="rounded-lg border border-border bg-surface p-4 sm:p-6">
        {estado.paso === 1 && (
          <PasoIdentidad
            enfocar={enfocar}
            curp={estado.curp}
            errorCurp={estado.errores.jugador.curp}
            errorGeneral={errorBusqueda}
            buscando={buscarPersona.isPending}
            jugador={estado.jugador}
            jugadorNuevo={estado.jugadorNuevo}
            onCambiarCurp={(valor) => {
              setErrorBusqueda(undefined)
              actualizar({
                curp: normalizarCurp(valor),
                jugador: null,
                jugadorNuevo: false,
                errores: { ...estado.errores, jugador: {} },
              })
            }}
            onBuscar={() => void buscarJugador()}
            onContinuar={() => despachar({ tipo: 'ir', paso: 2 })}
          />
        )}

        {estado.paso === 2 && (
          <PasoDatosTutor
            estado={estado}
            despachar={despachar}
            enfocar={enfocar}
            hoy={hoy}
            grupos={grupos}
            equiposCargando={equipos.isPending}
            equiposError={equipos.error}
            onReintentarEquipos={() => void equipos.refetch()}
            procesando={procesando}
            buscandoTutor={buscandoTutor}
            puedeVolver={!escribio}
            onBuscarTutor={() => void buscarTutor()}
            onAnterior={() => despachar({ tipo: 'ir', paso: 1 })}
            onContinuar={() => void continuarDatos()}
          />
        )}

        {estado.paso === 3 && estado.jugador && (
          <PasoConsentimiento
            key={versionConsentimiento}
            enfocar={enfocar}
            aviso={aviso.data}
            avisoCargando={aviso.isPending}
            avisoError={aviso.error}
            onReintentarAviso={() => void aviso.refetch()}
            jugador={estado.jugador}
            tutor={estado.tutor}
            parentesco={parentescoFinal(estado.parentesco, estado.parentescoOtro)}
            equipo={equipoSeleccionado}
            consentimientoRegistrado={estado.consentimientoId !== null}
            bloqueo={estado.bloqueo}
            enviando={procesando}
            onAnterior={() => despachar({ tipo: 'ir', paso: 2 })}
            onCambiarEquipo={() => despachar({ tipo: 'ir', paso: 2 })}
            onEnviar={(finalidades) => void enviar(finalidades)}
          />
        )}

        {estado.paso === 4 && estado.registro && (
          <PasoDocumentosPendiente
            enfocar={enfocar}
            registroId={estado.registro.id}
            registro={estado.registro}
            rutaSalida={RUTA_SALIDA_ALTA}
            onOtroJugador={() => {
              setVersionConsentimiento((version) => version + 1)
              despachar({ tipo: 'reiniciar' })
            }}
          />
        )}
      </div>

      {confirmarSalida && (
        <ModalSalirAlta
          onQuedarse={() => setConfirmarSalida(false)}
          onSalir={() => void navigate(RUTA_SALIDA_ALTA)}
        />
      )}
    </div>
  )
}
