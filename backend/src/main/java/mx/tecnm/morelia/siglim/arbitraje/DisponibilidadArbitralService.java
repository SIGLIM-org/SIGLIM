package mx.tecnm.morelia.siglim.arbitraje;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.DisponibilidadArbitralRepository.ArbitroDisponible;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disponibilidad de los árbitros por fecha y horario (RF-022, B-09.1).
 *
 * <ul>
 *   <li><b>Quién la registra (Apéndice E).</b> La Comisión de Arbitraje con ámbito {@code LIGA} o
 *       el propio árbitro con ámbito {@code PROPIO}. El permiso es
 *       {@code arbitraje.disponibilidad:EDITAR} y el objetivo es la persona del árbitro: un ámbito
 *       de club no la alcanza, así que responde {@code 404} (VAL-024, ADR-0007).</li>
 *   <li><b>Solo árbitros validados.</b> El alta exige {@code estado = VALIDADO} ({@code 409
 *       RF-022}). Un árbitro propuesto, rechazado o dado de baja no registra disponibilidad. Los
 *       periodos ya creados se pueden consultar y retirar aunque el estado cambie, para que la
 *       Comisión pueda limpiar el calendario sin tocar el catálogo.</li>
 *   <li><b>Periodo.</b> {@code desde} debe ser anterior a {@code hasta}; el API usa intervalos
 *       {@code [desde, hasta)} como el {@code tstzrange} y la restricción
 *       {@code ck_disponibilidad_arbitral} no admite un periodo vacío. {@code disponible} es
 *       opcional y vale {@code true} por omisión: con {@code false} el árbitro declara que no puede
 *       ser designado en ese periodo.</li>
 *   <li><b>Borrado.</b> La disponibilidad no es historial deportivo; el diccionario no la marca como
 *       histórica, así que {@code DELETE} quita el registro. Si la regla cambiara, se sustituiría
 *       por una desactivación.</li>
 *   <li><b>Auditoría (RF-093).</b> El alta y el borrado se registran en {@code M-05} sobre
 *       {@code disponibilidad_arbitral} con {@code arbitroId}, {@code desde}, {@code hasta} y
 *       {@code disponible}; sin nombres, CURP ni datos de contacto.</li>
 * </ul>
 *
 * <p>En la designación solo advierte: {@link ElegibilidadArbitral} la muestra como {@code RF-022} en
 * los candidatos, sin filtrar ni bloquear (DEC-204).
 *
 * <p>Refs: B-09.1, RF-022, RF-093, ADR-0007
 */
@Service
public class DisponibilidadArbitralService {

    private static final String PERMISO = "arbitraje.disponibilidad:EDITAR";
    private static final String MODULO = "M-05";
    private static final String ENTIDAD = "disponibilidad_arbitral";

