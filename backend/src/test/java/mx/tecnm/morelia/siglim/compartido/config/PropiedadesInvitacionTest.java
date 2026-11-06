package mx.tecnm.morelia.siglim.compartido.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validación de la configuración de invitaciones: el origen del frontend al que vuelve el enlace
 * del correo debe existir y ser un solo origen (DEF-03, RNF-019).
 *
 * <p>Prueba unitaria sin Spring: usa el validador de Jakarta directamente.
 */
class PropiedadesInvitacionTest {

    private static final String MENSAJE_FORMATO =
            "SIGLIM_FRONTEND_ORIGEN es esquema://host[:puerto], sin ruta, diagonal final ni comodín";

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private static Set<String> mensajes(String origenFrontend) {
        var propiedades = new PropiedadesInvitacion("clave-secreta-de-prueba", Duration.ofSeconds(5),
                Duration.ofSeconds(10), origenFrontend);
        return validador.validate(propiedades).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("DEF-03: acepta un origen con esquema, host y puerto")
    void aceptaOrigenValido() {
        assertThat(mensajes("http://localhost:5173")).isEmpty();
        assertThat(mensajes("https://siglim.ejemplo.mx")).isEmpty();
    }

    @Test
    @DisplayName("DEF-03: rechaza SIGLIM_FRONTEND_ORIGEN vacío y lo dice")
    void rechazaOrigenVacio() {
        assertThat(mensajes("")).contains("Define SIGLIM_FRONTEND_ORIGEN en backend/.env");
        assertThat(mensajes(null)).contains("Define SIGLIM_FRONTEND_ORIGEN en backend/.env");
    }

    @Test
    @DisplayName("DEF-03: SIGLIM_FRONTEND_ORIGEN no admite ruta, diagonal final ni comodín")
    void rechazaRutaDiagonalOComodin() {
        assertThat(mensajes("http://localhost:5173/app")).containsExactly(MENSAJE_FORMATO);
        assertThat(mensajes("http://localhost:5173/")).containsExactly(MENSAJE_FORMATO);
        assertThat(mensajes("*")).containsExactly(MENSAJE_FORMATO);
        assertThat(mensajes("https://*.ejemplo.mx")).containsExactly(MENSAJE_FORMATO);
        assertThat(mensajes("http://localhost:5173,https://siglim.ejemplo.mx")).containsExactly(MENSAJE_FORMATO);
    }
}
