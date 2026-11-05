package mx.tecnm.morelia.siglim.canchas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
 * Catálogo de sedes de la liga, que agrupan canchas (RF-028).
 *
 * <ul>
 *   <li><b>Permisos (D1, [PROPUESTA]).</b> El listado exige {@code canchas.cancha:CONSULTAR} en
 *       cualquier ámbito, con {@code autorizacion.alcance}: las sedes son de la liga y un ámbito de
 *       club también las ve para filtrar sus canchas. Registrar y editar exigen
 *       {@code canchas.cancha:EDITAR} sobre la liga.</li>
 *   <li><b>Sin borrado (D3, [PROPUESTA]).</b> No hay baja ni desactivación de sedes: la tabla no
 *       tiene columna {@code activa} y el API no expone {@code DELETE}.</li>
 *   <li><b>Unicidad.</b> El nombre es único por liga. El servicio lo valida antes de escribir
 *       ({@code 409 DUPLICADO}) y la restricción {@code UNIQUE (liga_id, nombre)} es la última
 *       garantía.</li>
 *   <li><b>Listado (D5, [PROPUESTA]).</b> Sin paginar, por nombre y luego por id.</li>
 *   <li><b>Auditoría.</b> Alta y edición con el módulo M-06 (RF-093). El detalle solo nombra los
 *       campos: no lleva la dirección, con el mismo criterio de privacidad que las canchas.</li>
 * </ul>
 *
 * <p>Refs: B-11.1, RF-028, RF-093, VAL-015, ADR-0007, ADR-0011
 */
@Service
public class SedeService {

    private static final String CONSULTAR = "canchas.cancha:CONSULTAR";
    private static final String EDITAR = "canchas.cancha:EDITAR";
    private static final String MODULO = "M-06";
    private static final String ENTIDAD = "sedes";

    private final SedeRepository sedes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    SedeService(SedeRepository sedes, Autorizacion autorizacion, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.sedes = sedes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /** Sedes de la liga ordenadas por nombre (RF-028, D1, D5). */
    @Transactional(readOnly = true)
    public List<SedeDto> listar() {
        autorizacion.alcance(CONSULTAR);
        return sedes.listar(ligaId);
    }

    /** Registra una sede con nombre único en la liga (RF-028). */
    @Transactional
    public SedeDto crear(CrearSedeRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String nombre = nombreValido(peticion.nombre());
        String direccion = textoOpcional(peticion.direccion());
        validarNombreLibre(nombre, null);

        UUID id = sedes.insertar(ligaId, nombre, direccion);

        List<String> campos = direccion == null ? List.of("nombre") : List.of("nombre", "direccion");
        auditar("REGISTRAR_SEDE", id, Map.of("campos", campos));
        return buscar(id);
    }

    /**
     * Renombra la sede o cambia su dirección; lo ausente conserva su valor (RF-028). Sin cambios, no
     * escribe ni audita.
     */
    @Transactional
    public SedeDto actualizar(UUID id, ActualizarSedeRequest peticion) {
        SedeDto actual = sedes.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String nombre = peticion.nombre() == null ? actual.nombre() : nombreValido(peticion.nombre());
        String direccion = peticion.direccion() == null ? actual.direccion() : textoOpcional(peticion.direccion());

        List<String> campos = new ArrayList<>();
        if (!nombre.equals(actual.nombre())) {
            validarNombreLibre(nombre, id);
            campos.add("nombre");
        }
        if (!Objects.equals(direccion, actual.direccion())) {
            campos.add("direccion");
        }
        if (campos.isEmpty()) {
            return actual;
        }
        sedes.actualizar(id, nombre, direccion);

        auditar("EDITAR_SEDE", id, Map.of("campos", campos));
        return buscar(id);
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private SedeDto buscar(UUID id) {
        return sedes.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Sede " + id);
    }

    private static String nombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "El nombre de la sede no puede estar vacío.");
        }
        return nombre.strip();
    }

    private void validarNombreLibre(String nombre, UUID excepto) {
        if (sedes.existeNombre(ligaId, nombre, excepto)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe una sede llamada «" + nombre + "» en la liga.");
        }
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private void auditar(String accion, UUID id, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, id.toString(), null, detalle));
    }
}
