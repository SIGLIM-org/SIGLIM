package mx.tecnm.morelia.siglim.compartido.contexto;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Sesión del perfil Sistema durante una tarea programada (ADR-0005 punto 3, ADR-0009 punto 4).
 *
 * <p>El perfil Sistema no tiene cuenta en Supabase Auth ({@code ck_perfiles_sistema}), así que no
 * hay JWT: solo el {@code perfiles.id} de la semilla.
 *
 * <p><b>Por qué el constructor es privado del paquete:</b> esta sesión no debe poder nacer de una
 * petición HTTP. La única forma de obtenerla es {@link EjecutorSistema}, que además se niega a
 * ejecutarse dentro de una petición. La cadena de seguridad solo produce {@link AutenticacionSiglim}
 * a partir de un JWT, y ningún token puede resolverse al perfil Sistema porque su
 * {@code usuario_auth_id} es siempre nulo.
 *
 * <p>Sin autoridades, igual que la sesión con JWT: los permisos viven en la base de datos (ADR-0007).
 *
 * <p>Refs: B-31.1, ADR-0005, ADR-0009, ADR-0014
 */
public final class AutenticacionSistema extends AbstractAuthenticationToken implements SesionSiglim {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID perfilId;

    AutenticacionSistema(UUID perfilId) {
        super(List.of());
        this.perfilId = perfilId;
        setAuthenticated(true);
    }

    @Override
    public UUID perfilId() {
        return perfilId;
    }

    /** Sin credenciales: el perfil Sistema no inicia sesión. */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return perfilId;
    }
}
