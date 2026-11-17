package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.programacion.eventos.JornadaPublicada;
import mx.tecnm.morelia.siglim.tesoreria.CobroAutomaticoRepository.CobroAutomatico;
import mx.tecnm.morelia.siglim.tesoreria.CobroAutomaticoRepository.EquipoCobrable;
import mx.tecnm.morelia.siglim.tesoreria.CobroAutomaticoRepository.PartidoCobrable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobros automáticos de arbitraje y cancha al publicarse una jornada (M-13.2, B-30, RF-136).
 *
 * <ul>
 *   <li><b>Disparo (ADR-0006).</b> Oyente síncrono de {@link JornadaPublicada}: corre dentro de la
 *       transacción del dictamen del Presidente ({@link Propagation#MANDATORY}). Si algo falla, se
 *       revierte también la publicación (RNF-004). No pasa por {@code Autorizacion.exigir}: es un
 *       efecto del sistema sobre la aprobación que ya se autorizó con
 *       {@code programacion.jornada:APROBAR}; RLS sigue aplicando ({@code cobros_crear}).</li>
 *   <li><b>Qué se cobra (RF-136, DEC-36).</b> Por cada partido de la jornada que no esté
 *       {@code CANCELADO}, a <b>cada</b> equipo: un cobro {@code ARBITRAJE} y un cobro
 *       {@code CANCHA} con el monto de la tarifa de la temporada y la categoría (RF-135), cargados
 *       al club del equipo. No hay cobro de cancha al equipo cuyo club es dueño de la cancha
 *       (DEC-33): juega en la propia. [PROPUESTA] Cada equipo paga la tarifa completa; el ERS no
 *       dice que se reparta entre los dos.</li>
 *   <li><b>Sin tarifa.</b> [PROPUESTA] Si la categoría no tiene tarifa del concepto, o su monto es
 *       cero ({@code ck_cobros_monto} exige monto positivo), ese cobro se omite y la omisión queda en
 *       la auditoría de la jornada; la publicación no se bloquea por falta de tarifas.</li>
 *   <li><b>Periodo de pago.</b> [PROPUESTA] Empieza el día de la publicación y termina
 *       {@code siglim.tesoreria.cobros-automaticos.dias-para-pagar} días después del día del partido
 *       (7 por defecto), ambos en {@code America/Mexico_City}; si el partido ya pasó, se cuentan desde
 *       hoy. Después, la tarea de vencimiento lo vence (RF-141, DEC-60).</li>
 *   <li><b>Idempotencia.</b> {@code ux_cobros_partido} impide dos cobros del mismo partido, equipo y
 *       tipo; el {@code INSERT} usa {@code ON CONFLICT DO NOTHING} y solo audita los nuevos.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code GENERAR_COBRO} en {@code M-13} por cobro (entidad
 *       {@code cobros}, con partido, equipo, tipo, monto y fechas) y {@code GENERAR_COBROS_JORNADA}
 *       sobre la jornada con los totales y las omisiones. Sin concepto ni datos personales.</li>
 * </ul>
 *
 * <p>Refs: B-30, RF-093, RF-135, RF-136, RF-141, RNF-004, DEC-33, DEC-36, DEC-66, ADR-0006
 */
@Service
public class CobroAutomaticoService {

    static final String MOTIVO = "cobro automático al publicar la jornada";
    private static final String MODULO = "M-13";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final int LONGITUD_CONCEPTO = 200;

    private final CobroAutomaticoRepository repositorio;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final int diasParaPagar;

    CobroAutomaticoService(CobroAutomaticoRepository repositorio, AuditoriaService auditoria, Clock reloj,
            @Value("${siglim.tesoreria.cobros-automaticos.dias-para-pagar:7}") int diasParaPagar) {
        if (diasParaPagar < 0) {
            throw new IllegalArgumentException(
                    "siglim.tesoreria.cobros-automaticos.dias-para-pagar no puede ser negativo: " + diasParaPagar);
        }
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.diasParaPagar = diasParaPagar;
    }

    /**
     * Genera los cobros de la jornada recién publicada, en la transacción de la publicación.
     *
     * @return cuántos cobros nuevos se generaron
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public int alPublicarJornada(JornadaPublicada evento) {
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
        List<Map<String, Object>> omitidos = new ArrayList<>();
        int generados = 0;
        int partidos = 0;
        for (PartidoCobrable partido : repositorio.partidosDeJornada(evento.ligaId(), evento.jornadaId())) {
            partidos++;
            Map<ConceptoTarifa, BigDecimal> tarifas = repositorio.tarifas(partido.temporadaId(), partido.categoriaId());
            LocalDate fechaLimite = fechaLimite(partido, hoy);
            for (EquipoCobrable equipo : List.of(partido.local(), partido.visitante())) {
                EquipoCobrable rival = equipo == partido.local() ? partido.visitante() : partido.local();
                for (ConceptoTarifa concepto : ConceptoTarifa.values()) {
                    if (concepto == ConceptoTarifa.CANCHA && equipo.clubId().equals(partido.canchaClubId())) {
                        continue;
                    }
                    BigDecimal monto = tarifas.get(concepto);
                    if (monto == null || monto.signum() <= 0) {
                        omitidos.add(omision(partido, equipo, concepto, monto == null ? "SIN_TARIFA" : "TARIFA_EN_CERO"));
                        continue;
                    }
                    CobroAutomatico cobro = new CobroAutomatico(partido.id(), equipo.clubId(), equipo.id(),
                            TipoCobro.valueOf(concepto.name()), concepto(concepto, partido, equipo, rival), monto,
                            hoy, fechaLimite);
                    Optional<UUID> id = repositorio.insertarSiNoExiste(evento.ligaId(), cobro, evento.aprobadaPor());
                    if (id.isPresent()) {
                        generados++;
                        auditoria.registrar(new EventoAuditoria(evento.ligaId(), MODULO, "GENERAR_COBRO", "cobros",
                                id.get().toString(), MOTIVO, detalle(evento, cobro)));
                    }
                }
            }
        }
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("partidos", partidos);
        resumen.put("cobrosGenerados", generados);
        resumen.put("omitidos", omitidos);
        auditoria.registrar(new EventoAuditoria(evento.ligaId(), MODULO, "GENERAR_COBROS_JORNADA", "jornadas",
                evento.jornadaId().toString(), MOTIVO, resumen));
        return generados;
    }

    /** [PROPUESTA] Día del partido (o hoy, si ya pasó) más el plazo configurado. */
    private LocalDate fechaLimite(PartidoCobrable partido, LocalDate hoy) {
        LocalDate diaPartido = partido.inicio().atZoneSameInstant(ZONA_DE_NEGOCIO).toLocalDate();
        return (diaPartido.isBefore(hoy) ? hoy : diaPartido).plusDays(diasParaPagar);
    }

    /** Lo que ve el club: «Arbitraje · Jornada 3 · Halcones vs Toros», recortado a 200 caracteres. */
    private static String concepto(ConceptoTarifa concepto, PartidoCobrable partido, EquipoCobrable equipo,
            EquipoCobrable rival) {
        String nombre = concepto == ConceptoTarifa.ARBITRAJE ? "Arbitraje" : "Cancha";
        String texto = nombre + " · Jornada " + partido.jornadaNumero() + " · " + equipo.nombre() + " vs "
                + rival.nombre();
        return texto.length() <= LONGITUD_CONCEPTO ? texto : texto.substring(0, LONGITUD_CONCEPTO - 1) + "…";
    }

    private static Map<String, Object> omision(PartidoCobrable partido, EquipoCobrable equipo,
            ConceptoTarifa concepto, String causa) {
        Map<String, Object> omision = new LinkedHashMap<>();
        omision.put("partidoId", partido.id().toString());
        omision.put("equipoId", equipo.id().toString());
        omision.put("tipo", concepto.name());
        omision.put("causa", causa);
        return omision;
    }

    private static Map<String, Object> detalle(JornadaPublicada evento, CobroAutomatico cobro) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("jornadaId", evento.jornadaId().toString());
        detalle.put("partidoId", cobro.partidoId().toString());
        detalle.put("clubId", cobro.clubId().toString());
        detalle.put("equipoId", cobro.equipoId().toString());
        detalle.put("tipo", cobro.tipo().name());
        detalle.put("monto", cobro.monto());
        detalle.put("periodoInicio", cobro.periodoInicio().toString());
        detalle.put("fechaLimite", cobro.fechaLimite().toString());
        detalle.put("estado", EstadoCobro.PENDIENTE.name());
        return detalle;
    }
}
