package mx.tecnm.morelia.siglim.programacion;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.CanchaAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.PeriodoCancha;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.TipoPeriodo;

/**
 * Ventanas de juego de una cancha en un día y aritmética de intervalos semiabiertos
 * {@code [inicio, fin)}. Dominio puro: sin Spring ni base de datos.
 *
 * <p><b>DEC-176.</b> Si la cancha tiene periodos {@code DISPONIBLE} que tocan el día, la ventana son
 * esos periodos (recortados al día); si no, el horario de juego por defecto. {@code BLOQUEO} y
 * {@code MANTENIMIENTO} siempre se restan. Una cancha inactiva no tiene ventanas (VAL-005).
 *
 * <p>Refs: H-33.1, RF-029, RF-032, VAL-005, DEC-176, ADR-0015
 */
final class VentanasDeCancha {

    private VentanasDeCancha() {
    }

    /**
     * @return ventanas del día ordenadas y sin traslapes; vacía si la cancha está inactiva
     */
    static List<Intervalo> ventanas(CanchaAsignable cancha, LocalDate dia, LocalTime horaInicio,
            LocalTime horaFin, ZoneId zona) {
        if (!cancha.activa()) {
            return List.of();
        }
        Intervalo delDia = new Intervalo(dia.atStartOfDay(zona).toInstant(),
                dia.plusDays(1).atStartOfDay(zona).toInstant());
        List<Intervalo> disponibles = recortar(periodos(cancha, TipoPeriodo.DISPONIBLE), delDia);
        List<Intervalo> base = disponibles.isEmpty()
                ? List.of(new Intervalo(dia.atTime(horaInicio).atZone(zona).toInstant(),
                        dia.atTime(horaFin).atZone(zona).toInstant()))
                : unir(disponibles);
        List<Intervalo> restricciones = new ArrayList<>(periodos(cancha, TipoPeriodo.BLOQUEO));
        restricciones.addAll(periodos(cancha, TipoPeriodo.MANTENIMIENTO));
        return restar(base, restricciones);
    }

    /**
     * Resta {@code quitar} de {@code base}.
     *
     * @return intervalos ordenados que quedan, sin vacíos
     */
    static List<Intervalo> restar(List<Intervalo> base, List<Intervalo> quitar) {
        List<Intervalo> quedan = unir(base);
        for (Intervalo q : unir(quitar)) {
            List<Intervalo> siguiente = new ArrayList<>();
            for (Intervalo v : quedan) {
                if (!v.traslapa(q)) {
                    siguiente.add(v);
                    continue;
                }
                if (v.inicio().isBefore(q.inicio())) {
                    siguiente.add(new Intervalo(v.inicio(), q.inicio()));
                }
                if (q.fin().isBefore(v.fin())) {
                    siguiente.add(new Intervalo(q.fin(), v.fin()));
                }
            }
            quedan = siguiente;
        }
        return List.copyOf(quedan);
    }

    /** Cuántos bloques de {@code minutos} caben, sin partirlos, en los intervalos libres. */
    static int franjas(List<Intervalo> libres, int minutos) {
        long total = 0;
        for (Intervalo libre : libres) {
            total += libre.duracion().toMinutes() / minutos;
        }
        return Math.toIntExact(total);
    }

    /** Ordena y fusiona intervalos que se traslapan o se tocan. */
    static List<Intervalo> unir(List<Intervalo> intervalos) {
        List<Intervalo> ordenados = new ArrayList<>(intervalos);
        ordenados.sort(Comparator.comparing(Intervalo::inicio).thenComparing(Intervalo::fin));
        List<Intervalo> unidos = new ArrayList<>();
        for (Intervalo i : ordenados) {
            if (!unidos.isEmpty() && !i.inicio().isAfter(unidos.getLast().fin())) {
                Intervalo ultimo = unidos.removeLast();
                unidos.add(new Intervalo(ultimo.inicio(),
                        i.fin().isAfter(ultimo.fin()) ? i.fin() : ultimo.fin()));
            } else {
                unidos.add(i);
            }
        }
        return unidos;
    }

    private static List<Intervalo> periodos(CanchaAsignable cancha, TipoPeriodo tipo) {
        return cancha.periodos().stream()
                .filter(periodo -> periodo.tipo() == tipo)
                .map(VentanasDeCancha::intervalo)
                .toList();
    }

    private static Intervalo intervalo(PeriodoCancha periodo) {
        return new Intervalo(periodo.desde().toInstant(), periodo.hasta().toInstant());
    }

    private static List<Intervalo> recortar(List<Intervalo> intervalos, Intervalo limite) {
        List<Intervalo> recortados = new ArrayList<>();
        for (Intervalo i : intervalos) {
            if (i.traslapa(limite)) {
                recortados.add(new Intervalo(
                        i.inicio().isAfter(limite.inicio()) ? i.inicio() : limite.inicio(),
                        i.fin().isBefore(limite.fin()) ? i.fin() : limite.fin()));
            }
        }
        return recortados;
    }

    /** Intervalo semiabierto {@code [inicio, fin)}, con {@code fin} posterior a {@code inicio}. */
    record Intervalo(Instant inicio, Instant fin) {

        Intervalo {
            if (!fin.isAfter(inicio)) {
                throw new IllegalArgumentException("El fin del intervalo debe ser posterior a su inicio.");
            }
        }

        /** Dos intervalos semiabiertos se traslapan si cada uno empieza antes de que el otro termine. */
        boolean traslapa(Intervalo otro) {
            return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
        }

        /** {@code true} si {@code otro} queda completo dentro de este. */
        boolean contiene(Intervalo otro) {
            return !otro.inicio.isBefore(inicio) && !otro.fin.isAfter(fin);
        }

        Duration duracion() {
            return Duration.between(inicio, fin);
        }
    }
}
