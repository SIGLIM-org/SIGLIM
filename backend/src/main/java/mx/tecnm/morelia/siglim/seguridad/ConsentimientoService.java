package mx.tecnm.morelia.siglim.seguridad;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aviso de privacidad vigente y consentimiento expreso del titular o de su tutor (RF-097, VAL-025).
 *
 * <ul>
 *   <li><b>Aviso vigente (DEC-80).</b> De la liga configurada, la versión del tipo pedido con mayor
 *       {@code vigente_desde} que no sea posterior a hoy en {@code America/Mexico_City}. Basta con
 *       tener sesión: el aviso se muestra antes de pedir el consentimiento.</li>
 *   <li><b>Consentimiento (DEC-81).</b> Con {@code seguridad.consentimiento:REGISTRAR} en cualquier
 *       ámbito, porque la persona puede no estar aún en el club. Debe darse sobre el aviso vigente y
 *       solo con finalidades de ese aviso.</li>
 *   <li><b>Quién otorga (DEC-76).</b> Un menor necesita a su tutor vigente, la haya registrado el club
 *       que sea ({@code 422 VAL-025}); en un adulto lo otorga el propio titular y se guarda
 *       {@code otorgado_por_id = persona_id}. {@code registrado_por} es el perfil de la sesión.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_CONSENTIMIENTO} en M-01, con {@code avisoId} y
 *       las finalidades, sin datos personales.</li>
 * </ul>
 *
 * <p>Revocar un consentimiento queda fuera de H-09.1.
 *
 * <p>Refs: H-09.1, RF-097, RN-055, VAL-025, DEC-40, DEC-67, DEC-74, DEC-76, DEC-80, DEC-81
 */
@Service
public class ConsentimientoService {

    private static final String REGISTRAR = "seguridad.consentimiento:REGISTRAR";
    private static final String MODULO = "M-01";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private final ConsentimientoRepository consentimientos;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final UUID ligaId;

    ConsentimientoService(ConsentimientoRepository consentimientos, Autorizacion autorizacion,
            AuditoriaService auditoria, Clock reloj, SiglimPropiedades propiedades) {
        this.consentimientos = consentimientos;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Aviso vigente del tipo pedido ({@code SIMPLIFICADO} si no se indica).
     *
     * @throws ReglaNegocioException        400 {@code VALIDACION} si el tipo no existe
     * @throws RecursoNoEncontradoException si la liga no tiene un aviso vigente de ese tipo
     */
    @Transactional(readOnly = true)
    public AvisoPrivacidadDto avisoVigente(String tipo) {
        UsuarioActual.idRequerido();
        TipoAvisoPrivacidad tipoAviso = tipoValido(tipo);
        return consentimientos.buscarAvisoVigente(ligaId, tipoAviso, hoy())
                .orElseThrow(() -> new RecursoNoEncontradoException("Aviso de privacidad vigente " + tipoAviso));
    }

    /**
     * Registra el consentimiento de la persona.
     *
     * <p>Orden: permiso ({@code 403 VAL-015}) → datos ({@code 400 VALIDACION}) → la persona existe
     * ({@code 404}) → el aviso existe ({@code 422 REFERENCIA_INVALIDA}) → es el vigente de su tipo en
     * la liga ({@code 422 RF-097}) → las finalidades son del aviso ({@code 422 RF-097}) → menor con
     * tutor vigente ({@code 422 VAL-025}) o adulto que otorga por sí mismo ({@code 422 RF-097}).
     */
    @Transactional
    public ConsentimientoDto registrar(UUID personaId, RegistrarConsentimientoRequest peticion) {
        autorizacion.alcance(REGISTRAR);
        if (peticion == null || peticion.avisoId() == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica la versión del aviso de privacidad que se aceptó.");
        }
        List<String> finalidades = finalidadesValidas(peticion.finalidades());

        LocalDate hoy = hoy();
        boolean esMenor = consentimientos.esMenor(personaId, hoy)
                .orElseThrow(() -> new RecursoNoEncontradoException("Persona " + personaId));
        AvisoPrivacidadDto aviso = consentimientos.buscarAviso(peticion.avisoId())
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "El aviso de privacidad indicado no existe. Recarga la página para ver el aviso vigente."));
        boolean esVigente = consentimientos.buscarAvisoVigente(ligaId, aviso.tipo(), hoy)
                .filter(vigente -> vigente.id().equals(aviso.id()))
                .isPresent();
        if (!esVigente) {
            throw ReglaNegocioException.noProcesable("RF-097",
                    "El consentimiento debe darse sobre el aviso de privacidad vigente. Recarga la página "
                            + "y vuelve a mostrar el aviso.");
        }
        if (!aviso.finalidades().containsAll(finalidades)) {
            throw ReglaNegocioException.noProcesable("RF-097",
                    "Alguna finalidad no está en el aviso de privacidad. Elige solo las finalidades que "
                            + "muestra el aviso.");
        }

        UUID otorgadoPor;
        if (esMenor) {
            if (peticion.otorgadoPorId() == null
                    || !consentimientos.esTutorVigente(personaId, peticion.otorgadoPorId())) {
                throw ReglaNegocioException.noProcesable("VAL-025",
                        "El consentimiento de un menor lo otorga su tutor. Registra primero la tutela e "
                                + "indica al tutor que otorga el consentimiento.");
            }
            otorgadoPor = peticion.otorgadoPorId();
        } else {
            if (peticion.otorgadoPorId() != null && !peticion.otorgadoPorId().equals(personaId)) {
                throw ReglaNegocioException.noProcesable("RF-097",
                        "Una persona mayor de edad otorga su propio consentimiento. No indiques a otra "
                                + "persona como quien lo otorga.");
            }
            otorgadoPor = personaId;
        }

        ConsentimientoDto consentimiento = consentimientos.insertar(personaId, otorgadoPor, aviso.id(),
                finalidades, UsuarioActual.idRequerido());
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("avisoId", aviso.id().toString());
        detalle.put("finalidades", finalidades);
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REGISTRAR_CONSENTIMIENTO", "consentimientos",
                consentimiento.id().toString(), null, detalle));
        return consentimiento;
    }

    /** Sin elementos vacíos; se recortan y se quitan las repetidas, en su orden <b>[PROPUESTA]</b>. */
    private static List<String> finalidadesValidas(List<String> finalidades) {
        if (finalidades == null || finalidades.isEmpty()) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Elige al menos una finalidad del aviso de privacidad.");
        }
        Set<String> limpias = new LinkedHashSet<>();
        for (String finalidad : finalidades) {
            if (finalidad == null || finalidad.isBlank()) {
                throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                        "Las finalidades no pueden ir vacías. Elige las finalidades del aviso de privacidad.");
            }
            limpias.add(finalidad.strip());
        }
        return List.copyOf(limpias);
    }

    private static TipoAvisoPrivacidad tipoValido(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return TipoAvisoPrivacidad.SIMPLIFICADO;
        }
        try {
            return TipoAvisoPrivacidad.valueOf(tipo.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "El tipo de aviso debe ser INTEGRAL o SIMPLIFICADO.");
        }
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
    }
}
