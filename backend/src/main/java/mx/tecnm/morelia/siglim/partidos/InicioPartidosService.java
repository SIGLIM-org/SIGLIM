package mx.tecnm.morelia.siglim.partidos;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.EjecutorSistema;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.partidos.PartidosRepository.PartidoIniciado;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tarea programada que inicia los partidos a su hora (RF-127, ADR-0009, H-15.2).
 *
 * <ul>
 *   <li><b>Regla.</b> Pasa a {@code EN_CURSO} todo partido {@code PROGRAMADO} o {@code REPROGRAMADO}
 *       cuyo {@code inicio} ya llegó según el {@link Clock} inyectado, solo en jornadas
 *       {@code PUBLICADO} <b>[PROPUESTA]</b> (DEC-163): una jornada en borrador o revisión todavía puede
 *       cambiar y no se juega. {@code SUSPENDIDO}, {@code FINALIZADO} y {@code CANCELADO} no se tocan.</li>
 *   <li><b>Idempotencia (ADR-0009 §2).</b> Un solo {@code UPDATE ... WHERE estado IN (...) RETURNING};
 *       solo las filas devueltas generan auditoría. Repetir la tarea no cambia nada, y como la condición
 *       es «ya llegó», una ejecución tardía se pone al corriente (§3).</li>
 *   <li><b>Usuario Sistema (ADR-0009 §4).</b> Corre con {@link EjecutorSistema}: el historial
 *       ({@code tg_partidos_historial}) y la auditoría {@code INICIAR_PARTIDO} (M-08) quedan a nombre del
 *       perfil Sistema con el motivo fijo {@value #MOTIVO} (RN-021). No pasa por
 *       {@code Autorizacion.exigir}: no es la acción de una persona.</li>
 *   <li><b>Atomicidad (RNF-015).</b> Cambios y auditoría van en una transacción: si algo falla, se
 *       revierte completa y la siguiente ejecución lo vuelve a intentar.</li>
 *   <li><b>Cuándo corre.</b> Con el cron {@code siglim.tareas.inicio-partidos.cron} (cada minuto por
 *       defecto, ADR-0009 §5); se apaga con {@code siglim.tareas.inicio-partidos.habilitada=false},
 *       como en las pruebas, que llaman a {@link #iniciar()} directamente.</li>
 *   <li><b>Bitácora (ADR-0009 §6).</b> Un {@code INFO} por ejecución que inició algún partido, con la
 *       cantidad y la duración y sin datos personales; un error se registra como {@code ERROR} sin
 *       detener el planificador.</li>
 * </ul>
 *
 * <p>Refs: H-15.2, RF-127, RF-045, RN-021, RNF-015, DEC-23, DEC-163, ADR-0005, ADR-0009
 */
@Service
public class InicioPartidosService {

    static final String MOTIVO = "inicio automático por fecha y hora";
    private static final Logger log = LoggerFactory.getLogger(InicioPartidosService.class);
    private static final String MODULO = "M-08";
    private static final String ACCION = "INICIAR_PARTIDO";
    private static final String ENTIDAD = "partidos";

    private final PartidosRepository partidos;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final EjecutorSistema ejecutorSistema;
    private final TransactionTemplate transaccion;
    private final Clock reloj;
    private final UUID ligaId;
    private final boolean habilitada;

    InicioPartidosService(PartidosRepository partidos, AuditoriaService auditoria, MotivoOperacion motivoOperacion,
            EjecutorSistema ejecutorSistema, PlatformTransactionManager transacciones, Clock reloj,
            SiglimPropiedades propiedades,
            @Value("${siglim.tareas.inicio-partidos.habilitada:true}") boolean habilitada) {
        this.partidos = partidos;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.ejecutorSistema = ejecutorSistema;
        this.transaccion = new TransactionTemplate(transacciones);
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
        this.habilitada = habilitada;
    }

    /** Ejecución periódica (ADR-0009 §5); el cron se configura en {@code application.yml}. */
    @Scheduled(cron = "${siglim.tareas.inicio-partidos.cron:0 * * * * *}", zone = "America/Mexico_City")
    void ejecutarProgramada() {
        if (!habilitada) {
            return;
        }
        try {
            iniciar();
        } catch (RuntimeException error) {
            log.error("Inicio de partidos falló; no se inició ningún partido y se reintentará en la siguiente "
                    + "ejecución.", error);
        }
    }

    /**
     * Inicia, como el perfil Sistema, los partidos cuya hora ya llegó.
     *
     * @return cuántos partidos se iniciaron en esta ejecución
     * @throws RuntimeException si algo falla; la transacción ya se revirtió completa
     */
    public int iniciar() {
        long inicio = System.nanoTime();
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        List<PartidoIniciado> iniciados = ejecutorSistema.ejecutar(() ->
                transaccion.execute(estado -> iniciarEnTransaccion(ahora)));
        int cantidad = iniciados == null ? 0 : iniciados.size();
        if (cantidad > 0) {
            log.info("Inicio de partidos: {} partido(s) en curso en {} ms", cantidad,
                    Duration.ofNanos(System.nanoTime() - inicio).toMillis());
        }
        return cantidad;
    }

    private List<PartidoIniciado> iniciarEnTransaccion(OffsetDateTime ahora) {
        motivoOperacion.fijar(MOTIVO);
        List<PartidoIniciado> iniciados = partidos.iniciarLlegados(ligaId, ahora);
        for (PartidoIniciado partido : iniciados) {
            auditoria.registrar(new EventoAuditoria(ligaId, MODULO, ACCION, ENTIDAD, partido.id().toString(), MOTIVO,
                    detalle(partido)));
        }
        return iniciados;
    }

    /** Sin datos personales: solo estados e inicio (RNF-012). */
    private static Map<String, Object> detalle(PartidoIniciado partido) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", partido.estadoAnterior().name());
        detalle.put("estadoNuevo", EstadoPartido.EN_CURSO.name());
        detalle.put("inicio", partido.inicio().toString());
        return detalle;
    }
}
