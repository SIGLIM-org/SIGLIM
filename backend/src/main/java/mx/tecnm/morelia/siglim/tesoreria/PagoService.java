package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.AlmacenamientoDocumentos;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ArchivoAlmacenado;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.CarpetaDocumento;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ClaveDocumento;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.tesoreria.PagoRepository.DatosPago;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagos de cobros: registro de un pago hecho fuera del sistema con su comprobante (RF-139) y
 * consulta de los pagos e intentos de un cobro (RF-140).
 *
 * <p>Es una clase aparte de {@link CobroService} porque usa otros permisos
 * ({@code tesoreria.pago:*}) y el almacenamiento de documentos, que el alta de cobros no necesita; el
 * pago simulado (RF-138) se agregará aquí.
 *
 * <ul>
 *   <li><b>Ámbito (ADR-0007).</b> El pago pertenece al club del cobro: se autoriza con
 *       {@link ObjetivoAutorizacion#deClubes} de ese club, así que un cobro fuera del ámbito responde
 *       404 igual que uno inexistente (VAL-024).</li>
 *   <li><b>Orden (H-31.1).</b> Cobro (404) → permiso → campos (400) → estado (409 VAL-028) → monto
 *       (422 RF-139) → subida a S3 → pago → comprobante → auditoría. La subida va después de todas
 *       las validaciones para no dejar objetos huérfanos por un error previsible.</li>
 *   <li><b>Última garantía.</b> El trigger {@code tg_pagos_validar} bloquea el cobro y repite las
 *       reglas de estado y monto: si dos pagos llegan a la vez, el segundo termina en
 *       {@code 409 VAL-028} por {@code TraductorErroresBd}. Su subida a S3 queda huérfana, como
 *       documenta {@link AlmacenamientoDocumentos}.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_PAGO} en la misma transacción; el detalle no
 *       lleva URL, bucket, clave ni nombre del archivo.</li>
 * </ul>
 *
 * <p>Refs: H-31.1, RF-139, RF-140, VAL-024, VAL-028, RNF-018, ADR-0007, ADR-0012, ADR-0013, DEC-56
 */
@Service
public class PagoService {

    private static final String REGISTRAR = "tesoreria.pago:REGISTRAR";
    private static final String CONSULTAR = "tesoreria.cobro:CONSULTAR";
    private static final String MODULO = "M-13";
    private static final String ENTIDAD = "pagos";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final int LONGITUD_REFERENCIA = 60;
    private static final Set<EstadoCobro> ADMITEN_PAGO = EnumSet.of(EstadoCobro.PENDIENTE, EstadoCobro.VENCIDO);

    private final CobroRepository cobros;
    private final PagoRepository pagos;
    private final AlmacenamientoDocumentos almacenamiento;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final UUID ligaId;

