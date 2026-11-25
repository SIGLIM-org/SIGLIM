package mx.tecnm.morelia.siglim.programacion;

import java.util.Objects;
import java.util.UUID;

/**
 * Un renglón del calendario de un grupo: quién juega contra quién en una fecha y vuelta, o quién
 * descansa. Los nombres coinciden con las columnas de {@code siglim.emparejamientos}.
 *
 * <p>{@code equipoVisitanteId == null} significa descanso: el equipo que descansa va en
 * {@code equipoLocalId} (RF-037), igual que en la tabla.
 *
 * <p>Refs: H-32.1, RF-037, DEC-174
 *
 * @param vuelta            vuelta del calendario, desde 1
 * @param numeroFecha       número de fecha de la temporada, desde 1
 * @param equipoLocalId     equipo local, o el que descansa
 * @param equipoVisitanteId equipo visitante; {@code null} si es descanso
 */
record Emparejamiento(int vuelta, int numeroFecha, UUID equipoLocalId, UUID equipoVisitanteId) {

    Emparejamiento {
        if (vuelta <= 0) {
            throw new IllegalArgumentException("La vuelta debe ser mayor que cero.");
        }
        if (numeroFecha <= 0) {
            throw new IllegalArgumentException("El número de fecha debe ser mayor que cero.");
        }
        Objects.requireNonNull(equipoLocalId, "El equipo local es obligatorio.");
        if (equipoLocalId.equals(equipoVisitanteId)) {
            throw new IllegalArgumentException("Un equipo no puede jugar contra sí mismo.");
        }
    }

    /** {@code true} si el renglón es el descanso de {@code equipoLocalId} (RF-037). */
    boolean esDescanso() {
        return equipoVisitanteId == null;
    }
}
