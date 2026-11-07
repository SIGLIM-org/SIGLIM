package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas de la pasarela simulada (RF-138, ADR-0012). Sin Spring ni base de datos. */
class PasarelaSimuladaTest {

    private static final UUID COBRO = UUID.fromString("11111111-1111-4111-8111-111111111111");

    /** 2030-03-01 12:00 UTC: en Ciudad de México todavía es 1 de marzo. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2030-03-01T12:00:00Z"), ZoneOffset.UTC);

    private final PasarelaSimulada pasarela = new PasarelaSimulada(RELOJ);

    @Test
    @DisplayName("RF-138 · ADR-0012: la referencia es ficticia, con la fecha de negocio y seis caracteres aleatorios")
    void generaReferenciaFicticia() {
        String referencia = pasarela.iniciar(COBRO);

        assertThat(referencia).matches("^SIM-20300301-[A-Z0-9]{6}$");
    }

    @Test
    @DisplayName("ADR-0012: la fecha de la referencia usa America/Mexico_City, no la zona del servidor")
    void fechaEnZonaDeNegocio() {
        // 2030-03-02 03:00 UTC = 2030-03-01 21:00 en Ciudad de México.
        PasarelaSimulada deNoche = new PasarelaSimulada(
                Clock.fixed(Instant.parse("2030-03-02T03:00:00Z"), ZoneOffset.UTC));

        assertThat(deNoche.iniciar(COBRO)).startsWith("SIM-20300301-");
    }

    @Test
    @DisplayName("RF-138: cada intento recibe una referencia distinta")
    void referenciasDistintas() {
        Set<String> referencias = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            referencias.add(pasarela.iniciar(COBRO));
        }

        assertThat(referencias).hasSize(50);
    }

    @Test
    @DisplayName("RF-138: la confirmación acepta una referencia válida y un resultado conocido")
    void confirmaReferenciaValida() {
        pasarela.confirmar("SIM-20300301-ABC123", ResultadoPagoSimulado.APROBADO);
        pasarela.confirmar("SIM-20300301-Z9Y8X7", ResultadoPagoSimulado.RECHAZADO);
    }

    @Test
    @DisplayName("ADR-0012: una referencia con otro formato o un resultado nulo se rechazan")
    void rechazaReferenciaInvalida() {
        assertThatThrownBy(() -> pasarela.confirmar("SIM-2030-ABC123", ResultadoPagoSimulado.APROBADO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIM-AAAAMMDD-XXXXXX");
        assertThatThrownBy(() -> pasarela.confirmar("sim-20300301-abc123", ResultadoPagoSimulado.APROBADO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pasarela.confirmar("SIM-20300301-ABC123", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> pasarela.iniciar(null)).isInstanceOf(NullPointerException.class);
    }
}