    PagoService(CobroRepository cobros, PagoRepository pagos, AlmacenamientoDocumentos almacenamiento,
            Autorizacion autorizacion, AuditoriaService auditoria, Clock reloj, SiglimPropiedades propiedades) {
        this.cobros = cobros;
        this.pagos = pagos;
        this.almacenamiento = almacenamiento;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Registra el pago total de un cobro {@code PENDIENTE} o {@code VENCIDO}, recibido fuera del
     * sistema, con su comprobante; el cobro queda {@code PAGADO} (RF-139).
     *
     * @param archivo bytes del comprobante (JPEG, PNG, WebP o PDF por firma binaria)
     */
    @Transactional
    public PagoDto registrar(UUID cobroId, RegistrarPagoRequest peticion, byte[] archivo) {
        CobroDto cobro = buscarCobro(cobroId);
        autorizacion.exigir(REGISTRAR, ObjetivoAutorizacion.deClubes(cobro.club().id()));

        validarArchivo(archivo);
        validarMedio(peticion.medio());
        validarMonto(peticion.monto());
        OffsetDateTime pagadoEn = pagadoEn(peticion.fechaPago());
        String referencia = referenciaValida(peticion.referencia());

        if (!ADMITEN_PAGO.contains(cobro.estado())) {
            throw ReglaNegocioException.conflicto("VAL-028",
                    "El cobro está " + cobro.estado() + "; no admite otro pago.");
        }
        if (peticion.monto().compareTo(cobro.monto()) != 0) {
            throw ReglaNegocioException.noProcesable("RF-139", "El pago debe cubrir el monto total del cobro ("
                    + cobro.monto().setScale(2, RoundingMode.HALF_UP).toPlainString() + " MXN).");
        }

        UUID pagoId = UUID.randomUUID();
        UUID documentoId = UUID.randomUUID();
        ArchivoAlmacenado comprobante = almacenamiento.guardar(
                new ClaveDocumento(ligaId, CarpetaDocumento.COMPROBANTES, pagoId, documentoId), archivo);

        UUID usuario = UsuarioActual.idRequerido();
        pagos.insertar(new DatosPago(pagoId, cobro.id(), peticion.medio(), peticion.monto(), referencia, pagadoEn,
                usuario));
        pagos.insertarComprobante(documentoId, pagoId, comprobante, usuario);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("cobroId", cobro.id().toString());
        detalle.put("medio", peticion.medio().name());
        detalle.put("monto", peticion.monto());
        detalle.put("pagadoEn", pagadoEn.toString());
        detalle.put("estadoAnterior", cobro.estado().name());
        detalle.put("documentoId", documentoId.toString());
        detalle.put("tipoMime", comprobante.tipoMime());
        detalle.put("tamanoBytes", comprobante.tamanoBytes());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REGISTRAR_PAGO", ENTIDAD, pagoId.toString(), null,
                detalle));

        return pagos.buscar(ligaId, pagoId)
                .orElseThrow(() -> new IllegalStateException("El pago recién registrado no se encontró: " + pagoId));
    }

    /** Pagos e intentos de pago simulado de un cobro del ámbito (RF-140). */
    @Transactional(readOnly = true)
    public HistorialPagosDto historial(UUID cobroId) {
        CobroDto cobro = buscarCobro(cobroId);
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deClubes(cobro.club().id()));
        return new HistorialPagosDto(pagos.listarDeCobro(ligaId, cobroId), pagos.listarIntentos(ligaId, cobroId));
    }

    private CobroDto buscarCobro(UUID cobroId) {
        return cobros.buscar(ligaId, cobroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cobro " + cobroId));
    }

    private static void validarArchivo(byte[] archivo) {
        if (archivo == null) {
            throw invalida("El comprobante de pago es obligatorio.");
        }
        if (archivo.length == 0) {
            throw ArchivoRechazadoException.vacio();
        }
    }

    private static void validarMedio(MedioPago medio) {
        if (medio == null) {
            throw invalida("El medio de pago es obligatorio.");
        }
        if (medio == MedioPago.EN_LINEA_SIMULADO) {
            throw invalida("El pago en línea se registra desde el pago simulado. Elige efectivo, transferencia u otro.");
        }
    }

    /** La escala (dos decimales) la valida {@link RegistrarPagoRequest} con {@code @Digits}. */
    private static void validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw invalida("El monto debe ser mayor que cero.");
        }
    }

    /** Sin fecha, el momento del registro; con fecha, el inicio de ese día en la zona de negocio. */
    private OffsetDateTime pagadoEn(LocalDate fechaPago) {
        if (fechaPago == null) {
            return OffsetDateTime.now(reloj);
        }
        if (fechaPago.isAfter(LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO)))) {
            throw invalida("La fecha de pago no puede ser posterior a hoy.");
        }
        return fechaPago.atStartOfDay(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    private static String referenciaValida(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return null;
        }
        String texto = referencia.strip();
        if (texto.length() > LONGITUD_REFERENCIA) {
            throw invalida("La referencia admite hasta " + LONGITUD_REFERENCIA + " caracteres.");
        }
        return texto;
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
