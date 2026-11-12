package mx.tecnm.morelia.siglim.registros;

/**
 * Cuerpo de {@code POST /api/v1/documentos/{id}/validacion} (RF-109, DEC-96).
 *
 * <p>Sin anotaciones de validación: los obligatorios y la longitud de las observaciones los valida
 * {@link DocumentoRegistroService} después del permiso (DEC-91). Un {@code decision} con un valor que
 * no es del enum no se puede leer y responde {@code 400 VALIDACION} antes de llegar al servicio.
 *
 * <p>Refs: H-10.1, RF-109, DEC-91, DEC-96
 *
 * @param decision      aprobar o rechazar el documento
 * @param observaciones obligatorias al rechazar (10 a 500 caracteres); opcionales al aprobar
 */
public record ValidarDocumentoRequest(DecisionDocumento decision, String observaciones) {
}
