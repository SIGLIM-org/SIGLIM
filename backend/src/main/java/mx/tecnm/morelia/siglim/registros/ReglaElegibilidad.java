package mx.tecnm.morelia.siglim.registros;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Elegibilidad de un registro para jugar (RF-019, RF-020): {@code elegible} si no hay ninguna causa
 * que lo impida. Es la única fuente de esta regla: la usa la consulta propia (H-24.1) y la reutilizará
 * la alineación de la cédula (H-16.1).
 *
 * <p>Sin Spring ni base de datos: recibe el estado del registro y las sanciones de la persona.
 *
 * <ul>
 *   <li><b>RF-019.</b> Un registro que no está {@code ACTIVO} no juega; la causa explica su estado
 *       ({@code PENDIENTE_VALIDACION}, {@code RECHAZADO}, {@code BAJA} o {@code SUSPENDIDO}).</li>
 *   <li><b>RF-020.</b> Cada sanción {@code VIGENTE} de tipo {@code SUSPENSION_PARTIDOS} o {@code BAJA}
 *       es una causa, con el artículo y la vigencia (partidos restantes y fecha de término) en el
 *       mensaje. Cuenta el estado {@code VIGENTE} de la sanción, sin recalcular su vigencia: cerrarla
 *       al cumplirse es tarea de M-09 (DEC-142).</li>
 *   <li><b>[PROPUESTA]</b> {@code MULTA}, {@code PERDIDA_PUNTOS} y {@code PERDIDA_PARTIDO} no impiden
 *       jugar: la multa es un cobro y las pérdidas afectan al equipo en la tabla, no al jugador. El ERS
 *       no lo dice; queda a validar con la Liga.</li>
 * </ul>
 *
 * <p>Las causas van en ese orden: primero la del registro y después una por sanción, en el orden
 * recibido.
 *
 * <p>Refs: H-24.1, RF-019, RF-020, RF-144, DEC-142
 */
public final class ReglaElegibilidad {

    /** Causa por el estado del registro. */
    public static final String REGISTRO_NO_ACTIVO = "RF-019";
    /** Causa por una sanción vigente. */
    public static final String SANCION_VIGENTE = "RF-020";

    private static final String VIGENTE = "VIGENTE";
    private static final String SUSPENSION_PARTIDOS = "SUSPENSION_PARTIDOS";
    private static final String BAJA = "BAJA";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ReglaElegibilidad() {
    }

    /**
     * @param estado    estado del registro
     * @param sanciones sanciones de sujeto {@code JUGADOR} de la persona, de cualquier estado; solo
     *                  cuentan las {@code VIGENTE} de los tipos que impiden jugar
     * @return si es elegible y las causas que lo impiden
     */
    public static Elegibilidad evaluar(EstadoRegistro estado, List<SancionPropia> sanciones) {
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(sanciones, "sanciones");
        List<Causa> causas = new ArrayList<>();
        if (estado != EstadoRegistro.ACTIVO) {
            causas.add(new Causa(REGISTRO_NO_ACTIVO, porEstado(estado)));
        }
        for (SancionPropia sancion : sanciones) {
            if (impideJugar(sancion)) {
                causas.add(new Causa(SANCION_VIGENTE, porSancion(sancion)));
            }
        }
        return new Elegibilidad(causas.isEmpty(), List.copyOf(causas));
    }

    /** Sanción {@code VIGENTE} de suspensión por partidos o de baja (RF-020). */
    static boolean impideJugar(SancionPropia sancion) {
        return VIGENTE.equals(sancion.estado())
                && (SUSPENSION_PARTIDOS.equals(sancion.tipo()) || BAJA.equals(sancion.tipo()));
    }

    private static String porEstado(EstadoRegistro estado) {
        return switch (estado) {
            case PENDIENTE_VALIDACION ->
                    "El registro aún no está validado por la liga: podrá jugar cuando la liga lo active.";
            case RECHAZADO -> "El registro fue rechazado por la liga: con este registro no puede jugar.";
            case BAJA -> "El registro está dado de baja: ya no puede jugar con este equipo.";
            case SUSPENDIDO -> "El registro está suspendido: no puede jugar mientras dure la suspensión.";
            case ACTIVO -> throw new IllegalArgumentException("Un registro ACTIVO no es causa de inelegibilidad");
        };
    }

    /** «Suspensión de 3 partidos por el artículo 12 («Juego brusco»): faltan 2 partidos.» */
    private static String porSancion(SancionPropia sancion) {
        StringBuilder mensaje = new StringBuilder();
        if (SUSPENSION_PARTIDOS.equals(sancion.tipo())) {
            mensaje.append("Suspensión");
            if (sancion.partidos() != null) {
                mensaje.append(" de ").append(partidos(sancion.partidos()));
            }
        } else {
            mensaje.append("Baja");
        }
        if (sancion.articulo() != null) {
            mensaje.append(" por el artículo ").append(sancion.articulo().numero());
            if (sancion.articulo().descripcion() != null) {
                mensaje.append(" («").append(sancion.articulo().descripcion()).append("»)");
            }
        }
        mensaje.append(": ").append(vigencia(sancion)).append('.');
        return mensaje.toString();
    }

    private static String vigencia(SancionPropia sancion) {
        List<String> partes = new ArrayList<>();
        if (sancion.partidos() != null) {
            int restantes = Math.max(0, sancion.partidos() - sancion.partidosCumplidos());
            partes.add(restantes == 1 ? "falta 1 partido" : "faltan " + partidos(restantes));
        }
        if (sancion.vigenteHasta() != null) {
            partes.add("vigente hasta el " + fecha(sancion.vigenteHasta()));
        }
        if (partes.isEmpty()) {
            return "vigente desde el " + fecha(sancion.vigenteDesde()) + ", sin fecha de término";
        }
        return String.join(", ", partes);
    }

    private static String partidos(int cantidad) {
        return cantidad == 1 ? "1 partido" : cantidad + " partidos";
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "—" : fecha.format(FECHA);
    }

    /**
     * Resultado de la regla.
     *
     * @param elegible {@code true} si no hay causas
     * @param causas   causas que impiden jugar, en orden
     */
    public record Elegibilidad(boolean elegible, List<Causa> causas) {

        public Elegibilidad {
            causas = List.copyOf(causas);
        }
    }

    /**
     * Una causa que impide jugar.
     *
     * @param codigo  {@code RF-019} (estado del registro) o {@code RF-020} (sanción vigente)
     * @param mensaje explicación en español para el jugador o su tutor, sin códigos internos
     */
    public record Causa(String codigo, String mensaje) {
    }
}
