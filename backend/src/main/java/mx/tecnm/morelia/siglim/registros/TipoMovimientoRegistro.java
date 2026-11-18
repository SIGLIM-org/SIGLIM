package mx.tecnm.morelia.siglim.registros;

/**
 * Tipo de movimiento de un registro entre temporadas: renovación en el mismo club (RF-015) o
 * transferencia a otro club (RF-016), con su código del ERS y la acción de auditoría (DEC-139).
 *
 * <p>Refs: B-06, RF-015, RF-016, RF-093, DEC-139
 */
enum TipoMovimientoRegistro {

    /** El registro anterior es del mismo club: se conserva al jugador y se reutilizan sus documentos. */
    RENOVACION("RF-015", "RENOVAR_REGISTRO", "renueva"),

    /** El registro anterior es de otro club y la temporada destino permite el cambio de club. */
    TRANSFERENCIA("RF-016", "TRANSFERIR_REGISTRO", "transfiere");

    private final String codigo;
    private final String accion;
    private final String verbo;

    TipoMovimientoRegistro(String codigo, String accion, String verbo) {
        this.codigo = codigo;
        this.accion = accion;
        this.verbo = verbo;
    }

    String codigo() {
        return codigo;
    }

    String accion() {
        return accion;
    }

    String verbo() {
        return verbo;
    }
}
