package mx.tecnm.morelia.siglim.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Validaciones del evento de auditoría. Sin Spring ni BD. */
class EventoAuditoriaTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Test
    @DisplayName("RF-093: rechaza liga, módulo, acción o entidad nulos")
    void rechazaObligatoriosNulos() {
        assertThatThrownBy(() -> new EventoAuditoria(null, "M-01", "CREAR", "perfiles", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("ligaId");
        assertThatThrownBy(() -> new EventoAuditoria(LIGA, null, "CREAR", "perfiles", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("modulo");
        assertThatThrownBy(() -> new EventoAuditoria(LIGA, "M-01", null, "perfiles", null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("accion");
        assertThatThrownBy(() -> new EventoAuditoria(LIGA, "M-01", "CREAR", null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("entidad");
    }

    @ParameterizedTest
    @ValueSource(strings = {"M-1", "M-001", "m-01", "M01", "M-0A", "X-01"})
    @DisplayName("RF-093: rechaza un módulo que no cumple ^M-[0-9]{2}$ (CHECK de la tabla)")
    void rechazaModuloInvalido(String modulo) {
        assertThatThrownBy(() -> new EventoAuditoria(LIGA, modulo, "CREAR", "perfiles", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RF-093: rechaza una acción vacía o más larga que la columna")
    void rechazaAccionVaciaOLarga() {
        assertThatThrownBy(() -> new EventoAuditoria(LIGA, "M-01", " ", "perfiles", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventoAuditoria(LIGA, "M-01", "A".repeat(81), "perfiles", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("RN-021: un motivo en blanco cuenta como ausente para heredar el de la transacción")
    void motivoEnBlancoEsNulo() {
        assertThat(new EventoAuditoria(LIGA, "M-01", "CREAR", "perfiles", null, "   ", null).motivo()).isNull();
    }
}
