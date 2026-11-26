package mx.tecnm.morelia.siglim.programacion;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.PartidoAsignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.PartidoNoAsignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.ResumenDia;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.CanchaAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.EquipoAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.Ocupacion;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.PartidoPorColocar;
import mx.tecnm.morelia.siglim.programacion.VentanasDeCancha.Intervalo;

/**
 * Motor voraz «más restringido primero» que asigna cancha y horario a los partidos de una fecha, en
 * todas las categorías y grupos a la vez (ADR-0015, decisión 2). Dominio puro: sin Spring, sin base
 * de datos y sin reloj; todo llega en la {@link SolicitudAsignacion}.
 *
 * <ol>
 *   <li><b>Ventanas (DEC-176).</b> Por día y cancha activa, con {@link VentanasDeCancha}.</li>
 *   <li><b>Días permitidos (DEC-177).</b> Los de la fecha que están en {@code dias_juego} de la
 *       categoría; si ninguno coincide, todos, con una advertencia.</li>
 *   <li><b>Elegibilidad (VAL-006).</b> Un partido con un equipo de baja o sancionado queda sin
 *       asignar y no consume franja.</li>
 *   <li><b>Orden (DEC-178).</b> Menos días permitidos, luego {@code orden} de la categoría, grupo y
 *       emparejamiento: dentro de su día, la categoría de menor {@code orden} toma los horarios más
 *       tempranos.</li>
 *   <li><b>Colocación (DEC-179, RF-032).</b> Día por día y, en cada día, canchas en este orden: las
 *       del club local, la unidad deportiva y las demás (en las fechas finales, la unidad deportiva
 *       primero), con desempate por id. En la primera cancha donde cabe el bloque completo dentro de
 *       una ventana se toma el inicio más temprano. Candidatos: el inicio de cada ventana, el fin de
 *       cada partido de esa cancha (partidos consecutivos) y el fin de cada partido de los dos
 *       equipos.</li>
 *   <li><b>Traslapes (VAL-003, VAL-004).</b> Filtro en memoria contra las ocupaciones previas y lo
 *       ya colocado. No sustituye a {@code ValidadorConflictos}: H-33.2 confirma con él cada
 *       candidato.</li>
 *   <li><b>Localía (VAL-027, DEC-179).</b> Fuera de la cancha del local (o de la unidad deportiva en
 *       fechas finales) se propone un {@code motivo_localia} «Asignación automática: …».</li>
 *   <li><b>Sin lugar (VAL-007, DEC-180).</b> Causa con el día, la categoría, el bloque, las franjas
 *       libres y los partidos requeridos.</li>
 *   <li><b>Resumen por día (RF-042, RN-017).</b> Ver {@link ResumenDia}.</li>
 *   <li><b>Determinismo.</b> Todo se ordena con desempate por el texto del id (DEC-185): los mismos
 *       datos, en cualquier orden, dan la misma asignación.</li>
 * </ol>
 *
 * <p>Refs: H-33.1, RF-032, RF-035, RF-036, RF-042, RN-016, RN-017, VAL-003, VAL-004, VAL-005,
 * VAL-006, VAL-007, VAL-027, DEC-176, DEC-177, DEC-178, DEC-179, DEC-180, ADR-0015
 */
final class MotorAsignacion {

    static final String NO_ELEGIBLE = "VAL-006";
    static final String SIN_LUGAR = "VAL-007";
    static final String PREFIJO_LOCALIA = "Asignación automática: ";

    private static final Locale ESPANOL = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Comparator<UUID> POR_TEXTO = Comparator.comparing(UUID::toString);

    private MotorAsignacion() {
    }

    /**
     * Asigna cancha y horario a los partidos de la solicitud.
     *
     * @return asignados, no asignados, advertencias y resumen por día; nunca {@code null}
     */
    static ResultadoAsignacion asignar(SolicitudAsignacion solicitud) {
        return new Corrida(solicitud).ejecutar();
    }

    /** Estado de una corrida: ventanas, ocupación en memoria y lo que se va proponiendo. */
    private static final class Corrida {

