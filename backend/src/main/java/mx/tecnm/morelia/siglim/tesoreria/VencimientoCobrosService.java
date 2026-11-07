package mx.tecnm.morelia.siglim.tesoreria;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.EjecutorSistema;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.tesoreria.CobroRepository.CobroVencidoFila;
import mx.tecnm.morelia.siglim.tesoreria.eventos.CobroVencido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tarea programada que vence los cobros no pagados (RF-141, ADR-0009).
 *
 * <ul>
 *   <li><b>Regla [PROPUESTA, DEC-60].</b> Vence todo cobro {@code PENDIENTE} con
 *       {@code fecha_limite < hoy}, donde «hoy» es el día en {@code America/Mexico_City} según el
 *       {@link Clock} inyectado: el día límite todavía se puede pagar. Pendiente de confirmar con el PO.</li>
 *   <li><b>Idempotencia (ADR-0009 §2).</b> Un solo {@code UPDATE ... WHERE estado = 'PENDIENTE'
 *       RETURNING}; solo las filas devueltas generan auditoría y evento. Repetir la tarea no cambia
 *       nada, y como la condición es «ya pasó», una ejecución tardía se pone al corriente (§3).</li>
 *   <li><b>Usuario Sistema (ADR-0009 §4).</b> Corre con {@link EjecutorSistema}: la transacción usa
 *       {@code siglim_app} con el perfil Sistema y la auditoría queda a su nombre con el motivo fijo
 *       {@value #MOTIVO}. No pasa por {@code Autorizacion.exigir}: no es la acción de una persona, y
 *       RLS sigue aplicando con la asignación {@code SISTEMA} de ámbito {@code LIGA}.</li>
 *   <li><b>Atomicidad (RNF-015).</b> Cambios, auditoría y eventos van en una transacción: si algo
 *       falla, se revierte completa y la siguiente ejecución lo vuelve a intentar.</li>
 *   <li><b>Cuándo corre.</b> Con el cron {@code siglim.tareas.vencimiento-cobros.cron} (00:05 de
 *       México por defecto) y una vez al arrancar, por si el backend estuvo apagado a esa hora
 *       [DEC-60]. Ambas se apagan con {@code siglim.tareas.vencimiento-cobros.habilitada=false}, como
 *       en las pruebas, que llaman a {@link #vencer()} directamente.</li>
 *   <li><b>Bitácora (ADR-0009 §6).</b> Un {@code INFO} por ejecución con la cantidad y la duración,
 *       sin datos personales; un error se registra como {@code ERROR} sin detener el planificador.</li>
 * </ul>
 *
 * <p>Refs: B-31.1, RF-141, VAL-028, RN-021, RNF-015, DEC-60, ADR-0005, ADR-0009
 */
@Service
public class VencimientoCobrosService {

    static final String MOTIVO = "vencimiento automático por fecha límite";
    private static final Logger log = LoggerFactory.getLogger(VencimientoCobrosService.class);
    private static final String MODULO = "M-13";
    private static final String ACCION = "VENCER_COBRO";
    private static final String ENTIDAD = "cobros";
    private static final String ZONA = "America/Mexico_City";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of(ZONA);

    private final CobroRepository cobros;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final ApplicationEventPublisher eventos;
    private final EjecutorSistema ejecutorSistema;
    private final TransactionTemplate transaccion;
    private final Clock reloj;
    private final UUID ligaId;
    private final boolean habilitada;

    VencimientoCobrosService(CobroRepository cobros, AuditoriaService auditoria, MotivoOperacion motivoOperacion,
            ApplicationEventPublisher eventos, EjecutorSistema ejecutorSistema, PlatformTransactionManager transacciones,
            Clock reloj, SiglimPropiedades propiedades,
            @Value("${siglim.tareas.vencimiento-cobros.habilitada:true}") boolean habilitada) {
        this.cobros = cobros;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.eventos = eventos;
        this.ejecutorSistema = ejecutorSistema;
        this.transaccion = new TransactionTemplate(transacciones);
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
        this.habilitada = habilitada;
    }

    /** Ejecución diaria (ADR-0009 §5); el cron se configura en {@code application.yml}. */
    @Scheduled(cron = "${siglim.tareas.vencimiento-cobros.cron:0 5 0 * * *}", zone = ZONA)
    void ejecutarProgramada() {
        ejecutarSiEstaHabilitada("programada");
    }

    /** Ponerse al corriente al arrancar, por si el backend estuvo apagado a la hora del cron [DEC-60]. */
    @EventListener(ApplicationReadyEvent.class)
    void ejecutarAlArrancar() {
        ejecutarSiEstaHabilitada("al arrancar");
    }

    /**
     * Vence los cobros {@code PENDIENTE} con fecha límite anterior a hoy, como el perfil Sistema.
     *
     * @return cuántos cobros vencieron en esta ejecución
     * @throws RuntimeException si algo falla; la transacción ya se revirtió completa
     */
    public int vencer() {
        long inicio = System.nanoTime();
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
        List<CobroVencidoFila> vencidos = ejecutorSistema.ejecutar(() ->
                transaccion.execute(estado -> vencerEnTransaccion(hoy)));
        int cantidad = vencidos == null ? 0 : vencidos.size();
        log.info("Vencimiento de cobros: {} cobro(s) vencido(s) con fecha límite anterior a {} en {} ms",
                cantidad, hoy, Duration.ofNanos(System.nanoTime() - inicio).toMillis());
        return cantidad;
    }

    private List<CobroVencidoFila> vencerEnTransaccion(LocalDate hoy) {
        motivoOperacion.fijar(MOTIVO);
        List<CobroVencidoFila> vencidos = cobros.vencerPendientes(ligaId, hoy);
        for (CobroVencidoFila cobro : vencidos) {
            auditoria.registrar(new EventoAuditoria(cobro.ligaId(), MODULO, ACCION, ENTIDAD, cobro.id().toString(),
                    MOTIVO, detalle(cobro)));
            eventos.publishEvent(new CobroVencido(cobro.ligaId(), cobro.id(), cobro.clubId(), cobro.equipoId(),
                    cobro.tipo(), cobro.monto(), cobro.fechaLimite()));
        }
        return vencidos;
    }

    /** Sin datos personales: solo estados, fecha y monto (RNF-012). */
    private static Map<String, Object> detalle(CobroVencidoFila cobro) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoCobro.PENDIENTE.name());
        detalle.put("estadoNuevo", EstadoCobro.VENCIDO.name());
        detalle.put("fechaLimite", cobro.fechaLimite().toString());
        detalle.put("monto", cobro.monto());
        return detalle;
    }

    /** Un error no debe detener el hilo del planificador: se registra y la siguiente ejecución reintenta. */
    private void ejecutarSiEstaHabilitada(String origen) {
        if (!habilitada) {
            return;
        }
        try {
            vencer();
        } catch (RuntimeException error) {
            log.error("Vencimiento de cobros ({}) falló; no se venció ningún cobro y se reintentará en la "
                    + "siguiente ejecución.", origen, error);
        }
    }
}
