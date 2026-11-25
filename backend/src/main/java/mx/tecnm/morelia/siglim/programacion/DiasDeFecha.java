package mx.tecnm.morelia.siglim.programacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Días de juego por defecto de una fecha del calendario: la fecha {@code k} es el sábado de la
 * semana {@code k} contada desde el primer sábado en o después del inicio que da el Programador, y el
 * domingo siguiente (DEC-176, DEC-186). Si el inicio cae en domingo, la fecha 1 empieza el sábado
 * siguiente, no el día anterior. Dominio puro: sin Spring ni base de datos.
 *
 * <p>Refs: H-32, DEC-176, DEC-186
 */
final class DiasDeFecha {

    private DiasDeFecha() {
    }

    /**
     * @param inicio      fecha de inicio que da el Programador
     * @param numeroFecha número de fecha, desde 1
     * @return {@code [sábado, domingo]} de esa fecha
     * @throws IllegalArgumentException si {@code inicio} es nulo o {@code numeroFecha < 1}
     */
    static List<LocalDate> dias(LocalDate inicio, int numeroFecha) {
        if (inicio == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        }
        if (numeroFecha < 1) {
            throw new IllegalArgumentException("El número de fecha debe ser 1 o mayor; se recibió " + numeroFecha + ".");
        }
        LocalDate sabado = inicio.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                .plusWeeks(numeroFecha - 1L);
        return List.of(sabado, sabado.plusDays(1));
    }
}
