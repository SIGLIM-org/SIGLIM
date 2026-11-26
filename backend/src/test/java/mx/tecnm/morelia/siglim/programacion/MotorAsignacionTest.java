package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.PartidoAsignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.PartidoNoAsignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.ResumenDia;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.CanchaAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.CategoriaAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.EquipoAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.Ocupacion;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.PartidoPorColocar;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.PeriodoCancha;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.TipoPeriodo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Motor voraz de asignación de cancha y horario (H-33.1, ADR-0015), sin Spring ni base de datos. */
class MotorAsignacionTest {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate SABADO = LocalDate.of(2026, 12, 12);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 12, 13);
    private static final UUID GRUPO = id("grupo-1");

    private static final CategoriaAsignable LIBRE = categoria("Libre", 1, 120, DayOfWeek.SUNDAY);
    private static final CategoriaAsignable VETERANOS = categoria("Veteranos", 2, 120, DayOfWeek.SUNDAY);
    private static final CategoriaAsignable INFANTIL = categoria("Infantil", 3, 90, DayOfWeek.SATURDAY);

    // --- Datos de prueba ----------------------------------------------------------------------------

    private static UUID id(String texto) {
        return UUID.nameUUIDFromBytes(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static OffsetDateTime a(LocalDate dia, int hora, int minuto) {
        return dia.atTime(hora, minuto).atZone(ZONA).toOffsetDateTime();
    }

    private static OffsetDateTime a(LocalDate dia, int hora) {
        return a(dia, hora, 0);
    }

    private static CategoriaAsignable categoria(String nombre, int orden, int bloque, DayOfWeek... dias) {
        return new CategoriaAsignable(id("categoria-" + nombre), nombre, orden, Set.of(dias), bloque);
    }

    private static CanchaAsignable cancha(String nombre, boolean unidadDeportiva, PeriodoCancha... periodos) {
        return new CanchaAsignable(id("cancha-" + nombre), nombre, true, unidadDeportiva, List.of(periodos));
    }

    private static PeriodoCancha periodo(TipoPeriodo tipo, LocalDate dia, int desde, int hasta) {
        return new PeriodoCancha(tipo, a(dia, desde), a(dia, hasta));
    }

    private static EquipoAsignable equipo(String nombre) {
        return new EquipoAsignable(id("equipo-" + nombre), nombre, null);
    }

    private static PartidoPorColocar partido(String clave, CategoriaAsignable categoria, String local,
            String visitante, CanchaAsignable... delLocal) {
        return partido(clave, categoria, equipo(local), equipo(visitante), delLocal);
    }

    private static PartidoPorColocar partido(String clave, CategoriaAsignable categoria, EquipoAsignable local,
            EquipoAsignable visitante, CanchaAsignable... delLocal) {
        Set<UUID> canchas = Arrays.stream(delLocal).map(CanchaAsignable::id).collect(Collectors.toSet());
        return new PartidoPorColocar(id(clave), GRUPO, categoria, local, visitante, canchas);
    }

    private static SolicitudAsignacion solicitud(List<CanchaAsignable> canchas, List<PartidoPorColocar> partidos) {
        return solicitud(canchas, partidos, List.of(), false);
    }

    private static SolicitudAsignacion solicitud(List<CanchaAsignable> canchas, List<PartidoPorColocar> partidos,
            List<Ocupacion> ocupaciones, boolean fechasFinales) {
        return new SolicitudAsignacion(List.of(SABADO, DOMINGO), LocalTime.of(8, 0), LocalTime.of(18, 0), ZONA,
                fechasFinales, canchas, partidos, ocupaciones);
    }

    private static Map<UUID, PartidoAsignado> porEmparejamiento(ResultadoAsignacion resultado) {
        return resultado.asignados().stream()
                .collect(Collectors.toMap(PartidoAsignado::emparejamientoId, Function.identity()));
    }

    private static PartidoAsignado asignado(ResultadoAsignacion resultado, String clave) {
        PartidoAsignado asignado = porEmparejamiento(resultado).get(id(clave));
        assertThat(asignado).as("el partido %s quedó asignado", clave).isNotNull();
        return asignado;
    }

    // --- 1. Ventanas (DEC-176) ----------------------------------------------------------------------

    @Test
    @DisplayName("DEC-176: sin periodos se asigna desde el inicio del horario de juego por defecto")
    void usaElHorarioPorDefecto() {
        CanchaAsignable cancha = cancha("Cancha 1", false);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(
                solicitud(List.of(cancha), List.of(partido("p1", LIBRE, "Halcones", "Toros"))));

        PartidoAsignado p1 = asignado(resultado, "p1");
        assertThat(p1.canchaId()).isEqualTo(cancha.id());
        assertThat(p1.inicio()).isEqualTo(a(DOMINGO, 8));
        assertThat(p1.fin()).isEqualTo(a(DOMINGO, 10));
        assertThat(resultado.noAsignados()).isEmpty();
    }

    @Test
    @DisplayName("DEC-176: con periodos DISPONIBLE se asigna solo dentro de ellos")
    void usaLosPeriodosDisponibles() {
        CanchaAsignable cancha = cancha("Cancha 1", false, periodo(TipoPeriodo.DISPONIBLE, DOMINGO, 12, 16));

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha), List.of(
                partido("p1", LIBRE, "A", "B"), partido("p2", LIBRE, "C", "D"), partido("p3", LIBRE, "E", "F"))));

        assertThat(resultado.asignados()).extracting(PartidoAsignado::inicio)
                .containsExactly(a(DOMINGO, 12), a(DOMINGO, 14));
        assertThat(resultado.noAsignados()).extracting(PartidoNoAsignado::codigo).containsExactly("VAL-007");
    }

    @ParameterizedTest
    @EnumSource(value = TipoPeriodo.class, names = {"BLOQUEO", "MANTENIMIENTO"})
    @DisplayName("VAL-005, DEC-176: un bloqueo o mantenimiento a media ventana la parte y nada queda encima")
    void bloqueoAMediaVentana(TipoPeriodo tipo) {
        PeriodoCancha restriccion = periodo(tipo, DOMINGO, 9, 11);
        CanchaAsignable cancha = cancha("Cancha 1", false, restriccion);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha), List.of(
                partido("p1", LIBRE, "A", "B"), partido("p2", LIBRE, "C", "D"))));

        assertThat(resultado.asignados()).extracting(PartidoAsignado::inicio)
                .containsExactly(a(DOMINGO, 11), a(DOMINGO, 13));
        assertThat(resultado.asignados()).allSatisfy(p -> assertThat(
                p.inicio().isBefore(restriccion.hasta()) && restriccion.desde().isBefore(p.fin())).isFalse());
    }

    @Test
    @DisplayName("VAL-005: una cancha inactiva nunca se usa, aunque sea la preferida")
    void canchaInactivaNoSeUsa() {
        CanchaAsignable inactiva = new CanchaAsignable(id("cancha-inactiva"), "Unidad 1", false, true, List.of());
        CanchaAsignable activa = cancha("Cancha 2", false);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(
                solicitud(List.of(inactiva, activa), List.of(partido("p1", LIBRE, "A", "B"))));

        assertThat(asignado(resultado, "p1").canchaId()).isEqualTo(activa.id());
    }

    // --- 2. Días permitidos (DEC-177) ---------------------------------------------------------------

    @Test
    @DisplayName("DEC-177: cada categoría juega solo en sus días de juego")
    void respetaLosDiasDeJuego() {
        CanchaAsignable cancha = cancha("Cancha 1", false);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha), List.of(
                partido("libre", LIBRE, "A", "B"), partido("infantil", INFANTIL, "C", "D"))));

        assertThat(asignado(resultado, "libre").inicio()).isEqualTo(a(DOMINGO, 8));
        assertThat(asignado(resultado, "infantil").inicio()).isEqualTo(a(SABADO, 8));
        assertThat(resultado.advertencias()).isEmpty();
    }

    @Test
    @DisplayName("DEC-177: si ningún día de la fecha es de la categoría, se usan todos y se advierte")
    void sinDiaDeJuegoUsaTodosYAdvierte() {
        CategoriaAsignable lunes = categoria("Femenil", 4, 120, DayOfWeek.MONDAY);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha("Cancha 1", false)),
                List.of(partido("p1", lunes, "A", "B"), partido("p2", lunes, "C", "D"))));

        assertThat(asignado(resultado, "p1").inicio().toLocalDate()).isEqualTo(SABADO);
        assertThat(resultado.advertencias()).singleElement().asString()
                .contains("«Femenil»", "sábado 12/12/2026", "domingo 13/12/2026", "DEC-177");
    }

    // --- 3. Elegibilidad (VAL-006) ------------------------------------------------------------------

    @Test
    @DisplayName("VAL-006: un equipo con baja o sanción deja su partido sin asignar y no consume franja")
    void equipoNoElegibleQuedaSinAsignar() {
        EquipoAsignable sancionado = new EquipoAsignable(id("equipo-Toros"), "Toros",
                "El equipo «Toros» tiene una suspensión vigente de 2 partido(s) y no puede jugar.");

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha("Cancha 1", false)), List.of(
                partido("sancionado", LIBRE, equipo("Halcones"), sancionado),
                partido("normal", VETERANOS, "Pumas", "Leones"))));

        assertThat(resultado.noAsignados()).singleElement().satisfies(p -> {
            assertThat(p.emparejamientoId()).isEqualTo(id("sancionado"));
            assertThat(p.codigo()).isEqualTo("VAL-006");
            assertThat(p.causa()).isEqualTo("No se asigna «Halcones vs. Toros»: El equipo «Toros» tiene una"
                    + " suspensión vigente de 2 partido(s) y no puede jugar.");
        });
        assertThat(asignado(resultado, "normal").inicio()).isEqualTo(a(DOMINGO, 8));
        assertThat(resultado.resumen()).filteredOn(r -> r.dia().equals(DOMINGO))
                .singleElement().extracting(ResumenDia::partidosRequeridos).isEqualTo(1);
    }

    // --- 4. Orden «más restringido primero» (DEC-178) -----------------------------------------------

    @Test
    @DisplayName("DEC-178, RF-032: la categoría de menor orden va primero y los partidos quedan consecutivos")
    void menorOrdenPrimeroYConsecutivos() {
        CanchaAsignable cancha = cancha("Cancha 1", false);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha), List.of(
                partido("veteranos", VETERANOS, "A", "B"), partido("libre", LIBRE, "C", "D"))));

        assertThat(asignado(resultado, "libre").inicio()).isEqualTo(a(DOMINGO, 8));
        assertThat(asignado(resultado, "veteranos").inicio()).isEqualTo(a(DOMINGO, 10));
        assertThat(asignado(resultado, "libre").fin()).isEqualTo(asignado(resultado, "veteranos").inicio());
    }

    @Test
    @DisplayName("DEC-178: la categoría con menos días permitidos se coloca antes que una de menor orden")
    void menosDiasPrimero() {
        CategoriaAsignable flexible = categoria("Flexible", 1, 120, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        CategoriaAsignable sabatina = categoria("Sabatina", 2, 120, DayOfWeek.SATURDAY);
        CanchaAsignable cancha = cancha("Cancha 1", false,
                periodo(TipoPeriodo.DISPONIBLE, SABADO, 8, 10), periodo(TipoPeriodo.DISPONIBLE, DOMINGO, 8, 10));

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha), List.of(
                partido("flexible", flexible, "A", "B"), partido("sabatina", sabatina, "C", "D"))));

        assertThat(asignado(resultado, "sabatina").inicio()).isEqualTo(a(SABADO, 8));
        assertThat(asignado(resultado, "flexible").inicio()).isEqualTo(a(DOMINGO, 8));
        assertThat(resultado.noAsignados()).isEmpty();
    }

    // --- 5. Orden de canchas (DEC-179) --------------------------------------------------------------

    @Test
    @DisplayName("DEC-179: cancha del local, luego unidad deportiva, luego las demás")
    void prefiereLaCanchaDelLocalYLuegoLaUnidad() {
        CanchaAsignable otra = cancha("Otra", false);
        CanchaAsignable unidad = cancha("Unidad 1", true);
        CanchaAsignable delLocal = cancha("Halcones", false);
        List<CanchaAsignable> canchas = List.of(otra, unidad, delLocal);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(canchas, List.of(
                partido("con-cancha", LIBRE, "Halcones", "Toros", delLocal),
                partido("sin-cancha", LIBRE, "Pumas", "Leones"),
                partido("sin-cancha-2", LIBRE, "Lobos", "Osos"))));

        assertThat(asignado(resultado, "con-cancha").canchaId()).isEqualTo(delLocal.id());
        assertThat(asignado(resultado, "con-cancha").motivoLocalia()).isNull();
        // Sin cancha propia, los dos van a la unidad deportiva, uno tras otro, y no a «Otra».
        assertThat(List.of(asignado(resultado, "sin-cancha"), asignado(resultado, "sin-cancha-2")))
                .allSatisfy(p -> {
                    assertThat(p.canchaId()).isEqualTo(unidad.id());
                    assertThat(p.motivoLocalia()).isNull();
                })
                .extracting(PartidoAsignado::inicio)
                .containsExactlyInAnyOrder(a(DOMINGO, 8), a(DOMINGO, 10));
    }

    @Test
    @DisplayName("DEC-179, RN-049: en las fechas finales la unidad deportiva va antes que la cancha del local")
    void enFechasFinalesLaUnidadVaPrimero() {
        CanchaAsignable unidad = cancha("Unidad 1", true);
        CanchaAsignable delLocal = cancha("Halcones", false);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(delLocal, unidad),
                List.of(partido("p1", LIBRE, "Halcones", "Toros", delLocal)), List.of(), true));

        assertThat(asignado(resultado, "p1").canchaId()).isEqualTo(unidad.id());
        assertThat(asignado(resultado, "p1").motivoLocalia()).isNull();
    }

    // --- 6. Traslapes en memoria (VAL-003, VAL-004) -------------------------------------------------

    @Test
    @DisplayName("VAL-004, DEC-181: una ocupación previa (ajuste manual) se respeta y no se mueve")
    void respetaLaOcupacionPrevia() {
        CanchaAsignable cancha = cancha("Cancha 1", false);
        Ocupacion ajustado = new Ocupacion(cancha.id(), id("equipo-X"), id("equipo-Y"), a(DOMINGO, 8), a(DOMINGO, 10));

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha),
                List.of(partido("p1", LIBRE, "A", "B")), List.of(ajustado), false));

        assertThat(asignado(resultado, "p1").inicio()).isEqualTo(a(DOMINGO, 10));
        assertThat(resultado.asignados()).hasSize(1);
        assertThat(resultado.resumen()).filteredOn(r -> r.dia().equals(DOMINGO))
                .singleElement().extracting(ResumenDia::franjasLibres).isEqualTo(4);
    }

    @Test
    @DisplayName("VAL-003: un equipo con dos partidos en la fecha no se traslapa consigo mismo")
    void unEquipoNoSeTraslapaConsigoMismo() {
        CanchaAsignable c1 = cancha("Cancha 1", false);
        CanchaAsignable c2 = cancha("Cancha 2", false);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(c1, c2), List.of(
                partido("p1", LIBRE, "Halcones", "Toros"),
                new PartidoPorColocar(id("p2"), id("grupo-2"), LIBRE, equipo("Pumas"), equipo("Halcones"), Set.of()))));

        assertThat(List.of(asignado(resultado, "p1"), asignado(resultado, "p2")))
                .extracting(PartidoAsignado::inicio)
                .containsExactlyInAnyOrder(a(DOMINGO, 8), a(DOMINGO, 10));
    }

    @Test
    @DisplayName("VAL-003: si un equipo ya juega en otra cancha, se prueba el fin de ese partido como candidato")
    void usaElFinDelPartidoDelEquipoComoCandidato() {
        CanchaAsignable c1 = cancha("Cancha 1", false);
        CanchaAsignable c2 = cancha("Cancha 2", false);
        Ocupacion previo = new Ocupacion(c1.id(), id("equipo-A"), id("equipo-Z"), a(DOMINGO, 8), a(DOMINGO, 9, 30));
        Ocupacion llena = new Ocupacion(c1.id(), id("equipo-X"), id("equipo-Y"), a(DOMINGO, 9, 30), a(DOMINGO, 18));

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(c1, c2),
                List.of(partido("p1", LIBRE, "A", "B")), List.of(previo, llena), false));

        assertThat(asignado(resultado, "p1").canchaId()).isEqualTo(c2.id());
        assertThat(asignado(resultado, "p1").inicio()).isEqualTo(a(DOMINGO, 9, 30));
    }

    // --- 7. Localía (VAL-027, DEC-179) --------------------------------------------------------------

    @Test
    @DisplayName("VAL-027, DEC-179: fuera de la cancha del local se propone un motivo de localía automático")
    void motivoDeLocaliaFueraDeLaCanchaDelLocal() {
        CanchaAsignable delLocal = cancha("Halcones", false, periodo(TipoPeriodo.BLOQUEO, DOMINGO, 0, 23));
        CanchaAsignable unidad = cancha("Unidad 1", true);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(delLocal, unidad),
                List.of(partido("p1", LIBRE, "Halcones", "Toros", delLocal))));

        assertThat(asignado(resultado, "p1").canchaId()).isEqualTo(unidad.id());
        assertThat(asignado(resultado, "p1").motivoLocalia()).isEqualTo("Asignación automática: no hubo lugar en"
                + " la cancha del equipo local «Halcones»; se asignó «Unidad 1» (DEC-179).");
    }

    @Test
    @DisplayName("VAL-027, RN-049: en fechas finales, fuera de la unidad deportiva se propone el motivo")
    void motivoDeLocaliaEnFechasFinales() {
        CanchaAsignable unidad = cancha("Unidad 1", true, periodo(TipoPeriodo.MANTENIMIENTO, DOMINGO, 0, 23));
        CanchaAsignable otra = cancha("Otra", false);

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(unidad, otra),
                List.of(partido("p1", LIBRE, "Pumas", "Leones")), List.of(), true));

        assertThat(asignado(resultado, "p1").motivoLocalia())
                .startsWith(MotorAsignacion.PREFIJO_LOCALIA)
                .contains("unidad deportiva", "«Otra»");
    }

    // --- 8 y 9. Sin lugar (VAL-007) y resumen (RF-042) ----------------------------------------------

    @Test
    @DisplayName("VAL-007, RF-042: lo que no cabe queda sin asignar con una causa que cuenta franjas y partidos")
    void sinLugarConCausaYResumen() {
        CanchaAsignable cancha = cancha("Cancha 1", false, periodo(TipoPeriodo.DISPONIBLE, DOMINGO, 8, 12));

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha), List.of(
                partido("p1", LIBRE, "A", "B"), partido("p2", LIBRE, "C", "D"), partido("p3", LIBRE, "E", "F"))));

        assertThat(resultado.noAsignados()).singleElement().satisfies(p -> {
            assertThat(p.codigo()).isEqualTo("VAL-007");
            assertThat(p.causa()).isEqualTo(
                    "Sin horario libre el domingo para Libre (bloque de 120 min): 2 franjas libres, 3 partidos");
        });
        assertThat(resultado.resumen()).containsExactly(
                new ResumenDia(SABADO, 5, 120, 0),
                new ResumenDia(DOMINGO, 2, 120, 3));
    }

    @Test
    @DisplayName("VAL-007: con varios días permitidos la causa detalla cada día")
    void sinLugarEnVariosDias() {
        CategoriaAsignable flexible = categoria("Flexible", 1, 120, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        CanchaAsignable cancha = cancha("Cancha 1", false,
                periodo(TipoPeriodo.DISPONIBLE, SABADO, 8, 10), periodo(TipoPeriodo.DISPONIBLE, DOMINGO, 8, 10));

        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud(List.of(cancha), List.of(
                partido("p1", flexible, "A", "B"), partido("p2", flexible, "C", "D"), partido("p3", flexible, "E", "F"))));

        assertThat(resultado.noAsignados()).singleElement().extracting(PartidoNoAsignado::causa)
                .isEqualTo("Sin horario libre el sábado ni el domingo para Flexible (bloque de 120 min): "
                        + "sábado 1 franjas libres, 3 partidos; domingo 1 franjas libres, 3 partidos");
    }

    // --- 10. Determinismo y carga -------------------------------------------------------------------

    @Test
    @DisplayName("Determinismo: dos corridas y la entrada barajada dan la misma asignación")
    void esDeterminista() {
        SolicitudAsignacion original = cargaGrande();
        List<CanchaAsignable> canchas = new ArrayList<>(original.canchas());
        List<PartidoPorColocar> partidos = new ArrayList<>(original.partidos());
        Collections.shuffle(canchas, new Random(7));
        Collections.shuffle(partidos, new Random(11));
        SolicitudAsignacion barajada = new SolicitudAsignacion(List.of(DOMINGO, SABADO), original.horaInicioJuego(),
                original.horaFinJuego(), ZONA, false, canchas, partidos, original.ocupaciones());

        ResultadoAsignacion primera = MotorAsignacion.asignar(original);

        assertThat(MotorAsignacion.asignar(original)).isEqualTo(primera);
        assertThat(MotorAsignacion.asignar(barajada)).isEqualTo(primera);
    }

    @Test
    @DisplayName("CA-002: 100 partidos y 8 canchas terminan en milisegundos y sin ningún traslape")
    void cargaSinTraslapes() {
        SolicitudAsignacion solicitud = cargaGrande();

        long antes = System.nanoTime();
        ResultadoAsignacion resultado = MotorAsignacion.asignar(solicitud);
        long milisegundos = (System.nanoTime() - antes) / 1_000_000;

        assertThat(milisegundos).isLessThan(1_000);
        assertThat(resultado.asignados().size() + resultado.noAsignados().size()).isEqualTo(100);
        assertThat(resultado.asignados()).hasSizeGreaterThan(60);

        Map<UUID, PartidoPorColocar> partidos = solicitud.partidos().stream()
                .collect(Collectors.toMap(PartidoPorColocar::emparejamientoId, Function.identity()));
        List<Ocupacion> todas = new ArrayList<>(solicitud.ocupaciones());
        for (PartidoAsignado asignado : resultado.asignados()) {
            PartidoPorColocar partido = partidos.get(asignado.emparejamientoId());
            DayOfWeek dia = asignado.inicio().getDayOfWeek();
            assertThat(partido.categoria().diasJuego()).contains(dia);
            assertThat(asignado.inicio().toLocalTime()).isAfterOrEqualTo(LocalTime.of(8, 0));
            assertThat(asignado.fin().toLocalTime()).isBeforeOrEqualTo(LocalTime.of(18, 0));
            todas.add(new Ocupacion(asignado.canchaId(), partido.local().id(), partido.visitante().id(),
                    asignado.inicio(), asignado.fin()));
        }
        for (int i = 0; i < todas.size(); i++) {
            for (int j = i + 1; j < todas.size(); j++) {
                Ocupacion x = todas.get(i);
                Ocupacion y = todas.get(j);
                boolean traslapan = x.inicio().isBefore(y.fin()) && y.inicio().isBefore(x.fin());
                if (!traslapan) {
                    continue;
                }
                assertThat(x.canchaId()).as("cancha ocupada dos veces").isNotEqualTo(y.canchaId());
                assertThat(Set.of(x.equipoLocalId(), x.equipoVisitanteId()))
                        .as("equipo en dos partidos a la vez")
                        .doesNotContainAnyElementsOf(Set.of(y.equipoLocalId(), y.equipoVisitanteId()));
            }
        }
    }

    /** 100 partidos en 4 categorías y 10 grupos, 8 canchas (2 en la unidad deportiva) y 2 ocupaciones previas. */
    private static SolicitudAsignacion cargaGrande() {
        List<CanchaAsignable> canchas = new ArrayList<>();
        for (int c = 1; c <= 8; c++) {
            canchas.add(c == 3
                    ? cancha("Cancha " + c, c <= 2, periodo(TipoPeriodo.BLOQUEO, DOMINGO, 12, 14))
                    : cancha("Cancha " + c, c <= 2));
        }
        List<CategoriaAsignable> categorias = List.of(LIBRE, VETERANOS, INFANTIL,
                categoria("Juvenil", 4, 90, DayOfWeek.SATURDAY));
        List<PartidoPorColocar> partidos = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            CategoriaAsignable categoria = categorias.get(i % categorias.size());
            int grupo = i % 10;
            // Los equipos se repiten entre partidos para que haya choques de equipo que evitar.
            EquipoAsignable local = equipo(categoria.nombre() + "-" + (i % 30));
            EquipoAsignable visitante = equipo(categoria.nombre() + "-" + (30 + i % 37));
            CanchaAsignable[] delLocal = i % 5 == 0 ? new CanchaAsignable[] {canchas.get(3 + i % 5)}
                    : new CanchaAsignable[0];
            Set<UUID> deLocal = Arrays.stream(delLocal).map(CanchaAsignable::id).collect(Collectors.toSet());
            partidos.add(new PartidoPorColocar(id("partido-" + i), id("grupo-" + grupo), categoria, local, visitante,
                    deLocal));
        }
        List<Ocupacion> ocupaciones = List.of(
                new Ocupacion(canchas.get(0).id(), id("equipo-Libre-0"), id("equipo-X"), a(DOMINGO, 8), a(DOMINGO, 10)),
                new Ocupacion(canchas.get(4).id(), id("equipo-Y"), id("equipo-Z"), a(SABADO, 9), a(SABADO, 11)));
        return new SolicitudAsignacion(List.of(SABADO, DOMINGO), LocalTime.of(8, 0), LocalTime.of(18, 0), ZONA,
                false, canchas, partidos, ocupaciones);
    }

    // --- Entrada inválida ---------------------------------------------------------------------------

    @Test
    @DisplayName("Una solicitud con el horario de juego al revés o sin días se rechaza")
    void rechazaSolicitudInvalida() {
        assertThatThrownBy(() -> new SolicitudAsignacion(List.of(DOMINGO), LocalTime.of(18, 0), LocalTime.of(8, 0),
                ZONA, false, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
        assertThatThrownBy(() -> new SolicitudAsignacion(List.of(), LocalTime.of(8, 0), LocalTime.of(18, 0),
                ZONA, false, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