        private final SolicitudAsignacion solicitud;
        private final List<LocalDate> dias;
        private final List<CanchaAsignable> canchas;
        private final Map<LocalDate, Map<UUID, List<Intervalo>>> ventanas = new TreeMap<>();
        private final Map<UUID, List<Intervalo>> ocupadoPorCancha = new TreeMap<>(POR_TEXTO);
        private final Map<UUID, List<Intervalo>> ocupadoPorEquipo = new TreeMap<>(POR_TEXTO);
        private final Map<LocalDate, List<Intervalo>> libresIniciales = new TreeMap<>();
        private final Map<LocalDate, Integer> requeridos = new TreeMap<>();

        Corrida(SolicitudAsignacion solicitud) {
            this.solicitud = solicitud;
            this.dias = solicitud.dias().stream().distinct().sorted().toList();
            this.canchas = solicitud.canchas().stream()
                    .sorted(Comparator.comparing(CanchaAsignable::id, POR_TEXTO))
                    .toList();
        }

        ResultadoAsignacion ejecutar() {
            for (Ocupacion ocupacion : solicitud.ocupaciones()) {
                Intervalo intervalo = new Intervalo(ocupacion.inicio().toInstant(), ocupacion.fin().toInstant());
                ocupar(ocupacion.canchaId(), ocupacion.equipoLocalId(), ocupacion.equipoVisitanteId(), intervalo);
            }
            for (LocalDate dia : dias) {
                Map<UUID, List<Intervalo>> delDia = new TreeMap<>(POR_TEXTO);
                List<Intervalo> libres = new ArrayList<>();
                for (CanchaAsignable cancha : canchas) {
                    List<Intervalo> ventanasCancha = VentanasDeCancha.ventanas(cancha, dia,
                            solicitud.horaInicioJuego(), solicitud.horaFinJuego(), solicitud.zona());
                    delDia.put(cancha.id(), ventanasCancha);
                    libres.addAll(VentanasDeCancha.restar(ventanasCancha,
                            ocupadoPorCancha.getOrDefault(cancha.id(), List.of())));
                }
                ventanas.put(dia, delDia);
                libresIniciales.put(dia, libres);
                requeridos.put(dia, 0);
            }

            List<Pendiente> pendientes = solicitud.partidos().stream()
                    .map(partido -> new Pendiente(partido, diasPermitidos(partido)))
                    .sorted(Comparator.comparingInt((Pendiente p) -> p.diasPermitidos().size())
                            .thenComparingInt(p -> p.partido().categoria().orden())
                            .thenComparing(p -> p.partido().grupoId(), POR_TEXTO)
                            .thenComparing(p -> p.partido().emparejamientoId(), POR_TEXTO))
                    .toList();
            for (Pendiente pendiente : pendientes) {
                if (elegible(pendiente.partido())) {
                    pendiente.diasPermitidos().forEach(dia -> requeridos.merge(dia, 1, Integer::sum));
                }
            }

            List<PartidoAsignado> asignados = new ArrayList<>();
            List<PartidoNoAsignado> noAsignados = new ArrayList<>();
            for (Pendiente pendiente : pendientes) {
                PartidoPorColocar partido = pendiente.partido();
                if (!elegible(partido)) {
                    noAsignados.add(new PartidoNoAsignado(partido.emparejamientoId(), NO_ELEGIBLE,
                            causaNoElegible(partido)));
                    continue;
                }
                PartidoAsignado asignado = colocar(pendiente);
                if (asignado != null) {
                    asignados.add(asignado);
                } else {
                    noAsignados.add(new PartidoNoAsignado(partido.emparejamientoId(), SIN_LUGAR,
                            causaSinLugar(pendiente)));
                }
            }
            asignados.sort(Comparator.comparing((PartidoAsignado a) -> a.inicio().toInstant())
                    .thenComparing(PartidoAsignado::canchaId, POR_TEXTO));
            return new ResultadoAsignacion(asignados, noAsignados, advertencias(pendientes), resumen(pendientes));
        }

        // ── Días permitidos (DEC-177) ───────────────────────────────────────────────────────────

