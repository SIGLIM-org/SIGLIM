package mx.tecnm.morelia.siglim.disciplina;

import java.util.ArrayList;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de tipos de incidencia de la liga (F-32, RF-128).
 *
 * <ul>
 *   <li><b>Permiso.</b> Todo el catálogo se administra con {@code disciplina.catalogo:EDITAR} sobre
 *       la liga; el ámbito de club no lo alcanza (ADR-0007).</li>
 *   <li><b>Sin borrado.</b> Un tipo que deja de usarse se desactiva con {@code activo: false}; las
 *       incidencias históricas lo siguen referenciando (RN-005).</li>
 *   <li><b>Unicidad.</b> El código es único por liga. El servicio lo valida antes de escribir y la
 *       restricción de la tabla es la última garantía.</li>
 *   <li><b>Auditoría.</b> Cada alta y edición queda en la bitácora con el módulo M-09 (RF-093).</li>
 * </ul>
 *
 * <p>Refs: H-26.1, RF-128, RF-093, RN-005, VAL-015, ADR-0007, ADR-0011
 */
@Service
public class TiposIncidenciaService {

    private static final String EDITAR = "disciplina.catalogo:EDITAR";
    private static final String MODULO = "M-09";
    private static final String ENTIDAD = "tipos_incidencia";

    private final TiposIncidenciaRepository tipos;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    TiposIncidenciaService(TiposIncidenciaRepository tipos, Autorizacion autorizacion,
            AuditoriaService auditoria, SiglimPropiedades propiedades) {
        this.tipos = tipos;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /** Catálogo completo o solo los activos (RF-128). */
    @Transactional(readOnly = true)
    public List<TipoIncidenciaDto> listar(Boolean activo) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        return tipos.listar(ligaId, activo);
    }

    /** Registra un tipo de incidencia activo (RF-128). */
    @Transactional
    public TipoIncidenciaDto crear(CrearTipoIncidenciaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String codigo = codigoValido(peticion.codigo());
        String nombre = nombreValido(peticion.nombre());
        validarCodigoLibre(codigo, null);

        long id = tipos.insertar(ligaId, codigo, nombre, peticion.abreExpediente());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("codigo", codigo);
        detalle.put("abreExpediente", peticion.abreExpediente());
        auditar("REGISTRAR_TIPO_INCIDENCIA", id, detalle);
        return buscar(id);
    }

    /**
     * Edita el código, el nombre o la marca de expediente; lo ausente conserva su valor (RF-128).
     * Desactivar el tipo no lo borra (RN-005).
     */
    @Transactional
    public TipoIncidenciaDto actualizar(long id, ActualizarTipoIncidenciaRequest peticion) {
        TipoIncidenciaDto actual = buscarConCandado(id);
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        String codigo = peticion.codigo() == null ? actual.codigo() : codigoValido(peticion.codigo());
        String nombre = peticion.nombre() == null ? actual.nombre() : nombreValido(peticion.nombre());
        boolean abreExpediente = peticion.abreExpediente() == null
                ? actual.abreExpediente() : peticion.abreExpediente();
        boolean activo = peticion.activo() == null ? actual.activo() : peticion.activo();
        if (!codigo.equals(actual.codigo())) {
            validarCodigoLibre(codigo, id);
        }

        List<String> campos = camposCambiados(actual, codigo, nombre, abreExpediente, activo);
        if (campos.isEmpty()) {
            return actual;
        }
        tipos.actualizar(id, codigo, nombre, abreExpediente, activo);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", campos);
        auditar("EDITAR_TIPO_INCIDENCIA", id, detalle);
        return buscar(id);
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private TipoIncidenciaDto buscar(long id) {
        return tipos.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private TipoIncidenciaDto buscarConCandado(long id) {
        return tipos.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(long id) {
        return new RecursoNoEncontradoException("Tipo de incidencia " + id);
    }

    private static String codigoValido(String codigo) {
        String texto = codigo == null ? "" : codigo.strip();
        if (texto.isEmpty()) {
            throw invalida("El código del tipo de incidencia es obligatorio.");
        }
        if (texto.length() > 40) {
            throw invalida("El código admite hasta 40 caracteres.");
        }
        return texto;
    }

    private static String nombreValido(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty()) {
            throw invalida("El nombre del tipo de incidencia es obligatorio.");
        }
        if (texto.length() > 100) {
            throw invalida("El nombre admite hasta 100 caracteres.");
        }
        return texto;
    }

    private void validarCodigoLibre(String codigo, Long excepto) {
        if (tipos.existeCodigo(ligaId, codigo, excepto)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe un tipo de incidencia con el código «" + codigo + "» en la liga.");
        }
    }

    private static List<String> camposCambiados(TipoIncidenciaDto actual, String codigo, String nombre,
            boolean abreExpediente, boolean activo) {
        List<String> campos = new ArrayList<>();
        if (!codigo.equals(actual.codigo())) {
            campos.add("codigo");
        }
        if (!nombre.equals(actual.nombre())) {
            campos.add("nombre");
        }
        if (abreExpediente != actual.abreExpediente()) {
            campos.add("abreExpediente");
        }
        if (activo != actual.activo()) {
            campos.add("activo");
        }
        return campos;
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }

    private void auditar(String accion, long id, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, Long.toString(id), null, detalle));
    }
}
