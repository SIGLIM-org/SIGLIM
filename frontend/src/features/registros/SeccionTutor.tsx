import { Search, ShieldCheck, UserRoundPlus, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { AvisoBloqueo } from '@/features/registros/AvisoBloqueo'
import { FormularioPersona } from '@/features/registros/FormularioPersona'
import { TarjetaPersona } from '@/features/registros/TarjetaPersona'
import {
  MAXIMO_PARENTESCO,
  PARENTESCOS,
  type DatosPersona,
  type ErroresPersona,
  type OpcionParentesco,
} from '@/features/registros/asistenteAlta'
import type { PersonaConMinoria } from '@/features/registros/tipos'

type Props = {
  idBase: string
  hoy: string
  deshabilitado: boolean
  curp: string
  buscando: boolean
  tutor: PersonaConMinoria | null
  tutorNuevo: boolean
  datos: DatosPersona
  errores: ErroresPersona
  parentesco: OpcionParentesco | ''
  parentescoOtro: string
  errorParentesco?: string
  /** Bloqueo de la sección: falta el tutor, es menor, VAL-022 o VAL-025 (DEC-107). */
  bloqueo?: string
  /** Con la tutela ya registrada el tutor no se cambia (DEC-103). */
  puedeCambiarTutor: boolean
  onCambiarCurp: (valor: string) => void
  onBuscar: () => void
  onCambiarTutor: () => void
  onCambiarDato: (campo: keyof DatosPersona, valor: string) => void
  onCambiarParentesco: (valor: OpcionParentesco | '') => void
  onCambiarParentescoOtro: (valor: string) => void
}

/**
 * Tutor de un menor (RF-106, DEC-75, DEC-105): se busca por CURP con la misma consulta del jugador;
 * si no existe se captura con el formulario de DEC-84. Debe ser adulto y no necesita cuenta.
 */
export function SeccionTutor({
  idBase,
  hoy,
  deshabilitado,
  curp,
  buscando,
  tutor,
  tutorNuevo,
  datos,
  errores,
  parentesco,
  parentescoOtro,
  errorParentesco,
  bloqueo,
  puedeCambiarTutor,
  onCambiarCurp,
  onBuscar,
  onCambiarTutor,
  onCambiarDato,
  onCambiarParentesco,
  onCambiarParentescoOtro,
}: Props) {
  const idTitulo = `${idBase}-titulo`

  return (
    <section
      aria-labelledby={idTitulo}
      className="flex flex-col gap-4 rounded-lg border border-border bg-background p-4 sm:p-5"
    >
      <div className="flex items-start gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
          <ShieldCheck aria-hidden="true" className="size-5" />
        </span>
        <div className="min-w-0">
          <h3 id={idTitulo} className="text-headline-md text-text-main">
            Tutor del menor
          </h3>
          <p className="mt-1 max-w-prose text-body-md text-text-muted">
            Un menor de edad necesita un tutor adulto y su consentimiento para registrarse. El tutor
            no necesita cuenta en el sistema.
          </p>
        </div>
      </div>

      {bloqueo && <AvisoBloqueo titulo="Falta resolver el tutor" mensaje={bloqueo} />}

      {tutor ? (
        <TarjetaPersona
          rol="Tutor"
          persona={tutor}
          accion={
            puedeCambiarTutor && (
              <Button variante="secundario" icono={X} disabled={deshabilitado} onClick={onCambiarTutor}>
                Cambiar tutor
              </Button>
            )
          }
        />
      ) : (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start">
          <div className="min-w-0 flex-1 sm:max-w-md">
            <CampoTexto
              id={`${idBase}-curp`}
              etiqueta="CURP del tutor"
              required
              maxLength={18}
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              value={curp}
              error={errores.curp}
              disabled={deshabilitado || buscando}
              onChange={(evento) => onCambiarCurp(evento.target.value)}
              onKeyDown={(evento) => {
                if (evento.key === 'Enter') {
                  evento.preventDefault()
                  onBuscar()
                }
              }}
            />
          </div>
          <Button
            variante="secundario"
            icono={Search}
            cargando={buscando}
            disabled={deshabilitado}
            onClick={onBuscar}
            className="shrink-0 sm:mt-6.5"
          >
            Buscar tutor
          </Button>
        </div>
      )}

      {!tutor && tutorNuevo && (
        <div className="flex flex-col gap-3">
          <p className="flex items-start gap-2 text-body-md text-text-muted">
            <UserRoundPlus aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-primary" />
            No hay nadie con esa CURP. Captura los datos del tutor; se registra una sola vez.
          </p>
          <FormularioPersona
            idBase={`${idBase}-datos`}
            datos={datos}
            errores={errores}
            hoy={hoy}
            conContacto
            deshabilitado={deshabilitado}
            onCambiar={onCambiarDato}
          />
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <CampoSeleccion
          id={`${idBase}-parentesco`}
          etiqueta="Parentesco con el menor"
          required
          value={parentesco}
          error={errorParentesco && parentesco !== 'Otro' ? errorParentesco : undefined}
          disabled={deshabilitado || !puedeCambiarTutor}
          onChange={(evento) => onCambiarParentesco(evento.target.value as OpcionParentesco | '')}
        >
          <option value="">Elige el parentesco</option>
          {PARENTESCOS.map((opcion) => (
            <option key={opcion} value={opcion}>
              {opcion}
            </option>
          ))}
        </CampoSeleccion>
        {parentesco === 'Otro' && (
          <CampoTexto
            id={`${idBase}-parentesco-otro`}
            etiqueta="¿Cuál parentesco?"
            required
            maxLength={MAXIMO_PARENTESCO}
            value={parentescoOtro}
            error={errorParentesco}
            ayuda={`Hasta ${MAXIMO_PARENTESCO} caracteres, p. ej. «Abuela».`}
            disabled={deshabilitado || !puedeCambiarTutor}
            onChange={(evento) => onCambiarParentescoOtro(evento.target.value)}
          />
        )}
      </div>
    </section>
  )
}