        private List<LocalDate> diasPermitidos(PartidoPorColocar partido) {
            List<LocalDate> permitidos = dias.stream()
                    .filter(dia -> partido.categoria().diasJuego().contains(dia.getDayOfWeek()))
                    .toList();
            return permitidos.isEmpty() ? dias : permitidos;
        }

        private boolean sinDiaDeJuego(PartidoPorColocar partido) {
            return dias.stream().noneMatch(dia -> partido.categoria().diasJuego().contains(dia.getDayOfWeek()));
        }

        private List<String> advertencias(List<Pendiente> pendientes) {
            Set<String> advertencias = new LinkedHashSet<>();
            for (Pendiente pendiente : pendientes) {
                PartidoPorColocar partido = pendiente.partido();
                if (elegible(partido) && sinDiaDeJuego(partido)) {
                    advertencias.add("La categoría «" + partido.categoria().nombre()
                            + "» no juega ningún día de la fecha (" + listaDeDias(dias)
                            + "): sus partidos se asignan en cualquiera de ellos (DEC-177).");
                }
            }
            return List.copyOf(advertencias);
        }

        // ── Colocación (DEC-178, DEC-179, RF-032, VAL-003, VAL-004) ─────────────────────────────

        private PartidoAsignado colocar(Pendiente pendiente) {
            PartidoPorColocar partido = pendiente.partido();
            Duration bloque = Duration.ofMinutes(partido.categoria().bloqueMinutos());
            for (LocalDate dia : pendiente.diasPermitidos()) {
                for (CanchaAsignable cancha : ordenDeCanchas(partido)) {
                    List<Intervalo> ventanasCancha = ventanas.get(dia).get(cancha.id());
                    if (ventanasCancha.isEmpty()) {
                        continue;
                    }
                    List<Intervalo> deCancha = ocupadoPorCancha.getOrDefault(cancha.id(), List.of());
                    List<Intervalo> deEquipos = new ArrayList<>(
                            ocupadoPorEquipo.getOrDefault(partido.local().id(), List.of()));
                    deEquipos.addAll(ocupadoPorEquipo.getOrDefault(partido.visitante().id(), List.of()));

                    TreeSet<Instant> candidatos = new TreeSet<>();
                    ventanasCancha.forEach(ventana -> candidatos.add(ventana.inicio()));
                    deCancha.forEach(ocupado -> candidatos.add(ocupado.fin()));
                    deEquipos.forEach(ocupado -> candidatos.add(ocupado.fin()));
                    for (Instant inicio : candidatos) {
                        Intervalo propuesto = new Intervalo(inicio, inicio.plus(bloque));
                        if (ventanasCancha.stream().anyMatch(ventana -> ventana.contiene(propuesto))
                                && deCancha.stream().noneMatch(propuesto::traslapa)
                                && deEquipos.stream().noneMatch(propuesto::traslapa)) {
                            ocupar(cancha.id(), partido.local().id(), partido.visitante().id(), propuesto);
                            return new PartidoAsignado(partido.emparejamientoId(), cancha.id(),
                                    propuesto.inicio().atZone(solicitud.zona()).toOffsetDateTime(),
                                    propuesto.fin().atZone(solicitud.zona()).toOffsetDateTime(),
                                    motivoLocalia(partido, cancha));
                        }
                    }
                }
            }
            return null;
        }

        /** DEC-179: local → unidad deportiva → demás; en fechas finales, la unidad deportiva primero. */
        private List<CanchaAsignable> ordenDeCanchas(PartidoPorColocar partido) {
            boolean finales = solicitud.fechaFinalUnidadDeportiva();
            return canchas.stream()
                    .sorted(Comparator.comparingInt((CanchaAsignable cancha) -> {
                        boolean delLocal = partido.canchasDelLocal().contains(cancha.id());
                        if (finales) {
                            return cancha.unidadDeportiva() ? 0 : delLocal ? 1 : 2;
                        }
                        return delLocal ? 0 : cancha.unidadDeportiva() ? 1 : 2;
                    }))
                    .toList();
        }

