package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import mx.tecnm.morelia.siglim.tesoreria.TarifaRepository.TemporadaTarifas;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarifas de arbitraje y de cancha por temporada y categoría (RF-135, M-13.1).
 *
 * <ul>
 *   <li><b>Permiso (ADR-0007).</b> Consultar y configurar exigen {@code tesoreria.tarifa:EDITAR} en
 *       ámbito de liga (convenciones de API §6.13): la matriz no tiene un permiso de consulta propio.</li>
 *   <li><b>Unicidad.</b> Una tarifa por temporada, categoría y concepto ({@code ARBITRAJE} o
 *       {@code CANCHA}). Se valida antes y responde {@code 409 DUPLICADO}; la restricción
 *       {@code UNIQUE} de la tabla es la última garantía ante dos altas simultáneas.</li>
 *   <li><b>Categoría de la temporada.</b> La categoría debe estar asignada a la temporada
 *       ({@code categorias_temporada}); si no, {@code 422 REFERENCIA_INVALIDA}.</li>
 *   <li><b>Monto.</b> {@link BigDecimal} mayor o igual a cero; la escala (dos decimales) se valida en
 *       las peticiones con {@code @Digits}, con la regla de la columna {@code numeric(12,2)}.</li>
 *   <li><b>Temporada cerrada (RF-003).</b> Sus tarifas son de solo lectura: el alta y la edición
 *       responden {@code 409 RF-003}. La temporada se lee con {@code FOR SHARE} para serializar con
 *       su cierre.</li>
 *   <li><b>Valores [DEMO] (PEN-042).</b> Las tarifas reales están pendientes: todo monto capturado es
 *       de demostración y la respuesta lo marca con {@code esDemo} para el rótulo «No oficial».</li>
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_TARIFA} y {@code EDITAR_TARIFA} en la misma
 *       transacción; la edición guarda el monto anterior y el nuevo.</li>
 * </ul>
 *
 * <p>Refs: B-29.1, RF-135, RF-003, RF-093, PEN-042, ADR-0007
 */
@Service
public class TarifaService {

    /** Mientras PEN-042 siga abierto, ninguna tarifa capturada es oficial. */
    static final boolean TARIFAS_DEMO = true;

    private static final String EDITAR = "tesoreria.tarifa:EDITAR";
    private static final String MODULO = "M-13";
    private static final String ENTIDAD = "tarifas";

    private final TarifaRepository tarifas;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    TarifaService(TarifaRepository tarifas, Autorizacion autorizacion, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.tarifas = tarifas;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Tarifas de una temporada (RF-135). Orden: permiso ({@code 403}) → temporada indicada
     * ({@code 400 VALIDACION}) → temporada de la liga ({@code 404}).
     */
    @Transactional(readOnly = true)
    public List<TarifaDto> listar(UUID temporadaId) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (temporadaId == null) {
            throw invalida("Indica la temporada de las tarifas.");
        }
        tarifas.buscarTemporada(ligaId, temporadaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Temporada " + temporadaId));
        return tarifas.listar(ligaId, temporadaId);
    }

    /**
     * Registra la tarifa de un concepto para una categoría de la temporada (RF-135).
     *
     * <p>Orden: permiso ({@code 403}) → monto ({@code 400}) → temporada, con candado
     * ({@code 422 REFERENCIA_INVALIDA}) → temporada cerrada ({@code 409 RF-003}) → categoría de la
     * temporada ({@code 422 REFERENCIA_INVALIDA}) → combinación repetida ({@code 409 DUPLICADO}) →
     * alta y auditoría.
     */
    @Transactional
    public TarifaDto crear(CrearTarifaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (peticion == null || peticion.temporadaId() == null || peticion.categoriaId() == null
                || peticion.concepto() == null) {
            throw invalida("Indica la temporada, la categoría y el concepto de la tarifa.");
        }
        validarMonto(peticion.monto());

        TemporadaTarifas temporada = tarifas.bloquearTemporada(ligaId, peticion.temporadaId())
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "La temporada indicada no existe. Actualiza la página y elige otra."));
        exigirAbierta(temporada);
        if (!tarifas.categoriaEnTemporada(temporada.id(), peticion.categoriaId())) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "La categoría indicada no participa en la temporada «" + temporada.nombre() + "».");
        }
        if (tarifas.existe(temporada.id(), peticion.categoriaId(), peticion.concepto())) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe la tarifa de " + texto(peticion.concepto())
                            + " para esa categoría en la temporada: edita su monto.");
        }

        UUID id = tarifas.insertar(temporada.id(), peticion.categoriaId(), peticion.concepto(), peticion.monto());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", temporada.id().toString());
        detalle.put("categoriaId", peticion.categoriaId().toString());
        detalle.put("concepto", peticion.concepto().name());
        detalle.put("monto", peticion.monto());
        detalle.put("esDemo", TARIFAS_DEMO);
        auditar("REGISTRAR_TARIFA", id, detalle);
        return buscar(id);
    }

    /**
     * Cambia el monto de una tarifa (RF-135).
     *
     * <p>Orden: permiso ({@code 403}) → monto ({@code 400}) → tarifa, con candado ({@code 404}) →
     * temporada cerrada ({@code 409 RF-003}) → cambio y auditoría. Un monto igual al vigente no
     * cambia nada ni se audita.
     */
    @Transactional
    public TarifaDto actualizar(UUID id, ActualizarTarifaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        validarMonto(peticion == null ? null : peticion.monto());

        TarifaDto actual = tarifas.bloquear(ligaId, id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tarifa " + id));
        TemporadaTarifas temporada = tarifas.bloquearTemporada(ligaId, actual.temporada().id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tarifa " + id));
        exigirAbierta(temporada);
        if (actual.monto().compareTo(peticion.monto()) == 0) {
            return actual;
        }

        tarifas.actualizarMonto(id, peticion.monto());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", temporada.id().toString());
        detalle.put("categoriaId", actual.categoria().id().toString());
        detalle.put("concepto", actual.concepto().name());
        detalle.put("montoAnterior", actual.monto());
        detalle.put("monto", peticion.monto());
        detalle.put("esDemo", TARIFAS_DEMO);
        auditar("EDITAR_TARIFA", id, detalle);
        return buscar(id);
    }

    private TarifaDto buscar(UUID id) {
        return tarifas.buscar(ligaId, id).orElseThrow(() -> new RecursoNoEncontradoException("Tarifa " + id));
    }

    private static void exigirAbierta(TemporadaTarifas temporada) {
        if (temporada.estado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + temporada.nombre() + "» está cerrada: sus tarifas son de solo lectura.");
        }
    }

    /**
     * Monto mayor o igual a cero (criterio de B-29 y {@code ck_tarifas_monto}). La escala se valida
     * una sola vez, en las peticiones con {@code @Digits}.
     */
    private static void validarMonto(BigDecimal monto) {
        if (monto == null) {
            throw invalida("El monto es obligatorio.");
        }
        if (monto.signum() < 0) {
            throw invalida("El monto no puede ser negativo.");
        }
    }

    private static String texto(ConceptoTarifa concepto) {
        return concepto == ConceptoTarifa.ARBITRAJE ? "arbitraje" : "cancha";
    }

    private void auditar(String accion, UUID id, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, id.toString(), null, detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
