/**
 * Tipos del contrato de avisos (convenciones de API §6.14, H-25.1).
 */

/** Eventos que hoy generan un aviso; el backend puede agregar otros (RF-142). */
export type EventoAviso = 'CEDULA_PENDIENTE' | 'COBRO_VENCIDO' | 'PROGRAMACION_PUBLICADA'

/** Aviso del usuario de la sesión (`NotificacionDto`). */
export type Aviso = {
  id: string
  /** Un {@link EventoAviso} o uno nuevo que la interfaz aún no conoce. */
  evento: string
  titulo: string
  cuerpo: string | null
  /** Ruta interna del frontend a la pantalla del trámite. */
  enlace: string | null
  creadaEn: string
  /** `null` mientras no se lea. */
  leidaEn: string | null
}

/** Respuesta de `POST /notificaciones/lectura`. */
export type LecturaMasiva = {
  marcadas: number
}

/** Pestañas de F-09. */
export type PestanaAvisos = 'NO_LEIDAS' | 'TODAS'
