package mx.tecnm.morelia.siglim.temporadas;

import java.util.LinkedHashMap;
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
import mx.tecnm.morelia.siglim.temporadas.TemporadaRepository.DatosTemporada;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referencia de la convocatoria de una temporada (RF-002, RN-002): es el documento normativo del
 * que se derivan las reglas de operación y se guarda como referencia, no como archivo.
 *
 * <ul>
 *   <li><b>Una por temporada (RN-002).</b> La tabla tiene {@code UNIQUE (temporada_id)} y el
 *       endpoint es un {@code PUT}: crea la fila si no existe o reemplaza la anterior. La temporada
 *       puede quedar sin convocatoria mientras se configura (CU-001).</li>
 *   <li><b>Temporada cerrada (RF-003, RN-005).</b> Una temporada {@code CERRADA} es de solo
 *       lectura; cambiarle la convocatoria responde {@code 409 RF-003}. Se toma el candado de la
 *       temporada para no competir con el cierre.</li>
 *   <li><b>Permiso.</b> {@code temporadas.convocatoria:EDITAR} con ámbito de liga (ADR-0007); el
 *       Secretario lo tiene en la matriz inicial del Apéndice E.</li>
 *   <li><b>Auditoría (RF-093).</b> Cada asociación deja un evento con la temporada y la versión,
 *       sin copiar el documento.</li>
 * </ul>
 *
 * <p>Refs: B-02.1, RF-002, RF-003, RF-093, RN-002, RN-005, CU-001, UI-003, ADR-0007
 */
@Service
public class ConvocatoriaService {

    private static final String EDITAR = "temporadas.convocatoria:EDITAR";
    private static final String MODULO = "M-02";
    private static final String ENTIDAD = "convocatorias";

    /** Longitud de {@code convocatorias.version}, que es {@code varchar(30)}. */
    private static final int LONGITUD_MAXIMA_VERSION = 30;

    private final ConvocatoriaRepository convocatorias;
    private final TemporadaRepository temporadas;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    ConvocatoriaService(ConvocatoriaRepository convocatorias, TemporadaRepository temporadas,
            Autorizacion autorizacion, AuditoriaService auditoria, SiglimPropiedades propiedades) {
        this.convocatorias = convocatorias;
        this.temporadas = temporadas;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Asocia o reemplaza la convocatoria de una temporada (RF-002).
     *
     * @throws RecursoNoEncontradoException 404 si la temporada no existe o no es de la liga
     * @throws ReglaNegocioException       409 {@code RF-003} si la temporada está cerrada; 400
     *                                     {@code VALIDACION} si la referencia está vacía, la versión
     *                                     pasa de 30 caracteres o el enlace no es http(s)
     */
    @Transactional
    public ConvocatoriaDto reemplazar(UUID temporadaId, ReemplazarConvocatoriaRequest peticion) {
        // El candado serializa con el cierre de la temporada: no se asocia la convocatoria de una
        // temporada que se está cerrando al mismo tiempo.
        DatosTemporada temporada = temporadas.buscarParaActualizar(ligaId, temporadaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Temporada " + temporadaId));
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (temporada.estado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + temporada.nombre() + "» está cerrada: su convocatoria es de solo lectura.");
        }

        String referencia = referenciaValida(peticion == null ? null : peticion.referencia());
        String version = versionValida(peticion == null ? null : peticion.version());
        String documentoUrl = enlaceValido(peticion == null ? null : peticion.documentoUrl());

        UUID id = convocatorias.guardar(temporadaId, referencia, version, documentoUrl);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", temporadaId.toString());
        detalle.put("version", version);
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "ASOCIAR_CONVOCATORIA", ENTIDAD,
                id.toString(), null, detalle));

        return ConvocatoriaDto.desde(convocatorias.buscar(ligaId, temporadaId)
                .orElseThrow(() -> new IllegalStateException(
                        "La convocatoria de la temporada " + temporadaId + " no se pudo releer")));
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private static String referenciaValida(String referencia) {
        String texto = referencia == null ? "" : referencia.strip();
        if (texto.isEmpty()) {
            throw invalida("La referencia de la convocatoria es obligatoria.");
        }
        return texto;
    }

    private static String versionValida(String version) {
        String texto = textoOpcional(version);
        if (texto != null && texto.length() > LONGITUD_MAXIMA_VERSION) {
            throw invalida("La versión de la convocatoria admite hasta " + LONGITUD_MAXIMA_VERSION
                    + " caracteres.");
        }
        return texto;
    }

    /**
     * [PROPUESTA] El enlace se guarda como URL absoluta http o https: es un campo de enlace y así la
     * interfaz no muestra uno roto. No se exige que el documento exista.
     */
    private static String enlaceValido(String documentoUrl) {
        String texto = textoOpcional(documentoUrl);
        if (texto == null) {
            return null;
        }
        if (!texto.matches("(?i)^https?://\\S+$")) {
            throw invalida("El enlace de la convocatoria debe empezar por http:// o https://.");
        }
        return texto;
    }

    private static String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.strip();
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
