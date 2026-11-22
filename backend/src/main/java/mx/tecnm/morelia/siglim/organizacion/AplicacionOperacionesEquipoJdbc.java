package mx.tecnm.morelia.siglim.organizacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoRepository.OperacionAplicable;
import mx.tecnm.morelia.siglim.temporadas.AplicacionOperacionesEquipo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica las operaciones de equipo {@code AUTORIZADA} cuando {@code temporadas} cierra la temporada
 * (B-05.2, RF-103, RF-104, RN-040).
 *
 * <p>Por cada operación, en este orden y dentro de la transacción del cierre:
 *
 * <ol>
 *   <li>una {@code VENTA} cambia {@code equipos.club_id} al club destino, solo si el equipo sigue
 *       siendo del club de origen; el {@code club_origen_id} de la operación no se reescribe y queda
 *       como historial de propietarios (RN-040);</li>
 *   <li>la operación pasa a {@code APLICADA} con {@code aplicada_en};</li>
 *   <li>se audita {@code APLICAR_OPERACION_EQUIPO} en {@code M-03}, con tipo, equipo, clubes y
 *       estados; el motivo es el del cierre, que {@code MotivoOperacion} ya fijó (RF-093).</li>
 * </ol>
 *
 * <p>{@code CONSERVACION_PLAZA} no mueve el club: conserva la plaza del equipo y solo se marca
 * aplicada (RF-104). La transacción es obligatoria ({@link Propagation#MANDATORY}) porque el cierre
 * y la aplicación deben confirmarse o revertirse juntos.
 *
 * <p>Refs: B-05.2, H-04.1, RF-103, RF-104, RN-040, RN-021, RF-093, ADR-0006
 */
@Component
class AplicacionOperacionesEquipoJdbc implements AplicacionOperacionesEquipo {

    private static final String MODULO = "M-03";
    private static final String ENTIDAD = "operaciones_equipo";

    private final OperacionEquipoRepository operaciones;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final UUID ligaId;

    AplicacionOperacionesEquipoJdbc(OperacionEquipoRepository operaciones, AuditoriaService auditoria,
            Clock reloj, SiglimPropiedades propiedades) {
        this.operaciones = operaciones;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int aplicarAutorizadas(UUID temporadaId) {
        List<OperacionAplicable> autorizadas = operaciones.listarAutorizadas(temporadaId);
        if (autorizadas.isEmpty()) {
            return 0;
        }

        OffsetDateTime aplicadaEn = OffsetDateTime.now(reloj);
        for (OperacionAplicable operacion : autorizadas) {
            if (operacion.tipo() == TipoOperacionEquipo.VENTA) {
                int equiposMovidos = operaciones.cambiarClub(operacion.equipoId(),
                        operacion.clubOrigenId(), operacion.clubDestinoId());
                if (equiposMovidos != 1) {
                    throw new IllegalStateException("El equipo " + operacion.equipoId()
                            + " ya no pertenece al club de origen de la operación " + operacion.id() + ".");
                }
            }
            if (operaciones.marcarAplicada(operacion.id(), aplicadaEn) != 1) {
                throw new IllegalStateException("La operación " + operacion.id()
                        + " dejó de estar AUTORIZADA durante el cierre de la temporada.");
            }
            auditar(operacion, temporadaId);
        }
        return autorizadas.size();
    }

    private void auditar(OperacionAplicable operacion, UUID temporadaId) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tipo", operacion.tipo().name());
        detalle.put("equipoId", operacion.equipoId().toString());
        detalle.put("clubOrigenId", operacion.clubOrigenId().toString());
        if (operacion.clubDestinoId() != null) {
            detalle.put("clubDestinoId", operacion.clubDestinoId().toString());
        }
        detalle.put("temporadaId", temporadaId.toString());
        detalle.put("estadoAnterior", EstadoOperacionEquipo.AUTORIZADA.name());
        detalle.put("estado", EstadoOperacionEquipo.APLICADA.name());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "APLICAR_OPERACION_EQUIPO", ENTIDAD,
                operacion.id().toString(), null, detalle));
    }
}
