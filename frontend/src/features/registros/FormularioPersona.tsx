import { CampoTexto } from '@/components/ui/CampoTexto'
import type { DatosPersona, ErroresPersona } from '@/features/registros/asistenteAlta'

type Props = {
  idBase: string
  datos: DatosPersona
  errores: ErroresPersona
  onCambiar: (campo: keyof DatosPersona, valor: string) => void
  /** Día de hoy en la zona de negocio: la fecha de nacimiento no puede ser futura (DEC-84). */
  hoy: string
  deshabilitado?: boolean
  /** Correo y teléfono opcionales del tutor (DEC-62, DEC-75, DEC-105). */
  conContacto?: boolean
}

/**
 * Datos de una persona nueva con el formulario de DEC-84. La CURP ya se buscó y no se edita aquí:
 * la identidad es única por CURP (RF-010).
 */
export function FormularioPersona({
  idBase,
  datos,
  errores,
  onCambiar,
  hoy,
  deshabilitado = false,
  conContacto = false,
}: Props) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <CampoTexto
        id={`${idBase}-nombre`}
        etiqueta="Nombre(s)"
        required
        maxLength={100}
        autoComplete="off"
        value={datos.nombre}
        error={errores.nombre}
        disabled={deshabilitado}
        onChange={(evento) => onCambiar('nombre', evento.target.value)}
      />
      <CampoTexto
        id={`${idBase}-paterno`}
        etiqueta="Apellido paterno"
        required
        maxLength={100}
        autoComplete="off"
        value={datos.apellidoPaterno}
        error={errores.apellidoPaterno}
        disabled={deshabilitado}
        onChange={(evento) => onCambiar('apellidoPaterno', evento.target.value)}
      />
      <CampoTexto
        id={`${idBase}-materno`}
        etiqueta="Apellido materno"
        maxLength={100}
        autoComplete="off"
        value={datos.apellidoMaterno}
        error={errores.apellidoMaterno}
        ayuda="Opcional."
        disabled={deshabilitado}
        onChange={(evento) => onCambiar('apellidoMaterno', evento.target.value)}
      />
      <CampoTexto
        id={`${idBase}-nacimiento`}
        etiqueta="Fecha de nacimiento"
        type="date"
        required
        max={hoy}
        value={datos.fechaNacimiento}
        error={errores.fechaNacimiento}
        disabled={deshabilitado}
        onChange={(evento) => onCambiar('fechaNacimiento', evento.target.value)}
      />
      {conContacto && (
        <>
          <CampoTexto
            id={`${idBase}-correo`}
            etiqueta="Correo electrónico"
            type="email"
            maxLength={254}
            autoComplete="off"
            value={datos.correo}
            error={errores.correo}
            ayuda="Opcional. Servirá para invitarlo después a consultar la información del menor."
            disabled={deshabilitado}
            onChange={(evento) => onCambiar('correo', evento.target.value)}
          />
          <CampoTexto
            id={`${idBase}-telefono`}
            etiqueta="Teléfono"
            type="tel"
            maxLength={20}
            autoComplete="off"
            value={datos.telefono}
            error={errores.telefono}
            ayuda="Opcional."
            disabled={deshabilitado}
            onChange={(evento) => onCambiar('telefono', evento.target.value)}
          />
        </>
      )}
    </div>
  )
}
