package mx.tecnm.morelia.siglim.compartido.contexto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** {@link EjecutorSistema} y el contexto del perfil Sistema. Sin Spring ni base de datos. */
class EjecutorSistemaTest {

    private static final UUID SISTEMA = UUID.fromString("a0000000-0000-4000-8000-000000000002");

    private final EjecutorSistema ejecutor = new EjecutorSistema();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("ADR-0009 punto 4: dentro del ejecutor la operación es del perfil Sistema de la semilla")
    void fijaLaSesionDelSistema() {
        assertThat(EjecutorSistema.PERFIL_SISTEMA).as("UUID de la semilla §12.1").isEqualTo(SISTEMA);

        UUID perfil = ejecutor.ejecutar(() -> {
            assertThat(UsuarioActual.sesion()).containsInstanceOf(AutenticacionSistema.class);
            assertThat(UsuarioActual.autenticacion()).as("no hay JWT").isEmpty();
            return UsuarioActual.idRequerido();
        });

        assertThat(perfil).isEqualTo(SISTEMA);
    }

    @Test
    @DisplayName("ADR-0009: al terminar, el hilo del planificador queda sin sesión")
    void limpiaAlTerminar() {
        ejecutor.ejecutar(() -> UsuarioActual.idRequerido());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UsuarioActual.perfilId()).isEmpty();
    }

    @Test
    @DisplayName("ADR-0009: si el proceso lanza una excepción, la propaga y también limpia la sesión")
    void limpiaSiFalla() {
        IllegalArgumentException error = new IllegalArgumentException("falla del proceso");

        assertThatThrownBy(() -> ejecutor.ejecutar(() -> {
            throw error;
        })).isSameAs(error);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("ADR-0009: el perfil Sistema no se activa dentro de una petición HTTP")
    void rechazaDentroDeUnaPeticion() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AtomicBoolean ejecutado = new AtomicBoolean();

        assertThatThrownBy(() -> ejecutor.ejecutar(() -> ejecutado.getAndSet(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("petición HTTP");

        assertThat(ejecutado).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("ADR-0005: dentro de una transacción abierta se rechaza, porque su contexto ya quedó fijado")
    void rechazaDentroDeUnaTransaccion() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> ejecutor.ejecutar(() -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transacción");
    }

    @Test
    @DisplayName("ADR-0005: no reemplaza la sesión de una persona que ya está en el hilo")
    void noReemplazaUnaSesion() {
        UUID persona = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, persona));

        assertThatThrownBy(() -> ejecutor.ejecutar(() -> true)).isInstanceOf(IllegalStateException.class);

        assertThat(UsuarioActual.idRequerido()).isEqualTo(persona);
    }

    @Test
    @DisplayName("ADR-0014: los claims del Sistema llevan solo perfil_id, sin sub")
    void claimsDelSistemaSinSub() {
        assertThat(GestorTransaccionesSiglim.claims(new AutenticacionSistema(SISTEMA)))
                .isEqualTo("{\"perfil_id\":\"" + SISTEMA + "\"}");
    }
}
