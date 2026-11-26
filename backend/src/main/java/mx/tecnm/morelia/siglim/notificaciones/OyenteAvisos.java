package mx.tecnm.morelia.siglim.notificaciones;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.notificaciones.NotificacionRepository.DatosJornada;
import mx.tecnm.morelia.siglim.notificaciones.NotificacionRepository.DatosPartido;
import mx.tecnm.morelia.siglim.partidos.eventos.CedulaEnviada;
import mx.tecnm.morelia.siglim.programacion.eventos.JornadaPublicada;
import mx.tecnm.morelia.siglim.tesoreria.TipoCobro;
import mx.tecnm.morelia.siglim.tesoreria.eventos.CobroVencido;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe los avisos de RF-142 al escuchar los eventos de dominio de los demás módulos (M-14.1).
 *
 * <ul>
 *   <li><b>Misma transacción (ADR-0010 §1).</b> Cada oyente es síncrono y exige la transacción de
 *       quien publica el evento ({@link Propagation#MANDATORY}): el aviso existe si y solo si la
 *       operación se confirma, y un error aquí la revierte (RNF-004). Ningún otro módulo depende de
 *       este paquete (ADR-0006).</li>
 *   <li><b>Sin {@code Autorizacion.exigir}.</b> Es un efecto del sistema sobre una operación ya
 *       autorizada, como los cobros automáticos; RLS sigue aplicando ({@code notificaciones_crear}).</li>
 *   <li><b>Destinatarios por permiso (ADR-0007).</b> Nunca por nombre de rol; ver
 *       {@link NotificacionRepository}. Sin destinatarios no se escribe nada y la operación sigue.</li>
 *   <li><b>Contenido (RNF-012).</b> Qué pasó y un enlace a la pantalla del trámite; sin CURP, nombres
 *       de personas, documentos ni datos de menores. Los textos son plantillas de este módulo
 *       (ADR-0010 §6).</li>
 *   <li><b>Sin correo.</b> RF-143 es backlog (B-32): no se escribe en {@code correos_salientes}.</li>
 * </ul>
 *
 * <p>Refs: H-25.1, RF-121, RF-122, RF-141, RF-142, RNF-004, RNF-012, ADR-0006, ADR-0007, ADR-0010
 */
@Component
class OyenteAvisos {

    static final String PERMISO_COBROS = "tesoreria.cobro:CONSULTAR";
    static final String PERMISO_JORNADAS = "programacion.jornada:CONSULTAR";

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale MEXICO = Locale.forLanguageTag("es-MX");

    private final NotificacionRepository notificaciones;

    OyenteAvisos(NotificacionRepository notificaciones) {
        this.notificaciones = notificaciones;
    }

    /**
     * Cédula enviada a validación → aviso al árbitro central designado (RF-122, criterio 1 de H-25).
     *
     * @return cuántos avisos se escribieron
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public int alEnviarCedula(CedulaEnviada evento) {
        List<UUID> arbitros = notificaciones.arbitrosCentrales(evento.ligaId(), evento.partidoId());
        if (arbitros.isEmpty()) {
            return 0;
        }
        String partido = notificaciones.datosPartido(evento.ligaId(), evento.partidoId())
                .map(OyenteAvisos::partido).orElse("tu partido");
        String cuerpo = "La cédula de " + partido + " espera tu validación";
        if (evento.plazoVenceEn() != null) {
            cuerpo += " antes del " + fechaYHora(evento.plazoVenceEn());
        }
        notificaciones.insertar(arbitros, EventoAviso.CEDULA_PENDIENTE, "Cédula pendiente de validación",
                cuerpo + ".", "/app/partidos/" + evento.partidoId());
        return arbitros.size();
    }

    /**
     * Cobro vencido → aviso a la directiva del club deudor (RF-141).
     *
     * @return cuántos avisos se escribieron
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public int alVencerCobro(CobroVencido evento) {
        List<UUID> directivas = notificaciones.directivasDeClubes(evento.ligaId(), Set.of(evento.clubId()),
                PERMISO_COBROS);
        String cuerpo = "Venció un cobro de " + tipo(evento.tipo()) + " por " + monto(evento.monto())
                + " MXN con fecha límite del " + evento.fechaLimite().format(FECHA) + ".";
        notificaciones.insertar(directivas, EventoAviso.COBRO_VENCIDO, "Cobro vencido", cuerpo, "/app/tesoreria");
        return directivas.size();
    }

    /**
     * Jornada publicada → aviso a la directiva de cada club que juega en ella (RF-121, RF-142).
     *
     * @return cuántos avisos se escribieron
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public int alPublicarJornada(JornadaPublicada evento) {
        DatosJornada jornada = notificaciones.datosJornada(evento.ligaId(), evento.jornadaId()).orElse(null);
        if (jornada == null) {
            return 0;
        }
        List<UUID> directivas = notificaciones.directivasDeClubes(evento.ligaId(), jornada.clubes(),
                PERMISO_JORNADAS);
        String cuerpo = "Se publicó la jornada " + jornada.numero() + " de " + jornada.categoria() + " – "
                + jornada.grupo() + ". Consulta tus partidos.";
        notificaciones.insertar(directivas, EventoAviso.PROGRAMACION_PUBLICADA, "Programación publicada", cuerpo,
                "/app/programacion/jornadas");
        return directivas.size();
    }

    private static String partido(DatosPartido datos) {
        return datos.local() + " vs " + datos.visitante() + " (jornada " + datos.jornada() + ")";
    }

    private static String fechaYHora(OffsetDateTime momento) {
        return momento.atZoneSameInstant(ZONA_DE_NEGOCIO).format(FECHA_Y_HORA);
    }

    private static String monto(BigDecimal monto) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(MEXICO);
        return formato.format(monto);
    }

    private static String tipo(TipoCobro tipo) {
        return switch (tipo) {
            case ARBITRAJE -> "arbitraje";
            case CANCHA -> "cancha";
            case MULTA -> "multa";
            case MANUAL -> "cuota";
        };
    }
}
