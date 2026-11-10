package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Adaptador de la autorización de la consulta de auditoría (ADR-0006 §2), sin Spring ni base de datos. */
class AutorizacionConsultaAuditoriaJdbcTest {

    private static final String PERMISO = "auditoria.registro:CONSULTAR";

    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AutorizacionConsultaAuditoriaJdbc adaptador = new AutorizacionConsultaAuditoriaJdbc(autorizacion);

    @Test
    @DisplayName("RF-134: con ámbito LIGA en el permiso exacto alcanza toda la liga")
    void ambitoLiga() {
        given(autorizacion.alcance(PERMISO)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));

        assertThat(adaptador.alcanzaTodaLaLiga()).isTrue();
    }

    @Test
    @DisplayName("RF-134: con ámbito CLUB o PROPIO no alcanza toda la liga")
    void ambitoLimitado() {
        given(autorizacion.alcance(PERMISO))
                .willReturn(new AlcanceAutorizacion(false, Set.of(UUID.randomUUID()), true));

        assertThat(adaptador.alcanzaTodaLaLiga()).isFalse();
    }

    @Test
    @DisplayName("VAL-015: sin el permiso propaga el 403")
    void sinPermiso() {
        given(autorizacion.alcance(PERMISO)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(adaptador::alcanzaTodaLaLiga)
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
