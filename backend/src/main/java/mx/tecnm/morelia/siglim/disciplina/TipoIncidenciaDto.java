package mx.tecnm.morelia.siglim.disciplina;

/**
 * Tipo de incidencia del catálogo de la Comisión Disciplinaria (F-32, RF-128). La tabla usa
 * {@code bigint} como identificador (convenciones de API §1); {@code esDemo} marca el valor de
 * demostración [DEMO], que la interfaz rotula «no oficial» (H-26 criterio 3).
 *
 * @param id             identificador del catálogo
 * @param codigo         código único legible por el sistema
 * @param nombre         nombre para la persona
 * @param abreExpediente si la incidencia abre expediente al aprobarse la cÃ³dula (DEC-22)
 * @param activo         si el tipo se sigue ofreciendo al capturar una cÃ³dula
 * @param esDemo         valor de demostración [DEMO]: configurable y rotulado «no oficial»
 */
public record TipoIncidenciaDto(long id, String codigo, String nombre, boolean abreExpediente,
                                boolean activo, boolean esDemo) {
}
