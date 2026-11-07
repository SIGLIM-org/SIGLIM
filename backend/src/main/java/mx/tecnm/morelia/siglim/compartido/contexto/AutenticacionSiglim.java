package mx.tecnm.morelia.siglim.compartido.contexto;

import java.io.Serial;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Sesión autenticada de SIGLIM: el JWT de Supabase Auth más el perfil de SIGLIM que le corresponde.
 *
 * <p>El {@code sub} del token es el usuario de {@code auth.users}; el sistema trabaja con el
 * {@code perfiles.id}, que también existe para el perfil Sistema sin cuenta en Auth (ADR-0014). Se
 * resuelve una vez por petición en {@link ConvertidorAutenticacion}.
 *
 * <p>No lleva autoridades: los permisos ({@code modulo.recurso:ACCION} con ámbito) se consultan en
 * la base de datos al autorizar cada operación (ADR-0003 punto 3, ADR-0007).
 *
 * <p>Refs: T-02.3, ADR-0005, ADR-0014
 */
public final class AutenticacionSiglim extends JwtAuthenticationToken implements SesionSiglim {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID perfilId;

    public AutenticacionSiglim(Jwt token, UUID perfilId) {
        super(token, List.of(), token.getSubject());
        this.perfilId = perfilId;
    }

    /** {@code perfiles.id} del usuario: el que registran la auditoría y los historiales. */
    @Override
    public UUID perfilId() {
        return perfilId;
    }

    /** {@code auth.users.id}: el {@code sub} del token. */
    public UUID usuarioAuthId() {
        return UUID.fromString(getName());
    }
}
