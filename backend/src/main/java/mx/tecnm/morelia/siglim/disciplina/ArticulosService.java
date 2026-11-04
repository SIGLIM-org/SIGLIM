package mx.tecnm.morelia.siglim.disciplina;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import mx.tecnm.morelia.siglim.disciplina.ArticulosRepository.DatosArticulo;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de artículos del reglamento con su sanción (F-32, RF-129); se consulta al resolver un
 * expediente (RF-060).
 *
 * <ul>
 *   <li><b>Valores por tipo de sanción.</b> {@code SUSPENSION_PARTIDOS} exige partidos,
 *       {@code PERDIDA_PUNTOS} exige puntos y {@code MULTA} exige monto; {@code PERDIDA_PARTIDO} y
 *       {@code BAJA} no llevan ninguno. Un valor de otro tipo se rechaza con {@code 400} y, al
 *       cambiar de tipo, los valores del anterior se limpian (DEC-30).</li>
 *   <li><b>Valores por defecto.</b> El efecto nace {@code HACIA_ADELANTE}, la aplicación automática
 *       en {@code false} y la entrega de partidos restantes al rival en {@code true}, como en la
 *       tabla y en DEC-31 y DEC-32.</li>
 *   <li><b>Sin borrado.</b> Un artículo que deja de aplicarse se desactiva con {@code activo:
 *       false}; las resoluciones históricas lo siguen referenciando (RN-005).</li>
 *   <li><b>Permiso y auditoría.</b> Se administra con {@code disciplina.catalogo:EDITAR} sobre la
 *       liga y cada alta o edición queda en la bitácora M-09 (RF-093).</li>
 * </ul>
 *
 * <p>Refs: H-26.1, RF-129, RF-060, RF-093, DEC-30, DEC-31, DEC-32, RN-005, VAL-015, ADR-0007,
 * ADR-0011
 */
@Service
public class ArticulosService {

    private static final String EDITAR = "disciplina.catalogo:EDITAR";
    private static final String MODULO = "M-09";
    private static final String ENTIDAD = "articulos";

    private final ArticulosRepository articulos;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    ArticulosService(ArticulosRepository articulos, Autorizacion autorizacion, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.articulos = articulos;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /** Catálogo completo o solo los artículos activos (RF-129, RF-060). */
    @Transactional(readOnly = true)
    public List<ArticuloDto> listar(Boolean activo) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        return articulos.listar(ligaId, activo);
    }

    /** Registra un artículo con su sanción, sujeto, efecto y aplicación automática (RF-129). */
    @Transactional
    public ArticuloDto crear(CrearArticuloRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String numero = numeroValido(peticion.numero());
        String descripcion = descripcionValida(peticion.descripcion());
        DatosArticulo datos = datos(numero, descripcion, peticion.tipoSancion(), peticion.sujeto(),
                peticion.efecto(), peticion.aplicacionAutomatica(), peticion.partidosSuspension(),
                peticion.puntos(), peticion.montoMulta(), peticion.otorgaDefaultRestantes());
        validarNumeroLibre(numero, null);

        UUID id = articulos.insertar(ligaId, datos);

        auditar("REGISTRAR_ARTICULO", id, detalle(datos));
        return buscar(id);
    }

