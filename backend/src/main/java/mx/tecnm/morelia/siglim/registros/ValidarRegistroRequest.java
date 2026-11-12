package mx.tecnm.morelia.siglim.registros;

/**
 * Cuerpo de {@code POST /api/v1/registros-jugador/{id}/validacion} (RF-109, DEC-97, DEC-98).
 *
 * <p>Sin anotaciones de validación: los obligatorios y la longitud de las observaciones los valida
 * {@link RegistroJugadorService} después del permiso (DEC-97). Un {@code decision} con un valor que no
 * es del enum no se puede leer y responde {@code 400 VALIDACION} antes de llegar al servicio.
 *
 * <p>Refs: H-10.2, RF-109, DEC-97, DEC-98
 *
 * @param decision      activar o rechazar el registro
 * @param observaciones obligatorias al rechazar (10 a 500 caracteres); opcionales al aprobar (hasta 500)
 */
public record ValidarRegistroRequest(DecisionRegistro decision, String observaciones) {
}
