package mx.tecnm.morelia.siglim.temporadas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.TemporadaDto.ActivacionDto;
import mx.tecnm.morelia.siglim.temporadas.TemporadaDto.RequisitoActivacionDto;
import mx.tecnm.morelia.siglim.temporadas.TemporadaRepository.DatosTemporada;
import mx.tecnm.morelia.siglim.temporadas.TemporadaRepository.TemporadaActiva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de vida de las temporadas: alta, edición, activación y cierre.
 *
 * <ul>
 *   <li><b>Una sola activa (RN-058).</b> Antes de activar se comprueba que no haya otra temporada
 *       activa en la liga y el error nombra la que lo está. El índice parcial
 *       {@code ux_temporadas_activa} es la última garantía.</li>
 *   <li><b>Lista de verificación (RF-004, RF-005, RF-100).</b> Una temporada en
 *       {@code CONFIGURACION} solo se activa con parámetros deportivos, al menos una categoría y al
 *       menos un grupo. El detalle los incluye para que F-10 deshabilite el botón y explique qué
 *       falta; al rechazar la activación, el problema RFC 9457 lleva los requisitos incumplidos.</li>
 *   <li><b>Histórico protegido (RF-003, RN-005).</b> Una temporada {@code CERRADA} es de solo
 *       lectura: cualquier edición responde {@code 409 RF-003}. No existe borrado.</li>
 *   <li><b>Cierre con motivo (RN-021, RF-093).</b> Solo una temporada {@code ACTIVA} se cierra, con
 *       un motivo de al menos 10 caracteres que se fija en la transacción y queda en la auditoría.</li>
 *   <li><b>Ámbito.</b> La temporada pertenece a la liga, así que todas las operaciones se autorizan
 *       con {@link ObjetivoAutorizacion#deLiga()}; un ámbito de club no la alcanza. El listado filtra
 *       con el alcance del permiso y cuenta con la misma condición (ADR-0007).</li>
 * </ul>
 *
 * <p>Refs: H-04.1, RF-001, RF-003, RF-004, RF-005, RF-093, RF-100, RN-005, RN-021, RN-058, VAL-024,
 * ADR-0007, ADR-0011
 */
@Service
public class TemporadaService {

    private static final String CONSULTAR = "temporadas.temporada:CONSULTAR";
    private static final String EDITAR = "temporadas.temporada:EDITAR";
    private static final String MODULO = "M-02";
    private static final String ENTIDAD = "temporadas";

    /** [PROPUESTA] Convenciones de API §5: mínimo del motivo de una transición (RN-021). */
    private static final int MINIMO_MOTIVO = 10;

    private static final int TAMANO_MAXIMO = 100;

    private final TemporadaRepository temporadas;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final UUID ligaId;

    TemporadaService(TemporadaRepository temporadas, Autorizacion autorizacion, AuditoriaService auditoria,
            MotivoOperacion motivoOperacion, SiglimPropiedades propiedades) {
        this.temporadas = temporadas;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Listado de temporadas de la liga, de la más reciente a la más antigua (RF-001).
     *
     * <p>Un usuario cuyo permiso de consulta solo alcanza un club no ve temporadas, porque no
     * pertenecen a ningún club; su página llega vacía y el total no delata filas (ADR-0007).
     *
     * @param pagina página, desde 0
     * @param tamano filas por página, de 1 a 100
     */
    @Transactional(readOnly = true)
    public Pagina<TemporadaDto> listar(int pagina, int tamano) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalida("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        long desplazamiento = (long) pagina * tamano;
        List<DatosTemporada> filas =
                temporadas.listar(ligaId, tamano, desplazamiento, alcance.todaLaLiga());
        TemporadaActiva activa = filas.stream().anyMatch(fila -> fila.estado() == EstadoTemporada.CONFIGURACION)
                ? temporadas.buscarActiva(ligaId, null).orElse(null)
                : null;
        List<TemporadaDto> contenido = filas.stream().map(fila -> aDto(fila, activa)).toList();
        return Pagina.de(contenido, pagina, tamano,
                temporadas.contar(ligaId, alcance.todaLaLiga()));
    }

    /** Detalle de una temporada con su lista de verificación si está en configuración (RF-001). */
    @Transactional(readOnly = true)
    public TemporadaDto consultar(UUID id) {
        DatosTemporada datos = buscar(id);
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return aDto(datos);
    }

    /** Registra una temporada nueva en {@code CONFIGURACION} (RF-001). */
    @Transactional
    public TemporadaDto crear(CrearTemporadaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String nombre = nombreValido(peticion.nombre());
        validarFechas(peticion.fechaInicio(), peticion.fechaFin());
        validarNombreLibre(nombre, null);

        UUID id = temporadas.insertar(ligaId, nombre, peticion.fechaInicio(), peticion.fechaFin(),
                UsuarioActual.idRequerido());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estado", EstadoTemporada.CONFIGURACION.name());
        auditar("REGISTRAR_TEMPORADA", id, null, detalle);
        return aDto(buscar(id));
    }

    /**
     * Edita nombre o fechas; lo ausente conserva su valor (RF-001).
     *
     * @throws ReglaNegocioException 409 {@code RF-003} si la temporada está cerrada
     */
    @Transactional
    public TemporadaDto actualizar(UUID id, ActualizarTemporadaRequest peticion) {
        DatosTemporada actual = buscarConCandado(id);
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (actual.estado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + actual.nombre() + "» está cerrada: sus datos son de solo lectura.");
        }

        String nombre = peticion.nombre() == null ? actual.nombre() : nombreValido(peticion.nombre());
        LocalDate fechaInicio = peticion.fechaInicio() == null ? actual.fechaInicio() : peticion.fechaInicio();
        LocalDate fechaFin = peticion.fechaFin() == null ? actual.fechaFin() : peticion.fechaFin();
        validarFechas(fechaInicio, fechaFin);
        if (!nombre.equals(actual.nombre())) {
            validarNombreLibre(nombre, id);
        }

        List<String> campos = camposCambiados(actual, nombre, fechaInicio, fechaFin);
        if (campos.isEmpty()) {
            return aDto(actual);
        }
        temporadas.actualizar(id, nombre, fechaInicio, fechaFin);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", campos);
        auditar("EDITAR_TEMPORADA", id, null, detalle);
        return aDto(buscar(id));
    }

    /**
     * Activa una temporada en {@code CONFIGURACION} si cumple la lista de verificación (RF-001,
     * RF-004, RF-005, RF-100, RN-058).
     *
     * <p>El orden del rechazo es el de la lista: primero otra temporada activa, después los
     * parámetros deportivos y al final categorías y grupos.
     */
    @Transactional
    public TemporadaDto activar(UUID id) {
        DatosTemporada datos = buscarConCandado(id);
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (datos.estado() != EstadoTemporada.CONFIGURACION) {
            throw ReglaNegocioException.conflicto("RF-001",
                    "Solo una temporada en configuración puede activarse; «" + datos.nombre() + "» está "
                            + datos.estado().texto() + ".");
        }

        TemporadaActiva otra = temporadas.buscarActiva(ligaId, id).orElse(null);
        ActivacionDto verificacion = evaluarActivacion(datos, otra);
        List<RequisitoActivacionDto> incumplidos = verificacion.requisitos().stream()
                .filter(requisito -> !requisito.cumple())
                .toList();
        if (!incumplidos.isEmpty()) {
            RequisitoActivacionDto primero = incumplidos.getFirst();
            throw ReglaNegocioException.conflicto(primero.codigo(), mensajeActivacion(primero.codigo(), datos, otra))
                    .con("requisitos", incumplidos);
        }

        temporadas.cambiarEstado(id, EstadoTemporada.ACTIVA);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoTemporada.CONFIGURACION.name());
        detalle.put("estado", EstadoTemporada.ACTIVA.name());
        detalle.put("versionParametros", datos.versionParametros());
        auditar("ACTIVAR_TEMPORADA", id, null, detalle);
        return aDto(buscar(id));
    }

    /**
     * Cierra una temporada activa con motivo obligatorio (RF-003, RN-021).
     *
     * @throws ReglaNegocioException 409 {@code RF-001} si no está activa; 400 {@code RN-021} sin
     *                               motivo
     */
    @Transactional
    public TemporadaDto cerrar(UUID id, CerrarTemporadaRequest peticion) {
        DatosTemporada datos = buscarConCandado(id);
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (datos.estado() != EstadoTemporada.ACTIVA) {
            throw ReglaNegocioException.conflicto("RF-001",
                    "Solo una temporada activa puede cerrarse; «" + datos.nombre() + "» está "
                            + datos.estado().texto() + ".");
        }
        String motivo = motivoValido(peticion == null ? null : peticion.motivo());
        motivoOperacion.fijar(motivo);
        temporadas.cambiarEstado(id, EstadoTemporada.CERRADA);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoTemporada.ACTIVA.name());
        detalle.put("estado", EstadoTemporada.CERRADA.name());
        auditar("CERRAR_TEMPORADA", id, motivo, detalle);
        return aDto(buscar(id));
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private DatosTemporada buscar(UUID id) {
        return temporadas.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private DatosTemporada buscarConCandado(UUID id) {
        return temporadas.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Temporada " + id);
    }

    private static String nombreValido(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty()) {
            throw invalida("El nombre de la temporada es obligatorio.");
        }
        return texto;
    }

    private static void validarFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio == null) {
            throw invalida("La fecha de inicio es obligatoria.");
        }
        if (fechaFin != null && fechaFin.isBefore(fechaInicio)) {
            throw invalida("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
    }

    private void validarNombreLibre(String nombre, UUID excepto) {
        if (temporadas.existeNombre(ligaId, nombre, excepto)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe una temporada llamada «" + nombre + "» en la liga.");
        }
    }

    private static String motivoValido(String motivo) {
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < MINIMO_MOTIVO) {
            throw ReglaNegocioException.invalida("RN-021",
                    "Escribe un motivo de al menos " + MINIMO_MOTIVO + " caracteres para cerrar la temporada.");
        }
        return texto;
    }

    private static List<String> camposCambiados(DatosTemporada actual, String nombre, LocalDate fechaInicio,
            LocalDate fechaFin) {
        List<String> campos = new ArrayList<>();
        if (!nombre.equals(actual.nombre())) {
            campos.add("nombre");
        }
        if (!fechaInicio.equals(actual.fechaInicio())) {
            campos.add("fechaInicio");
        }
        if (!Objects.equals(fechaFin, actual.fechaFin())) {
            campos.add("fechaFin");
        }
        return campos;
    }

    // ── Lista de verificación de activación ────────────────────────────────────────────────────

    private TemporadaDto aDto(DatosTemporada datos) {
        TemporadaActiva activa = datos.estado() == EstadoTemporada.CONFIGURACION
                ? temporadas.buscarActiva(ligaId, datos.id()).orElse(null)
                : null;
        return aDto(datos, activa);
    }

    private static TemporadaDto aDto(DatosTemporada datos, TemporadaActiva activa) {
        ActivacionDto activacion = datos.estado() == EstadoTemporada.CONFIGURACION
                ? evaluarActivacion(datos, activa)
                : null;
        return new TemporadaDto(datos.id(), datos.nombre(), datos.fechaInicio(), datos.fechaFin(), datos.estado(),
                datos.creadoEn(), datos.categoriasAsignadas(), datos.gruposCreados(), datos.versionParametros(),
                activacion, ConvocatoriaDto.desde(datos.convocatoria()));
    }

    private static ActivacionDto evaluarActivacion(DatosTemporada datos, TemporadaActiva activa) {
        List<RequisitoActivacionDto> requisitos = new ArrayList<>();
        requisitos.add(activa == null
                ? new RequisitoActivacionDto("RN-058", "No hay otra temporada activa.", true)
                : new RequisitoActivacionDto("RN-058",
                        "La temporada «" + activa.nombre() + "» está activa; ciérrala antes de activar esta.", false));
        requisitos.add(datos.versionParametros() == null
                ? new RequisitoActivacionDto("RF-100", "Faltan los parámetros deportivos de la temporada.", false)
                : new RequisitoActivacionDto("RF-100",
                        "Parámetros deportivos registrados (versión " + datos.versionParametros() + ").", true));
        requisitos.add(datos.categoriasAsignadas() > 0
                ? new RequisitoActivacionDto("RF-004", "Al menos una categoría participa en la temporada.", true)
                : new RequisitoActivacionDto("RF-004", "Falta asignar al menos una categoría a la temporada.", false));
        requisitos.add(datos.gruposCreados() > 0
                ? new RequisitoActivacionDto("RF-005", "Al menos un grupo está creado.", true)
                : new RequisitoActivacionDto("RF-005", "Falta crear al menos un grupo en una categoría.", false));
        return new ActivacionDto(requisitos.stream().allMatch(RequisitoActivacionDto::cumple), requisitos);
    }

    private static String mensajeActivacion(String codigo, DatosTemporada datos, TemporadaActiva activa) {
        return switch (codigo) {
            case "RN-058" -> "La temporada «" + activa.nombre() + "» ya está activa. Ciérrala antes de activar «"
                    + datos.nombre() + "».";
            case "RF-100" -> "La temporada necesita parámetros deportivos antes de activarse.";
            case "RF-004" -> "La temporada necesita al menos una categoría antes de activarse.";
            case "RF-005" -> "La temporada necesita al menos un grupo antes de activarse.";
            default -> "La temporada no cumple los requisitos para activarse.";
        };
    }

    // ── Auditoría (RF-093): solo identificadores y estados, nunca datos personales ──────────────

    private void auditar(String accion, UUID entidadId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(), motivo,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
