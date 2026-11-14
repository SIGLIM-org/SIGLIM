package mx.tecnm.morelia.siglim.registros;

/**
 * Cuerpo de {@code POST /api/v1/registros-jugador/{id}/baja} (RF-014, RN-021, DEC-121).
 *
 * <p>Sin anotaciones de validación: el motivo lo valida {@link RegistroJugadorService} después del
 * permiso y antes de buscar el registro (DEC-121). Va solo al historial del registro
 * ({@code registro_estado_historial}) y a la auditoría.
 *
 * <p>Refs: H-23.1, RF-014, RN-021, DEC-121
 *
 * @param motivo obligatorio, de 10 a 500 caracteres sin contar los espacios de los extremos
 */
public record BajaRegistroRequest(String motivo) {
}
