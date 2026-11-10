package mx.tecnm.morelia.siglim.seguridad;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros y página de {@code GET /api/v1/usuarios} (convenciones de API §4). El orden es fijo, por
 * nombre.
 *
 * <p>Los filtros de rol, ámbito y club usan las asignaciones activas: la tabla de F-12 muestra los
 * roles vigentes de cada cuenta.
 *
 * <p>Refs: H-03.1, RF-094
 *
 * @param q      texto dentro del nombre o del correo; {@code null} o en blanco para no filtrar
 * @param rolId  rol con una asignación activa; {@code null} para todos
 * @param ambito ámbito de alguna asignación activa; {@code null} para todos
 * @param clubId club de alguna asignación activa; {@code null} para todos
 * @param activo estado de la cuenta; {@code null} para todas
 * @param pagina página, desde 0
 * @param tamano filas por página, de 1 a 100
 */
record FiltroUsuarios(String q, UUID rolId, Ambito ambito, UUID clubId, Boolean activo, int pagina, int tamano) {

    private static final int TAMANO_MAXIMO = 100;

    /** @throws ReglaNegocioException 400 {@code VALIDACION} si la página o el tamaño no son válidos */
    void validar() {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
    }

    /** Texto buscado, sin espacios en los extremos, o {@code null} si no se filtra por texto. */
    String texto() {
        return q == null || q.isBlank() ? null : q.strip();
    }

    long desplazamiento() {
        return (long) pagina * tamano;
    }
}
