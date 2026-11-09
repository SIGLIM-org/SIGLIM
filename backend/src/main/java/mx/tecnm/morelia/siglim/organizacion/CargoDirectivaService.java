package mx.tecnm.morelia.siglim.organizacion;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta, registra y autoriza cargos de directiva, conservando su vigencia histórica (RF-102). */
@Service
public class CargoDirectivaService {

    private static final String CONSULTAR_CLUB = "organizacion.club:CONSULTAR";
    private static final String EDITAR_DIRECTIVA = "organizacion.directiva:EDITAR";
    private static final String APROBAR_DIRECTIVA = "organizacion.directiva:APROBAR";
    private static final String MODULO = "M-03";
    private static final String ENTIDAD = "cargos_directiva";

    private final CargoDirectivaRepository cargos;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    CargoDirectivaService(CargoDirectivaRepository cargos, Autorizacion autorizacion,
            AuditoriaService auditoria, SiglimPropiedades propiedades) {
        this.cargos = cargos;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /** Consulta el historial, incluidos los cambios pendientes, del club dentro del ámbito (RF-102). */
    @Transactional(readOnly = true)
    public List<CargoDirectivaDto> consultar(UUID clubId) {
        autorizacion.exigir(CONSULTAR_CLUB, ObjetivoAutorizacion.deClubes(clubId));
        if (!cargos.existeClub(ligaId, clubId)) {
            throw new RecursoNoEncontradoException("Club " + clubId);
        }
        return cargos.listar(ligaId, clubId);
    }

    /** Registra el cambio con {@code autorizado_por = NULL}; queda pendiente de autorización (RF-102). */
    @Transactional
    public CargoDirectivaDto registrar(UUID clubId, RegistrarCargoDirectivaRequest peticion) {
        autorizacion.exigir(EDITAR_DIRECTIVA, ObjetivoAutorizacion.deClubes(clubId));
        if (!cargos.bloquearClub(ligaId, clubId)) {
            throw new RecursoNoEncontradoException("Club " + clubId);
        }
        UUID id = cargos.insertar(clubId, peticion);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clubId", clubId.toString());
        detalle.put("cargo", peticion.cargo().name());
        detalle.put("desde", peticion.desde().toString());
        detalle.put("estado", EstadoCargoDirectiva.PENDIENTE.name());
        auditar("REGISTRAR_CARGO_DIRECTIVA", id, detalle);
        return buscar(id);
    }

    /**
     * Autoriza un cambio y termina el cargo vigente anterior el día previo al nuevo {@code desde}.
     * Los bloqueos del club serializan autorizaciones concurrentes del mismo club (RF-102).
     */
    @Transactional
    public CargoDirectivaDto autorizar(UUID id) {
        CargoDirectivaDto referencia = buscar(id);
        autorizacion.exigir(APROBAR_DIRECTIVA, ObjetivoAutorizacion.deClubes(referencia.clubId()));
        if (!cargos.bloquearClub(ligaId, referencia.clubId())) {
            throw new RecursoNoEncontradoException("Club " + referencia.clubId());
        }
        CargoDirectivaDto pendiente = cargos.buscarParaActualizar(ligaId, id)
                .orElseThrow(() -> noExiste(id));
        if (pendiente.estado() != EstadoCargoDirectiva.PENDIENTE) {
            throw ReglaNegocioException.conflicto("VAL-003",
                    "El cargo ya fue autorizado y no admite otra autorización.");
        }

        CargoDirectivaRepository.VigenciaCargo anterior = cargos.buscarVigente(pendiente.clubId(), pendiente.cargo())
                .orElse(null);
        LocalDate hastaAnterior = null;
        if (anterior != null) {
            if (!pendiente.desde().isAfter(anterior.desde())) {
                throw ReglaNegocioException.conflicto("VAL-003",
                        "La vigencia nueva debe iniciar después del inicio del cargo vigente.");
            }
            hastaAnterior = pendiente.desde().minusDays(1);
            cargos.terminar(anterior.id(), hastaAnterior);
        }

        UUID perfilId = UsuarioActual.idRequerido();
        cargos.autorizar(id, perfilId);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clubId", pendiente.clubId().toString());
        detalle.put("cargo", pendiente.cargo().name());
        detalle.put("desde", pendiente.desde().toString());
        if (anterior != null) {
            detalle.put("cargoAnteriorId", anterior.id().toString());
            detalle.put("cargoAnteriorHasta", hastaAnterior.toString());
        }
        auditar("AUTORIZAR_CARGO_DIRECTIVA", id, detalle);
        return buscar(id);
    }

    private CargoDirectivaDto buscar(UUID id) {
        return cargos.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private void auditar(String accion, UUID id, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, id.toString(), null, detalle));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Cargo de directiva " + id);
    }
}
