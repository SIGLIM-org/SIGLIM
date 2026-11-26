package mx.tecnm.morelia.siglim.notificaciones;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aviso del usuario de la sesión (convenciones de API §6.14).
 *
 * <p>Refs: H-25.1, RF-142, UI-030
 *
 * @param id       identificador del aviso
 * @param evento   evento que lo originó ({@link EventoAviso})
 * @param titulo   título corto que muestra la campana
 * @param cuerpo   texto completo, sin datos personales (RNF-012)
 * @param enlace   ruta interna del frontend a la pantalla del trámite
 * @param creadaEn momento en que se generó
 * @param leidaEn  momento de la primera lectura; {@code null} mientras no se lea
 */
public record NotificacionDto(UUID id, String evento, String titulo, String cuerpo, String enlace,
                              OffsetDateTime creadaEn, OffsetDateTime leidaEn) {
}
