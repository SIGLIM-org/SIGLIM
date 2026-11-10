package mx.tecnm.morelia.siglim.auditoria;

import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe la auditoría con {@code siglim_app} en la transacción de quien llama.
 *
 * <p>Sin {@code RETURNING}: exigiría que la fila pase la política de lectura de la auditoría, que
 * solo tienen los usuarios de liga, y fallaría para los demás.
 *
 * <p>Refs: H-02.2, RF-093, RNF-015
 */
@Service
class AuditoriaServiceJdbc implements AuditoriaService {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditoriaServiceJdbc(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(EventoAuditoria evento) {
        jdbc.sql("""
                        INSERT INTO auditoria (liga_id, perfil_id, modulo, accion, entidad, entidad_id, motivo, detalle)
                        VALUES (:ligaId, :perfilId, :modulo, :accion, :entidad, :entidadId,
                                coalesce(CAST(:motivo AS text), siglim.motivo_actual()), CAST(:detalle AS jsonb))
                        """)
                .param("ligaId", evento.ligaId())
                .param("perfilId", UsuarioActual.idRequerido())
                .param("modulo", evento.modulo())
                .param("accion", evento.accion())
                .param("entidad", evento.entidad())
                .param("entidadId", evento.entidadId())
                .param("motivo", evento.motivo())
                .param("detalle", evento.detalle() == null ? null : json.writeValueAsString(evento.detalle()))
                .update();
    }
}
