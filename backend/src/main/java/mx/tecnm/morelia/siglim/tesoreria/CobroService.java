package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.tesoreria.CobroRepository.DatosCobro;
import mx.tecnm.morelia.siglim.tesoreria.CobroRepository.EquipoDeClub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobros manuales y consulta de cobros (RF-137, RF-140).
 *
 * <ul>
 *   <li><b>Ámbito (ADR-0007).</b> Un cobro pertenece al club al que se carga. Se autoriza con
 *       {@link ObjetivoAutorizacion#deClubes} de ese club, así que la directiva de otro club recibe
 *       404 igual que con un cobro inexistente (VAL-024). El listado filtra con el alcance del
 *       permiso y cuenta con la misma condición.</li>
 *   <li><b>Solo cobros manuales (RF-137).</b> {@code POST /cobros} crea {@code tipo = MANUAL} con
 *       estado {@code PENDIENTE}. Los cobros de arbitraje, cancha y multa los generan sus flujos
 *       (RF-136 y RF-133, backlog) y no se aceptan por este endpoint.</li>
 *   <li><b>Destino.</b> El club es obligatorio y debe existir en la liga. El equipo es opcional; si
 *       se indica, debe existir y pertenecer a ese club ({@code 422 REFERENCIA_INVALIDA}).</li>
 *   <li><b>Monto y periodo.</b> El monto es {@link BigDecimal} mayor que cero y con hasta dos
 *       decimales; la fecha límite no puede ser anterior al inicio del periodo, que sin indicarse es
 *       el día de la zona de negocio.</li>
 *   <li><b>Auditoría (RF-093).</b> El alta se audita en la misma transacción como
 *       {@code REGISTRAR_COBRO}; el detalle solo lleva identificadores, monto y fechas.</li>
 * </ul>
 *
 * <p>Refs: H-29.1, RF-137, RF-140, UI-027, VAL-024, ADR-0007
 */
@Service
public class CobroService {

    private static final String CONSULTAR = "tesoreria.cobro:CONSULTAR";
    private static final String CREAR = "tesoreria.cobro:CREAR";
    private static final String MODULO = "M-13";
    private static final String ENTIDAD = "cobros";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final int LONGITUD_CONCEPTO = 200;

    private final CobroRepository cobros;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final UUID ligaId;

    CobroService(CobroRepository cobros, Autorizacion autorizacion, AuditoriaService auditoria, Clock reloj,
            SiglimPropiedades propiedades) {
        this.cobros = cobros;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /** Listado filtrado por ámbito en el SQL, también en el total (RF-140, ADR-0007). */
    @Transactional(readOnly = true)
    public Pagina<CobroDto> listar(FiltroCobros filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        filtro.validar();
        return Pagina.de(cobros.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                cobros.contar(ligaId, filtro, alcance));
    }

    /**
     * Registra un cobro manual {@code PENDIENTE} a un club o a uno de sus equipos (RF-137).
     *
     * <p>Orden: club existe (404) → permiso sobre ese club → equipo existe y es de ese club (422) →
     * concepto y monto válidos (400) → periodo válido (400) → alta y auditoría.
     */
    @Transactional
    public CobroDto crear(CrearCobroRequest peticion) {
        CobroDto.ReferenciaDto club = cobros.buscarClub(ligaId, peticion.clubId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Club " + peticion.clubId()));
        autorizacion.exigir(CREAR, ObjetivoAutorizacion.deClubes(club.id()));

        EquipoDeClub equipo = null;
        if (peticion.equipoId() != null) {
            equipo = cobros.buscarEquipo(ligaId, peticion.equipoId())
                    .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                            "El equipo indicado no existe. Actualiza la página y elige otro."));
            if (!equipo.clubId().equals(club.id())) {
                throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "El equipo «" + equipo.nombre() + "» no pertenece al club indicado.");
            }
        }

        String concepto = conceptoValido(peticion.concepto());
        validarMonto(peticion.monto());
        LocalDate periodoInicio = peticion.periodoInicio() == null
                ? LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO)) : peticion.periodoInicio();
        validarPeriodo(periodoInicio, peticion.fechaLimite());

        DatosCobro datos = new DatosCobro(club.id(), equipo == null ? null : equipo.id(), TipoCobro.MANUAL,
                concepto, peticion.monto(), periodoInicio, peticion.fechaLimite());
        UUID id = cobros.insertar(ligaId, datos, UsuarioActual.idRequerido());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clubId", datos.clubId().toString());
        if (datos.equipoId() != null) {
            detalle.put("equipoId", datos.equipoId().toString());
        }
        detalle.put("tipo", datos.tipo().name());
        detalle.put("monto", datos.monto());
        detalle.put("periodoInicio", datos.periodoInicio().toString());
        detalle.put("fechaLimite", datos.fechaLimite().toString());
        detalle.put("estado", EstadoCobro.PENDIENTE.name());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REGISTRAR_COBRO", ENTIDAD, id.toString(), null,
                detalle));
        return buscar(id);
    }

    private CobroDto buscar(UUID id) {
        return cobros.buscar(ligaId, id).orElseThrow(() -> new RecursoNoEncontradoException("Cobro " + id));
    }

    private static String conceptoValido(String concepto) {
        String texto = concepto == null ? "" : concepto.strip();
        if (texto.isEmpty()) {
            throw invalida("El concepto es obligatorio.");
        }
        if (texto.length() > LONGITUD_CONCEPTO) {
            throw invalida("El concepto admite hasta " + LONGITUD_CONCEPTO + " caracteres.");
        }
        return texto;
    }

    private static void validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw invalida("El monto debe ser mayor que cero.");
        }
        if (monto.stripTrailingZeros().scale() > 2) {
            throw invalida("El monto admite hasta dos decimales.");
        }
    }

    private static void validarPeriodo(LocalDate periodoInicio, LocalDate fechaLimite) {
        if (fechaLimite == null) {
            throw invalida("La fecha límite es obligatoria.");
        }
        if (fechaLimite.isBefore(periodoInicio)) {
            throw invalida("La fecha límite no puede ser anterior al inicio del periodo de pago.");
        }
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
