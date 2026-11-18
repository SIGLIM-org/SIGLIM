import type {
  DatosOferta,
  DatosPrestamo,
  DestinatarioPrestamo,
  TipoSolicitudCancha,
} from '@/features/canchas/tramites/tipos'

/** Un campo de texto de los formatos del cliente (R8). */
export type CampoFormato = {
  nombre: string
  etiqueta: string
  tipo?: 'text' | 'date' | 'time' | 'tel' | 'email' | 'number'
  obligatorio?: boolean
  maximo: number
  ayuda?: string
  /** Ocupa las dos columnas en escritorio. */
  ancho?: boolean
  multilinea?: boolean
}

/** Campos del Formato 1 en el orden de la carta. */
export const camposPrestamo: readonly CampoFormato[] = [
  { nombre: 'destinatarioNombre', etiqueta: 'Destinatario', obligatorio: true, maximo: 150, ayuda: 'Propietario o responsable de la cancha.' },
  { nombre: 'destinatarioCargo', etiqueta: 'Cargo o institución del destinatario', obligatorio: true, maximo: 150 },
  { nombre: 'solicitanteNombre', etiqueta: 'Nombre de quien solicita', obligatorio: true, maximo: 150 },
  { nombre: 'solicitanteCargo', etiqueta: 'Cargo en el club', obligatorio: true, maximo: 100 },
  { nombre: 'instalacion', etiqueta: 'Instalación solicitada', obligatorio: true, maximo: 200, ayuda: 'Nombre o domicilio de la cancha.' },
  { nombre: 'finalidad', etiqueta: 'Finalidad', obligatorio: true, maximo: 200, ayuda: 'Partido oficial, jornada de liga, entrenamiento o evento.' },
  { nombre: 'fechaUtilizacion', etiqueta: 'Fecha de utilización', tipo: 'date', obligatorio: true, maximo: 10 },
  { nombre: 'horaInicio', etiqueta: 'Hora de inicio', tipo: 'time', obligatorio: true, maximo: 5 },
  { nombre: 'horaTermino', etiqueta: 'Hora de término', tipo: 'time', obligatorio: true, maximo: 5 },
  { nombre: 'asistentesAproximados', etiqueta: 'Asistentes aproximados', tipo: 'number', maximo: 6 },
  { nombre: 'actividad', etiqueta: 'Actividad deportiva', obligatorio: true, maximo: 500, ancho: true, multilinea: true },
  { nombre: 'equiposParticipantes', etiqueta: 'Equipos participantes', maximo: 300, ancho: true },
  { nombre: 'responsableActividad', etiqueta: 'Responsable de la actividad', obligatorio: true, maximo: 150 },
  { nombre: 'telefonoContacto', etiqueta: 'Teléfono de contacto', tipo: 'tel', obligatorio: true, maximo: 20 },
  { nombre: 'correoContacto', etiqueta: 'Correo electrónico', tipo: 'email', maximo: 150 },
  { nombre: 'lugar', etiqueta: 'Lugar de emisión', obligatorio: true, maximo: 120, ayuda: 'Por ejemplo: Morelia, Michoacán.' },
]

/** Campos del Formato 2 en el orden de la carta. */
export const camposOferta: readonly CampoFormato[] = [
  { nombre: 'oferenteNombre', etiqueta: 'Nombre de quien ofrece', obligatorio: true, maximo: 150 },
  { nombre: 'oferenteCaracter', etiqueta: 'Carácter', obligatorio: true, maximo: 100, ayuda: 'Propietario, administrador o representante.' },
  { nombre: 'institucion', etiqueta: 'Institución', maximo: 150 },
  { nombre: 'nombreCancha', etiqueta: 'Nombre de la cancha', obligatorio: true, maximo: 120 },
  { nombre: 'ubicacion', etiqueta: 'Ubicación', obligatorio: true, maximo: 300, ancho: true, ayuda: 'Domicilio completo.' },
  { nombre: 'tipoSuperficie', etiqueta: 'Tipo de superficie', obligatorio: true, maximo: 60, ayuda: 'Pasto natural, sintético, tierra u otra.' },
  { nombre: 'capacidadAproximada', etiqueta: 'Capacidad aproximada', tipo: 'number', maximo: 6 },
  { nombre: 'disponibilidad', etiqueta: 'Disponibilidad', obligatorio: true, maximo: 300, ancho: true, ayuda: 'Días y horarios disponibles.' },
  { nombre: 'serviciosDisponibles', etiqueta: 'Servicios disponibles', maximo: 300, ancho: true, ayuda: 'Sanitarios, vestidores, iluminación, estacionamiento u otros.' },
  { nombre: 'costoUso', etiqueta: 'Costo por uso', obligatorio: true, maximo: 150, ayuda: 'Importe por partido, hora o jornada, o «gratuito».' },
  { nombre: 'condicionesPago', etiqueta: 'Condiciones de pago', maximo: 300 },
  { nombre: 'personaResponsable', etiqueta: 'Persona responsable', obligatorio: true, maximo: 150 },
  { nombre: 'telefonoContacto', etiqueta: 'Teléfono de contacto', tipo: 'tel', obligatorio: true, maximo: 20 },
  { nombre: 'correoContacto', etiqueta: 'Correo electrónico', tipo: 'email', maximo: 150 },
  { nombre: 'lugar', etiqueta: 'Lugar de emisión', obligatorio: true, maximo: 120, ayuda: 'Por ejemplo: Morelia, Michoacán.' },
]

