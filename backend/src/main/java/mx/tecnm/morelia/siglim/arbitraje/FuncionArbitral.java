package mx.tecnm.morelia.siglim.arbitraje;

/**
 * Función del árbitro en un partido, igual que el enum {@code siglim.funcion_arbitral}.
 *
 * <p>Un partido tiene a lo más un {@code CENTRAL} vigente ({@code ux_designaciones_central}): es quien
 * aprueba la cédula (RF-123). Los {@code ASISTENTE} son a lo más dos <b>[PROPUESTA]</b> (DEC-201).
 *
 * <p>Refs: H-14.1, RF-023, RF-123, DEC-201
 */
public enum FuncionArbitral {

    CENTRAL("central"),
    ASISTENTE("asistente");

    private final String texto;

    FuncionArbitral(String texto) {
        this.texto = texto;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }
}
