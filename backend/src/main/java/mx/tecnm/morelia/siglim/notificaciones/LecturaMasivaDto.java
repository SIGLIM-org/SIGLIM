package mx.tecnm.morelia.siglim.notificaciones;

/**
 * Respuesta de {@code POST /notificaciones/lectura}.
 *
 * <p>Refs: H-25.1, RF-142
 *
 * @param marcadas avisos que estaban sin leer y quedaron leídos con esta petición
 */
public record LecturaMasivaDto(int marcadas) {
}
