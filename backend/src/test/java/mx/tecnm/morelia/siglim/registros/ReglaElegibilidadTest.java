package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.ReglaElegibilidad.Causa;
import mx.tecnm.morelia.siglim.registros.ReglaElegibilidad.Elegibilidad;
import mx.tecnm.morelia.siglim.registros.SancionPropia.ArticuloDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Regla de elegibilidad de un registro (H-24.1, RF-019, RF-020, DEC-142), sin Spring ni base de datos. */
class ReglaElegibilidadTest {

    private static final ArticuloDto ARTICULO = new ArticuloDto("12", "Juego brusco grave");

    @Test
    @DisplayName("RF-019 · RF-020: un registro ACTIVO sin sanciones es elegible y sin causas")
    void activoSinSanciones() {
        Elegibilidad elegibilidad = ReglaElegibilidad.evaluar(EstadoRegistro.ACTIVO, List.of());

        assertThat(elegibilidad.elegible()).isTrue();
        assertThat(elegibilidad.causas()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            PENDIENTE_VALIDACION | El registro aún no está validado por la liga: podrá jugar cuando la liga lo active.
            RECHAZADO            | El registro fue rechazado por la liga: con este registro no puede jugar.
            BAJA                 | El registro está dado de baja: ya no puede jugar con este equipo.
            SUSPENDIDO           | El registro está suspendido: no puede jugar mientras dure la suspensión.
            """)
    @DisplayName("RF-019: un registro que no está ACTIVO no es elegible y la causa explica su estado")
    void registroNoActivo(EstadoRegistro estado, String mensaje) {
        Elegibilidad elegibilidad = ReglaElegibilidad.evaluar(estado, List.of());

        assertThat(elegibilidad.elegible()).isFalse();
        assertThat(elegibilidad.causas()).containsExactly(new Causa("RF-019", mensaje));
    }

    @Test
    @DisplayName("RF-020: una suspensión VIGENTE impide jugar con el artículo y los partidos que faltan")
    void suspensionVigente() {
        SancionPropia suspension = sancion("SUSPENSION_PARTIDOS", "VIGENTE", 3, 1, null, ARTICULO);

        Elegibilidad elegibilidad = ReglaElegibilidad.evaluar(EstadoRegistro.ACTIVO, List.of(suspension));

        assertThat(elegibilidad.elegible()).isFalse();
        assertThat(elegibilidad.causas()).containsExactly(new Causa("RF-020",
                "Suspensión de 3 partidos por el artículo 12 («Juego brusco grave»): faltan 2 partidos."));
    }

    @Test
    @DisplayName("RF-020: la vigencia lleva los partidos restantes y la fecha de término si ambos existen")
    void suspensionConFechaDeTermino() {
        SancionPropia suspension = sancion("SUSPENSION_PARTIDOS", "VIGENTE", 2, 1, LocalDate.parse("2026-12-20"),
                ARTICULO);

        Elegibilidad elegibilidad = ReglaElegibilidad.evaluar(EstadoRegistro.ACTIVO, List.of(suspension));

        assertThat(elegibilidad.causas()).extracting(Causa::mensaje).containsExactly(
                "Suspensión de 2 partidos por el artículo 12 («Juego brusco grave»): falta 1 partido, "
                        + "vigente hasta el 20/12/2026.");
    }

    @Test
    @DisplayName("RF-020: una BAJA VIGENTE impide jugar; sin fecha de término lo dice")
    void bajaVigente() {
        SancionPropia baja = sancion("BAJA", "VIGENTE", null, 0, null, null);

        Elegibilidad elegibilidad = ReglaElegibilidad.evaluar(EstadoRegistro.ACTIVO, List.of(baja));

        assertThat(elegibilidad.elegible()).isFalse();
        assertThat(elegibilidad.causas()).containsExactly(new Causa("RF-020",
                "Baja: vigente desde el 01/11/2026, sin fecha de término."));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"MULTA", "PERDIDA_PUNTOS", "PERDIDA_PARTIDO"})
    @DisplayName("RF-020 [PROPUESTA]: multa, pérdida de puntos y pérdida de partido no impiden jugar")
    void sancionesQueNoImpiden(String tipo) {
        SancionPropia sancion = sancion(tipo, "VIGENTE", null, 0, null, ARTICULO);

        Elegibilidad elegibilidad = ReglaElegibilidad.evaluar(EstadoRegistro.ACTIVO, List.of(sancion));

        assertThat(elegibilidad.elegible()).isTrue();
        assertThat(elegibilidad.causas()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CUMPLIDA", "REVOCADA"})
    @DisplayName("RF-020: una suspensión CUMPLIDA o REVOCADA ya no impide jugar")
    void sancionNoVigente(String estado) {
        SancionPropia sancion = sancion("SUSPENSION_PARTIDOS", estado, 3, 3, null, ARTICULO);

        assertThat(ReglaElegibilidad.evaluar(EstadoRegistro.ACTIVO, List.of(sancion)).elegible()).isTrue();
    }

    @Test
    @DisplayName("RF-019 · RF-020: varias causas van todas, primero la del registro y luego las sanciones en orden")
    void variasCausas() {
        SancionPropia suspension = sancion("SUSPENSION_PARTIDOS", "VIGENTE", 1, 0, null, ARTICULO);
        SancionPropia multa = sancion("MULTA", "VIGENTE", null, 0, null, ARTICULO);
        SancionPropia baja = sancion("BAJA", "VIGENTE", null, 0, LocalDate.parse("2027-06-30"),
                new ArticuloDto("40", "Agresión"));

        Elegibilidad elegibilidad = ReglaElegibilidad.evaluar(EstadoRegistro.SUSPENDIDO,
                List.of(suspension, multa, baja));

        assertThat(elegibilidad.elegible()).isFalse();
        assertThat(elegibilidad.causas()).extracting(Causa::codigo).containsExactly("RF-019", "RF-020", "RF-020");
        assertThat(elegibilidad.causas()).extracting(Causa::mensaje).containsExactly(
                "El registro está suspendido: no puede jugar mientras dure la suspensión.",
                "Suspensión de 1 partido por el artículo 12 («Juego brusco grave»): falta 1 partido.",
                "Baja por el artículo 40 («Agresión»): vigente hasta el 30/06/2027.");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EstadoRegistro.class)
    @DisplayName("RF-019: elegible solo si no hay causas, para cada estado del registro")
    void elegibleSiNoHayCausas(EstadoRegistro estado) {
        Elegibilidad elegibilidad = ReglaElegibilidad.evaluar(estado, List.of());

        assertThat(elegibilidad.elegible()).isEqualTo(elegibilidad.causas().isEmpty())
                .isEqualTo(estado == EstadoRegistro.ACTIVO);
    }

    private static SancionPropia sancion(String tipo, String estado, Integer partidos, int cumplidos,
            LocalDate vigenteHasta, ArticuloDto articulo) {
        return new SancionPropia(UUID.randomUUID(), tipo, estado, partidos, cumplidos, LocalDate.parse("2026-11-01"),
                vigenteHasta, articulo, "EXP-2026-001");
    }
}