    /**
     * Edita el artículo; lo ausente conserva su valor (RF-129). Si cambia el tipo de sanción, el
     * valor del nuevo tipo debe venir en la petición y los del anterior se limpian (DEC-30).
     * Desactivar el artículo no lo borra (RN-005).
     */
    @Transactional
    public ArticuloDto actualizar(UUID id, ActualizarArticuloRequest peticion) {
        ArticuloDto actual = buscarConCandado(id);
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        String numero = peticion.numero() == null ? actual.numero() : numeroValido(peticion.numero());
        String descripcion = peticion.descripcion() == null
                ? actual.descripcion() : descripcionValida(peticion.descripcion());
        TipoSancion tipo = peticion.tipoSancion() == null ? actual.tipoSancion() : peticion.tipoSancion();
        boolean cambiaTipo = tipo != actual.tipoSancion();
        DatosArticulo datos = datos(numero, descripcion, tipo,
                peticion.sujeto() == null ? actual.sujeto() : peticion.sujeto(),
                peticion.efecto() == null ? actual.efecto() : peticion.efecto(),
                peticion.aplicacionAutomatica() == null
                        ? actual.aplicacionAutomatica() : peticion.aplicacionAutomatica(),
                peticion.partidosSuspension() != null ? peticion.partidosSuspension()
                        : (cambiaTipo ? null : actual.partidosSuspension()),
                peticion.puntos() != null ? peticion.puntos() : (cambiaTipo ? null : actual.puntos()),
                peticion.montoMulta() != null ? peticion.montoMulta() : (cambiaTipo ? null : actual.montoMulta()),
                peticion.otorgaDefaultRestantes() == null
                        ? actual.otorgaDefaultRestantes() : peticion.otorgaDefaultRestantes());
        if (!numero.equals(actual.numero())) {
            validarNumeroLibre(numero, id);
        }
        boolean activo = peticion.activo() == null ? actual.activo() : peticion.activo();

        List<String> campos = camposCambiados(actual, datos, activo);
        if (campos.isEmpty()) {
            return actual;
        }
        articulos.actualizar(id, datos, activo);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", campos);
        auditar("EDITAR_ARTICULO", id, detalle);
        return buscar(id);
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private ArticuloDto buscar(UUID id) {
        return articulos.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private ArticuloDto buscarConCandado(UUID id) {
        return articulos.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Artículo " + id);
    }

    private static String numeroValido(String numero) {
        String texto = numero == null ? "" : numero.strip();
        if (texto.isEmpty()) {
            throw invalida("El número del artículo es obligatorio.");
        }
        if (texto.length() > 20) {
            throw invalida("El número admite hasta 20 caracteres.");
        }
        return texto;
    }

    private static String descripcionValida(String descripcion) {
        String texto = descripcion == null ? "" : descripcion.strip();
        if (texto.isEmpty()) {
            throw invalida("La descripción del artículo es obligatoria.");
        }
        return texto;
    }

    /**
     * Estado completo del artículo, con los valores por defecto y solo el valor numérico que
     * corresponde a su tipo de sanción (DEC-30).
     */
    private static DatosArticulo datos(String numero, String descripcion, TipoSancion tipo, SujetoSancion sujeto,
            EfectoSancion efecto, Boolean aplicacionAutomatica, Integer partidosSuspension, Integer puntos,
            BigDecimal montoMulta, Boolean otorgaDefaultRestantes) {
        validarValores(tipo, partidosSuspension, puntos, montoMulta);
        Integer partidos = tipo == TipoSancion.SUSPENSION_PARTIDOS ? partidosSuspension : null;
        Integer puntosPerdidos = tipo == TipoSancion.PERDIDA_PUNTOS ? puntos : null;
        BigDecimal monto = tipo == TipoSancion.MULTA ? montoMulta : null;
        return new DatosArticulo(numero, descripcion, tipo, sujeto,
                efecto == null ? EfectoSancion.HACIA_ADELANTE : efecto,
                Boolean.TRUE.equals(aplicacionAutomatica),
                partidos, puntosPerdidos, monto,
                otorgaDefaultRestantes == null || otorgaDefaultRestantes);
    }

    private static void validarValores(TipoSancion tipo, Integer partidos, Integer puntos, BigDecimal monto) {
        switch (tipo) {
            case SUSPENSION_PARTIDOS -> {
                if (partidos == null || partidos < 1) {
                    throw invalida("Un artículo de suspensión debe indicar los partidos de suspensión (mayor a cero).");
                }
                if (puntos != null || monto != null) {
                    throw valoresDeOtroTipo(tipo);
                }
            }
            case PERDIDA_PUNTOS -> {
                if (puntos == null || puntos < 1) {
                    throw invalida("Un artículo de pérdida de puntos debe indicar los puntos (mayor a cero).");
                }
                if (partidos != null || monto != null) {
                    throw valoresDeOtroTipo(tipo);
                }
            }
            case MULTA -> {
                if (monto == null || monto.signum() <= 0) {
                    throw invalida("Un artículo de multa debe indicar el monto (mayor a cero).");
                }
                if (partidos != null || puntos != null) {
                    throw valoresDeOtroTipo(tipo);
                }
            }
            case PERDIDA_PARTIDO, BAJA -> {
                if (partidos != null || puntos != null || monto != null) {
                    throw valoresDeOtroTipo(tipo);
                }
            }
        }
    }

    private static ReglaNegocioException valoresDeOtroTipo(TipoSancion tipo) {
        return invalida("El artículo es de tipo " + tipo.name()
                + ": no lleves partidos, puntos ni monto de otro tipo de sanción.");
    }

    private void validarNumeroLibre(String numero, UUID excepto) {
        if (articulos.existeNumero(ligaId, numero, excepto)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe un artículo con el número «" + numero + "» en la liga.");
        }
    }

    private static List<String> camposCambiados(ArticuloDto actual, DatosArticulo datos, boolean activo) {
        List<String> campos = new ArrayList<>();
        if (!datos.numero().equals(actual.numero())) {
            campos.add("numero");
        }
        if (!datos.descripcion().equals(actual.descripcion())) {
            campos.add("descripcion");
        }
        if (datos.tipoSancion() != actual.tipoSancion()) {
            campos.add("tipoSancion");
        }
        if (datos.sujeto() != actual.sujeto()) {
            campos.add("sujeto");
        }
        if (datos.efecto() != actual.efecto()) {
            campos.add("efecto");
        }
        if (datos.aplicacionAutomatica() != actual.aplicacionAutomatica()) {
            campos.add("aplicacionAutomatica");
        }
        if (!Objects.equals(datos.partidosSuspension(), actual.partidosSuspension())) {
            campos.add("partidosSuspension");
        }
        if (!Objects.equals(datos.puntos(), actual.puntos())) {
            campos.add("puntos");
        }
        if (!mismoMonto(datos.montoMulta(), actual.montoMulta())) {
            campos.add("montoMulta");
        }
        if (datos.otorgaDefaultRestantes() != actual.otorgaDefaultRestantes()) {
            campos.add("otorgaDefaultRestantes");
        }
        if (activo != actual.activo()) {
            campos.add("activo");
        }
        return campos;
    }

    private static boolean mismoMonto(BigDecimal uno, BigDecimal otro) {
        return uno == null ? otro == null : otro != null && uno.compareTo(otro) == 0;
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }

    private static Map<String, Object> detalle(DatosArticulo datos) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("numero", datos.numero());
        detalle.put("tipoSancion", datos.tipoSancion().name());
        detalle.put("sujeto", datos.sujeto().name());
        detalle.put("efecto", datos.efecto().name());
        detalle.put("aplicacionAutomatica", datos.aplicacionAutomatica());
        return detalle;
    }

    private void auditar(String accion, UUID id, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, id.toString(), null, detalle));
    }
}