export function camposDe(tipo: TipoSolicitudCancha): readonly CampoFormato[] {
  return tipo === 'OFERTA' ? camposOferta : camposPrestamo
}

export type Valores = Record<string, string>

const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** Errores locales de los campos del formato; el backend vuelve a validar (R8). */
export function validarCampos(tipo: TipoSolicitudCancha, valores: Valores): Record<string, string> {
  const errores: Record<string, string> = {}

  for (const campo of camposDe(tipo)) {
    const valor = (valores[campo.nombre] ?? '').trim()

    if (campo.obligatorio && !valor) {
      errores[campo.nombre] = `Indica ${campo.etiqueta.toLowerCase()}.`
    } else if (valor.length > campo.maximo) {
      errores[campo.nombre] = `Admite hasta ${campo.maximo} caracteres.`
    } else if (campo.tipo === 'email' && valor && !CORREO.test(valor)) {
      errores[campo.nombre] = 'Escribe un correo electrónico válido.'
    } else if (campo.tipo === 'number' && valor && !/^\d+$/.test(valor)) {
      errores[campo.nombre] = 'Escribe un número entero sin signo.'
    }
  }

  if (tipo !== 'OFERTA' && !errores.horaTermino && valores.horaInicio && valores.horaTermino) {
    if (valores.horaTermino <= valores.horaInicio) {
      errores.horaTermino = 'La hora de término debe ser posterior a la de inicio.'
    }
  }

  return errores
}

function texto(valores: Valores, nombre: string): string | undefined {
  const valor = (valores[nombre] ?? '').trim()

  return valor ? valor : undefined
}

function entero(valores: Valores, nombre: string): number | undefined {
  const valor = texto(valores, nombre)

  return valor === undefined ? undefined : Number(valor)
}

/** Bloque `prestamo` del API; los opcionales vacíos se omiten. */
export function aPrestamo(valores: Valores, dirigidaA: DestinatarioPrestamo): DatosPrestamo {
  return {
    lugar: texto(valores, 'lugar') ?? '',
    dirigidaA,
    destinatarioNombre: texto(valores, 'destinatarioNombre') ?? '',
    destinatarioCargo: texto(valores, 'destinatarioCargo') ?? '',
    solicitanteNombre: texto(valores, 'solicitanteNombre') ?? '',
    solicitanteCargo: texto(valores, 'solicitanteCargo') ?? '',
    instalacion: texto(valores, 'instalacion') ?? '',
    finalidad: texto(valores, 'finalidad') ?? '',
    fechaUtilizacion: texto(valores, 'fechaUtilizacion') ?? '',
    horaInicio: texto(valores, 'horaInicio') ?? '',
    horaTermino: texto(valores, 'horaTermino') ?? '',
    actividad: texto(valores, 'actividad') ?? '',
    equiposParticipantes: texto(valores, 'equiposParticipantes'),
    asistentesAproximados: entero(valores, 'asistentesAproximados'),
    responsableActividad: texto(valores, 'responsableActividad') ?? '',
    telefonoContacto: texto(valores, 'telefonoContacto') ?? '',
    correoContacto: texto(valores, 'correoContacto'),
  }
}

/** Bloque `oferta` del API; los opcionales vacíos se omiten. */
export function aOferta(valores: Valores): DatosOferta {
  return {
    lugar: texto(valores, 'lugar') ?? '',
    oferenteNombre: texto(valores, 'oferenteNombre') ?? '',
    oferenteCaracter: texto(valores, 'oferenteCaracter') ?? '',
    institucion: texto(valores, 'institucion'),
    nombreCancha: texto(valores, 'nombreCancha') ?? '',
    ubicacion: texto(valores, 'ubicacion') ?? '',
    tipoSuperficie: texto(valores, 'tipoSuperficie') ?? '',
    disponibilidad: texto(valores, 'disponibilidad') ?? '',
    capacidadAproximada: entero(valores, 'capacidadAproximada'),
    serviciosDisponibles: texto(valores, 'serviciosDisponibles'),
    costoUso: texto(valores, 'costoUso') ?? '',
    condicionesPago: texto(valores, 'condicionesPago'),
    personaResponsable: texto(valores, 'personaResponsable') ?? '',
    telefonoContacto: texto(valores, 'telefonoContacto') ?? '',
    correoContacto: texto(valores, 'correoContacto'),
  }
}
