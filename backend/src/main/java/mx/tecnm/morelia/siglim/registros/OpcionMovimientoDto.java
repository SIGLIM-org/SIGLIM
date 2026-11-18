package mx.tecnm.morelia.siglim.registros;

/**
 * Movimiento entre temporadas que el asistente de alta (F-18) puede ofrecer para una persona y un
 * equipo de destino (B-06.2, DEC-147): {@code tipo} es {@code RENOVACION}, {@code TRANSFERENCIA} o
 * {@code null} si no aplica (sin registro anterior, o ya registrada en la temporada destino).
 * {@code permitido} es falso solo en una transferencia que la temporada no admite (RF-111), y
 * {@code motivo} lo explica. {@code temporadaAnterior} es el nombre de la temporada del registro
 * anterior; nunca se exponen el club, el equipo, el estado ni los ids del registro anterior
 * (DEC-88, DEC-90).
 *
 * <p>Refs: B-06.2, RF-015, RF-016, RF-111, DEC-147
 */
public record OpcionMovimientoDto(String tipo, boolean permitido, String temporadaAnterior, String motivo) {

    /** Sin movimiento que ofrecer: el asistente sigue con el alta normal. */
    static OpcionMovimientoDto ninguna() {
        return new OpcionMovimientoDto(null, false, null, null);
    }
}
