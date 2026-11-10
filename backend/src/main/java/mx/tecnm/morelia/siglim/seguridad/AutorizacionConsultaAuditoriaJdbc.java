package mx.tecnm.morelia.siglim.seguridad;

import mx.tecnm.morelia.siglim.auditoria.AutorizacionConsultaAuditoria;
import org.springframework.stereotype.Component;

/**
 * Resuelve el permiso de consulta de la auditoría con {@link Autorizacion}.
 *
 * <p>Refs: H-21.1, RF-134, VAL-015, ADR-0006, ADR-0007
 */
@Component
class AutorizacionConsultaAuditoriaJdbc implements AutorizacionConsultaAuditoria {

    private static final String CONSULTAR = "auditoria.registro:CONSULTAR";

    private final Autorizacion autorizacion;

    AutorizacionConsultaAuditoriaJdbc(Autorizacion autorizacion) {
        this.autorizacion = autorizacion;
    }

    @Override
    public boolean alcanzaTodaLaLiga() {
        return autorizacion.alcance(CONSULTAR).todaLaLiga();
    }
}
