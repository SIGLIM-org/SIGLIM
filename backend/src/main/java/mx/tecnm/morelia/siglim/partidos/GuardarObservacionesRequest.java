package mx.tecnm.morelia.siglim.partidos;

/**
 * Cuerpo de {@code PUT /cedulas/{id}/observaciones} (RF-025): reemplaza las observaciones del partido;
 * vacías o {@code null} las quitan.
 *
 * <p>Refs: H-16.2, RF-025
 *
 * @param observaciones hasta 2000 caracteres
 */
public record GuardarObservacionesRequest(String observaciones) {
}
