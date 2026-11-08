package mx.tecnm.morelia.siglim.tesoreria;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.organizacion.ConsultaAdeudosClub;
import org.springframework.stereotype.Component;

/**
 * Le dice a {@code organizacion} si un club tiene cobros sin liquidar, sin que ese módulo, de un
 * nivel inferior, dependa de este (ADR-0006). Corre en la transacción del servicio que llama.
 *
 * <p>Refs: B-04, RF-105, RN-052, DEC-61, ADR-0006
 */
@Component
class ConsultaAdeudosClubJdbc implements ConsultaAdeudosClub {

    private final CobroRepository cobros;
    private final UUID ligaId;

    ConsultaAdeudosClubJdbc(CobroRepository cobros, SiglimPropiedades propiedades) {
        this.cobros = cobros;
        this.ligaId = propiedades.ligaId();
    }

    @Override
    public boolean tieneCobrosPendientesOVencidos(UUID clubId) {
        return cobros.existenPendientesOVencidos(ligaId, clubId);
    }
}
