package mx.tecnm.morelia.siglim.organizacion;

import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Motivo obligatorio de las transiciones de estado de este módulo (RN-021): baja de equipo y cierre
 * de club por cambio de nombre.
 *
 * <p>Refs: H-06.1, B-04, RN-021
 */
final class MotivoTransicion {

    /** [PROPUESTA] Convenciones de API §5: mínimo del motivo de una transición (RN-021). */
    static final int MINIMO = 10;

    private MotivoTransicion() {
    }

    /**
     * Recorta el motivo y exige al menos {@link #MINIMO} caracteres.
     *
     * @param accion lo que el motivo justifica, para el mensaje («dar de baja el equipo»)
     * @throws ReglaNegocioException 400 {@code RN-021} si falta o es muy corto
     */
    static String exigir(String motivo, String accion) {
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < MINIMO) {
            throw ReglaNegocioException.invalida("RN-021",
                    "Escribe un motivo de al menos " + MINIMO + " caracteres para " + accion + ".");
        }
        return texto;
    }
}
