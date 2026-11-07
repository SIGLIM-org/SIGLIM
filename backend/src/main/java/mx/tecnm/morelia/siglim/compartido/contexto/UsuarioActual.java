package mx.tecnm.morelia.siglim.compartido.contexto;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acceso al usuario de la operación en curso: la persona de la petición o, en una tarea
 * programada, el perfil Sistema ({@link EjecutorSistema}).
 *
 * <p>Los servicios lo usan para llenar columnas como {@code creado_por} o {@code validado_por};
 * nunca reciben el perfil en el cuerpo de la petición (convenciones de API §5). Los historiales y
 * la función {@code siglim.usuario_actual()} lo obtienen solos del contexto de la transacción.
 *
 * <p>Refs: T-02.3, B-31.1, ADR-0005, ADR-0009
 */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    /** La sesión con JWT de la petición, si existe. No incluye al perfil Sistema. */
    public static Optional<AutenticacionSiglim> autenticacion() {
        return sesion().filter(AutenticacionSiglim.class::isInstance).map(AutenticacionSiglim.class::cast);
    }

    /** Quien ejecuta la operación: una persona con JWT o el perfil Sistema, si hay sesión. */
    public static Optional<SesionSiglim> sesion() {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        return actual instanceof SesionSiglim sesion && actual.isAuthenticated()
                ? Optional.of(sesion)
                : Optional.empty();
    }

    /** {@code perfiles.id} de quien ejecuta la operación, si hay sesión. */
    public static Optional<UUID> perfilId() {
        return sesion().map(SesionSiglim::perfilId);
    }

    /**
     * {@code perfiles.id} de quien ejecuta una operación que exige sesión.
     *
     * @throws IllegalStateException si se llama desde una ruta pública o fuera de una petición o de
     *                               {@link EjecutorSistema}: es un error de programación, no del
     *                               usuario
     */
    public static UUID idRequerido() {
        return perfilId().orElseThrow(() -> new IllegalStateException(
                "No hay un usuario autenticado en esta petición; la ruta debe exigir token."));
    }
}
