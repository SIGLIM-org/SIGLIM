package mx.tecnm.morelia.siglim.tesoreria;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.tesoreria.PagoRepository.DatosIntentoPago;
import mx.tecnm.morelia.siglim.tesoreria.PagoRepository.DatosPagoSimulado;
import mx.tecnm.morelia.siglim.tesoreria.PagoRepository.IntentoConCobro;
import mx.tecnm.morelia.siglim.tesoreria.eventos.CobroPagado;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pago en línea simulado de un cobro (RF-138, ADR-0012): inicia un intento con referencia ficticia y
 * lo confirma de forma atómica.
 *
 * <ul>
 *   <li><b>Ámbito (ADR-0007).</b> El cobro pertenece a su club: se autoriza con
 *       {@code tesoreria.pago:PAGAR} y {@link ObjetivoAutorizacion#deClubes} de ese club. Un cobro
 *       fuera del ámbito responde 404 igual que uno inexistente (VAL-024).</li>
 *   <li><b>Estados (ERS §4.3.5).</b> Solo un cobro {@code PENDIENTE} inicia un intento. Uno
 *       {@code RECHAZADO} vuelve a {@code PENDIENTE} al reintentar. Uno {@code PAGADO} o
 *       {@code VENCIDO} no se paga en línea ({@code 409 VAL-028}).</li>
 *   <li><b>Un intento pendiente por cobro.</b> {@code ux_intentos_pendiente} lo garantiza; si ya
 *       existe, el inicio devuelve el mismo intento para que un doble clic no cree otro.</li>
 *   <li><b>Confirmación atómica (RNF-004).</b> El cobro y el intento se bloquean con {@code FOR
 *       UPDATE}, siempre en ese orden. La confirmación actualiza el intento con {@code WHERE estado =
 *       'PENDIENTE'}; si otra persona ganó la carrera, responde {@code 409 CONCURRENCIA}. El pago
 *       lleva {@code EN_LINEA_SIMULADO}, el intento y el monto del cobro; el trigger
 *       {@code tg_pagos_validar} vuelve a validar el estado y deja el cobro {@code PAGADO}.</li>
 *   <li><b>Rechazo.</b> El intento queda {@code RECHAZADO} y el cobro pasa a {@code RECHAZADO}; desde
 *       ahí puede iniciarse otro intento.</li>
 *   <li><b>Auditoría y evento (RF-093, ADR-0012).</b> {@code INICIAR_PAGO_SIMULADO} y
 *       {@code CONFIRMAR_PAGO_SIMULADO} se registran en la misma transacción y el pago aprobado
 *       publica {@link CobroPagado} para los avisos (ADR-0010).</li>
 * </ul>
 *
 * <p>No se reciben ni se guardan datos de tarjeta: la referencia es ficticia.
 *
 * <p>Refs: H-30.1, RF-138, VAL-028, RNF-004, DEC-37, ADR-0007, ADR-0012
 */
@Service
public class PagoSimuladoService {

    private static final String PAGAR = "tesoreria.pago:PAGAR";
    private static final String MODULO = "M-13";
    private static final String ENTIDAD = "intentos_pago";
    private static final int INTENTOS_REFERENCIA = 5;

    private final CobroRepository cobros;
    private final PagoRepository pagos;
    private final PasarelaDePago pasarela;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final UUID ligaId;

    PagoSimuladoService(CobroRepository cobros, PagoRepository pagos, PasarelaDePago pasarela,
            Autorizacion autorizacion, AuditoriaService auditoria, ApplicationEventPublisher eventos, Clock reloj,
            SiglimPropiedades propiedades) {
        this.cobros = cobros;
        this.pagos = pagos;
        this.pasarela = pasarela;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Inicia un intento de pago simulado {@code PENDIENTE} para un cobro del club (RF-138).
     *
     * <p>Si el cobro estaba {@code RECHAZADO}, lo devuelve a {@code PENDIENTE} para reintentar. Si ya
     * hay un intento pendiente, devuelve ese mismo (idempotente ante un doble clic).
     *
     * @throws ReglaNegocioException 403 {@code VAL-015} sin permiso; 409 {@code VAL-028} si el cobro
     *                               está {@code PAGADO} o {@code VENCIDO}; 404 si no existe o está
     *                               fuera del ámbito (VAL-024)
     */
    @Transactional
    public IntentoPagoDto iniciar(UUID cobroId) {
        CobroDto visible = buscarCobro(cobroId);
        autorizacion.exigir(PAGAR, ObjetivoAutorizacion.deClubes(visible.club().id()));

        CobroDto cobro = bloquear(cobroId);
        exigirCobroAdmitePagoEnLinea(cobro);

        if (cobro.estado() == EstadoCobro.RECHAZADO && !cobros.reactivar(ligaId, cobroId)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.CONCURRENCIA,
                    "El cobro cambió mientras se iniciaba el pago. Recarga la página e intenta de nuevo.");
        }

        Optional<IntentoPagoDto> pendiente = pagos.buscarIntentoPendiente(ligaId, cobroId);
        if (pendiente.isPresent()) {
            return pendiente.get();
        }

        IntentoPagoDto intento = insertarIntento(cobroId);
        auditarInicio(cobro, intento);
        return intento;
    }

    /**
     * Confirma un intento: {@code APROBADO} crea el pago {@code EN_LINEA_SIMULADO} y deja el cobro
     * {@code PAGADO}; {@code RECHAZADO} deja el intento y el cobro {@code RECHAZADO} (RF-138).
     *
     * @throws ReglaNegocioException 400 {@code VALIDACION} sin resultado; 403 {@code VAL-015} sin
     *                               permiso; 404 si el intento no existe o está fuera del ámbito
     *                               (VAL-024); 409 {@code VAL-028} si el intento ya se resolvió o el
     *                               cobro no está {@code PENDIENTE}; 409 {@code CONCURRENCIA} si otra
     *                               confirmación ganó la carrera
     */
    @Transactional
    public IntentoPagoDto confirmar(UUID intentoId, ResultadoPagoSimulado resultado) {
        if (resultado == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica si el pago se aprueba o se rechaza.");
        }

        IntentoConCobro resumen = pagos.buscarIntentoConCobro(ligaId, intentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Intento de pago " + intentoId));
        autorizacion.exigir(PAGAR, ObjetivoAutorizacion.deClubes(resumen.clubId()));

        CobroDto cobro = bloquear(resumen.cobroId());
        IntentoPagoDto intento = pagos.buscarIntentoParaActualizar(ligaId, intentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Intento de pago " + intentoId));

        if (!"PENDIENTE".equals(intento.estado())) {
            throw ReglaNegocioException.conflicto("VAL-028",
                    "El intento de pago ya fue " + intento.estado().toLowerCase() + "; inicia uno nuevo si el cobro sigue pendiente.");
        }
        if (cobro.estado() != EstadoCobro.PENDIENTE) {
            throw ReglaNegocioException.conflicto("VAL-028", cobroNoPagable(cobro.estado()));
        }

        pasarela.confirmar(intento.referencia(), resultado);
        OffsetDateTime resueltoEn = OffsetDateTime.now(reloj);
        return resultado == ResultadoPagoSimulado.APROBADO
                ? aprobar(cobro, intento, resueltoEn)
                : rechazar(cobro, intento, resueltoEn);
    }

    private IntentoPagoDto aprobar(CobroDto cobro, IntentoPagoDto intento, OffsetDateTime resueltoEn) {
        if (!pagos.resolverIntento(intento.id(), ResultadoPagoSimulado.APROBADO.name(), resueltoEn)) {
            throw perdidaDeCarrera();
        }
        UUID pagoId = UUID.randomUUID();
        pagos.insertarPagoSimulado(new DatosPagoSimulado(pagoId, cobro.id(), intento.id(), cobro.monto(),
                intento.referencia(), resueltoEn, UsuarioActual.idRequerido()));

        IntentoPagoDto resuelto = resuelto(intento, ResultadoPagoSimulado.APROBADO, resueltoEn);
        Map<String, Object> detalle = detalleConfirmacion(resuelto, cobro, pagoId);
        detalle.put("monto", cobro.monto());
        auditar("CONFIRMAR_PAGO_SIMULADO", resuelto, detalle);

        eventos.publishEvent(new CobroPagado(ligaId, cobro.id(), cobro.club().id(), pagoId, intento.id(),
                cobro.monto(), MedioPago.EN_LINEA_SIMULADO, resueltoEn));
        return resuelto;
    }

    private IntentoPagoDto rechazar(CobroDto cobro, IntentoPagoDto intento, OffsetDateTime resueltoEn) {
        if (!pagos.resolverIntento(intento.id(), ResultadoPagoSimulado.RECHAZADO.name(), resueltoEn)) {
            throw perdidaDeCarrera();
        }
        if (!cobros.marcarRechazado(ligaId, cobro.id())) {
            throw perdidaDeCarrera();
        }
        IntentoPagoDto resuelto = resuelto(intento, ResultadoPagoSimulado.RECHAZADO, resueltoEn);
        auditar("CONFIRMAR_PAGO_SIMULADO", resuelto, detalleConfirmacion(resuelto, cobro, null));
        return resuelto;
    }

    private IntentoPagoDto insertarIntento(UUID cobroId) {
        UUID iniciadoPor = UsuarioActual.idRequerido();
        OffsetDateTime iniciadoEn = OffsetDateTime.now(reloj);
        for (int i = 0; i < INTENTOS_REFERENCIA; i++) {
            String referencia = pasarela.iniciar(cobroId);
            UUID id = UUID.randomUUID();
            if (pagos.insertarIntento(new DatosIntentoPago(id, cobroId, referencia, iniciadoPor, iniciadoEn))) {
                return new IntentoPagoDto(id, referencia, "PENDIENTE", iniciadoEn, null);
            }
        }
        throw ReglaNegocioException.conflicto(ErroresTecnicos.CONCURRENCIA,
                "No se pudo generar una referencia de pago única. Intenta de nuevo.");
    }

    private CobroDto buscarCobro(UUID cobroId) {
        return cobros.buscar(ligaId, cobroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cobro " + cobroId));
    }

    private CobroDto bloquear(UUID cobroId) {
        return cobros.bloquear(ligaId, cobroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cobro " + cobroId));
    }

    private static void exigirCobroAdmitePagoEnLinea(CobroDto cobro) {
        if (cobro.estado() == EstadoCobro.PAGADO || cobro.estado() == EstadoCobro.VENCIDO) {
            throw ReglaNegocioException.conflicto("VAL-028", cobroNoPagable(cobro.estado()));
        }
    }

    private static String cobroNoPagable(EstadoCobro estado) {
        return estado == EstadoCobro.VENCIDO
                ? "El cobro está VENCIDO; no se puede pagar en línea. Comunícate con Tesorería."
                : "El cobro está " + estado + "; no se puede pagar en línea.";
    }

    private static ReglaNegocioException perdidaDeCarrera() {
        return ReglaNegocioException.conflicto(ErroresTecnicos.CONCURRENCIA,
                "Otra confirmación de pago ganó la carrera. Recarga el cobro para ver su estado.");
    }

    private static IntentoPagoDto resuelto(IntentoPagoDto intento, ResultadoPagoSimulado resultado,
            OffsetDateTime resueltoEn) {
        return new IntentoPagoDto(intento.id(), intento.referencia(), resultado.name(), intento.iniciadoEn(),
                resueltoEn);
    }

    private static Map<String, Object> detalleConfirmacion(IntentoPagoDto intento, CobroDto cobro, UUID pagoId) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("cobroId", cobro.id().toString());
        detalle.put("referencia", intento.referencia());
        detalle.put("resultado", intento.estado());
        detalle.put("estadoCobro", intento.estado().equals(ResultadoPagoSimulado.APROBADO.name())
                ? EstadoCobro.PAGADO.name() : EstadoCobro.RECHAZADO.name());
        if (pagoId != null) {
            detalle.put("pagoId", pagoId.toString());
        }
        return detalle;
    }

    private void auditarInicio(CobroDto cobro, IntentoPagoDto intento) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("cobroId", cobro.id().toString());
        detalle.put("referencia", intento.referencia());
        detalle.put("estadoAnterior", cobro.estado().name());
        detalle.put("estado", EstadoCobro.PENDIENTE.name());
        auditar("INICIAR_PAGO_SIMULADO", intento, detalle);
    }

    private void auditar(String accion, IntentoPagoDto intento, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, intento.id().toString(), null,
                detalle));
    }
}
