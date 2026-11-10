package mx.tecnm.morelia.siglim.compartido.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convierte toda excepción que llega a un controlador en una respuesta RFC 9457
 * ({@code application/problem+json}) con la extensión {@code codigo} (convenciones de API §3).
 *
 * <ul>
 *   <li>{@link ReglaNegocioException}: el estado y el código que eligió el servicio, más sus
 *       extensiones ({@code aceptable}, {@code partidosAfectados}…).</li>
 *   <li>{@link RecursoNoEncontradoException}: 404 {@code NO_ENCONTRADO} (VAL-024).</li>
 *   <li>{@link ArchivoRechazadoException}: 400, 413 o 415 de la carga de documentos (§7);
 *       {@link ServicioNoDisponibleException}: 503 {@code SERVICIO_NO_DISPONIBLE}.</li>
 *   <li>Errores de PostgreSQL: {@link TraductorErroresBd} (convenciones §3.2).</li>
 *   <li>Errores de Spring MVC (JSON mal formado, validación, método o ruta inexistentes): mismo
 *       formato, con mensaje en español; la validación de campos agrega {@code errores}.</li>
 *   <li>Cualquier otra excepción: 500 {@code ERROR_INTERNO} sin detalles técnicos y con un
 *       identificador en {@code instance} que también va a la bitácora para encontrar la traza.</li>
 * </ul>
 *
 * <p>El 401 lo responde la cadena de seguridad antes de llegar aquí
 * ({@code PuntoEntradaNoAutenticado}), con el mismo formato.
 *
 * <p>Refs: T-02.4, VAL-015, VAL-024, RNF-011, convenciones de API §3
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    private final TraductorErroresBd traductor;

    public ManejadorGlobalErrores(TraductorErroresBd traductor) {
        this.traductor = traductor;
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ProblemDetail> reglaDeNegocio(ReglaNegocioException error, HttpServletRequest peticion) {
        ResponseEntity<ProblemDetail> respuesta =
                responder(new ErrorTraducido(error.estado(), error.codigo(), error.getMessage()), peticion);
        error.propiedades().forEach(respuesta.getBody()::setProperty);
        return respuesta;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ProblemDetail> noEncontrado(RecursoNoEncontradoException error, HttpServletRequest peticion) {
        log.debug("No encontrado o fuera de ámbito en {} {}: {}", peticion.getMethod(),
                peticion.getRequestURI(), error.getMessage());
        return responder(new ErrorTraducido(HttpStatus.NOT_FOUND, ErroresTecnicos.NO_ENCONTRADO,
                ErroresTecnicos.DETALLE_NO_ENCONTRADO), peticion);
    }

    /** Archivo vacío (400), demasiado grande (413) o de un tipo no permitido (415). */
    @ExceptionHandler(ArchivoRechazadoException.class)
    ResponseEntity<ProblemDetail> archivoRechazado(ArchivoRechazadoException error, HttpServletRequest peticion) {
        return responder(new ErrorTraducido(error.estado(), error.codigo(), error.getMessage()), peticion);
    }

    /** S3 u otro servicio externo no disponible: 503 con mensaje genérico; la causa, a la bitácora. */
    @ExceptionHandler(ServicioNoDisponibleException.class)
    ResponseEntity<ProblemDetail> servicioNoDisponible(ServicioNoDisponibleException error,
            HttpServletRequest peticion) {
        log.warn("Servicio externo no disponible en {} {}: {}", peticion.getMethod(), peticion.getRequestURI(),
                error.getMessage(), error.getCause());
        return responder(new ErrorTraducido(HttpStatus.SERVICE_UNAVAILABLE, ErroresTecnicos.SERVICIO_NO_DISPONIBLE,
                ErroresTecnicos.DETALLE_NO_DISPONIBLE), peticion);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> sinPermiso(AccessDeniedException error, HttpServletRequest peticion) {
        return responder(new ErrorTraducido(HttpStatus.FORBIDDEN, ErroresTecnicos.SIN_PERMISO,
                ErroresTecnicos.DETALLE_SIN_PERMISO), peticion);
    }

    /** Errores de la base de datos, incluidos los de los triggers (convenciones §3.2). */
    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> baseDeDatos(Exception error, HttpServletRequest peticion) {
        ErrorTraducido traducido = traductor.traducir(error, esLectura(peticion));
        if (traducido.esInterno()) {
            return errorInterno(error, peticion);
        }
        if (traducido.estado() == HttpStatus.SERVICE_UNAVAILABLE) {
            log.warn("Base de datos no disponible en {} {}: {}", peticion.getMethod(),
                    peticion.getRequestURI(), error.getMessage());
        }
        return responder(traducido, peticion);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception error, HttpServletRequest peticion) {
        return errorInterno(error, peticion);
    }

    /** Validación de {@code @Valid}: 400 {@code VALIDACION} con la lista {@code errores} por campo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        ProblemDetail problema = ProblemaHttp.crear(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION,
                ErroresTecnicos.tituloPara(HttpStatus.BAD_REQUEST), ErroresTecnicos.DETALLE_CAMPOS, ruta(peticion));
        List<Map<String, String>> errores = error.getBindingResult().getFieldErrors().stream()
                .map(ManejadorGlobalErrores::errorDeCampo)
                .toList();
        problema.setProperty("errores", errores);
        return handleExceptionInternal(error, problema, cabeceras, estado, peticion);
    }

    /**
     * Resto de errores de Spring MVC: se reemplaza su cuerpo en inglés por uno con {@code codigo}
     * y mensaje en español, conservando las cabeceras (por ejemplo {@code Allow} en un 405).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object cuerpo,
            HttpHeaders cabeceras, HttpStatusCode estadoCodigo, WebRequest peticion) {
        if (!(cuerpo instanceof ProblemDetail propio && tieneCodigo(propio))) {
            HttpStatus estado = HttpStatus.resolve(estadoCodigo.value());
            if (estado == null || estado.is5xxServerError()) {
                log.error("Error de Spring MVC con estado {} en {}", estadoCodigo, ruta(peticion), error);
                estado = estado == null ? HttpStatus.INTERNAL_SERVER_ERROR : estado;
            }
            cuerpo = ProblemaHttp.crear(estado, ErroresTecnicos.codigoPara(estado),
                    ErroresTecnicos.tituloPara(estado), ErroresTecnicos.detallePara(estado), ruta(peticion));
        }
        return super.handleExceptionInternal(error, cuerpo, cabeceras, estadoCodigo, peticion);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private static ResponseEntity<ProblemDetail> responder(ErrorTraducido error, HttpServletRequest peticion) {
        ProblemDetail problema = ProblemaHttp.crear(error.estado(), error.codigo(),
                ErroresTecnicos.tituloPara(error.estado()), error.detalle(), peticion.getRequestURI());
        return ResponseEntity.status(error.estado()).body(problema);
    }

    /**
     * 500: la traza completa va a la bitácora con un identificador; la respuesta solo lleva ese
     * identificador en {@code instance} (convenciones §3.1), nunca la causa.
     */
    private static ResponseEntity<ProblemDetail> errorInterno(Exception error, HttpServletRequest peticion) {
        String identificador = UUID.randomUUID().toString();
        log.error("Error interno {} en {} {}", identificador, peticion.getMethod(), peticion.getRequestURI(), error);
        ProblemDetail problema = ProblemaHttp.crear(HttpStatus.INTERNAL_SERVER_ERROR, ErroresTecnicos.ERROR_INTERNO,
                ErroresTecnicos.tituloPara(HttpStatus.INTERNAL_SERVER_ERROR), ErroresTecnicos.DETALLE_INTERNO,
                "urn:siglim:error:" + identificador);
        return ResponseEntity.internalServerError().body(problema);
    }

    /** GET y HEAD solo leen: un 42501 en ellos se responde como 404 para no revelar qué existe. */
    static boolean esLectura(HttpServletRequest peticion) {
        String metodo = peticion.getMethod();
        return HttpMethod.GET.matches(metodo) || HttpMethod.HEAD.matches(metodo);
    }

    private static Map<String, String> errorDeCampo(FieldError campo) {
        String mensaje = campo.getDefaultMessage() == null ? "Valor no válido" : campo.getDefaultMessage();
        return Map.of("campo", campo.getField(), "mensaje", mensaje);
    }

    private static boolean tieneCodigo(ProblemDetail problema) {
        return problema.getProperties() != null && problema.getProperties().containsKey("codigo");
    }

    private static String ruta(WebRequest peticion) {
        return peticion instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "/";
    }
}