        private void ocupar(UUID canchaId, UUID localId, UUID visitanteId, Intervalo intervalo) {
            ocupadoPorCancha.computeIfAbsent(canchaId, id -> new ArrayList<>()).add(intervalo);
            ocupadoPorEquipo.computeIfAbsent(localId, id -> new ArrayList<>()).add(intervalo);
            ocupadoPorEquipo.computeIfAbsent(visitanteId, id -> new ArrayList<>()).add(intervalo);
        }

        // ── Localía (VAL-027, DEC-179) ──────────────────────────────────────────────────────────

        private String motivoLocalia(PartidoPorColocar partido, CanchaAsignable cancha) {
            if (solicitud.fechaFinalUnidadDeportiva()) {
                return cancha.unidadDeportiva() ? null
                        : PREFIJO_LOCALIA + "la fecha es de las últimas de la fase regular y no hubo lugar en la"
                                + " unidad deportiva; se asignó «" + cancha.nombre() + "» (DEC-179).";
            }
            if (partido.canchasDelLocal().isEmpty() || partido.canchasDelLocal().contains(cancha.id())) {
                return null;
            }
            return PREFIJO_LOCALIA + "no hubo lugar en la cancha del equipo local «" + partido.local().nombre()
                    + "»; se asignó «" + cancha.nombre() + "» (DEC-179).";
        }

        // ── Causas (VAL-006, VAL-007) y resumen (RF-042) ────────────────────────────────────────

        private static boolean elegible(PartidoPorColocar partido) {
            return partido.local().elegible() && partido.visitante().elegible();
        }

        private static String causaNoElegible(PartidoPorColocar partido) {
            String causas = Stream.of(partido.local(), partido.visitante())
                    .filter(equipo -> !equipo.elegible())
                    .map(EquipoAsignable::causaNoElegible)
                    .collect(Collectors.joining(" "));
            return "No se asigna «" + partido.local().nombre() + " vs. " + partido.visitante().nombre() + "»: "
                    + causas;
        }

        /** «Sin horario libre el domingo para Libre (bloque de 120 min): 14 franjas libres, 16 partidos». */
        private String causaSinLugar(Pendiente pendiente) {
            int minutos = pendiente.partido().categoria().bloqueMinutos();
            List<LocalDate> permitidos = pendiente.diasPermitidos();
            String detalle = permitidos.stream()
                    .map(dia -> (permitidos.size() > 1 ? nombreDia(dia) + " " : "")
                            + VentanasDeCancha.franjas(libresIniciales.get(dia), minutos) + " franjas libres, "
                            + requeridos.get(dia) + " partidos")
                    .collect(Collectors.joining("; "));
            return "Sin horario libre el "
                    + permitidos.stream().map(MotorAsignacion::nombreDia).collect(Collectors.joining(" ni el "))
                    + " para " + pendiente.partido().categoria().nombre() + " (bloque de " + minutos + " min): "
                    + detalle;
        }

        private List<ResumenDia> resumen(List<Pendiente> pendientes) {
            int bloqueMenor = solicitud.partidos().stream()
                    .mapToInt(partido -> partido.categoria().bloqueMinutos())
                    .min().orElse(0);
            List<ResumenDia> resumen = new ArrayList<>();
            for (LocalDate dia : dias) {
                int bloque = pendientes.stream()
                        .filter(p -> elegible(p.partido()) && p.diasPermitidos().contains(dia))
                        .mapToInt(p -> p.partido().categoria().bloqueMinutos())
                        .min().orElse(bloqueMenor);
                int franjas = bloque == 0 ? 0 : VentanasDeCancha.franjas(libresIniciales.get(dia), bloque);
                resumen.add(new ResumenDia(dia, franjas, bloque, requeridos.get(dia)));
            }
            return resumen;
        }
    }

    private static String nombreDia(LocalDate dia) {
        return dia.getDayOfWeek().getDisplayName(TextStyle.FULL, ESPANOL);
    }

    private static String listaDeDias(List<LocalDate> dias) {
        return dias.stream()
                .map(dia -> nombreDia(dia) + " " + dia.format(FORMATO_DIA))
                .collect(Collectors.joining(", "));
    }

    /** Partido con los días en que puede jugar (DEC-177). */
    private record Pendiente(PartidoPorColocar partido, List<LocalDate> diasPermitidos) {
    }
}
