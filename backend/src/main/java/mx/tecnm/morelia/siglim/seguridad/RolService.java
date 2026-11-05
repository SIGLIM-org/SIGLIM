package mx.tecnm.morelia.siglim.seguridad;

import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaRol;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de roles de la liga que la pantalla de usuarios usa para invitar y asignar (RF-094,
 * RF-095).
 *
 * <p>Solo lectura en esta historia: la edición de permisos de un rol es H-20 (RF-095, RF-096).
 * Exige {@code seguridad.rol:CONSULTAR} en toda la liga; los roles no pertenecen a un club.
 *
 * <p>Refs: H-03.1, RF-094, RF-095, ADR-0007
 */
@Service
public class RolService {

    private static final String CONSULTAR = "seguridad.rol:CONSULTAR";

    private final RolRepository roles;
    private final Autorizacion autorizacion;
    private final UUID ligaId;

    RolService(RolRepository roles, Autorizacion autorizacion, SiglimPropiedades propiedades) {
        this.roles = roles;
        this.autorizacion = autorizacion;
        this.ligaId = propiedades.ligaId();
    }

    /** Todos los roles de la liga, con su ámbito predeterminado y si se pueden asignar. */
    @Transactional(readOnly = true)
    public List<RolDto> listar() {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return roles.listar(ligaId).stream().map(RolService::aDto).toList();
    }

    private static RolDto aDto(FilaRol fila) {
        return new RolDto(fila.id(), fila.codigo(), fila.nombre(), fila.descripcion(),
                Ambito.valueOf(fila.ambitoPredeterminado()), fila.asignable());
    }
}