    private final DisponibilidadArbitralRepository disponibilidades;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    DisponibilidadArbitralService(DisponibilidadArbitralRepository disponibilidades,
            Autorizacion autorizacion, AuditoriaService auditoria, SiglimPropiedades propiedades) {
        this.disponibilidades = disponibilidades;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Periodos de disponibilidad del árbitro, por inicio. Sin fechas devuelve todos; con {@code
     * desde} y {@code hasta} solo los que se traslapan con el rango.
     *
     * @throws ReglaNegocioException 400 {@code VALIDACION} si solo llega una fecha o el orden es
     *                               incorrecto
     * @throws RecursoNoEncontradoException 404 si el árbitro no existe en la liga o está fuera de
     *                                      ámbito (VAL-024)
     */
    @Transactional(readOnly = true)
    public List<DisponibilidadArbitralDto> listar(UUID arbitroId, OffsetDateTime desde,
            OffsetDateTime hasta) {
        ArbitroDisponible arbitro = buscarArbitro(arbitroId);
        autorizacion.exigir(PERMISO, ObjetivoAutorizacion.dePersonas(arbitro.personaId()));
        return disponibilidades.listar(arbitroId, rango(desde, hasta));
    }

    /**
     * Registra un periodo de disponibilidad (RF-022).
     *
     * <p>Orden: árbitro de la liga ({@code 404}) → permiso ({@code 403}/{@code 404}) → periodo
     * ({@code 400 VALIDACION}) → estado {@code VALIDADO} ({@code 409 RF-022}) → alta.
     *
     * @throws ReglaNegocioException 409 {@code RF-022} si el árbitro no está validado
     */
    @Transactional
    public DisponibilidadArbitralDto registrar(UUID arbitroId,
            RegistrarDisponibilidadArbitralRequest peticion) {
        ArbitroDisponible arbitro = buscarArbitro(arbitroId);
        autorizacion.exigir(PERMISO, ObjetivoAutorizacion.dePersonas(arbitro.personaId()));

        if (peticion == null || peticion.periodo() == null) {
            throw invalida("Indica el periodo de disponibilidad.");
        }
        PeriodoArbitral periodo = peticion.periodo();
        validarPeriodo(periodo);
        if (arbitro.estado() != EstadoArbitro.VALIDADO) {
            throw ReglaNegocioException.conflicto("RF-022",
                    "Solo se registra disponibilidad de un árbitro validado; este está "
                            + arbitro.estado().texto() + ".");
        }

        boolean disponible = peticion.disponible() == null || peticion.disponible();
        DisponibilidadArbitralDto registro = disponibilidades.insertar(arbitroId, periodo, disponible);
        auditar("REGISTRAR_DISPONIBILIDAD_ARBITRAL", registro.id(), arbitroId, periodo, disponible);
        return registro;
    }

    /**
     * Retira un periodo de disponibilidad (RF-022). El registro debe pertenecer al árbitro; si no
     * existe, responde {@code 404} para no revelar si es de otro (VAL-024).
     *
     * @throws RecursoNoEncontradoException 404 si el árbitro o el periodo no existen
     */
    @Transactional
    public void eliminar(UUID arbitroId, UUID disponibilidadId) {
        ArbitroDisponible arbitro = buscarArbitro(arbitroId);
        autorizacion.exigir(PERMISO, ObjetivoAutorizacion.dePersonas(arbitro.personaId()));
        if (disponibilidadId == null) {
            throw invalida("Indica el periodo de disponibilidad que se retira.");
        }

        DisponibilidadArbitralDto actual = disponibilidades.buscar(arbitroId, disponibilidadId)
                .orElseThrow(() -> noExisteDisponibilidad(disponibilidadId));
        if (!disponibilidades.eliminar(arbitroId, disponibilidadId)) {
            throw noExisteDisponibilidad(disponibilidadId);
        }
        auditar("ELIMINAR_DISPONIBILIDAD_ARBITRAL", disponibilidadId, arbitroId,
                actual.periodo(), actual.disponible());
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private ArbitroDisponible buscarArbitro(UUID arbitroId) {
        if (arbitroId == null) {
            throw noExiste(null);
        }
        return disponibilidades.buscarArbitro(ligaId, arbitroId)
                .orElseThrow(() -> noExiste(arbitroId));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Árbitro " + id);
    }

    private static RecursoNoEncontradoException noExisteDisponibilidad(UUID id) {
        return new RecursoNoEncontradoException("Disponibilidad " + id);
    }

    private static void validarPeriodo(PeriodoArbitral periodo) {
        if (periodo.desde() == null || periodo.hasta() == null) {
            throw invalida("Indica el inicio y el fin del periodo de disponibilidad.");
        }
        if (!periodo.desde().isBefore(periodo.hasta())) {
            throw invalida("El inicio del periodo debe ser anterior a su fin.");
        }
    }

    /** Rango opcional del listado: o vienen las dos fechas, o no viene ninguna. */
    private static PeriodoArbitral rango(OffsetDateTime desde, OffsetDateTime hasta) {
        if (desde == null && hasta == null) {
            return null;
        }
        if (desde == null || hasta == null) {
            throw invalida("Indica las fechas desde y hasta del rango, o ninguna de las dos.");
        }
        PeriodoArbitral rango = new PeriodoArbitral(desde, hasta);
        validarPeriodo(rango);
        return rango;
    }

    private void auditar(String accion, UUID entidadId, UUID arbitroId, PeriodoArbitral periodo,
            boolean disponible) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("arbitroId", arbitroId.toString());
        detalle.put("desde", periodo.desde().toString());
        detalle.put("hasta", periodo.hasta().toString());
        detalle.put("disponible", disponible);
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(),
                null, detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
